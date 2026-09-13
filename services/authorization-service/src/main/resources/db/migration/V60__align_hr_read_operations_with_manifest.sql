-- The HR manifest exposes employee view and independent reference-page view.
-- Older proxy operations still required list on obsolete reference resources.
UPDATE route_operation operation SET
  resource_id=resource.id,
  action_id=action.id,
  version=operation.version+1,
  updated_at=now(),
  updated_by='migration-v60'
FROM proxy_route route, resource, action
WHERE operation.proxy_route_id=route.id
  AND route.service_slug='hr'
  AND operation.http_method='GET'
  AND action.action_key='view'
  AND (
    (operation.normalized_path_pattern IN ('/employees','/employees/*')
      AND resource.resource_key='business:hr.employee')
    OR (operation.normalized_path_pattern IN ('/departments','/departments/*')
      AND resource.resource_key='page:hr.departments')
    OR (operation.normalized_path_pattern IN ('/positions','/positions/*')
      AND resource.resource_key='page:hr.positions')
  )
  AND (operation.resource_id IS DISTINCT FROM resource.id
       OR operation.action_id IS DISTINCT FROM action.id);

INSERT INTO schema_version(component,version) VALUES('control-plane','60')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
