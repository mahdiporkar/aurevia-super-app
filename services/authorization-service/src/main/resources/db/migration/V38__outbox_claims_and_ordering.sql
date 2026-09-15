ALTER TABLE outbox_event
  ADD COLUMN sequence bigserial,
  ADD COLUMN claimed_at timestamptz,
  ADD COLUMN claim_owner uuid;

ALTER TABLE outbox_event ALTER COLUMN sequence SET NOT NULL;
CREATE UNIQUE INDEX outbox_sequence_idx ON outbox_event(sequence);
CREATE INDEX outbox_claimable_idx ON outbox_event(available_at,sequence)
  WHERE processed_at IS NULL AND dead_lettered_at IS NULL;

INSERT INTO schema_version(component,version) VALUES ('outbox-processor','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
