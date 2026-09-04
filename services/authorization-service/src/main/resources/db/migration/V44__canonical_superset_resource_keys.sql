UPDATE resource
SET resource_key='external_resource:'||replace(substring(resource_key from 19),':','/'),
    version=version+1,updated_at=now()
WHERE resource_key LIKE 'external_resource:superset:%'
   OR resource_key LIKE 'external_resource:superset-public:%';

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',child.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','external_resource:superset-public','relation','parent',
  'object','external_resource:'||replace(regexp_replace(child.resource_key,'^external_resource:',''),':','/')),
  'v44:superset-parent:'||child.id
FROM resource child JOIN resource parent ON parent.id=child.parent_id
WHERE child.resource_key LIKE 'external_resource:superset/%'
  AND parent.resource_key='external_resource:superset-public'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('superset-resource-contract','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
