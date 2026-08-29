INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user',case when p.type='APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','') when p.type='EXTERNAL_RESOURCE' then 'external_resource:'||regexp_replace(p.resource_key,'^external_resource:','') else 'resource:'||p.resource_key end,
  'relation','parent',
  'object',case when c.type='APPLICATION' then 'application:'||regexp_replace(c.resource_key,'^application:','') when c.type='EXTERNAL_RESOURCE' then 'external_resource:'||regexp_replace(c.resource_key,'^external_resource:','') else 'resource:'||c.resource_key end),
  'bootstrap-resource-parent:'||c.id||':'||p.id
FROM resource c JOIN resource p ON p.id=c.parent_id
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','13')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
