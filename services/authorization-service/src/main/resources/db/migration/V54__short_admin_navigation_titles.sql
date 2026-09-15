-- Keep detailed descriptions as tooltips while making the right-side navigation concise.
WITH title_map(route_id,title) AS (VALUES
  ('operator-guide','راهنما'),
  ('ou-access-ous','واحدهای سازمانی'),
  ('ou-access-groups','گروه‌ها'),
  ('ou-access-applications','برنامه‌ها'),
  ('ou-access-explain','تحلیل دسترسی'),
  ('access-studio','منابع و مجوزها'),
  ('panels','میکروفرانت‌ها'),
  ('proxy-targets','مقصدها'),
  ('proxy-routes','مسیرها'),
  ('proxy-operations','عملیات API'),
  ('outbound-connections','اتصال‌ها'),
  ('outbound-auth','احراز هویت'),
  ('integration-test','تست اتصال'),
  ('superset-instances','محیط‌های گزارش'),
  ('identity','هویت و نقش'),
  ('logs-api','لاگ API'),
  ('logs-audit','لاگ راهبری'),
  ('superset','گزارش‌ها')
), admin_panel AS (
  SELECT id AS panel_id,active_artifact_id FROM panel WHERE code='ADMIN'
), source AS (
  SELECT a.* FROM ui_module_artifact a JOIN admin_panel p ON p.active_artifact_id=a.id
), rewritten AS (
  SELECT s.*,
    (SELECT jsonb_agg(
       CASE WHEN tm.title IS NULL THEN menu
            ELSE jsonb_set(menu,'{title}',to_jsonb(tm.title),false) END
       ORDER BY ordinality)
     FROM jsonb_array_elements(s.manifest_snapshot->'menus') WITH ORDINALITY AS items(menu,ordinality)
     LEFT JOIN title_map tm ON tm.route_id=menu->>'routeId') AS menus
  FROM source s
)
INSERT INTO ui_module_artifact(id,panel_id,artifact_version,remote_entry_url,remote_name,
  exposed_module,contract_version,schema_version,integrity,manifest_snapshot,
  validation_status,created_by)
SELECT gen_random_uuid(),r.panel_id,'0.4.0',r.remote_entry_url,r.remote_name,r.exposed_module,
  r.contract_version,r.schema_version,r.integrity,jsonb_set(r.manifest_snapshot,'{menus}',r.menus),
  'VALID','migration-v54'
FROM rewritten r
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

UPDATE panel p SET active_artifact_id=a.id,semantic_version=a.artifact_version,
  version=p.version+1,updated_at=now()
FROM ui_module_artifact a
WHERE p.code='ADMIN' AND a.panel_id=p.id AND a.artifact_version='0.4.0';

INSERT INTO schema_version(component,version) VALUES('admin-navigation-tooltips','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
