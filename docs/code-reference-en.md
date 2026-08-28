# File-by-File Code Reference

This document explains every hand-written area by responsibility and logical block. Generated `*.d.ts` files contain no independent runtime logic.

## Repository root

| File | Responsibility |
|---|---|
| `pom.xml` | Maven reactor and shared Java versions |
| `package.json` | npm workspaces and frontend commands |
| `package-lock.json` | exact npm dependency graph |
| `tsconfig.base.json` | shared TypeScript rules |
| `.nvmrc` | pinned Node.js version |
| `.env.example` | environment-variable contract without real secrets |

## Shell and shared packages

- `apps/shell/src/index.tsx` initializes RTL styling, loads the user manifest, creates navigation, and mounts remote MFEs.
- `remote-loader.ts` injects `remoteEntry.js`, initializes the Module Federation share scope, resolves the exposed module, and reports isolated loading failures.
- `packages/contracts` contains shared panel/manifest/permission types.
- `packages/i18n` contains Persian and English translations.
- `packages/sh-core-ui` contains shared UI and permission-aware presentation controls.

## Micro frontends

### Administration

- `bootstrap.tsx` mounts the administration application.
- `Panels.tsx` loads panel registry data and implements loading, retry, error, form, and active-state UI.
- `SupersetAssets.tsx` registers reports/dashboards and their internal resources.

Admin mutations obtain a CSRF token from `/api/v1/csrf` and call `/api/v1/admin/**`.

### Reports

`apps/mfe-reports/src/bootstrap.tsx` calls `/api/v1/reports`, renders authorized assets, handles empty/error states, and opens each `url_path` below `/reports-runtime`.

### HR and Finance

These are currently small Module Federation examples. Future operational calls must use registered BFF routes and must not call the Operation Gateway directly.

## Super App BFF

### Security and token storage

- `SuperappBffApplication.java`: Spring Boot entry point.
- `security/SecurityConfig.java`: public login/health paths, authentication for everything else, Spring CSRF, Superset CSRF exception, and logout integration.
- `TokenVaultCrypto`: AES-GCM encryption and key IDs.
- `TokenVaultService`: Redis token record and TTL management.
- `RefreshCoordinator`: prevents concurrent refresh storms.
- `VaultLogoutHandler`: removes vault data during logout.

### Controllers

| Class | Route | Responsibility |
|---|---|---|
| `CsrfController` | `/api/v1/csrf` | exposes a Spring CSRF token |
| `MeController` | `/api/v1/me`, `/api/v1/me/manifest` | current identity and manifest |
| `AdminProxyController` | `/api/v1/admin/**` | control-plane proxy |
| `ReportsController` | `/api/v1/reports` | authorized report catalog |
| `OperationSupersetProxyController` | `/api/v1/superset/**` | secured Operation Superset tunnel |

### Operation Superset proxy execution order

1. Request and response header allowlists are declared.
2. The gateway URL is validated by `RouteNormalizer.allowlistedTarget`.
3. Automatic redirects are disabled so the browser receives and follows rewritten locations.
4. Incoming paths are normalized and query strings preserved.
5. Authenticated subject, correlation ID, forwarded prefix, protocol, and host are added.
6. Request bodies are streamed only for POST/PUT/PATCH.
7. Upstream status and selected headers are copied.
8. The upstream body is consumed inside `exchangeToMono`; returning it for later subscription would release the response and produce an empty page.
9. Root-relative `Location` values are rewritten below `/reports-runtime`.

`RouteNormalizer` blocks invalid destinations/path traversal. `ProxyRetryPolicy` limits retries to safe transient cases. `AuthorizationServiceClient` is the internal manifest/decision client.

## Authorization Service

- `config/SecurityConfig.java`: health is public; internal APIs require BFF Basic Auth.
- `AuthorizationController`: single/batch relationship checks and subject manifest.
- `RegistryController`: panel CRUD, optimistic locking, logical archive, audit lookup, and panel outbox events.
- `AccessAdminController`: resource/action/user/grant administration and audit.
- `SupersetAssetController`: transactional creation and listing of Superset assets and backing resources.
- `StructuredPolicyEvaluator`: allowlisted fields, operators, obligations, and fail-closed behavior.
- `OperationalRules`: organization scope and payment separation of duties.
- `RelationshipAuthorizationPort`: domain boundary for relationship decisions.
- `OpenFgaRelationshipAdapter` and `OpenFgaConfiguration`: OpenFGA integration.
- `OutboxReconciler`: scheduled pending-event processing.

`AccessAdminController` is currently formatted too densely. Its behavior is documented here, but a readability-only refactor is recommended.

## Database migrations

- `V1__control_plane.sql`: enums, users, groups, roles, resources, grants, policies, Superset mapping, audit, and outbox.
- `V2__bootstrap_catalog.sql`: initial panels, actions, and root resources.
- `V3__development_users_and_actions.sql`: development users and action bindings.
- `V4__superset_report_catalog.sql`: sample dashboard and initial grants.

Never modify an already-applied migration; add a new versioned migration.

## Infrastructure

- `infra/nginx/nginx.conf`: public entry point, CSP, static files, MFE caching, BFF routing, and Superset tunnel routing.
- `infra/superset-public/Dockerfile`: copies Superset and Flask-AppBuilder static assets into an Nginx-only image.
- `infra/superset-public/nginx.conf`: serves only `/static` and `/health`; everything else is 404.
- `infra/superset-operation/superset_config.py`: database, remote-user auth, isolated cookie, ProxyFix, and telemetry settings.
- `infra/mock-operation/gateway.conf`: private `/superset/` gateway route on port 80.
- `infra/keycloak/realm-aurevia.json`: local realm, client, and development identities.
- `infra/openfga/model.fga`: types, relationships, and derived permissions.
- `infra/openfga/model-tests.yaml`: direct, group-role, and default-deny checks.

## Tests

Java tests cover token encryption, path normalization, retry safety, CSRF contracts, structured policy decisions, organizational filtering, and separation of duties. OpenFGA model tests prove group-role inheritance, direct grants, and denial for unrelated users.
