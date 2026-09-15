-- OpenFGA v1.18 rejects ':' inside the object-id portion. Keep the type separator only.
UPDATE outbox_event
SET payload=jsonb_set(payload,'{object}',to_jsonb('resource:'||replace(
      substring(payload->>'object' from length('resource:')+1),':','/')))
WHERE payload->>'object' LIKE 'resource:%:%';

UPDATE outbox_event
SET payload=jsonb_set(payload,'{user}',to_jsonb('resource:'||replace(
      substring(payload->>'user' from length('resource:')+1),':','/')))
WHERE payload->>'user' LIKE 'resource:%:%';

-- Requeue tuples that failed under the former invalid representation.
UPDATE outbox_event
SET attempts=0,available_at=now(),dead_lettered_at=null,last_error=null
WHERE dead_lettered_at is not null
  AND ((payload->>'object') LIKE 'resource:%' OR (payload->>'user') LIKE 'resource:%');

INSERT INTO schema_version(component,version) VALUES('control-plane','21')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
