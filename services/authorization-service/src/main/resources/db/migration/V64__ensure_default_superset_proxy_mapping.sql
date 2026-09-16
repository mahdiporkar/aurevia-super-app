-- Reports without an explicit instance use the default logical Superset
-- mapping. Repair environments where an active mapping was saved without a
-- default; the repository also has a runtime fallback for future resilience.
UPDATE superset_proxy_mapping
SET is_default=true,version=version+1,updated_at=now(),updated_by='migration-v64'
WHERE id=(
  SELECT id FROM superset_proxy_mapping
  WHERE active
  ORDER BY updated_at DESC,id
  LIMIT 1
)
AND NOT EXISTS(
  SELECT 1 FROM superset_proxy_mapping WHERE active AND is_default
);

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','64'),('superset-registry','4')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
