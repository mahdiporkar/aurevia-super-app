# ADR 0008: Public Superset dual enforcement

Status: Accepted. OpenFGA is the super-app grant source, while native Superset viewer/editor assignments prevent known direct URLs from bypassing a revoked grant. Changes use an idempotent transactional outbox and visible PENDING/APPLIED/FAILED/REVOKING/DRIFTED states. The integration uses supported APIs or a narrow authenticated extension—never metadata-database writes.
