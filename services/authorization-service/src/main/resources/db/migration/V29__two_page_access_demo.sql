-- Two-page end-to-end demo:
--   demo-full-access -> HR employee page + Finance payment page
--   demo-hr-only     -> HR employee page only

INSERT INTO app_user(issuer,external_id,username,display_name,email) VALUES
 ('http://localhost:8180/realms/aurevia','demo-full-access','demo-full-access','کاربر دموی هر دو صفحه','demo-full-access@aurevia.local'),
 ('http://localhost:8180/realms/aurevia','demo-hr-only','demo-hr-only','کاربر دموی فقط منابع انسانی','demo-hr-only@aurevia.local')
ON CONFLICT(issuer,external_id) DO UPDATE SET
 username=excluded.username,display_name=excluded.display_name,email=excluded.email,status='ACTIVE',updated_at=now();

-- The canonical employee resource also owns the GET collection route, whose action is list.
INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r JOIN action a ON a.action_key='list'
WHERE r.resource_key='business:hr.employee'
ON CONFLICT DO NOTHING;

-- Panel and page visibility. The second user deliberately receives no Finance tuple.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,'viewer'
FROM (VALUES
 ('demo-full-access','application:aurevia/hr','view'),
 ('demo-full-access','application:aurevia/finance','view'),
 ('demo-full-access','page:hr.employee.list','view'),
 ('demo-full-access','page:finance.payments','view'),
 ('demo-hr-only','application:aurevia/hr','view'),
 ('demo-hr-only','page:hr.employee.list','view')
) v(user_key,resource_key,action_key)
JOIN app_user u ON u.external_id=v.user_key
JOIN resource r ON r.resource_key=v.resource_key
JOIN action a ON a.action_key=v.action_key
WHERE NOT EXISTS (
 SELECT 1 FROM authorization_grant g WHERE g.subject_type='USER' AND g.subject_id=u.id
 AND g.resource_id=r.id AND g.action_id=a.id AND g.status='ACTIVE'
);

-- Operational API permissions are independent from the UI page permission.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,
 CASE a.action_key WHEN 'list' THEN 'viewer' WHEN 'view' THEN 'viewer'
  WHEN 'create' THEN 'creator' ELSE 'editor' END
FROM (VALUES
 ('demo-full-access','business:hr.employee','list'),
 ('demo-full-access','business:hr.employee','view'),
 ('demo-full-access','business:hr.employee','create'),
 ('demo-full-access','business:hr.employee','update'),
 ('demo-full-access','hr.department','list'),
 ('demo-full-access','hr.department','view'),
 ('demo-full-access','hr.position','list'),
 ('demo-full-access','hr.position','view'),
 ('demo-full-access','finance.payment','list'),
 ('demo-full-access','finance.payment','view'),
 ('demo-full-access','finance.payment','create'),
 ('demo-full-access','finance.payment','approve'),
 ('demo-full-access','finance.payment','reject'),
 ('demo-hr-only','business:hr.employee','list'),
 ('demo-hr-only','business:hr.employee','view'),
 ('demo-hr-only','business:hr.employee','create'),
 ('demo-hr-only','business:hr.employee','update'),
 ('demo-hr-only','hr.department','list'),
 ('demo-hr-only','hr.department','view'),
 ('demo-hr-only','hr.position','list'),
 ('demo-hr-only','hr.position','view')
) v(user_key,resource_key,action_key)
JOIN app_user u ON u.external_id=v.user_key
JOIN resource r ON r.resource_key=v.resource_key
JOIN action a ON a.action_key=v.action_key
WHERE NOT EXISTS (
 SELECT 1 FROM authorization_grant g WHERE g.subject_type='USER' AND g.subject_id=u.id
 AND g.resource_id=r.id AND g.action_id=a.id AND g.status='ACTIVE'
);

-- Publish every direct demo grant through the transactional outbox. This is the only path
-- used to project PostgreSQL control-plane changes into OpenFGA.
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
 'user','user:'||u.external_id,
 'relation',g.relation,
 'object',CASE
   WHEN r.type='APPLICATION' THEN 'application:'||regexp_replace(r.resource_key,'^application:','')
   WHEN r.type='EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(regexp_replace(r.resource_key,'^(external_resource:|external:)',''),':','/')
   ELSE 'resource:'||replace(r.resource_key,':','/')
 END),
 'two-page-demo-grant:'||g.id
FROM authorization_grant g
JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE u.external_id IN ('demo-full-access','demo-hr-only') AND g.status='ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','29')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
