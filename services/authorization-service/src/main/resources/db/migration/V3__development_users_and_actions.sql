INSERT INTO app_user(issuer,external_id,username,display_name,email) VALUES
('http://localhost:8180/realms/aurevia','administrator','administrator','مدیر سامانه','administrator@aurevia.local'),
('http://localhost:8180/realms/aurevia','hr-user','hr-user','کاربر منابع انسانی','hr-user@aurevia.local'),
('http://localhost:8180/realms/aurevia','finance-maker','finance-maker','ایجادکننده پرداخت','finance-maker@aurevia.local'),
('http://localhost:8180/realms/aurevia','finance-approver','finance-approver','تأییدکننده پرداخت','finance-approver@aurevia.local'),
('http://localhost:8180/realms/aurevia','report-designer','report-designer','طراح گزارش','report-designer@aurevia.local'),
('http://localhost:8180/realms/aurevia','viewer','viewer','مشاهده‌گر','viewer@aurevia.local')
ON CONFLICT(issuer,external_id) DO UPDATE SET display_name=excluded.display_name,email=excluded.email,updated_at=now();

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE (r.resource_key='application:aurevia' AND a.action_key IN ('view','admin'))
   OR (r.resource_key='business_resource:employee' AND a.action_key IN ('view','create','update'))
   OR (r.resource_key='business_resource:payment' AND a.action_key IN ('view','create','approve'))
   OR (r.resource_key='external_resource:superset-public' AND a.action_key='view')
ON CONFLICT DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','3')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
