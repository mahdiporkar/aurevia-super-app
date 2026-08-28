INSERT INTO resource_action(resource_id, action_id)
SELECT superset_asset.resource_id, action.id
FROM superset_asset
CROSS JOIN action
WHERE action.action_key IN ('view', 'update', 'admin')
ON CONFLICT DO NOTHING;

INSERT INTO schema_version(component, version)
VALUES ('control-plane', '5')
ON CONFLICT(component)
DO UPDATE SET version = excluded.version, updated_at = now();
