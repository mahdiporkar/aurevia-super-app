-- Stored relations are derived exclusively from action/object semantics.
UPDATE authorization_grant g
SET relation = CASE
  WHEN a.action_key IN ('view','list') THEN 'viewer'
  WHEN a.action_key = 'create' AND r.type <> 'EXTERNAL_RESOURCE' THEN 'creator'
  WHEN a.action_key IN ('update','approve','reject') THEN 'editor'
  WHEN a.action_key = 'delete' AND r.type = 'EXTERNAL_RESOURCE' THEN 'manager'
  WHEN a.action_key = 'delete' THEN 'deleter'
  WHEN a.action_key = 'share' AND r.type = 'EXTERNAL_RESOURCE' THEN 'sharer'
  WHEN a.action_key = 'export' AND r.type = 'EXTERNAL_RESOURCE' THEN 'exporter'
  WHEN a.action_key IN ('admin','manage') THEN 'manager'
  ELSE g.relation
END
FROM action a, resource r
WHERE a.id=g.action_id AND r.id=g.resource_id;

INSERT INTO schema_version(component, version) VALUES ('control-plane', '18')
ON CONFLICT(component) DO UPDATE
SET version=excluded.version, updated_at=now();
