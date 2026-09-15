-- Root administrators manage the platform through application:aurevia. Keep the
-- deployment applications in the same persisted hierarchy so OpenFGA inheritance
-- is reproducible by reconciliation instead of relying on ad-hoc tuples.
UPDATE resource child
SET parent_id=root.id
FROM resource root
WHERE root.resource_key='application:aurevia'
  AND child.resource_key IN (
    'application:aurevia/admin',
    'application:aurevia/hr',
    'application:aurevia/finance',
    'application:aurevia/reports'
  )
  AND child.parent_id IS DISTINCT FROM root.id;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',child.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:aurevia',
  'relation','parent',
  'object','application:'||regexp_replace(child.resource_key,'^application:','')),
  'application-root-parent-v49:'||child.id||':'||root.id
FROM resource child
JOIN resource root ON root.id=child.parent_id
WHERE root.resource_key='application:aurevia'
  AND child.resource_key IN (
    'application:aurevia/admin',
    'application:aurevia/hr',
    'application:aurevia/finance',
    'application:aurevia/reports'
  )
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','49')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
