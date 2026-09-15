-- Insert each hierarchy level in a separate statement so newly-created parents are visible.
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification)
SELECT v.key,'PAGE',p.id,v.fa,v.en,v.domain,v.classification FROM (VALUES
 ('page:hr.employees','module:hr.erp','مدیریت کارکنان','Employee Management','hr','INTERNAL'),
 ('page:finance.payments','module:finance.erp','مدیریت پرداخت‌ها','Payment Management','finance','CONFIDENTIAL')
)v(key,parent,fa,en,domain,classification) JOIN resource p ON p.resource_key=v.parent
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification)
SELECT v.key,'UI_COMPONENT',p.id,v.fa,v.en,v.domain,v.classification FROM (VALUES
 ('component:hr.employee.create-button','page:hr.employees','دکمه افزودن کارمند','Create Employee Button','hr','INTERNAL'),
 ('component:hr.employee.grid','page:hr.employees','گرید کارکنان','Employee Grid','hr','CONFIDENTIAL'),
 ('component:hr.employee.salary-field','page:hr.employees','فیلد حقوق','Salary Field','hr','RESTRICTED'),
 ('component:finance.payment.create-button','page:finance.payments','دکمه ایجاد پرداخت','Create Payment Button','finance','CONFIDENTIAL'),
 ('component:finance.payment.approval-grid','page:finance.payments','گرید تأیید پرداخت','Payment Approval Grid','finance','RESTRICTED'),
 ('component:finance.payment.amount-field','page:finance.payments','فیلد مبلغ','Payment Amount Field','finance','RESTRICTED')
)v(key,parent,fa,en,domain,classification) JOIN resource p ON p.resource_key=v.parent
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,external_system,external_type,external_id)
SELECT v.key,cast(v.type as resource_type),p.id,v.fa,v.en,v.domain,'RESTRICTED',v.system,v.external_type,v.external_id FROM (VALUES
 ('external_resource:superset/hr-workforce','EXTERNAL_RESOURCE','module:hr.erp','داشبورد نیروی انسانی Superset','Superset Workforce Dashboard','hr','superset','dashboard','1'),
 ('data:hr.payroll','DATA_RESOURCE','module:hr.erp','جدول حقوق و دستمزد','Payroll Table','hr',NULL,NULL,NULL),
 ('external_resource:superset/finance-executive','EXTERNAL_RESOURCE','module:finance.erp','داشبورد مدیریت مالی Superset','Superset Finance Executive Dashboard','finance','superset','dashboard','2'),
 ('data:finance.ledger','DATA_RESOURCE','module:finance.erp','دفتر کل مالی','General Ledger','finance',NULL,NULL,NULL)
)v(key,type,parent,fa,en,domain,system,external_type,external_id) JOIN resource p ON p.resource_key=v.parent
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification)
SELECT v.key,'DATA_GOVERNANCE_RESOURCE',p.id,v.fa,v.en,v.domain,'RESTRICTED' FROM (VALUES
 ('governance:hr.payroll.masking','data:hr.payroll','سیاست ماسک‌سازی حقوق','Payroll Masking Policy','hr'),
 ('governance:finance.ledger.retention','data:finance.ledger','سیاست نگهداری دفتر کل','Ledger Retention Policy','finance')
)v(key,parent,fa,en,domain) JOIN resource p ON p.resource_key=v.parent
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a WHERE
 (r.resource_key LIKE 'page:hr.%' OR r.resource_key LIKE 'component:hr.%' OR r.resource_key LIKE 'data:hr.%' OR r.resource_key LIKE 'governance:hr.%'
 OR r.resource_key LIKE 'page:finance.%' OR r.resource_key LIKE 'component:finance.%' OR r.resource_key LIKE 'data:finance.%' OR r.resource_key LIKE 'governance:finance.%')
 AND a.action_key IN ('view','list','create','update','approve','reject','export','admin') ON CONFLICT DO NOTHING;
INSERT INTO resource_action(resource_id,action_id) SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key IN ('external_resource:superset/hr-workforce','external_resource:superset/finance-executive')
AND a.action_key IN ('view','share','export','admin') ON CONFLICT DO NOTHING;

INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'ROLE',role.id,res.id,act.id,case act.action_key when 'view' then 'viewer' when 'create' then 'creator' when 'export' then 'exporter' else 'editor' end
FROM (VALUES
 ('hr-viewer','page:hr.employees','view'),('hr-viewer','component:hr.employee.grid','view'),('hr-viewer','component:hr.employee.create-button','view'),
 ('hr-viewer','component:hr.employee.salary-field','view'),('hr-viewer','data:hr.payroll','view'),('hr-viewer','governance:hr.payroll.masking','view'),
 ('finance-maker','page:finance.payments','view'),('finance-maker','component:finance.payment.create-button','view'),('finance-maker','component:finance.payment.amount-field','view'),
 ('finance-approver','page:finance.payments','view'),('finance-approver','component:finance.payment.approval-grid','view'),
 ('finance-approver','data:finance.ledger','view'),('finance-approver','governance:finance.ledger.retention','view')
)v(role_key,resource_key,action_key) JOIN application_role role ON role.role_key=v.role_key
JOIN resource res ON res.resource_key=v.resource_key JOIN action act ON act.action_key=v.action_key ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object('user',case when p.type='APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','') when p.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(p.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(p.resource_key,':','/') end,'relation','parent','object',case when c.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(c.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(c.resource_key,':','/') end),'erp-v26-parent:'||c.id||':'||p.id
FROM resource c JOIN resource p ON p.id=c.parent_id WHERE c.resource_key LIKE ANY(ARRAY['module:hr.%','page:hr.%','component:hr.%','data:hr.%','governance:hr.%','module:finance.%','page:finance.%','component:finance.%','data:finance.%','governance:finance.%']) OR c.resource_key IN ('external_resource:superset/hr-workforce','external_resource:superset/finance-executive') ON CONFLICT(idempotency_key) DO NOTHING;
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object('user','role:'||ar.role_key||'#assignee','relation',g.relation,'object',case when res.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(res.resource_key,'^external_resource:',''),':','/') else 'resource:'||replace(res.resource_key,':','/') end),'erp-v26-grant:'||g.id
FROM authorization_grant g JOIN application_role ar ON g.subject_type='ROLE' AND ar.id=g.subject_id JOIN resource res ON res.id=g.resource_id
WHERE res.resource_key LIKE ANY(ARRAY['page:hr.%','component:hr.%','data:hr.%','governance:hr.%','page:finance.%','component:finance.%','data:finance.%','governance:finance.%']) ON CONFLICT(idempotency_key) DO NOTHING;
INSERT INTO schema_version(component,version) VALUES('control-plane','26') ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
