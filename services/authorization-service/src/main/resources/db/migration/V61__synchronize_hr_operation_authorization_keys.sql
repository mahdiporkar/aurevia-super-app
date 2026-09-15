-- Route resolution reads the canonical key snapshots as well as the foreign keys.
-- V60 aligned the foreign keys; keep both representations consistent on upgrades.
UPDATE route_operation operation SET
  resource_key=resource.resource_key,
  action_key=action.action_key,
  version=operation.version+1,
  updated_at=now(),
  updated_by='migration-v61'
FROM proxy_route route, resource, action
WHERE operation.proxy_route_id=route.id
  AND route.service_slug='hr'
  AND operation.http_method='GET'
  AND resource.id=operation.resource_id
  AND action.id=operation.action_id
  AND (operation.resource_key IS DISTINCT FROM resource.resource_key
       OR operation.action_key IS DISTINCT FROM action.action_key);

INSERT INTO schema_version(component,version) VALUES('control-plane','61')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
