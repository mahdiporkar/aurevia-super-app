-- OpenFGA object identifiers cannot contain a second colon. Keep the database
-- resource key readable, but project nested external resources with '/' segments.
UPDATE outbox_event
SET payload = jsonb_set(
  payload,
  '{object}',
  to_jsonb('external_resource:' || replace(
    substring(payload->>'object' from length('external_resource:') + 1), ':', '/'))
)
WHERE processed_at IS NULL
  AND payload->>'object' LIKE 'external_resource:%:%';

UPDATE outbox_event
SET payload = jsonb_set(
  payload,
  '{user}',
  to_jsonb('external_resource:' || replace(
    substring(payload->>'user' from length('external_resource:') + 1), ':', '/'))
)
WHERE processed_at IS NULL
  AND payload->>'user' LIKE 'external_resource:%:%';

-- Retry immediately after canonicalization.
UPDATE outbox_event
SET available_at = now(), last_error = NULL
WHERE processed_at IS NULL
  AND idempotency_key LIKE 'legacy-grant-projection:%';

INSERT INTO schema_version(component, version) VALUES ('control-plane', '15')
ON CONFLICT(component) DO UPDATE
SET version = excluded.version, updated_at = now();
