CREATE TABLE outbound_auth_profile (
 id uuid PRIMARY KEY, code varchar(160) NOT NULL UNIQUE, name varchar(255) NOT NULL, description varchar(1000),
 auth_mode varchar(40) NOT NULL CHECK(auth_mode IN ('FORWARD_USER_TOKEN','LEGACY_SERVICE_TOKEN')),
 token_connection_ref varchar(255), token_endpoint_path varchar(500),
 request_format varchar(40) NOT NULL CHECK(request_format IN ('FORM_URLENCODED','JSON','HTTP_BASIC','OAUTH_CLIENT_CREDENTIALS','CUSTOM_LEGACY_ADAPTER')),
 credential_secret_ref varchar(500), client_id_secret_ref varchar(500), client_secret_ref varchar(500),
 scope varchar(500), audience varchar(500), token_response_pointer varchar(255) NOT NULL DEFAULT '/access_token',
 expires_in_response_pointer varchar(255) NOT NULL DEFAULT '/expires_in', refresh_token_response_pointer varchar(255),
 token_type_response_pointer varchar(255) NOT NULL DEFAULT '/token_type', authorization_scheme varchar(40) NOT NULL DEFAULT 'Bearer',
 credential_transport varchar(40) NOT NULL DEFAULT 'USER_AUTHORIZATION_HEADER'
  CHECK(credential_transport IN ('USER_AUTHORIZATION_HEADER','INTERNAL_LEGACY_HEADER')),
 expiry_skew_seconds integer NOT NULL DEFAULT 30 CHECK(expiry_skew_seconds BETWEEN 5 AND 600),
 connect_timeout_ms integer NOT NULL DEFAULT 3000 CHECK(connect_timeout_ms BETWEEN 100 AND 30000),
 response_timeout_ms integer NOT NULL DEFAULT 10000 CHECK(response_timeout_ms BETWEEN 100 AND 120000),
 max_token_response_size bigint NOT NULL DEFAULT 1048576 CHECK(max_token_response_size BETWEEN 1024 AND 5242880),
 active boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), created_by varchar(255) NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by varchar(255) NOT NULL,
 CHECK(token_endpoint_path IS NULL OR (token_endpoint_path LIKE '/%' AND token_endpoint_path NOT LIKE '%://%')),
 CHECK(auth_mode='FORWARD_USER_TOKEN' OR (token_connection_ref IS NOT NULL AND token_endpoint_path IS NOT NULL
  AND credential_secret_ref IS NOT NULL AND credential_transport='INTERNAL_LEGACY_HEADER'))
);
ALTER TABLE service_target ADD COLUMN outbound_auth_profile_id uuid REFERENCES outbound_auth_profile(id);
ALTER TABLE service_target ADD COLUMN gateway_connection_ref varchar(255) NOT NULL DEFAULT 'connection://operation-gateway';
INSERT INTO outbound_auth_profile(id,code,name,description,auth_mode,request_format,credential_transport,active,created_by,updated_by)
VALUES(gen_random_uuid(),'public-iam-forward','Public IAM user token','Forward current Public IAM token unchanged',
 'FORWARD_USER_TOKEN','FORM_URLENCODED','USER_AUTHORIZATION_HEADER',true,'migration','migration');
UPDATE service_target SET outbound_auth_profile_id=(SELECT id FROM outbound_auth_profile WHERE code='public-iam-forward');
ALTER TABLE service_target ALTER COLUMN outbound_auth_profile_id SET NOT NULL;
INSERT INTO action(action_key,name_fa,name_en) VALUES
 ('invalidate-token','ابطال توکن','Invalidate token'),('update-credential-reference','تغییر مرجع اعتبارنامه','Update credential reference')
ON CONFLICT(action_key) DO NOTHING;
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain)
SELECT 'integration.auth-profile','API_RESOURCE',p.id,'پروفایل‌های احراز هویت سرویس‌ها','Outbound authentication profiles','platform'
FROM resource p WHERE p.resource_key='application:aurevia/admin' ON CONFLICT(resource_key) DO NOTHING;
INSERT INTO resource_action(resource_id,action_id) SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key='integration.auth-profile' AND a.action_key IN
 ('list','view','create','update','activate','test','invalidate-token','update-credential-reference','admin') ON CONFLICT DO NOTHING;
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',r.id,'RESOURCE_PARENT_WRITE',jsonb_build_object('user','application:aurevia','relation','parent','object','resource:integration.auth-profile'),
 'outbound-auth-profile-root-parent:'||r.id FROM resource r WHERE r.resource_key='integration.auth-profile' ON CONFLICT(idempotency_key) DO NOTHING;
INSERT INTO schema_version(component,version) VALUES('control-plane','24')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
