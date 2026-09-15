-- The legacy admin application was independently viewable but was never attached to the
-- Aurevia root in OpenFGA. Attach proxy administration directly to the root, preserving
-- the existing root-manager grant without introducing role-name shortcuts.
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',r.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:aurevia',
  'relation','parent',
  'object','resource:'||r.resource_key),
  'proxy-resource-root-parent:'||r.id
FROM resource r
WHERE r.resource_key IN ('proxy.target','proxy.route','proxy.operation')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','23')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
