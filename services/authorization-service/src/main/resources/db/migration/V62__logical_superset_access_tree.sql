-- A PUBLIC/OPERATION pair is one logical Superset integration. Physical routing
-- instances remain registry records and must not appear as separate grant roots.
UPDATE resource
SET name_fa='سوپرست',name_en='Superset',status='ACTIVE',
    metadata=metadata||jsonb_build_object('provider','SUPERSET','logicalIntegration',true),
    version=version+1,updated_at=now()
WHERE resource_key='external_resource:superset-public';

INSERT INTO resource_action(resource_id,action_id)
SELECT resource.id,action.id
FROM resource JOIN action ON action.action_key IN ('view','admin')
WHERE resource.resource_key='external_resource:superset-public'
ON CONFLICT DO NOTHING;

-- V58 exposed deployment topology as application resources. Keep the records for
-- audit/grant compatibility, but remove them from the active access tree.
UPDATE resource
SET status='DEPRECATED',version=version+1,updated_at=now(),
    metadata=metadata||jsonb_build_object('hiddenReason','PHYSICAL_SUPERSET_INSTANCE')
WHERE type='APPLICATION' AND metadata->>'provider'='SUPERSET';

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',root.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:aurevia','relation','parent',
  'object','external_resource:superset-public'),
  'SUPERSET_LOGICAL_PARENT_V62:'||root.id
FROM resource root WHERE root.resource_key='external_resource:superset-public'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','62'),('superset-registry','3'),('superset-resource-contract','3')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
