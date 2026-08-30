INSERT INTO action(action_key,name_fa,name_en) VALUES
  ('list','فهرست','List'),
  ('reject','رد','Reject'),
  ('delete','حذف','Delete'),
  ('share','اشتراک‌گذاری','Share'),
  ('export','خروجی','Export'),
  ('manage','مدیریت کامل','Manage')
ON CONFLICT(action_key) DO NOTHING;

INSERT INTO schema_version(component, version) VALUES ('control-plane', '19')
ON CONFLICT(component) DO UPDATE
SET version=excluded.version, updated_at=now();
