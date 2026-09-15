-- OpenFGA rejects deletion of an already absent tuple. The projection adapter
-- now treats absence as the successful final state, so replay only delete
-- events that exhausted retries under the former non-idempotent behaviour.
UPDATE outbox_event
SET attempts=0,
    available_at=now(),
    last_error=NULL,
    dead_lettered_at=NULL,
    claimed_at=NULL,
    claim_owner=NULL
WHERE processed_at IS NULL
  AND dead_lettered_at IS NOT NULL
  AND event_type IN (
    'GRANT_DELETE',
    'ROLE_ASSIGNMENT_DELETE',
    'RESOURCE_PARENT_DELETE',
    'GROUP_MEMBERSHIP_DELETE',
    'ACCESS_GROUP_MEMBERSHIP_DELETE',
    'APPLICATION_GROUP_GRANT_DELETE'
  );

INSERT INTO schema_version(component,version)
VALUES ('openfga-idempotent-delete','1')
ON CONFLICT(component) DO UPDATE
SET version=excluded.version,updated_at=now();
