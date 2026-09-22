-- V3/V7/V9/V20 installed an implicitly privileged demo identity whose Keycloak subject was
-- literally 'administrator' (the local realm fixture imported users with id = username).
-- Historical migrations stay immutable, but that identity must no longer be an administrator
-- by default: a fresh installation now provisions its first administrator once, from the
-- explicitly configured stable Keycloak user id (AUREVIA_BOOTSTRAP_ADMIN_SUB).
-- UUID identities and administrator-created grants for other users are untouched.

-- Cancel not-yet-projected writes for this exact fixture identity before appending deletes,
-- so the outbox never re-applies a tuple that is archived in the same migration.
UPDATE outbox_event e SET processed_at=now(),claimed_at=NULL,claim_owner=NULL,
  dead_lettered_at=NULL,last_error=NULL
FROM app_user u
WHERE u.issuer='http://localhost:8180/realms/aurevia' AND u.external_id='administrator'
  AND e.processed_at IS NULL AND e.event_type='GRANT_WRITE'
  AND e.payload->>'user' IN ('user:'||u.external_id,'user:'||u.subject_key,
    'user:'||u.canonical_user_id);

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_DELETE',jsonb_build_object(
  'user','user:'||u.canonical_user_id,'relation',g.relation,
  'object',CASE WHEN r.type='APPLICATION' THEN 'application:'||regexp_replace(r.resource_key,'^application:','')
    WHEN r.type='EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(
      regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    ELSE 'resource:'||replace(r.resource_key,':','/') END),
  'retire-demo-administrator-v78:'||g.id
FROM authorization_grant g
JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE u.issuer='http://localhost:8180/realms/aurevia' AND u.external_id='administrator'
  AND g.status='ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;

UPDATE authorization_grant g SET status='ARCHIVED',version=g.version+1
FROM app_user u
WHERE g.subject_type='USER' AND g.subject_id=u.id AND g.status='ACTIVE'
  AND u.issuer='http://localhost:8180/realms/aurevia' AND u.external_id='administrator';

-- schema_version is the existing persistent initialization-metadata store. The
-- 'first-administrator' marker row is written only by the runtime bootstrap transaction,
-- together with the canonical identity, the grant, its audit entry and its outbox event.
INSERT INTO schema_version(component,version) VALUES('control-plane','78')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
