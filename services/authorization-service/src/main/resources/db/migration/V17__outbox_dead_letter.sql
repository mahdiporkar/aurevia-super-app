ALTER TABLE outbox_event
  ADD COLUMN dead_lettered_at timestamptz;

DROP INDEX outbox_pending_idx;
CREATE INDEX outbox_pending_idx ON outbox_event(available_at, created_at)
  WHERE processed_at IS NULL AND dead_lettered_at IS NULL;
CREATE INDEX outbox_dead_letter_idx ON outbox_event(dead_lettered_at)
  WHERE dead_lettered_at IS NOT NULL;

INSERT INTO schema_version(component, version) VALUES ('control-plane', '17')
ON CONFLICT(component) DO UPDATE
SET version=excluded.version, updated_at=now();
