-- Complete the existing route registry without introducing a parallel model.
ALTER TABLE service_target
  RENAME COLUMN target_key TO code;
ALTER TABLE service_target
  RENAME COLUMN base_url TO gateway_base_url;
ALTER TABLE service_target
  RENAME COLUMN mtls_secret_ref TO tls_profile_ref;
ALTER TABLE service_target
  RENAME COLUMN max_response_bytes TO max_response_size;
ALTER TABLE service_target
  ADD COLUMN name varchar(255),
  ADD COLUMN description varchar(1000),
  ADD COLUMN upstream_base_path varchar(500) NOT NULL DEFAULT '/',
  ADD COLUMN environment varchar(80) NOT NULL DEFAULT 'OPERATION',
  ADD COLUMN secret_ref varchar(255),
  ADD COLUMN health_check_path varchar(500) NOT NULL DEFAULT '/health',
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN created_by varchar(255) NOT NULL DEFAULT 'migration',
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN updated_by varchar(255) NOT NULL DEFAULT 'migration';
UPDATE service_target SET name=code WHERE name IS NULL;
ALTER TABLE service_target ALTER COLUMN name SET NOT NULL;

ALTER TABLE proxy_route
  RENAME COLUMN route_key TO code;
ALTER TABLE proxy_route
  RENAME COLUMN target_id TO service_target_id;
ALTER TABLE proxy_route
  ADD COLUMN panel_id uuid REFERENCES panel(id) ON DELETE RESTRICT,
  ADD COLUMN normalized_path_prefix varchar(500),
  ADD COLUMN rewrite_pattern varchar(500),
  ADD COLUMN rewrite_replacement varchar(500),
  ADD COLUMN priority integer NOT NULL DEFAULT 0,
  ADD COLUMN allowed_methods varchar(12)[] NOT NULL DEFAULT ARRAY['GET','HEAD','OPTIONS','POST','PUT','PATCH','DELETE'],
  ADD COLUMN preserve_host boolean NOT NULL DEFAULT false,
  ADD COLUMN request_header_policy jsonb NOT NULL DEFAULT '{}',
  ADD COLUMN response_header_policy jsonb NOT NULL DEFAULT '{}',
  ADD COLUMN retry_enabled boolean NOT NULL DEFAULT false,
  ADD COLUMN max_retries integer NOT NULL DEFAULT 0,
  ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN created_by varchar(255) NOT NULL DEFAULT 'migration',
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN updated_by varchar(255) NOT NULL DEFAULT 'migration';
UPDATE proxy_route pr SET
  panel_id=p.id,
  normalized_path_prefix=CASE WHEN right(pr.path_prefix,1)='/' THEN pr.path_prefix ELSE pr.path_prefix||'/' END
FROM panel p WHERE pr.path_prefix=p.route_base_path OR pr.path_prefix='/'||p.slug
  OR pr.path_prefix='/'||p.slug||'-micro';
ALTER TABLE proxy_route ALTER COLUMN panel_id SET NOT NULL;
ALTER TABLE proxy_route ALTER COLUMN normalized_path_prefix SET NOT NULL;
ALTER TABLE proxy_route ADD CONSTRAINT proxy_route_retry_range CHECK(max_retries BETWEEN 0 AND 3);
ALTER TABLE proxy_route ADD CONSTRAINT proxy_route_prefix_panel CHECK(normalized_path_prefix LIKE '/%/');
CREATE INDEX proxy_route_resolution_idx ON proxy_route(active,normalized_path_prefix,priority DESC);

ALTER TABLE route_operation
  RENAME COLUMN route_id TO proxy_route_id;
ALTER TABLE route_operation
  RENAME COLUMN relative_pattern TO path_pattern;
ALTER TABLE route_operation
  ADD COLUMN normalized_path_pattern varchar(500),
  ADD COLUMN resource_key varchar(500),
  ADD COLUMN action_key varchar(100),
  ADD COLUMN authorization_required boolean NOT NULL DEFAULT true,
  ADD COLUMN data_policy_key varchar(160),
  ADD COLUMN active boolean NOT NULL DEFAULT true,
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN created_by varchar(255) NOT NULL DEFAULT 'migration',
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN updated_by varchar(255) NOT NULL DEFAULT 'migration';
UPDATE route_operation ro SET normalized_path_pattern=ro.path_pattern,
  resource_key=r.resource_key,action_key=a.action_key
FROM resource r, action a WHERE r.id=ro.resource_id AND a.id=ro.action_id;
ALTER TABLE route_operation ALTER COLUMN normalized_path_pattern SET NOT NULL;
ALTER TABLE route_operation ALTER COLUMN resource_key SET NOT NULL;
ALTER TABLE route_operation ALTER COLUMN action_key SET NOT NULL;
ALTER TABLE route_operation ADD CONSTRAINT route_operation_method_upper CHECK(http_method=upper(http_method));
CREATE INDEX route_operation_resolution_idx ON route_operation(proxy_route_id,http_method,active);

UPDATE proxy_route SET strip_prefix=1,
  rewrite_pattern='^/api/v1',rewrite_replacement='/hr-service/api/v1'
WHERE code='hr-api';
UPDATE proxy_route SET strip_prefix=1,
  rewrite_pattern='^/api/v1',rewrite_replacement='/finance-service/api/v1'
WHERE code='finance-api';

INSERT INTO action(action_key,name_fa,name_en) VALUES
  ('activate','فعال‌سازی','Activate'),('test','آزمایش','Test')
ON CONFLICT(action_key) DO NOTHING;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain)
SELECT v.resource_key,'API_RESOURCE',p.id,v.name_fa,v.name_en,'platform'
FROM (VALUES
  ('proxy.target','مقصدهای پراکسی','Proxy targets'),
  ('proxy.route','مسیرهای پراکسی','Proxy routes'),
  ('proxy.operation','عملیات مسیر','Route operations')
) v(resource_key,name_fa,name_en)
JOIN resource p ON p.resource_key='application:aurevia/admin'
ON CONFLICT(resource_key) DO NOTHING;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key IN ('proxy.target','proxy.route','proxy.operation')
  AND a.action_key IN ('list','view','create','update','delete','activate','test','admin')
ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:'||regexp_replace(p.resource_key,'^application:',''),
  'relation','parent','object','resource:'||c.resource_key),
  'proxy-resource-parent:'||c.id||':'||p.id
FROM resource c JOIN resource p ON p.id=c.parent_id
WHERE c.resource_key IN ('proxy.target','proxy.route','proxy.operation')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','22')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
