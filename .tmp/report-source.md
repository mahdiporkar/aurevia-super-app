# Canonical research notes — Super App governance guide

Date: 2026-09-08

## Scope

Create a Persian, zero-to-operations guide for every administrator-facing form and field in
`apps/mfe-admin`, grounded in the UI implementation and backend contracts. The public artifact is
`docs/operator-admin-form-field-guide-fa.md`.

## Primary evidence

- `apps/mfe-admin/src/admin-route-catalog.ts`: complete administrator route inventory and required permissions.
- `apps/mfe-admin/src/AccessStudio.tsx`: resource, action, subject and grant semantics.
- `apps/mfe-admin/src/OuAccessManagement.tsx`: OU access groups, rules, previews and application grants.
- `apps/mfe-admin/src/Panels.tsx`: panel, immutable artifact, resource-manifest and navigation forms.
- `apps/mfe-admin/src/ProxyRoutes.tsx`: target, route, operation and route-resolution probes.
- `apps/mfe-admin/src/OutboundConnections.tsx` and `OutboundAuthProfiles.tsx`: outbound allowlist and auth profiles.
- `apps/mfe-admin/src/IntegrationTestLab.tsx`: safe connectivity test workflow.
- `apps/mfe-admin/src/SupersetInstances.tsx` and `SupersetAssets.tsx`: Superset topology and asset grants.
- `apps/mfe-admin/src/IdentityAndRoles.tsx`: synchronized identities, application roles and assignments.
- `apps/mfe-admin/src/Logs.tsx`: API and audit filters.
- Registry/Authz controller DTOs and validation under `services/registry-service` and `services/authorization-service`.
- `docs/resource-catalog-manifest-architecture-fa.md`: ownership and publication architecture.

## Editorial decisions

- Describe only controls implemented in the repository; distinguish UI validation from deployment policy.
- Treat PostgreSQL + Outbox + OpenFGA propagation as eventually consistent and require verification.
- Prefer disable/deprecate and version activation over destructive recovery.
- Explain field purpose, permitted value, example, dependency, operational risk and verification.
- Add zero-level concepts, prerequisites, end-to-end recipes, troubleshooting and evidence links.

