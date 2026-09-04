\set ON_ERROR_STOP on
BEGIN;

-- Development-only catalog. This file is applied by docker-compose after
-- Flyway finishes; it is never part of the production migration chain.
INSERT INTO outbound_connection(id,connection_ref,name,kind,base_url,tls_required,active,created_by,updated_by)
VALUES('45000000-0000-0000-0000-000000000001','connection://demo/legacy',
  'Legacy demo token endpoint','LEGACY_TOKEN','http://mock-legacy:8080',false,true,'demo-bootstrap','demo-bootstrap')
ON CONFLICT(connection_ref) DO UPDATE SET name=excluded.name,base_url=excluded.base_url,
  kind=excluded.kind,tls_required=excluded.tls_required,active=excluded.active,
  version=outbound_connection.version+1,updated_at=now(),updated_by='demo-bootstrap'
WHERE (outbound_connection.name,outbound_connection.kind,outbound_connection.base_url,
       outbound_connection.tls_required,outbound_connection.active)
  IS DISTINCT FROM
      (excluded.name,excluded.kind,excluded.base_url,excluded.tls_required,excluded.active);

INSERT INTO outbound_auth_profile(id,code,name,description,auth_mode,token_connection_ref,
  token_endpoint_path,request_format,credential_secret_ref,token_response_pointer,
  expires_in_response_pointer,token_type_response_pointer,authorization_scheme,
  credential_transport,expiry_skew_seconds,connect_timeout_ms,response_timeout_ms,
  max_token_response_size,active,created_by,updated_by)
VALUES('45000000-0000-0000-0000-000000000002','legacy-demo-password','Legacy demo password token',
  'Local-only FORM_URLENCODED token acquisition fixture','LEGACY_SERVICE_TOKEN',
  'connection://demo/legacy','/oauth/token','FORM_URLENCODED','secret://demo/legacy',
  '/access_token','/expires_in','/token_type','Bearer','INTERNAL_LEGACY_HEADER',
  30,3000,10000,1048576,true,'demo-bootstrap','demo-bootstrap')
ON CONFLICT(code) DO UPDATE SET name=excluded.name,description=excluded.description,
  auth_mode=excluded.auth_mode,token_connection_ref=excluded.token_connection_ref,
  token_endpoint_path=excluded.token_endpoint_path,request_format=excluded.request_format,
  credential_secret_ref=excluded.credential_secret_ref,
  token_response_pointer=excluded.token_response_pointer,
  expires_in_response_pointer=excluded.expires_in_response_pointer,
  token_type_response_pointer=excluded.token_type_response_pointer,
  authorization_scheme=excluded.authorization_scheme,
  credential_transport=excluded.credential_transport,
  expiry_skew_seconds=excluded.expiry_skew_seconds,
  connect_timeout_ms=excluded.connect_timeout_ms,
  response_timeout_ms=excluded.response_timeout_ms,
  max_token_response_size=excluded.max_token_response_size,
  active=excluded.active,version=outbound_auth_profile.version+1,updated_at=now(),updated_by='demo-bootstrap'
WHERE (outbound_auth_profile.name,outbound_auth_profile.description,outbound_auth_profile.auth_mode,
       outbound_auth_profile.token_connection_ref,outbound_auth_profile.token_endpoint_path,
       outbound_auth_profile.request_format,outbound_auth_profile.credential_secret_ref,
       outbound_auth_profile.token_response_pointer,outbound_auth_profile.expires_in_response_pointer,
       outbound_auth_profile.token_type_response_pointer,outbound_auth_profile.authorization_scheme,
       outbound_auth_profile.credential_transport,outbound_auth_profile.expiry_skew_seconds,
       outbound_auth_profile.connect_timeout_ms,outbound_auth_profile.response_timeout_ms,
       outbound_auth_profile.max_token_response_size,outbound_auth_profile.active)
  IS DISTINCT FROM
      (excluded.name,excluded.description,excluded.auth_mode,excluded.token_connection_ref,
       excluded.token_endpoint_path,excluded.request_format,excluded.credential_secret_ref,
       excluded.token_response_pointer,excluded.expires_in_response_pointer,
       excluded.token_type_response_pointer,excluded.authorization_scheme,
       excluded.credential_transport,excluded.expiry_skew_seconds,excluded.connect_timeout_ms,
       excluded.response_timeout_ms,excluded.max_token_response_size,excluded.active);

INSERT INTO resource(id,resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source)
SELECT v.id,v.resource_key,'API_RESOURCE',parent.id,v.name_fa,v.name_en,'platform','INTERNAL','SYSTEM'
FROM (VALUES
  ('45000000-0000-0000-0000-000000000007'::uuid,'api:integration.legacy-demo','تست اتصال Legacy','Legacy integration test'),
  ('45000000-0000-0000-0000-000000000008'::uuid,'api:integration.oauth2-demo','تست اتصال OAuth2','OAuth2 integration test')
)v(id,resource_key,name_fa,name_en)
JOIN resource parent ON parent.resource_key='application:aurevia/admin'
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id,name_fa=excluded.name_fa,
  name_en=excluded.name_en,status='ACTIVE',updated_at=now();

INSERT INTO resource_action(resource_id,action_id)
SELECT resource.id,action.id FROM resource CROSS JOIN action
WHERE resource.resource_key IN ('api:integration.legacy-demo','api:integration.oauth2-demo')
  AND action.action_key='view'
ON CONFLICT DO NOTHING;

INSERT INTO service_target(id,code,name,description,gateway_base_url,upstream_base_path,environment,
  health_check_path,connect_timeout_ms,response_timeout_ms,max_response_size,outbound_auth_profile_id,
  active,created_by,updated_by)
SELECT v.id,v.code,v.name,v.description,'http://operation-gateway:80',v.upstream,'OPERATION',
  '/health',3000,10000,1048576,profile.id,true,'demo-bootstrap','demo-bootstrap'
FROM (VALUES
  ('45000000-0000-0000-0000-000000000003'::uuid,'legacy-demo','Legacy demo service',
    'Development fixture using a server-side Legacy token', '/legacy-demo','legacy-demo-password'),
  ('45000000-0000-0000-0000-000000000004'::uuid,'oauth2-demo','OAuth2 demo service',
    'Development fixture forwarding the current Keycloak access token','/oauth-demo','public-iam-forward')
)v(id,code,name,description,upstream,profile_code)
JOIN outbound_auth_profile profile ON profile.code=v.profile_code
ON CONFLICT(code) DO UPDATE SET name=excluded.name,description=excluded.description,
  gateway_base_url=excluded.gateway_base_url,upstream_base_path=excluded.upstream_base_path,
  environment=excluded.environment,health_check_path=excluded.health_check_path,
  connect_timeout_ms=excluded.connect_timeout_ms,response_timeout_ms=excluded.response_timeout_ms,
  max_response_size=excluded.max_response_size,
  outbound_auth_profile_id=excluded.outbound_auth_profile_id,active=excluded.active,
  version=service_target.version+1,updated_at=now(),updated_by='demo-bootstrap'
WHERE (service_target.name,service_target.description,service_target.gateway_base_url,
       service_target.upstream_base_path,service_target.environment,service_target.health_check_path,
       service_target.connect_timeout_ms,service_target.response_timeout_ms,
       service_target.max_response_size,service_target.outbound_auth_profile_id,service_target.active)
  IS DISTINCT FROM
      (excluded.name,excluded.description,excluded.gateway_base_url,excluded.upstream_base_path,
       excluded.environment,excluded.health_check_path,excluded.connect_timeout_ms,
       excluded.response_timeout_ms,excluded.max_response_size,
       excluded.outbound_auth_profile_id,excluded.active);

INSERT INTO proxy_route(id,code,panel_id,service_target_id,path_prefix,normalized_path_prefix,
  service_slug,strip_prefix,rewrite_pattern,rewrite_replacement,priority,allowed_methods,
  preserve_host,retry_enabled,max_retries,active,created_by,updated_by)
SELECT v.id,v.code,panel.id,target.id,v.path_prefix,v.path_prefix||'/',v.service_slug,0,
  '^'||v.path_prefix,v.replacement,100,ARRAY['GET']::varchar(12)[],false,false,0,true,
  'demo-bootstrap','demo-bootstrap'
FROM (VALUES
  ('45000000-0000-0000-0000-000000000005'::uuid,'legacy-demo-route',
    '/api/proxy/legacy-demo','legacy-demo','/legacy-demo'),
  ('45000000-0000-0000-0000-000000000006'::uuid,'oauth2-demo-route',
    '/api/proxy/oauth2-demo','oauth2-demo','/oauth-demo')
)v(id,code,path_prefix,service_slug,replacement)
JOIN panel ON panel.code='ADMIN'
JOIN service_target target ON target.code=v.service_slug
ON CONFLICT(code) DO UPDATE SET panel_id=excluded.panel_id,service_target_id=excluded.service_target_id,
  path_prefix=excluded.path_prefix,normalized_path_prefix=excluded.normalized_path_prefix,
  service_slug=excluded.service_slug,strip_prefix=excluded.strip_prefix,
  rewrite_pattern=excluded.rewrite_pattern,
  rewrite_replacement=excluded.rewrite_replacement,allowed_methods=excluded.allowed_methods,
  priority=excluded.priority,preserve_host=excluded.preserve_host,
  retry_enabled=excluded.retry_enabled,max_retries=excluded.max_retries,
  active=excluded.active,version=proxy_route.version+1,updated_at=now(),updated_by='demo-bootstrap'
WHERE (proxy_route.panel_id,proxy_route.service_target_id,proxy_route.path_prefix,
       proxy_route.normalized_path_prefix,proxy_route.service_slug,proxy_route.strip_prefix,
       proxy_route.rewrite_pattern,proxy_route.rewrite_replacement,proxy_route.priority,
       proxy_route.allowed_methods,proxy_route.preserve_host,proxy_route.retry_enabled,
       proxy_route.max_retries,proxy_route.active)
  IS DISTINCT FROM
      (excluded.panel_id,excluded.service_target_id,excluded.path_prefix,
       excluded.normalized_path_prefix,excluded.service_slug,excluded.strip_prefix,
       excluded.rewrite_pattern,excluded.rewrite_replacement,excluded.priority,
       excluded.allowed_methods,excluded.preserve_host,excluded.retry_enabled,
       excluded.max_retries,excluded.active);

INSERT INTO route_operation(id,proxy_route_id,http_method,path_pattern,normalized_path_pattern,
  resource_id,resource_key,action_id,action_key,authorization_required,max_body_bytes,
  active,created_by,updated_by)
SELECT v.id,route.id,'GET','/ping','/ping',resource.id,resource.resource_key,
  action.id,'view',true,1024,true,'demo-bootstrap','demo-bootstrap'
FROM (VALUES
  ('45000000-0000-0000-0000-00000000000a'::uuid,'legacy-demo-route','api:integration.legacy-demo'),
  ('45000000-0000-0000-0000-00000000000b'::uuid,'oauth2-demo-route','api:integration.oauth2-demo')
)v(id,route_code,resource_key)
JOIN proxy_route route ON route.code=v.route_code
JOIN resource ON resource.resource_key=v.resource_key
JOIN action ON action.action_key='view'
ON CONFLICT(proxy_route_id,http_method,path_pattern) DO UPDATE SET
  normalized_path_pattern=excluded.normalized_path_pattern,resource_id=excluded.resource_id,
  resource_key=excluded.resource_key,action_id=excluded.action_id,action_key=excluded.action_key,
  authorization_required=excluded.authorization_required,max_body_bytes=excluded.max_body_bytes,
  active=excluded.active,version=route_operation.version+1,
  updated_at=now(),updated_by='demo-bootstrap'
WHERE (route_operation.normalized_path_pattern,route_operation.resource_id,
       route_operation.resource_key,route_operation.action_id,route_operation.action_key,
       route_operation.authorization_required,route_operation.max_body_bytes,route_operation.active)
  IS DISTINCT FROM
      (excluded.normalized_path_pattern,excluded.resource_id,excluded.resource_key,
       excluded.action_id,excluded.action_key,excluded.authorization_required,
       excluded.max_body_bytes,excluded.active);

INSERT INTO authorization_grant(id,subject_type,subject_id,resource_id,action_id,relation)
SELECT gen_random_uuid(),'USER',app_user.id,resource.id,action.id,'viewer'
FROM app_user CROSS JOIN resource CROSS JOIN action
WHERE app_user.id=(
    SELECT candidate.id FROM app_user candidate
    WHERE candidate.username='administrator' AND candidate.status='ACTIVE'
    ORDER BY CASE WHEN candidate.external_id=candidate.username THEN 1 ELSE 0 END,
      candidate.updated_at DESC
    LIMIT 1
  )
  AND resource.resource_key IN ('api:integration.legacy-demo','api:integration.oauth2-demo')
  AND action.action_key='view'
ON CONFLICT(subject_type,subject_id,resource_id,action_id) WHERE status='ACTIVE' DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',child.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:aurevia/admin','relation','parent',
  'object','resource:'||replace(child.resource_key,':','/')),
  'demo:resource-parent:'||child.id
FROM resource child
WHERE child.resource_key IN ('api:integration.legacy-demo','api:integration.oauth2-demo')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||app_user.subject_key,'relation','viewer',
  'object','resource:'||replace(resource.resource_key,':','/')),
  'demo:administrator-grant:'||g.id
FROM authorization_grant g
JOIN app_user ON g.subject_type='USER' AND app_user.id=g.subject_id
JOIN resource ON resource.id=g.resource_id
JOIN action ON action.id=g.action_id
WHERE app_user.id=(
    SELECT candidate.id FROM app_user candidate
    WHERE candidate.username='administrator' AND candidate.status='ACTIVE'
    ORDER BY CASE WHEN candidate.external_id=candidate.username THEN 1 ELSE 0 END,
      candidate.updated_at DESC
    LIMIT 1
  ) AND action.action_key='view'
  AND resource.resource_key IN ('api:integration.legacy-demo','api:integration.oauth2-demo')
  AND g.status='ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;

-- A reused Keycloak volume can retain a generated immutable `sub` even when
-- the tracked realm fixture now declares deterministic development ids. Mirror
-- only the local bootstrap administrator's grants to that observed identity so
-- the demo remains representative without trusting username at runtime.
WITH bootstrap_user AS (
  SELECT id FROM app_user
  WHERE issuer='http://localhost:8180/realms/aurevia'
    AND username='administrator' AND external_id='administrator'
), runtime_user AS (
  SELECT id FROM app_user
  WHERE issuer='http://localhost:8180/realms/aurevia'
    AND username='administrator' AND external_id<>'administrator' AND status='ACTIVE'
  ORDER BY updated_at DESC LIMIT 1
)
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation,
  condition_id,expires_at,status)
SELECT 'USER',runtime_user.id,source.resource_id,source.action_id,source.relation,
  source.condition_id,source.expires_at,source.status
FROM authorization_grant source CROSS JOIN bootstrap_user CROSS JOIN runtime_user
WHERE source.subject_type='USER' AND source.subject_id=bootstrap_user.id
  AND source.status='ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM authorization_grant existing
    WHERE existing.subject_type='USER' AND existing.subject_id=runtime_user.id
      AND existing.resource_id=source.resource_id AND existing.action_id=source.action_id
      AND existing.status='ACTIVE'
  );

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||runtime_user.subject_key,'relation',g.relation,
  'object',case resource.type
    when 'APPLICATION' then 'application:'||regexp_replace(resource.resource_key,'^application:','')
    when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(resource.resource_key,'^external_resource:',''),':','/')
    else 'resource:'||replace(resource.resource_key,':','/') end),
  'demo:runtime-administrator-grant:'||g.id
FROM app_user runtime_user
JOIN authorization_grant g ON g.subject_type='USER'
  AND g.subject_id=runtime_user.id AND g.status='ACTIVE'
JOIN resource ON resource.id=g.resource_id
WHERE runtime_user.issuer='http://localhost:8180/realms/aurevia'
  AND runtime_user.username='administrator'
  AND runtime_user.external_id<>'administrator'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('development-integration-fixture','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
COMMIT;
