-- OpenFGA permits one ':' between type and identifier. Historical Superset
-- events embedded a second ':' in identifiers such as dashboard:11. Runtime
-- serializers already use slash-separated identifiers; repair and replay the
-- remaining outbox records without discarding their audit identity.
UPDATE outbox_event
SET payload=jsonb_set(
      payload,
      '{object}',
      to_jsonb(
        split_part(payload->>'object',':',1) || ':' ||
        replace(substring(payload->>'object'
          from position(':' in payload->>'object') + 1),':','/')
      )
    ),
    attempts=0,
    available_at=now(),
    last_error=NULL,
    dead_lettered_at=NULL,
    claimed_at=NULL,
    claim_owner=NULL
WHERE processed_at IS NULL
  AND payload ? 'object'
  AND position(':' in payload->>'object') > 0
  AND position(':' in substring(payload->>'object'
      from position(':' in payload->>'object') + 1)) > 0;

INSERT INTO schema_version(component,version) VALUES ('openfga-object-serialization','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
