ALTER TABLE authorization_decision_log
  ADD COLUMN normalized_permission varchar(100),
  ADD COLUMN openfga_allowed boolean,
  ADD COLUMN policy_allowed boolean,
  ADD COLUMN latency_ms bigint,
  ADD COLUMN policy_references jsonb NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX authorization_decision_retention_idx
  ON authorization_decision_log(decided_at);
CREATE INDEX authorization_decision_correlation_idx
  ON authorization_decision_log(correlation_id, decided_at DESC);

INSERT INTO schema_version(component, version) VALUES ('control-plane', '16')
ON CONFLICT(component) DO UPDATE
SET version=excluded.version, updated_at=now();
