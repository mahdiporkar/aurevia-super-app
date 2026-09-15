-- V4 and V7 predate the transactional OpenFGA outbox. Project their active
-- bootstrap grants through the same reconciler used by all new grants.
INSERT INTO outbox_event(
  aggregate_type, aggregate_id, event_type, payload, idempotency_key
)
SELECT
  'grant', g.id, 'GRANT_WRITE',
  jsonb_build_object(
    'user', 'user:' || u.external_id,
    'relation', CASE a.action_key
      WHEN 'admin' THEN 'manager'
      WHEN 'view' THEN 'viewer'
      ELSE g.relation
    END,
    'object', CASE r.type
      WHEN 'APPLICATION' THEN
        'application:' || regexp_replace(r.resource_key, '^application:', '')
      WHEN 'EXTERNAL_RESOURCE' THEN
        'external_resource:' || regexp_replace(r.resource_key, '^external_resource:', '')
      ELSE 'resource:' || r.resource_key
    END
  ),
  'legacy-grant-projection:' || g.id
FROM authorization_grant g
JOIN app_user u
  ON g.subject_type = 'USER' AND u.id = g.subject_id
JOIN resource r ON r.id = g.resource_id
JOIN action a ON a.id = g.action_id
WHERE g.status = 'ACTIVE'
  AND (
    (r.resource_key = 'application:aurevia' AND a.action_key = 'admin')
    OR (r.resource_key = 'external_resource:superset-public:dashboard:welcome-dashboard'
        AND a.action_key = 'view')
  )
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component, version) VALUES ('control-plane', '14')
ON CONFLICT(component) DO UPDATE
SET version = excluded.version, updated_at = now();
