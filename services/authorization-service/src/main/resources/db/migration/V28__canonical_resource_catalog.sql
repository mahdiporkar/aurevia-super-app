-- Canonical seven-type resource catalog. Legacy API/DATA/GOVERNANCE enum values remain only
-- so existing installations can migrate without destructive rewrites; new writes reject them.
ALTER TABLE resource ADD COLUMN IF NOT EXISTS source varchar(40) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE resource ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE resource_api_binding (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE RESTRICT,
  action_id uuid NOT NULL REFERENCES action(id) ON DELETE RESTRICT,
  http_method varchar(12) NOT NULL,
  path_pattern varchar(500) NOT NULL,
  service_code varchar(160),
  active boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(http_method,path_pattern,service_code)
);

CREATE TABLE resource_external_binding (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE RESTRICT,
  provider varchar(80) NOT NULL,
  external_type varchar(80) NOT NULL,
  external_id varchar(255) NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  active boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(provider,external_type,external_id),
  UNIQUE(resource_id,provider)
);

CREATE TABLE resource_manifest_import (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  application_key varchar(500) NOT NULL,
  manifest_version varchar(100) NOT NULL,
  checksum varchar(128) NOT NULL,
  imported_by varchar(255) NOT NULL,
  imported_at timestamptz NOT NULL DEFAULT now(),
  payload jsonb NOT NULL,
  UNIQUE(application_key,manifest_version,checksum)
);

INSERT INTO action(action_key,name_fa,name_en) VALUES
 ('access','دسترسی','Access'),('download','دریافت','Download'),('execute','اجرا','Execute'),
 ('import','ورود اطلاعات','Import'),('upload','بارگذاری','Upload'),('assign','انتصاب','Assign')
ON CONFLICT(action_key) DO NOTHING;

-- Canonical HR example tree. Actions remain separate records.
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source)
VALUES ('application:hr','APPLICATION',NULL,'منابع انسانی','Human Resources','hr','INTERNAL','SYSTEM')
ON CONFLICT(resource_key) DO UPDATE SET name_fa=excluded.name_fa,name_en=excluded.name_en;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source)
SELECT 'module:hr.employee-management','MODULE',id,'مدیریت کارکنان','Employee Management','hr','INTERNAL','APPLICATION_MANIFEST'
FROM resource WHERE resource_key='application:hr' ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source,metadata)
SELECT v.key,cast(v.type as resource_type),p.id,v.fa,v.en,'hr',v.classification,'APPLICATION_MANIFEST',v.metadata::jsonb
FROM (VALUES
 ('page:hr.employee.list','PAGE','لیست کارکنان','Employee List','INTERNAL','{"route":"/hr/employees"}'),
 ('page:hr.employee.detail','PAGE','جزئیات کارمند','Employee Details','INTERNAL','{"route":"/hr/employees/:id"}'),
 ('business:hr.employee','BUSINESS_RESOURCE','کارمند','Employee','CONFIDENTIAL','{}')
) v(key,type,fa,en,classification,metadata)
CROSS JOIN resource p WHERE p.resource_key='module:hr.employee-management'
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id,name_fa=excluded.name_fa,name_en=excluded.name_en,metadata=excluded.metadata;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source)
SELECT 'component:hr.employee.salary-information','UI_COMPONENT',id,'اطلاعات حقوق و مزایا','Salary Information','hr','RESTRICTED','APPLICATION_MANIFEST'
FROM resource WHERE resource_key='page:hr.employee.detail' ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source)
SELECT 'field:hr.employee.salary-amount','FIELD',id,'مبلغ حقوق','Salary Amount','hr','RESTRICTED','APPLICATION_MANIFEST'
FROM resource WHERE resource_key='component:hr.employee.salary-information' ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source,external_system,external_type,external_id,metadata)
SELECT 'external:hr.workforce-dashboard','EXTERNAL_RESOURCE',id,'داشبورد نیروی انسانی','Workforce Dashboard','hr','CONFIDENTIAL','EXTERNAL_SYNC','SUPERSET','DASHBOARD','example-dashboard-id',
 '{"provider":"SUPERSET","external_type":"DASHBOARD","external_id":"example-dashboard-id"}'::jsonb
FROM resource WHERE resource_key='module:hr.employee-management'
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id,metadata=excluded.metadata;

INSERT INTO resource_external_binding(resource_id,provider,external_type,external_id)
SELECT id,'SUPERSET','DASHBOARD','example-dashboard-id' FROM resource WHERE resource_key='external:hr.workforce-dashboard'
ON CONFLICT(provider,external_type,external_id) DO UPDATE SET resource_id=excluded.resource_id,active=true;

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r JOIN action a ON
 (r.type='APPLICATION' AND a.action_key='access') OR
 (r.type='MODULE' AND a.action_key='access') OR
 (r.type IN ('PAGE','UI_COMPONENT','FIELD') AND a.action_key='view') OR
 (r.resource_key='business:hr.employee' AND a.action_key IN ('view','create','update','delete','export')) OR
 (r.resource_key='external:hr.workforce-dashboard' AND a.action_key IN ('view','download'))
WHERE r.resource_key LIKE '%:hr.%' OR r.resource_key='application:hr'
ON CONFLICT DO NOTHING;

-- Preserve the intent of demo role grants while moving authorization from widgets to capabilities.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'ROLE',g.subject_id,target.id,a.id,
 case a.action_key when 'view' then 'viewer' when 'create' then 'creator' else 'editor' end
FROM authorization_grant g JOIN resource old ON old.id=g.resource_id
JOIN resource target ON target.resource_key=case when old.resource_key='component:hr.employee.salary-field'
 then 'field:hr.employee.salary-amount' else 'business:hr.employee' end
JOIN action a ON a.action_key=case old.resource_key when 'component:hr.employee.create-button' then 'create' else 'view' end
WHERE g.subject_type='ROLE' AND g.status='ACTIVE' AND old.resource_key IN
 ('component:hr.employee.create-button','component:hr.employee.grid','component:hr.employee.salary-field')
ON CONFLICT DO NOTHING;

UPDATE route_operation SET resource_id=(SELECT id FROM resource WHERE resource_key='business:hr.employee'),
 resource_key='business:hr.employee',version=version+1,updated_at=now()
WHERE resource_key='hr.employee';

-- Button/grid/ordinary-field records were a modeling error. Preserve audit identity but remove
-- active grants and deprecate them; callers now use actions on business:hr.employee.
UPDATE authorization_grant SET status='INACTIVE',version=version+1
WHERE resource_id IN (SELECT id FROM resource WHERE resource_key IN
 ('component:hr.employee.create-button','component:hr.employee.grid','component:hr.employee.salary-field',
  'component:finance.payment.create-button','component:finance.payment.approval-grid','component:finance.payment.amount-field'))
AND status='ACTIVE';
UPDATE resource SET status='DEPRECATED',version=version+1 WHERE resource_key IN
 ('component:hr.employee.create-button','component:hr.employee.grid','component:hr.employee.salary-field',
  'component:finance.payment.create-button','component:finance.payment.approval-grid','component:finance.payment.amount-field');

INSERT INTO schema_version(component,version) VALUES('control-plane','28')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
