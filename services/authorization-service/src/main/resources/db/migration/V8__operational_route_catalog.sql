INSERT INTO resource(resource_key, type, name_fa, name_en, owner_domain)
VALUES
 ('hr.employee', 'BUSINESS_RESOURCE', 'کارکنان', 'Employees', 'hr'),
 ('hr.department', 'BUSINESS_RESOURCE', 'واحدهای سازمانی', 'Departments', 'hr'),
 ('hr.position', 'BUSINESS_RESOURCE', 'سمت‌ها', 'Positions', 'hr'),
 ('finance.invoice', 'BUSINESS_RESOURCE', 'صورتحساب‌ها', 'Invoices', 'finance'),
 ('finance.budget', 'BUSINESS_RESOURCE', 'بودجه‌ها', 'Budgets', 'finance'),
 ('finance.payment', 'BUSINESS_RESOURCE', 'پرداخت‌ها', 'Payments', 'finance')
ON CONFLICT(resource_key) DO NOTHING;

INSERT INTO action(action_key,name_fa,name_en) VALUES
 ('list','فهرست','List'),('reject','رد','Reject') ON CONFLICT(action_key) DO NOTHING;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a WHERE
 (r.resource_key='hr.employee' and a.action_key in ('list','view','create','update')) or
 (r.resource_key='hr.department' and a.action_key in ('list','view')) or
 (r.resource_key='hr.position' and a.action_key in ('list','view')) or
 (r.resource_key='finance.invoice' and a.action_key in ('list','view','create')) or
 (r.resource_key='finance.budget' and a.action_key in ('list','view','update')) or
 (r.resource_key='finance.payment' and a.action_key in ('list','view','create','approve','reject'))
ON CONFLICT DO NOTHING;

INSERT INTO service_target(target_key,base_url,mtls_secret_ref)
VALUES ('operation-gateway','http://operation-gateway:80','secret://gateway-client')
ON CONFLICT(target_key) DO NOTHING;

INSERT INTO proxy_route(route_key,path_prefix,target_id,strip_prefix)
SELECT 'hr-api','/hr-micro',id,0 FROM service_target WHERE target_key='operation-gateway'
ON CONFLICT(route_key) DO NOTHING;
INSERT INTO proxy_route(route_key,path_prefix,target_id,strip_prefix)
SELECT 'finance-api','/finance-micro',id,0 FROM service_target WHERE target_key='operation-gateway'
ON CONFLICT(route_key) DO NOTHING;

INSERT INTO route_operation(route_id,http_method,relative_pattern,resource_id,action_id,max_body_bytes)
SELECT pr.id,v.method,v.pattern,r.id,a.id,v.max_body FROM proxy_route pr
JOIN (VALUES
 ('hr-api','GET','/api/v1/employees','hr.employee','list',1048576::bigint),
 ('finance-api','POST','/api/v1/payments/*/approve','finance.payment','approve',1048576::bigint)
) v(route_key,method,pattern,resource_key,action_key,max_body) ON v.route_key=pr.route_key
JOIN resource r ON r.resource_key=v.resource_key JOIN action a ON a.action_key=v.action_key
ON CONFLICT(route_id,http_method,relative_pattern) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','8')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
