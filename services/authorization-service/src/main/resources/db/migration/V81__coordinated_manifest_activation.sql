ALTER TABLE resource_manifest_import ADD CONSTRAINT resource_manifest_panel_id_unique UNIQUE(panel_id,id);
ALTER TABLE panel ADD COLUMN active_resource_manifest_id uuid;
ALTER TABLE panel ADD CONSTRAINT panel_active_resource_manifest_fk
  FOREIGN KEY(id,active_resource_manifest_id) REFERENCES resource_manifest_import(panel_id,id);
ALTER TABLE ui_module_artifact ADD COLUMN resource_manifest_id uuid;
ALTER TABLE ui_module_artifact ADD CONSTRAINT artifact_resource_manifest_fk
  FOREIGN KEY(panel_id,resource_manifest_id) REFERENCES resource_manifest_import(panel_id,id);

-- Historical UI and resource versions were independent. Neither max(version) nor the
-- latest publish proves which catalog belongs to an artifact. Leave pointers unassigned;
-- panels with published history fail closed until an operator explicitly activates a pair.
CREATE FUNCTION protect_resource_manifest_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.payload IS DISTINCT FROM OLD.payload OR NEW.checksum IS DISTINCT FROM OLD.checksum
    OR NEW.panel_id IS DISTINCT FROM OLD.panel_id
    OR NEW.manifest_version IS DISTINCT FROM OLD.manifest_version
    OR (OLD.workflow_status='PUBLISHED' AND NEW IS DISTINCT FROM OLD) THEN
    RAISE EXCEPTION 'resource manifest revisions are immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER resource_manifest_revision_immutable BEFORE UPDATE ON resource_manifest_import
  FOR EACH ROW EXECUTE FUNCTION protect_resource_manifest_revision();

CREATE FUNCTION protect_artifact_resource_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.resource_manifest_id IS NOT NULL
    AND NEW.resource_manifest_id IS DISTINCT FROM OLD.resource_manifest_id THEN
    RAISE EXCEPTION 'artifact resource manifest association is immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER artifact_resource_revision_immutable BEFORE UPDATE ON ui_module_artifact
  FOR EACH ROW EXECUTE FUNCTION protect_artifact_resource_revision();

CREATE INDEX outbox_manifest_projection_pending_idx
  ON outbox_event ((payload->>'manifestPanelId')) WHERE processed_at IS NULL;

-- A catalog change is committed before its asynchronous graph projection. Reads fail
-- closed while any event for that panel is pending, including retries/dead letters.
CREATE VIEW effective_resource_catalog AS
WITH RECURSIVE eligible AS (
  SELECT r.* FROM resource r LEFT JOIN panel p ON p.id=r.panel_id
  WHERE r.status='ACTIVE'
    AND (p.id IS NULL OR (
      NOT EXISTS (SELECT 1 FROM outbox_event e WHERE e.processed_at IS NULL
        AND e.payload->>'manifestPanelId'=p.id::text)
      AND (p.active_resource_manifest_id IS NOT NULL OR NOT EXISTS (
        SELECT 1 FROM resource_manifest_import m WHERE m.panel_id=p.id AND m.workflow_status='PUBLISHED'))))
), tree AS (
  SELECT id FROM eligible WHERE parent_id IS NULL
  UNION ALL SELECT c.id FROM eligible c JOIN tree p ON p.id=c.parent_id
) SELECT r.* FROM resource r JOIN tree t ON t.id=r.id;

-- Stored grants survive catalog rollback, but only declared active capabilities are
-- projected. Shared relations (e.g. view/list) are deduplicated by callers.
CREATE VIEW catalog_grant_tuple AS
SELECT g.resource_id,
  CASE g.subject_type WHEN 'USER' THEN 'user:'||u.canonical_user_id
    WHEN 'GROUP' THEN 'group:directory/'||dg.id||'#member'
    WHEN 'ACCESS_GROUP' THEN 'group:'||lower(ag.code)||'#member'
    WHEN 'ROLE' THEN 'role:'||ar.role_key||'#assignee' END AS "user",
  g.relation,
  CASE r.type WHEN 'APPLICATION' THEN 'application:'||regexp_replace(r.resource_key,'^application:','')
    WHEN 'EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    ELSE 'resource:'||replace(r.resource_key,':','/') END AS object
FROM authorization_grant g JOIN resource r ON r.id=g.resource_id
  JOIN resource_action ra ON ra.resource_id=r.id AND ra.action_id=g.action_id
  LEFT JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
  LEFT JOIN directory_group dg ON g.subject_type='GROUP' AND dg.id=g.subject_id
  LEFT JOIN access_group ag ON g.subject_type='ACCESS_GROUP' AND ag.id=g.subject_id
  LEFT JOIN application_role ar ON g.subject_type='ROLE' AND ar.id=g.subject_id
WHERE r.status='ACTIVE' AND g.status='ACTIVE' AND (g.expires_at IS NULL OR g.expires_at>now())
  AND ((g.subject_type='USER' AND u.id IS NOT NULL) OR (g.subject_type='GROUP' AND dg.status='ACTIVE')
    OR (g.subject_type='ACCESS_GROUP' AND ag.active) OR (g.subject_type='ROLE' AND ar.status='ACTIVE'));

INSERT INTO schema_version(component,version) VALUES ('control-plane','81')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
