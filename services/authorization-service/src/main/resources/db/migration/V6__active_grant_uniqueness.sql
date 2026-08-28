CREATE UNIQUE INDEX authorization_grant_active_unique_idx
ON authorization_grant(subject_type, subject_id, resource_id, action_id)
WHERE status = 'ACTIVE';

INSERT INTO schema_version(component, version)
VALUES ('control-plane', '6')
ON CONFLICT(component)
DO UPDATE SET version = excluded.version, updated_at = now();
