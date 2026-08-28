WITH parent AS (
  SELECT id FROM resource WHERE resource_key = 'external_resource:superset-public'
)
INSERT INTO resource(
  resource_key, type, parent_id, name_fa, name_en, owner_domain,
  external_system, external_type, external_id
)
SELECT
  'external_resource:superset-public:dashboard:welcome-dashboard',
  'EXTERNAL_RESOURCE', parent.id, 'داشبورد مدیریتی Aurevia',
  'Aurevia management dashboard', 'reports', 'superset-public',
  'DASHBOARD', 'welcome-dashboard'
FROM parent
ON CONFLICT(resource_key) DO NOTHING;

INSERT INTO resource_action(resource_id, action_id)
SELECT resource.id, action.id
FROM resource CROSS JOIN action
WHERE resource.resource_key = 'external_resource:superset-public:dashboard:welcome-dashboard'
  AND action.action_key = 'view'
ON CONFLICT DO NOTHING;

INSERT INTO superset_asset(
  resource_id, external_id, asset_type, title, url_path,
  owner_external_id, published, tags, synchronized_at
)
SELECT
  resource.id, 'welcome-dashboard', 'DASHBOARD', 'داشبورد مدیریتی Aurevia',
  '/superset/welcome/', 'administrator', true,
  '["مدیریتی", "عمومی"]'::jsonb, now()
FROM resource
WHERE resource.resource_key = 'external_resource:superset-public:dashboard:welcome-dashboard'
ON CONFLICT(external_id) DO NOTHING;

INSERT INTO authorization_grant(subject_type, subject_id, resource_id, action_id, relation)
SELECT 'USER', app_user.id, resource.id, action.id, 'allowed'
FROM app_user CROSS JOIN resource CROSS JOIN action
WHERE app_user.external_id IN ('administrator', 'report-designer')
  AND resource.resource_key = 'external_resource:superset-public:dashboard:welcome-dashboard'
  AND action.action_key = 'view'
  AND NOT EXISTS (
    SELECT 1 FROM authorization_grant existing
    WHERE existing.subject_type = 'USER'
      AND existing.subject_id = app_user.id
      AND existing.resource_id = resource.id
      AND existing.action_id = action.id
      AND existing.status = 'ACTIVE'
  );

INSERT INTO schema_version(component, version) VALUES('control-plane', '4')
ON CONFLICT(component) DO UPDATE SET version = excluded.version, updated_at = now();
