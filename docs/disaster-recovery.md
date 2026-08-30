# Disaster recovery runbook

This runbook contains examples only. Credentials must come from the deployment secret store.
Operations must fill in the approved values for **RPO: TBD** and **RTO: TBD**.

## Protected state

1. Authorization PostgreSQL is the relationship control-plane source of truth, including
   resources, grants, policies, audit records and the transactional outbox.
2. Keycloak PostgreSQL contains identities, clients and sessions.
3. Operation Superset PostgreSQL contains dashboards, charts, datasets and native RBAC.
4. OpenFGA PostgreSQL is a rebuildable runtime projection, but backing it up reduces RTO.
5. Redis contains transient sessions, rate-limit/cache state and encrypted token-vault values.
   Redis loss logs users out and discards refresh capability; it must never grant access.

## Backup examples

Run consistent encrypted backups from an isolated operations workload:

```sh
pg_dump --format=custom --dbname="$AUTH_DATABASE_URL" --file=authorization.dump
pg_dump --format=custom --dbname="$KEYCLOAK_DATABASE_URL" --file=keycloak.dump
pg_dump --format=custom --dbname="$SUPERSET_DATABASE_URL" --file=superset.dump
pg_dump --format=custom --dbname="$OPENFGA_DATABASE_URL" --file=openfga.dump
```

Encrypt, checksum, replicate and retention-lock the artifacts according to organizational
policy. Test that backup identities have read-only backup privileges and cannot administer
the applications.

## Restore order

1. Stop ingress, BFF, Authorization Service, Superset and outbox workers.
2. Restore Authorization, Keycloak and Superset metadata databases.
3. Restore the OpenFGA database when usable; otherwise migrate an empty OpenFGA database,
   deploy the pinned model, and run OpenFGA reconciliation first in dry-run and then repair.
4. Validate Flyway history and application schema compatibility.
5. Start OpenFGA, Authorization Service and reconciliation/outbox processing.
6. Require zero unexplained missing/unexpected tuples before opening BFF traffic.
7. Start Keycloak, Superset, BFF and ingress; execute negative authorization smoke tests.
8. Redis may start empty. All sessions must be treated as expired and users must log in again.

## Encryption-key implications

Loss of the token-vault encryption key makes existing encrypted access/refresh tokens
unrecoverable. Do not restore an old key unless its custody and compromise status are known.
When a key is unavailable or suspect, flush only the token-vault/session namespaces through an
approved targeted procedure and require reauthentication. Never weaken decryption validation.

## OpenFGA projection rebuild

The admin-only endpoint defaults to dry-run:

```http
POST /api/v1/admin/operations/openfga-reconcile?repair=false
```

Review every unexpected tuple. After approval, use `repair=true`, repeat dry-run, and archive
the correlation ID and report. Repair requires healthy Redis because the graph epoch must be
advanced before tuple mutation.

## Quarterly restore exercise

Once per quarter, restore the latest backups into an isolated environment, record actual RPO
and RTO, validate checksums and Flyway state, rebuild/compare OpenFGA, verify Superset assets,
rotate test secrets, and run allow plus deny/revoke/inheritance tests. Record owners, timings,
deviations and corrective actions. A backup without a successful restore test is not accepted.
