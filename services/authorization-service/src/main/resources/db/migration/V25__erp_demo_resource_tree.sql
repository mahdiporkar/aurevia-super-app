ALTER TYPE resource_type RENAME TO resource_type_before_data_governance;
CREATE TYPE resource_type AS ENUM ('APPLICATION','MODULE','PAGE','UI_COMPONENT','BUSINESS_RESOURCE','EXTERNAL_RESOURCE','API_RESOURCE','DATA_RESOURCE','DATA_GOVERNANCE_RESOURCE');
ALTER TABLE resource ALTER COLUMN type TYPE resource_type USING type::text::resource_type;
DROP TYPE resource_type_before_data_governance;

-- Complete HR and Finance ERP trees. Existing application roots are reused.
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,external_system,external_type,external_id)
SELECT v.resource_key,cast(v.type as resource_type),p.id,v.fa,v.en,v.domain,v.classification,v.system,v.external_type,v.external_id
FROM (VALUES
 ('module:hr.erp','MODULE','application:aurevia/hr','عملیات منابع انسانی','HR Operations','hr','INTERNAL',NULL,NULL,NULL),
 ('page:hr.employees','PAGE','module:hr.erp','مدیریت کارکنان','Employee Management','hr','INTERNAL',NULL,NULL,NULL),
 ('component:hr.employee.create-button','UI_COMPONENT','page:hr.employees','دکمه افزودن کارمند','Create Employee Button','hr','INTERNAL',NULL,NULL,NULL),
 ('component:hr.employee.grid','UI_COMPONENT','page:hr.employees','گرید کارکنان','Employee Grid','hr','CONFIDENTIAL',NULL,NULL,NULL),
 ('component:hr.employee.salary-field','UI_COMPONENT','page:hr.employees','فیلد حقوق','Salary Field','hr','RESTRICTED',NULL,NULL,NULL),
 ('external_resource:superset/hr-workforce','EXTERNAL_RESOURCE','module:hr.erp','داشبورد نیروی انسانی Superset','Superset Workforce Dashboard','hr','CONFIDENTIAL','superset','dashboard','1'),
 ('data:hr.payroll','DATA_RESOURCE','module:hr.erp','جدول حقوق و دستمزد','Payroll Table','hr','RESTRICTED',NULL,NULL,NULL),
 ('governance:hr.payroll.masking','DATA_GOVERNANCE_RESOURCE','data:hr.payroll','سیاست ماسک‌سازی حقوق','Payroll Masking Policy','hr','RESTRICTED',NULL,NULL,NULL),
 ('module:finance.erp','MODULE','application:aurevia/finance','عملیات مالی','Finance Operations','finance','CONFIDENTIAL',NULL,NULL,NULL),
 ('page:finance.payments','PAGE','module:finance.erp','مدیریت پرداخت‌ها','Payment Management','finance','CONFIDENTIAL',NULL,NULL,NULL),
 ('component:finance.payment.create-button','UI_COMPONENT','page:finance.payments','دکمه ایجاد پرداخت','Create Payment Button','finance','CONFIDENTIAL',NULL,NULL,NULL),
 ('component:finance.payment.approval-grid','UI_COMPONENT','page:finance.payments','گرید تأیید پرداخت','Payment Approval Grid','finance','RESTRICTED',NULL,NULL,NULL),
 ('component:finance.payment.amount-field','UI_COMPONENT','page:finance.payments','فیلد مبلغ','Payment Amount Field','finance','RESTRICTED',NULL,NULL,NULL),
 ('external_resource:superset/finance-executive','EXTERNAL_RESOURCE','module:finance.erp','داشبورد مدیریت مالی Superset','Superset Finance Executive Dashboard','finance','RESTRICTED','superset','dashboard','2'),
 ('data:finance.ledger','DATA_RESOURCE','module:finance.erp','دفتر کل مالی','General Ledger','finance','RESTRICTED',NULL,NULL,NULL),
 ('governance:finance.ledger.retention','DATA_GOVERNANCE_RESOURCE','data:finance.ledger','سیاست نگهداری دفتر کل','Ledger Retention Policy','finance','RESTRICTED',NULL,NULL,NULL)
) v(resource_key,type,parent_key,fa,en,domain,classification,system,external_type,external_id)
JOIN resource p ON p.resource_key=v.parent_key
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id,name_fa=excluded.name_fa,name_en=excluded.name_en,
 owner_domain=excluded.owner_domain,classification=excluded.classification,external_system=excluded.external_system,
 external_type=excluded.external_type,external_id=excluded.external_id;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE (r.resource_key LIKE 'module:hr.%' OR r.resource_key LIKE 'page:hr.%' OR r.resource_key LIKE 'component:hr.%'
 OR r.resource_key LIKE 'data:hr.%' OR r.resource_key LIKE 'governance:hr.%'
 OR r.resource_key LIKE 'module:finance.%' OR r.resource_key LIKE 'page:finance.%' OR r.resource_key LIKE 'component:finance.%'
 OR r.resource_key LIKE 'data:finance.%' OR r.resource_key LIKE 'governance:finance.%')
AND a.action_key IN ('view','list','create','update','approve','reject','export','admin')
ON CONFLICT DO NOTHING;
INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key IN ('external_resource:superset/hr-workforce','external_resource:superset/finance-executive')
AND a.action_key IN ('view','share','export','admin') ON CONFLICT DO NOTHING;

-- Role -> resource grants used by the demo manifest and OpenFGA.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'ROLE',role.id,res.id,act.id,
 CASE act.action_key WHEN 'view' THEN 'viewer' WHEN 'list' THEN 'viewer' WHEN 'create' THEN 'creator'
 WHEN 'update' THEN 'editor' WHEN 'approve' THEN 'editor' WHEN 'reject' THEN 'editor'
 WHEN 'export' THEN 'exporter' ELSE 'manager' END
FROM (VALUES
 ('hr-viewer','page:hr.employees','view'),('hr-viewer','component:hr.employee.grid','view'),
 ('hr-viewer','component:hr.employee.create-button','view'),('hr-viewer','component:hr.employee.salary-field','view'),
 ('hr-viewer','data:hr.payroll','view'),('hr-viewer','governance:hr.payroll.masking','view'),
 ('finance-maker','page:finance.payments','view'),('finance-maker','component:finance.payment.create-button','view'),
 ('finance-maker','component:finance.payment.amount-field','view'),('finance-maker','finance.payment','create'),
 ('finance-approver','page:finance.payments','view'),('finance-approver','component:finance.payment.approval-grid','view'),
 ('finance-approver','finance.payment','approve'),('finance-approver','finance.payment','reject'),
 ('finance-approver','data:finance.ledger','view'),('finance-approver','governance:finance.ledger.retention','view')
) v(role_key,resource_key,action_key)
JOIN application_role role ON role.role_key=v.role_key JOIN resource res ON res.resource_key=v.resource_key
JOIN action act ON act.action_key=v.action_key
ON CONFLICT DO NOTHING;

INSERT INTO user_role_assignment(user_id,role_id)
SELECT u.id,r.id FROM (VALUES('hr-user','hr-viewer'),('finance-maker','finance-maker'),('finance-approver','finance-approver')) v(user_key,role_key)
JOIN app_user u ON u.external_id=v.user_key JOIN application_role r ON r.role_key=v.role_key ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
 'user',case when p.type='APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','')
  when p.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(p.resource_key,'^external_resource:',''),':','/')
  else 'resource:'||replace(p.resource_key,':','/') end,
 'relation','parent',
 'object',case when c.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(c.resource_key,'^external_resource:',''),':','/')
  else 'resource:'||replace(c.resource_key,':','/') end),
 'erp-demo-parent:'||c.id||':'||p.id
FROM resource c JOIN resource p ON p.id=c.parent_id
WHERE c.resource_key LIKE ANY(ARRAY['module:hr.%','page:hr.%','component:hr.%','data:hr.%','governance:hr.%','module:finance.%','page:finance.%','component:finance.%','data:finance.%','governance:finance.%'])
 OR c.resource_key IN ('external_resource:superset/hr-workforce','external_resource:superset/finance-executive')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object('user','role:'||ar.role_key||'#assignee','relation',g.relation,
 'object',case when res.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(res.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(res.resource_key,':','/') end),
 'erp-demo-grant:'||g.id FROM authorization_grant g JOIN application_role ar ON g.subject_type='ROLE' AND ar.id=g.subject_id
JOIN resource res ON res.id=g.resource_id WHERE res.resource_key LIKE ANY(ARRAY['page:hr.%','component:hr.%','data:hr.%','governance:hr.%','page:finance.%','component:finance.%','data:finance.%','governance:finance.%'])
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'role-assignment',r.id,'ROLE_ASSIGNMENT_WRITE',jsonb_build_object('user','user:'||u.external_id,'relation','assignee','object','role:'||r.role_key),
 'erp-demo-role:'||u.id||':'||r.id FROM (VALUES('hr-user','hr-viewer'),('finance-maker','finance-maker'),('finance-approver','finance-approver')) v(user_key,role_key)
JOIN app_user u ON u.external_id=v.user_key JOIN application_role r ON r.role_key=v.role_key
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','25')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
