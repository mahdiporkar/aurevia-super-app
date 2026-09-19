-- Micro frontend discoverability through an owned resource subtree.
--
-- A micro frontend whose landing route declares an application-level resource became
-- invisible to subjects that legitimately hold access to one of the resources the panel
-- exists to present (for example a single Superset dashboard). The previous behaviour was
-- compensated for in Java by matching the literal "reports" slug and the
-- "application:aurevia/reports" resource key, which is neither declarative nor reusable.
--
-- discovery_resource_key makes the relationship explicit registry data: a subject holding
-- any effective permission on a descendant of that resource keeps the panel discoverable,
-- while every route and every runtime request continues to authorize its own resource.
ALTER TABLE panel
  ADD COLUMN discovery_resource_key varchar(500)
    REFERENCES resource(resource_key) ON DELETE RESTRICT;

COMMENT ON COLUMN panel.discovery_resource_key IS
  'Resource subtree whose authorized descendants keep this micro frontend discoverable. '
  'Grants no permission by itself; route and runtime authorization are unchanged.';

-- The Reports micro frontend exists to present the Superset catalog.
UPDATE panel SET discovery_resource_key='external_resource:superset-public'
WHERE code='REPORTS'
  AND EXISTS(SELECT 1 FROM resource WHERE resource_key='external_resource:superset-public');

INSERT INTO schema_version(component,version) VALUES ('control-plane','71')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
