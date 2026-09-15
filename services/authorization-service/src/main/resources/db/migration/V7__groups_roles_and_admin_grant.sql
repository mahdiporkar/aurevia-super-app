INSERT INTO directory_group(issuer, external_id, normalized_path, display_name)
VALUES
  ('http://localhost:8180/realms/aurevia', 'software-development', '/Software Development', 'Software Development'),
  ('http://localhost:8180/realms/aurevia', 'hr', '/HR', 'HR')
ON CONFLICT(issuer, external_id) DO NOTHING;

INSERT INTO application_role(role_key, name_fa, name_en)
VALUES
  ('aurevia-administrator', 'مدیر سامانه', 'Aurevia Administrator'),
  ('hr-viewer', 'مشاهده‌گر منابع انسانی', 'HR Viewer'),
  ('finance-maker', 'ایجادکننده پرداخت', 'Finance Maker'),
  ('finance-approver', 'تأییدکننده پرداخت', 'Finance Approver')
ON CONFLICT(role_key) DO NOTHING;

INSERT INTO authorization_grant(subject_type, subject_id, resource_id, action_id, relation)
SELECT 'USER', u.id, r.id, a.id, 'manager'
FROM app_user u, resource r, action a
WHERE u.external_id = 'administrator'
  AND r.resource_key = 'application:aurevia'
  AND a.action_key = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM authorization_grant g
    WHERE g.subject_type = 'USER' AND g.subject_id = u.id
      AND g.resource_id = r.id AND g.action_id = a.id AND g.status = 'ACTIVE'
  );

INSERT INTO schema_version(component, version) VALUES ('control-plane', '7')
ON CONFLICT(component) DO UPDATE SET version=excluded.version, updated_at=now();
