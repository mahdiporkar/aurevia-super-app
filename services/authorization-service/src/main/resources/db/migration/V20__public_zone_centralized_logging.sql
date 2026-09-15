CREATE TABLE api_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_time timestamptz NOT NULL,
  user_id varchar(500), actor_type varchar(80), service_name varchar(160) NOT NULL,
  http_method varchar(16) NOT NULL, route_template varchar(1000) NOT NULL,
  status_code integer NOT NULL CHECK(status_code BETWEEN 100 AND 599),
  duration_ms bigint NOT NULL CHECK(duration_ms >= 0),
  source_ip varchar(128), user_agent varchar(1000), correlation_id varchar(128) NOT NULL,
  request_size_bytes bigint, response_size_bytes bigint,
  authorization_result varchar(20), resource_type varchar(100), resource_id varchar(500),
  business_action varchar(100), openfga_duration_ms bigint, database_duration_ms bigint,
  redis_duration_ms bigint, downstream_duration_ms bigint,
  error_code varchar(160), error_type varchar(500), error_response_body text,
  error_response_redacted boolean NOT NULL DEFAULT false,
  error_response_truncated boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(error_response_body IS NULL OR status_code >= 400)
);
CREATE INDEX api_log_event_time_idx ON api_log(event_time DESC);
CREATE INDEX api_log_user_time_idx ON api_log(user_id,event_time DESC);
CREATE INDEX api_log_service_time_idx ON api_log(service_name,event_time DESC);
CREATE INDEX api_log_status_time_idx ON api_log(status_code,event_time DESC);
CREATE INDEX api_log_correlation_idx ON api_log(correlation_id);
CREATE INDEX api_log_route_time_idx ON api_log(route_template,event_time DESC);

CREATE TABLE audit_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_time timestamptz NOT NULL,
  event_version integer NOT NULL DEFAULT 1, actor_type varchar(80) NOT NULL,
  actor_id varchar(500) NOT NULL, event_category varchar(100) NOT NULL,
  event_type varchar(160) NOT NULL, subject_type varchar(100), subject_id varchar(500),
  target_type varchar(100), target_id varchar(500), target_name_snapshot varchar(500),
  action varchar(100), result varchar(40) NOT NULL, before_state jsonb,
  after_state jsonb, source_ip varchar(128), user_agent varchar(1000),
  service_name varchar(160) NOT NULL, correlation_id varchar(128) NOT NULL,
  metadata jsonb, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_event_time_idx ON audit_log(event_time DESC);
CREATE INDEX audit_log_actor_time_idx ON audit_log(actor_id,event_time DESC);
CREATE INDEX audit_log_event_type_time_idx ON audit_log(event_type,event_time DESC);
CREATE INDEX audit_log_target_time_idx ON audit_log(target_type,target_id,event_time DESC);
CREATE INDEX audit_log_correlation_idx ON audit_log(correlation_id);

INSERT INTO action(action_key,name_fa,name_en) VALUES
 ('view_api','مشاهده لاگ API','View API logs'),
 ('view_audit','مشاهده لاگ ممیزی','View audit logs'),
 ('view_errors','مشاهده خطاها','View error logs')
ON CONFLICT(action_key) DO NOTHING;

INSERT INTO resource(resource_key,type,name_fa,name_en,owner_domain,classification)
VALUES('business_resource:public-zone-logs','BUSINESS_RESOURCE',
  'لاگ‌های ناحیه عمومی','Public zone logs','platform','CONFIDENTIAL')
ON CONFLICT(resource_key) DO NOTHING;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key='business_resource:public-zone-logs'
  AND a.action_key IN ('view','view_api','view_audit','view_errors','export')
ON CONFLICT DO NOTHING;

-- Local bootstrap grants only this concrete identity; root administrators do not inherit it.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,
  CASE WHEN a.action_key='view_audit' THEN 'manager' ELSE 'viewer' END
FROM app_user u CROSS JOIN resource r CROSS JOIN action a
WHERE u.external_id='administrator'
  AND r.resource_key='business_resource:public-zone-logs'
  AND a.action_key IN ('view','view_api','view_audit','view_errors')
ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object('user','user:'||u.external_id,
  'relation',g.relation,'object','resource:'||r.resource_key),'logging-bootstrap:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE r.resource_key='business_resource:public-zone-logs'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','20')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
