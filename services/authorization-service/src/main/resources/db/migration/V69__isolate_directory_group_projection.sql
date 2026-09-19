-- Directory group UUIDs are scoped by (issuer, external_id). Never project an
-- external claim directly: two issuers, or an OU access group, can use that value.
-- Keep the existing OU namespace and migrate directory groups to directory/<UUID>.
-- Expected tuples are also used to protect legitimate OU tuples when an old
-- directory external ID collided with an access-group code.
CREATE TEMP TABLE directory_group_projection_expected ON COMMIT DROP AS
SELECT 'group-membership'::text aggregate_type,u.id aggregate_id,
  'GROUP_MEMBERSHIP_WRITE'::text event_type,
  jsonb_build_object('user','user:'||u.canonical_user_id,'relation','member',
    'object','group:directory/'||g.id) payload
FROM user_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN directory_group g ON g.id=m.group_id WHERE g.status='ACTIVE'
UNION ALL
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user',CASE g.subject_type WHEN 'GROUP' THEN 'group:directory/'||dg.id||'#member'
    ELSE 'group:'||lower(ag.code)||'#member' END,
  'relation',g.relation,'object',CASE r.type
    WHEN 'APPLICATION' THEN 'application:'||regexp_replace(r.resource_key,'^application:','')
    WHEN 'EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    ELSE 'resource:'||replace(r.resource_key,':','/') END)
FROM authorization_grant g JOIN resource r ON r.id=g.resource_id
LEFT JOIN directory_group dg ON g.subject_type='GROUP' AND dg.id=g.subject_id
LEFT JOIN access_group ag ON g.subject_type='ACCESS_GROUP' AND ag.id=g.subject_id
WHERE g.status='ACTIVE' AND (g.expires_at IS NULL OR g.expires_at>now())
  AND ((g.subject_type='GROUP' AND dg.status='ACTIVE')
    OR (g.subject_type='ACCESS_GROUP' AND ag.active))
UNION ALL
SELECT 'role-assignment',r.id,'ROLE_ASSIGNMENT_WRITE',jsonb_build_object(
  'user','group:directory/'||g.id||'#member','relation','assignee','object','role:'||r.role_key)
FROM group_role_assignment a JOIN directory_group g ON g.id=a.group_id
JOIN application_role r ON r.id=a.role_id
WHERE r.status='ACTIVE' AND g.status='ACTIVE' AND (a.expires_at IS NULL OR a.expires_at>now())
UNION ALL
SELECT 'role-assignment',r.id,'ROLE_ASSIGNMENT_WRITE',jsonb_build_object(
  'user','group:'||lower(g.code)||'#member','relation','assignee','object','role:'||r.role_key)
FROM access_group_role_assignment a JOIN access_group g ON g.id=a.access_group_id
JOIN application_role r ON r.id=a.role_id
WHERE r.status='ACTIVE' AND g.active AND (a.expires_at IS NULL OR a.expires_at>now())
UNION ALL
SELECT DISTINCT 'access-group-membership',g.id,'ACCESS_GROUP_MEMBERSHIP_WRITE',
  jsonb_build_object('user','user:'||u.canonical_user_id,'relation','member',
    'object','group:'||lower(g.code))
FROM effective_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN access_group g ON g.id=m.access_group_id WHERE m.active AND g.active
UNION ALL
SELECT 'application-group-grant',g.id,'APPLICATION_GROUP_GRANT_WRITE',
  jsonb_build_object('user','group:'||lower(a.code)||'#member','relation','viewer',
    'object','application:aurevia/'||p.slug)
FROM application_group_grant g JOIN access_group a ON a.id=g.access_group_id
JOIN panel p ON p.id=g.application_id
WHERE g.revoked_at IS NULL AND a.active AND p.active;

-- Include event history: deleted role assignments and old membership removals
-- may no longer exist in source tables but their projected tuples can survive.
CREATE TEMP TABLE directory_group_projection_legacy ON COMMIT DROP AS
SELECT DISTINCT jsonb_build_object('user',e.payload->>'user',
  'relation',e.payload->>'relation','object',e.payload->>'object') payload
FROM outbox_event e
WHERE EXISTS (SELECT 1 FROM directory_group g
  WHERE e.payload->>'user'='group:'||g.external_id||'#member'
     OR e.payload->>'object'='group:'||g.external_id)
  AND e.payload->>'user' IS NOT NULL AND e.payload->>'relation' IS NOT NULL
  AND e.payload->>'object' IS NOT NULL
UNION
SELECT jsonb_build_object('user','user:'||u.canonical_user_id,'relation','member',
  'object','group:'||g.external_id)
FROM user_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN directory_group g ON g.id=m.group_id
UNION
SELECT jsonb_build_object('user','group:'||dg.external_id||'#member',
  'relation',g.relation,'object',CASE r.type
    WHEN 'APPLICATION' THEN 'application:'||regexp_replace(r.resource_key,'^application:','')
    WHEN 'EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    ELSE 'resource:'||replace(r.resource_key,':','/') END)
FROM authorization_grant g JOIN directory_group dg ON g.subject_type='GROUP' AND dg.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
UNION
SELECT jsonb_build_object('user','group:'||g.external_id||'#member',
  'relation','assignee','object','role:'||r.role_key)
FROM group_role_assignment a JOIN directory_group g ON g.id=a.group_id
JOIN application_role r ON r.id=a.role_id;

-- Do not let historical retries recreate the old namespace after migration.
-- Current relationships are re-enqueued below using their normal aggregate so
-- later runtime revocations remain ordered after the replacement writes.
UPDATE outbox_event e SET processed_at=now(),dead_lettered_at=NULL,
  claimed_at=NULL,claim_owner=NULL,last_error='Superseded by directory group identity migration'
WHERE e.processed_at IS NULL AND EXISTS (SELECT 1 FROM directory_group g
  WHERE e.payload->>'user'='group:'||g.external_id||'#member'
     OR e.payload->>'object'='group:'||g.external_id);

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'directory-group-migration',md5(l.payload::text)::uuid,'GRANT_DELETE',l.payload,
  'DIRECTORY_GROUP_V2:DELETE:'||md5(l.payload::text)
FROM directory_group_projection_legacy l
WHERE NOT EXISTS (SELECT 1 FROM directory_group_projection_expected e WHERE e.payload=l.payload)
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT aggregate_type,aggregate_id,event_type,payload,
  'DIRECTORY_GROUP_V2:WRITE:'||aggregate_id||':'||md5(payload::text)
FROM directory_group_projection_expected ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('directory-group-identity','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
