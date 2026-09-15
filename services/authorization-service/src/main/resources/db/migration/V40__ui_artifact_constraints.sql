ALTER TABLE ui_module_artifact
  ADD CONSTRAINT ui_artifact_remote_entry_format
    CHECK(remote_entry_url ~ '^https?://[^/?#]+/.+[.]js$'),
  ADD CONSTRAINT ui_artifact_exposed_module_format
    CHECK(exposed_module ~ '^[.]/[A-Za-z][A-Za-z0-9_./-]*$'),
  ADD CONSTRAINT ui_artifact_integrity_format
    CHECK(integrity IS NULL OR integrity ~ '^sha(256|384|512)-[A-Za-z0-9+/]+={0,2}$');

INSERT INTO schema_version(component,version) VALUES ('ui-artifact-registry','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
