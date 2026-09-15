-- Resource manifests describe authorization resources only. The existing immutable
-- ui_module_artifact table remains the MF manifest revision registry.
ALTER TABLE panel
  ADD COLUMN mf_manifest_url varchar(1000);

UPDATE panel SET mf_manifest_url=
  regexp_replace(remote_entry_path,'[^/]+[.]js$','mf-manifest.json')
WHERE remote_entry_path ~ '^https?://[^/?#]+/.+[.]js$';

ALTER TABLE panel
  ADD CONSTRAINT panel_mf_manifest_url_check
    CHECK(mf_manifest_url IS NULL OR mf_manifest_url ~ '^https?://[^/?#]+/.+[.]json$');

ALTER TABLE ui_module_artifact
  ADD COLUMN manifest_checksum varchar(64),
  ADD COLUMN source_url varchar(1000),
  ADD COLUMN synchronized_at timestamptz;

UPDATE ui_module_artifact
SET manifest_checksum=encode(digest(manifest_snapshot::text,'sha256'),'hex')
WHERE manifest_checksum IS NULL;

ALTER TABLE ui_module_artifact ALTER COLUMN manifest_checksum SET NOT NULL;
CREATE INDEX ui_module_artifact_panel_checksum_idx
  ON ui_module_artifact(panel_id,manifest_checksum);

-- Remove frontend presentation leftovers from manifest-owned authorization metadata.
-- Administrator-owned domain metadata remains untouched.
UPDATE resource SET metadata=metadata
  - 'route' - 'path' - 'menu' - 'navigation' - 'icon' - 'iconKey'
  - 'component' - 'remoteEntry' - 'remoteEntryUrl' - 'exposedModule'
  - 'routePrefix' - 'slug' - 'label'
WHERE source='MANIFEST';

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','55'),('microfrontend-governance','3'),('mf-manifest','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
