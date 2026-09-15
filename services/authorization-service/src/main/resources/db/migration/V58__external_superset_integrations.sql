-- Superset is registry-owned external infrastructure. Core must never address it by
-- a Compose service name or include its availability in Core readiness.
ALTER TABLE superset_instance
  ADD COLUMN proxy_mode boolean NOT NULL DEFAULT true,
  ADD COLUMN health_status varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  ADD COLUMN health_checked_at timestamptz,
  ADD COLUMN metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE superset_instance ADD CONSTRAINT superset_instance_health_status_check
  CHECK (health_status IN ('UNKNOWN','ACTIVE','UNREACHABLE','DISABLED'));

UPDATE superset_instance
SET base_url=CASE code
      WHEN 'public-default' THEN 'http://localhost:8089'
      WHEN 'operation-default' THEN 'http://localhost:8088'
      ELSE base_url END,
    health_status=CASE WHEN active THEN 'UNKNOWN' ELSE 'DISABLED' END,
    updated_at=now(),version=version+1
WHERE (code='public-default' AND base_url='http://public-superset:8088')
   OR (code='operation-default' AND base_url='http://operation-superset:8088');

-- Every integration is an OpenFGA application resource. An instance-specific
-- grant can therefore open the integration without granting the Reports MFE or
-- another Superset deployment.
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,
  classification,status,source,metadata)
SELECT 'application:'||instance.code,'APPLICATION',root.id,instance.name,instance.name,
  'reports','EXTERNAL',CASE WHEN instance.active THEN 'ACTIVE'::lifecycle_status
    ELSE 'INACTIVE'::lifecycle_status END,'ADMIN',
  jsonb_build_object('provider','SUPERSET','instanceCode',instance.code)
FROM superset_instance instance
JOIN resource root ON root.resource_key='application:aurevia'
ON CONFLICT(resource_key) DO UPDATE SET
  name_fa=excluded.name_fa,name_en=excluded.name_en,parent_id=excluded.parent_id,
  status=excluded.status,metadata=excluded.metadata,version=resource.version+1,updated_at=now();

INSERT INTO resource_action(resource_id,action_id)
SELECT resource.id,action.id
FROM resource
JOIN superset_instance instance ON resource.resource_key='application:'||instance.code
JOIN action ON action.action_key IN ('view','admin')
ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',integration.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:aurevia','relation','parent',
  'object','application:'||instance.code),
  'SUPERSET_INTEGRATION_PARENT_V58:'||integration.id
FROM superset_instance instance
JOIN resource integration ON integration.resource_key='application:'||instance.code
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','58'),('superset-registry','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
