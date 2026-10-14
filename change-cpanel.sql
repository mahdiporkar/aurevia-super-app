BEGIN;

-- Update the panel registration
UPDATE panel
SET
  remote_entry_path     = 'http://localhost:3001/remoteEntry.js',
  name_en               = 'aurevia_cpanel_ui',
  mf_manifest_url       = 'http://localhost:3001/mf-manifest.json',
  resource_manifest_url = 'http://localhost:3001/resource-manifest.json',
  version               = version + 1,
  updated_at            = now()
WHERE slug = 'admin';

-- Update ONLY the active artifact used at runtime
UPDATE ui_module_artifact a
SET remote_entry_url = 'http://localhost:3001/remoteEntry.js'
  FROM panel p
WHERE p.slug = 'admin'
  AND p.active_artifact_id = a.id;

-- Verify exactly what runtime will use
SELECT
  p.slug,
  p.name_en,
  p.remote_entry_path AS panel_url,
  p.mf_manifest_url,
  p.resource_manifest_url,
  p.active_artifact_id,
  a.artifact_version,
  a.remote_entry_url AS runtime_url,
  a.remote_name,
  a.validation_status
FROM panel p
       LEFT JOIN ui_module_artifact a
                 ON a.id = p.active_artifact_id
WHERE p.slug = 'admin';

COMMIT;
