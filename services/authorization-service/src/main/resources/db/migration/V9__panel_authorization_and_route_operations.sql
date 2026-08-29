INSERT INTO resource(resource_key,type,name_fa,name_en,owner_domain) VALUES
 ('application:aurevia/admin','APPLICATION','پنل مدیریت','Administration panel','platform'),
 ('application:aurevia/hr','APPLICATION','پنل منابع انسانی','Human Resources panel','hr'),
 ('application:aurevia/finance','APPLICATION','پنل مالی','Finance panel','finance'),
 ('application:aurevia/reports','APPLICATION','پنل گزارش‌ها','Reports panel','reports')
ON CONFLICT(resource_key) DO NOTHING;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key LIKE 'application:aurevia/%' AND a.action_key IN ('view','admin')
ON CONFLICT DO NOTHING;

INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,'viewer' FROM app_user u
JOIN (VALUES
 ('administrator','application:aurevia/admin'),
 ('administrator','application:aurevia/hr'),
 ('administrator','application:aurevia/finance'),
 ('administrator','application:aurevia/reports'),
 ('hr-user','application:aurevia/hr'),
 ('finance-maker','application:aurevia/finance'),
 ('finance-approver','application:aurevia/finance'),
 ('report-designer','application:aurevia/reports'),
 ('viewer','application:aurevia/reports')
) allowed(user_key,resource_key) ON allowed.user_key=u.external_id
JOIN resource r ON r.resource_key=allowed.resource_key
JOIN action a ON a.action_key='view'
ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.external_id,'relation','viewer',
  'object','application:'||regexp_replace(r.resource_key,'^application:','')),
  'bootstrap-panel:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id JOIN action a ON a.id=g.action_id
WHERE r.resource_key LIKE 'application:aurevia/%' AND a.action_key='view'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO route_operation(route_id,http_method,relative_pattern,resource_id,action_id,max_body_bytes)
SELECT pr.id,v.method,v.pattern,r.id,a.id,v.max_body FROM proxy_route pr
JOIN (VALUES
 ('hr-api','GET','/api/v1/employees/*','hr.employee','view',1048576::bigint),
 ('hr-api','POST','/api/v1/employees','hr.employee','create',1048576::bigint),
 ('hr-api','PUT','/api/v1/employees/*','hr.employee','update',1048576::bigint),
 ('hr-api','GET','/api/v1/departments','hr.department','list',1048576::bigint),
 ('hr-api','GET','/api/v1/departments/*','hr.department','view',1048576::bigint),
 ('hr-api','GET','/api/v1/positions','hr.position','list',1048576::bigint),
 ('hr-api','GET','/api/v1/positions/*','hr.position','view',1048576::bigint),
 ('finance-api','GET','/api/v1/invoices','finance.invoice','list',1048576::bigint),
 ('finance-api','GET','/api/v1/invoices/*','finance.invoice','view',1048576::bigint),
 ('finance-api','POST','/api/v1/invoices','finance.invoice','create',1048576::bigint),
 ('finance-api','GET','/api/v1/budgets','finance.budget','list',1048576::bigint),
 ('finance-api','GET','/api/v1/budgets/*','finance.budget','view',1048576::bigint),
 ('finance-api','PUT','/api/v1/budgets/*','finance.budget','update',1048576::bigint),
 ('finance-api','GET','/api/v1/payments','finance.payment','list',1048576::bigint),
 ('finance-api','GET','/api/v1/payments/*','finance.payment','view',1048576::bigint),
 ('finance-api','POST','/api/v1/payments','finance.payment','create',1048576::bigint),
 ('finance-api','POST','/api/v1/payments/*/reject','finance.payment','reject',1048576::bigint)
) v(route_key,method,pattern,resource_key,action_key,max_body) ON v.route_key=pr.route_key
JOIN resource r ON r.resource_key=v.resource_key JOIN action a ON a.action_key=v.action_key
ON CONFLICT(route_id,http_method,relative_pattern) DO NOTHING;

INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,
  CASE a.action_key
    WHEN 'list' THEN 'viewer' WHEN 'view' THEN 'viewer' WHEN 'create' THEN 'creator'
    WHEN 'update' THEN 'editor' WHEN 'approve' THEN 'editor' WHEN 'reject' THEN 'editor'
    ELSE 'viewer' END
FROM app_user u CROSS JOIN resource r CROSS JOIN action a
WHERE r.resource_key IN ('hr.employee','hr.department','hr.position','finance.invoice','finance.budget','finance.payment')
  AND EXISTS (SELECT 1 FROM resource_action ra WHERE ra.resource_id=r.id AND ra.action_id=a.id)
  AND (
    u.external_id='administrator'
    OR (u.external_id='hr-user' AND r.resource_key LIKE 'hr.%')
    OR (u.external_id='finance-maker' AND r.resource_key LIKE 'finance.%'
        AND a.action_key IN ('list','view','create','update'))
    OR (u.external_id='finance-approver' AND r.resource_key LIKE 'finance.%'
        AND a.action_key IN ('list','view','approve','reject'))
  )
ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.external_id,'relation',g.relation,'object','resource:'||r.resource_key),
  'bootstrap-operation:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE r.resource_key LIKE 'hr.%' OR r.resource_key LIKE 'finance.%'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','9')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
