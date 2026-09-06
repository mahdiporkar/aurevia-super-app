-- A manifest version identifies immutable content for one registered MFE. The checksum remains
-- useful for idempotency, but a second payload must use a new semantic version.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM resource_manifest_import
    WHERE panel_id IS NOT NULL
    GROUP BY panel_id,manifest_version HAVING count(*)>1
  ) THEN
    RAISE EXCEPTION 'duplicate resource manifest versions must be resolved before V52';
  END IF;
END $$;

CREATE UNIQUE INDEX resource_manifest_panel_version_unique_idx
  ON resource_manifest_import(panel_id,manifest_version)
  WHERE panel_id IS NOT NULL;

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','52'),('microfrontend-governance','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
