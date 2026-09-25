-- Ordinary actions must not materialize as subtree-wide manager tuples.
-- Deploy with the updated OpenFGA model and reconcile tuples before serving traffic.
-- Parent tuples and explicit management actions (including bootstrap admin) are unchanged.
WITH mappings(action_key, relation) AS (VALUES
  ('create','creator'), ('import','creator'), ('upload','creator'),
  ('update','editor'), ('approve','editor'), ('reject','editor'),
  ('delete','deleter'), ('share','sharer'), ('export','exporter'), ('download','exporter')
)
UPDATE authorization_grant g
SET relation=m.relation, version=g.version+1
FROM action a, mappings m
WHERE g.action_id=a.id AND a.action_key=m.action_key AND g.relation='manager';

-- Pending/retry/dead-letter events must never restore a legacy manager tuple.
-- Preserve processed events as history. Reconciliation removes old tuples and writes
-- the complete active set, retaining manager where an explicit admin grant still exists.
UPDATE outbox_event e
SET payload=jsonb_set(e.payload, '{relation}', to_jsonb(g.relation))
FROM authorization_grant g
WHERE e.aggregate_type='grant' AND e.aggregate_id=g.id
  AND e.processed_at IS NULL AND e.payload->>'relation'='manager'
  AND g.relation IN ('creator','editor','deleter','sharer','exporter');

INSERT INTO schema_version(component,version) VALUES ('control-plane','80')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
