-- Prove the asset-only Superset access path instead of masking it.
--
-- V9 seeded the demo `viewer` identity with a broad `application:aurevia/reports : view`
-- grant, long before Superset assets became individually grantable resources. Once V65/V66
-- gave the same identity a single dashboard grant, that broad application grant made the
-- Reports micro frontend visible for the wrong reason: the demo passed even when asset-level
-- discoverability was broken, and the subject held more authority than the scenario intends.
--
-- V71 made panel discoverability derive from the Superset catalog subtree, so the broad grant
-- is no longer needed for the Reports micro frontend to appear. Revoking it leaves `viewer`
-- with exactly one dashboard, which is the least privilege the demo is meant to demonstrate.
--
-- Administrator and report-designer identities keep their application-level grants: they
-- manage and author the catalog rather than consume a single report.
WITH revoked AS (
  UPDATE authorization_grant g
  SET status='ARCHIVED', version=g.version+1
  FROM app_user u, resource r, action a
  WHERE g.subject_id=u.id AND g.resource_id=r.id AND g.action_id=a.id
    AND g.subject_type='USER' AND g.status='ACTIVE'
    AND u.issuer='http://localhost:8180/realms/aurevia'
    AND u.external_id='viewer'
    AND r.resource_key='application:aurevia/reports'
    AND a.action_key='view'
  RETURNING g.id, g.relation, u.canonical_user_id
)
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant', revoked.id, 'GRANT_DELETE',
  jsonb_build_object(
    'user','user:'||revoked.canonical_user_id,
    'relation',revoked.relation,
    'object','application:aurevia/reports'),
  'GRANT_DELETE:'||revoked.id||':v72'
FROM revoked
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('control-plane','72')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
