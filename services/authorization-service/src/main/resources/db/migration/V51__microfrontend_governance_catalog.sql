-- Evolve the existing panel/resource/artifact/override model into the governed MFE catalog.
-- panel is the canonical micro_frontend registration; no parallel identity table is needed.
ALTER TABLE panel
  ADD COLUMN resource_definition_mode varchar(16) NOT NULL DEFAULT 'HYBRID',
  ADD COLUMN classification varchar(8) NOT NULL DEFAULT 'REAL',
  ADD COLUMN resource_manifest_url varchar(1000);

UPDATE panel SET classification='DEMO'
WHERE code IN ('HR','FINANCE','REPORTS','GOVERNANCE');

UPDATE panel SET resource_manifest_url=
  regexp_replace(remote_entry_path,'[^/]+[.]js$','resource-manifest.json')
WHERE remote_entry_path ~ '^https?://[^/?#]+/.+[.]js$';

ALTER TABLE panel
  ADD CONSTRAINT panel_resource_definition_mode_check
    CHECK(resource_definition_mode IN ('MANIFEST','MANUAL','HYBRID')),
  ADD CONSTRAINT panel_classification_check
    CHECK(classification IN ('DEMO','REAL')),
  ADD CONSTRAINT panel_resource_manifest_url_check
    CHECK(resource_manifest_url IS NULL OR resource_manifest_url ~ '^https?://[^/?#]+/.+[.]json$');

-- A resource can now be traced to its owning MFE and manifest revision. Historical
-- sources are normalized to the public ownership vocabulary without deleting rows.
ALTER TABLE resource
  ADD COLUMN panel_id uuid REFERENCES panel(id) ON DELETE RESTRICT,
  ADD COLUMN manifest_version varchar(100),
  ADD COLUMN visibility_enabled boolean NOT NULL DEFAULT true;

UPDATE resource SET source=CASE source
  WHEN 'APPLICATION_MANIFEST' THEN 'MANIFEST'
  WHEN 'EXTERNAL_SYNC' THEN 'MANIFEST'
  WHEN 'SYSTEM' THEN 'ADMIN'
  ELSE source END;

UPDATE resource r SET panel_id=p.id
FROM panel p
WHERE r.panel_id IS NULL AND (
  lower(coalesce(r.owner_domain,'')) IN (lower(p.slug),lower(p.service_slug),lower(p.code))
  OR r.resource_key='application:aurevia/'||p.slug
);

-- Rows linked during this one-time backfill are the pre-governance MFE seed catalog.
-- Future administrator-created HYBRID rows keep source=ADMIN and are never overwritten.
UPDATE resource SET source='MANIFEST'
WHERE panel_id IS NOT NULL AND source='ADMIN';

ALTER TABLE resource
  ADD CONSTRAINT resource_source_check CHECK(source IN ('MANIFEST','ADMIN'));
CREATE INDEX resource_panel_idx ON resource(panel_id,status,resource_key);

-- resource_manifest_import is retained as the single revision/approval ledger. Existing
-- rows were already published by earlier versions, so they are migrated as PUBLISHED.
ALTER TABLE resource_manifest_import
  ADD COLUMN panel_id uuid REFERENCES panel(id) ON DELETE RESTRICT,
  ADD COLUMN schema_version varchar(30) NOT NULL DEFAULT '1.0',
  ADD COLUMN source_url varchar(1000),
  ADD COLUMN workflow_status varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  ADD COLUMN diff_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN published_at timestamptz,
  ADD COLUMN published_by varchar(255),
  ADD COLUMN rejected_at timestamptz,
  ADD COLUMN rejected_by varchar(255);

UPDATE resource_manifest_import
SET published_at=imported_at,published_by=imported_by
WHERE workflow_status='PUBLISHED' AND published_at IS NULL;

ALTER TABLE resource_manifest_import
  ADD CONSTRAINT resource_manifest_workflow_status_check
    CHECK(workflow_status IN ('DRAFT','PUBLISHED','REJECTED'));
CREATE INDEX resource_manifest_panel_workflow_idx
  ON resource_manifest_import(panel_id,workflow_status,created_at DESC);

-- Manifest navigation remains inside the immutable manifest JSON. This table stores only
-- operator overlays and operator-owned navigation nodes, never a copy of manifest nodes.
ALTER TABLE ui_menu_override
  ADD COLUMN source varchar(16) NOT NULL DEFAULT 'MANIFEST',
  ADD COLUMN node_type varchar(20),
  ADD COLUMN parent_key varchar(100),
  ADD COLUMN page_key varchar(160),
  ADD COLUMN external_url varchar(1000),
  ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE ui_menu_override SET node_type='PAGE' WHERE node_type IS NULL;
ALTER TABLE ui_menu_override
  ADD CONSTRAINT ui_navigation_override_source_check CHECK(source IN ('MANIFEST','ADMIN')),
  ADD CONSTRAINT ui_navigation_override_type_check CHECK(node_type IN ('GROUP','PAGE','EXTERNAL_LINK')),
  ADD CONSTRAINT ui_navigation_override_status_check CHECK(status IN ('ACTIVE','DEPRECATED')),
  ADD CONSTRAINT ui_navigation_external_url_check
    CHECK(external_url IS NULL OR external_url ~ '^https?://');

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','51'),('microfrontend-governance','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
