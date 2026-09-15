WITH app AS (SELECT id FROM resource WHERE resource_key='application:aurevia')
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification)
SELECT v.resource_key,cast(v.type as resource_type),
       CASE WHEN v.parent_key='application:aurevia' THEN app.id ELSE NULL END,
       v.name_fa,v.name_en,v.owner_domain,v.classification
FROM app CROSS JOIN (VALUES
 ('module:hr','MODULE','application:aurevia','منابع انسانی','Human Resources','hr','INTERNAL'),
 ('module:finance','MODULE','application:aurevia','مالی','Finance','finance','CONFIDENTIAL'),
 ('module:integration','MODULE','application:aurevia','یکپارچه‌سازی','Integration','platform','INTERNAL')
) v(resource_key,type,parent_key,name_fa,name_en,owner_domain,classification)
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,external_system,external_type,external_id)
SELECT v.resource_key,cast(v.type as resource_type),p.id,v.name_fa,v.name_en,v.owner_domain,v.classification,
       v.external_system,v.external_type,v.external_id
FROM (VALUES
 ('page:hr.employees','PAGE','module:hr','فهرست کارکنان','Employees page','hr','INTERNAL',NULL,NULL,NULL),
 ('component:hr.employee.create','UI_COMPONENT','page:hr.employees','دکمه ایجاد کارمند','Create employee button','hr','INTERNAL',NULL,NULL,NULL),
 ('api:hr.employees','API_RESOURCE','module:integration','API کارکنان','Employees API','hr','CONFIDENTIAL',NULL,NULL,NULL),
 ('business:hr.employee','BUSINESS_RESOURCE','module:hr','پرونده کارمند','Employee record','hr','CONFIDENTIAL',NULL,NULL,NULL),
 ('external:payroll','EXTERNAL_RESOURCE','module:integration','سامانه حقوق و دستمزد','Payroll system','hr','RESTRICTED','payroll','application','main')
) v(resource_key,type,parent_key,name_fa,name_en,owner_domain,classification,external_system,external_type,external_id)
JOIN resource p ON p.resource_key=v.parent_key
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

UPDATE resource SET parent_id=(SELECT id FROM resource WHERE resource_key='module:hr')
WHERE resource_key IN ('hr.employee','hr.department','hr.position');
UPDATE resource SET parent_id=(SELECT id FROM resource WHERE resource_key='module:finance')
WHERE resource_key IN ('finance.invoice','finance.budget','finance.payment');
UPDATE resource SET parent_id=(SELECT id FROM resource WHERE resource_key='application:aurevia')
WHERE resource_key='external_resource:superset-public';

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key IN ('module:hr','page:hr.employees','component:hr.employee.create','api:hr.employees','business:hr.employee','external:payroll')
  AND a.action_key IN ('view','create','update','delete','admin')
ON CONFLICT DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','12')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
