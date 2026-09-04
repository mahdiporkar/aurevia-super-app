-- OpenFGA uses ':' as the object type/id delimiter. The previous canonical
-- subject format (v1:<sha256>) therefore produced malformed user objects when
-- prefixed with "user:". Preserve the deterministic hash but use an identifier-
-- safe version separator.
CREATE OR REPLACE FUNCTION aurevia_subject_key(p_issuer text, p_subject text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
  SELECT 'v1_' || encode(digest(
    convert_to(trim(p_issuer), 'UTF8') || decode('00', 'hex') ||
    convert_to(trim(p_subject), 'UTF8'), 'sha256'), 'hex')
$$;

-- Stored generated columns are recalculated on UPDATE. This keeps the column,
-- index and all application references intact while atomically re-keying users.
UPDATE app_user SET issuer=issuer WHERE subject_key LIKE 'v1:%';

-- Repair only unprocessed/dead-lettered canonical events. Successful historical
-- events remain immutable audit records; malformed v1:* tuples could never have
-- been accepted by OpenFGA.
UPDATE outbox_event
SET payload=jsonb_set(payload,'{user}',to_jsonb(replace(payload->>'user','user:v1:','user:v1_'))),
    attempts=0,
    available_at=now(),
    last_error=NULL,
    dead_lettered_at=NULL,
    claimed_at=NULL,
    claim_owner=NULL
WHERE processed_at IS NULL AND payload->>'user' LIKE 'user:v1:%';

INSERT INTO schema_version(component,version) VALUES ('canonical-subject','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
