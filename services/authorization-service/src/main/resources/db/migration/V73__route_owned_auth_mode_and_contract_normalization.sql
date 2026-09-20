-- 1. Authentication mode belongs to the Proxy Route (V68), not to the Service Target.
--    The Target's profile column was a pre-V68 default that made the same concept exist in
--    two places and forced administrators to pick an auth profile for a network destination.
--    It becomes optional and is no longer consulted at runtime (resolution joins the route).
ALTER TABLE service_target ALTER COLUMN outbound_auth_profile_id DROP NOT NULL;
COMMENT ON COLUMN service_target.outbound_auth_profile_id IS
  'Deprecated since V73: authentication mode is defined per proxy_route. Kept for history only.';

-- 2. Demo registrations that predate the "1.0" contract used the bare value "1" while the
--    validator, the Shell and every newer artifact use "1.0". Normalize so demo and newly
--    registered micro frontends follow one contract.
UPDATE panel SET contract_version='1.0', version=version+1 WHERE contract_version='1';
UPDATE ui_module_artifact SET contract_version='1.0' WHERE contract_version='1';

INSERT INTO schema_version(component,version) VALUES ('control-plane','73')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
