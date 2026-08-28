# Operational runbooks

## Redis or token-vault failure

Fail login/refresh closed, keep tokens out of errors, mark readiness unhealthy, and do not fall back to browser storage. Restore Redis/TLS/ACL, validate TTLs, rotate the active envelope key if exposure is suspected, revoke affected Keycloak sessions, and verify deletion of session plus vault handle.

## OpenFGA outage or model rollout

Sensitive actions fail closed. Read-only cached decisions may survive only their short versioned TTL. Stop grant publishing, preserve the outbox, restore service, reconcile tuples, and compare model tests before resuming. Roll back by switching the configured model ID and invalidating decision/manifest caches.

## Outbox backlog or Superset drift

Alert on age and attempts, inspect redacted errors, repair identity/asset mappings, replay by idempotency key, then run reconciliation. A report grant is not operational until both OpenFGA and Superset are APPLIED. Revocations remain REVOKING and launch stays denied until both sides confirm removal.

## Certificate rotation

Issue overlapping mTLS certificates, load from the secret provider, reload BFF clients, verify gateway identity and expiry metrics, then revoke the old certificate. Never commit a keystore or password.

## Compromised session

Delete the session and token-vault record, revoke refresh/access tokens where supported, terminate the Public IAM session, invalidate subject caches, preserve safe audit evidence, rotate keys only if vault compromise is suspected, and notify according to incident policy.
