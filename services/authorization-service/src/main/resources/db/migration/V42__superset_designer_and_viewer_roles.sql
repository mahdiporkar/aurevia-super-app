INSERT INTO application_role(role_key,name_fa,name_en) VALUES
  ('superset-designer','طراح و راهبر گزارش','Superset report designer'),
  ('superset-viewer','مشاهده‌گر گزارش','Superset report viewer')
ON CONFLICT(role_key) DO NOTHING;

INSERT INTO user_role_assignment(user_id,role_id,assigned_by)
SELECT u.id,r.id,'migration-v42' FROM app_user u JOIN application_role r ON
  (u.external_id='report-designer' AND r.role_key='superset-designer') OR
  (u.external_id='viewer' AND r.role_key='superset-viewer')
ON CONFLICT(user_id,role_id) DO NOTHING;

INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source)
SELECT 'module:admin.superset-catalog','MODULE',id,
  'راهبری کاتالوگ گزارش‌ها','Superset catalog administration','reports','RESTRICTED','SYSTEM'
FROM resource WHERE resource_key='application:aurevia/admin'
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id,
  name_fa=excluded.name_fa,name_en=excluded.name_en,status='ACTIVE';

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key='module:admin.superset-catalog'
  AND a.action_key IN ('view','create','update','delete','admin','assign')
ON CONFLICT DO NOTHING;

-- Designers may load the admin MFE, but authorization inside it exposes only the reports tab.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'ROLE',role.id,resource.id,action.id,v.relation
FROM (VALUES
  ('superset-designer','application:aurevia/admin','view','viewer'),
  ('superset-designer','application:aurevia/reports','view','viewer'),
  ('superset-designer','module:admin.superset-catalog','admin','manager'),
  ('superset-viewer','application:aurevia/reports','view','viewer')
)v(role_key,resource_key,action_key,relation)
JOIN application_role role ON role.role_key=v.role_key
JOIN resource ON resource.resource_key=v.resource_key
JOIN action ON action.action_key=v.action_key
ON CONFLICT DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',child.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user','application:aurevia/admin','relation','parent',
  'object','resource:module/admin.superset-catalog'),
  'v42:superset-catalog-parent:'||child.id
FROM resource child WHERE child.resource_key='module:admin.superset-catalog'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'role-assignment',a.role_id,'ROLE_ASSIGNMENT_WRITE',jsonb_build_object(
  'user','user:'||u.subject_key,'relation','assignee','object','role:'||r.role_key),
  'v42:role-assignment:'||a.user_id||':'||a.role_id
FROM user_role_assignment a JOIN app_user u ON u.id=a.user_id
JOIN application_role r ON r.id=a.role_id
WHERE r.role_key IN ('superset-designer','superset-viewer')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','role:'||r.role_key||'#assignee','relation',g.relation,
  'object',case when resource.type='APPLICATION'
    then 'application:'||regexp_replace(resource.resource_key,'^application:','')
    else 'resource:'||replace(resource.resource_key,':','/') end),
  'v42:superset-role-grant:'||g.id
FROM authorization_grant g
JOIN application_role r ON g.subject_type='ROLE' AND r.id=g.subject_id
JOIN resource ON resource.id=g.resource_id
WHERE r.role_key IN ('superset-designer','superset-viewer') AND g.status='ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('superset-role-model','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
