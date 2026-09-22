# Environments: Production Core, Development demo, E2E fixtures

```text
Repository
├── Aurevia Core            → PRODUCTION  = Core only (clean by construction)
├── Development / demo      → DEVELOPMENT = Core + demo fixtures + mock services
└── E2E / test              → E2E         = Development + test services/MFEs registered at runtime
```

Demo and test data are **not installed** in production — not hidden, not disabled: a fresh production
database never receives them.

## Database: Core baseline vs historical chain vs fixtures

| Location (in `services/authorization-service/src/main/resources/`) | Role | Who runs it |
|---|---|---|
| `db/migration/V1..V79` | Historical versioned chain (Core **mixed with** demo seeds, immutable) | Existing databases only (upgrade path); repository tests that need the demo catalog |
| `db/baseline/B79__aurevia_core_baseline.sql` | **Core-only Flyway baseline** = the exact schema + Core seed of the chain at V79 with every demo row removed and the OpenFGA outbox regenerated for Core | Flyway, automatically, on every **empty** database (production and development alike) |
| `db/fixtures/development/010-development-demo.sql` | Demo micro frontends (HR, FINANCE, REPORTS registrations), demo resource tree, demo users/roles/grants, demo routes, Superset demo instances/assets and their outbox events | `demo-fixture-init` in `compose.development.yml` only |
| `db/fixtures/development/020-development-integration-catalog.sql` | Legacy/OAuth2 integration demo (mock targets, routes) | same |
| `db/fixtures/development/030-development-administrator-grants.sql` | Gives the bootstrapped first administrator the demo grants the retired demo identity used to have | same |

How Flyway chooses (`spring.flyway.locations=classpath:db/migration,classpath:db/baseline`):

- **Empty database** → Flyway applies `B79` (baseline migration) and skips `V1..V79`. Later migrations
  (`V80+`) apply normally. This is what production gets.
- **Database that already carries the chain** → `B79` is ignored; `V80+` apply as before. Existing
  development, E2E and any deployed environment keep their history and data untouched.

Migration strategy decision: the chain mixes Core and demo in ~40 migrations and is treated as
immutable by the project (repair migrations, "keep historical migrations immutable" comments,
fresh-install docs). Rewriting it (Case A) was rejected; the baseline (Case B) gives clean fresh
installs without touching applied history. Future migrations must be written so that they are
correct on both a baseline-installed (Core-only) and a chain-installed (Core+demo) database; demo
content changes belong in the fixtures, not in new `V` migrations.

Regenerating (whenever a new `V` migration changes **Core** seed data):

```bash
npm run db:baseline:generate     # tools/generate-core-baseline.mjs → B<n>__ + 010-development-demo.sql
```

The generator migrates the chain into a throwaway PostgreSQL container, applies
`tools/baseline/core-classification.sql` (explicit keep-lists of stable keys, no name patterns),
dumps Core as the baseline and the removed rows as the development fixture. Both files come from
one run, so their ids match. `CoreBaselineInstallationIntegrationTest` proves from an empty
database that (1) the baseline contains no demo/test artifact and every Core entity, (2) baseline +
fixture restores the demo, idempotently, and (3) baseline + fixture equals the chain by natural keys,
including the reconciler's expected OpenFGA relationships.

### What a fresh production database contains

| Entity | Why it is Core |
|---|---|
| full schema, types, functions, indexes | platform |
| all 24 actions | authorization vocabulary used by manifests and admin pages |
| `application:aurevia`, `application:aurevia/admin`, `application:aurevia/reports` | platform root (first-admin target), Admin Panel, Reports application referenced by the Superset role model |
| `module:admin.superset-catalog`, `business_resource:public-zone-logs`, `integration.auth-profile`, `proxy.target/route/operation` | resources the Admin Panel pages authorize against |
| `external_resource:superset-public` | Superset integration catalog root (`SupersetInstanceService`/`SupersetAssetService` depend on it) |
| panel `ADMIN` + its 6 published MF manifests (active 0.6.0) | the Admin Panel micro frontend |
| roles `aurevia-administrator`, `superset-designer`, `superset-viewer` + their 6 grants | platform role model (Superset designer/viewer) |
| outbound auth profile `public-iam-forward` | default token-forwarding profile for proxy routes |
| 13 outbox events | project the Core hierarchy and role grants into OpenFGA |
| `schema_version` markers | initialization metadata used by tooling |

No users, no demo panels/resources/grants/routes/targets, no Superset instances/assets, no
identity providers. The first administrator comes from `AUREVIA_BOOTSTRAP_ADMIN_SUB`
(see [primary-authentication-and-first-admin-bootstrap.md](primary-authentication-and-first-admin-bootstrap.md));
micro frontends are registered through the Admin Panel (panel → MF manifest sync → resource
manifest → grants). Note: the `ADMIN` panel's remote entry URL in the baseline is the repository's
local address (`http://localhost:3001`); update it through the registry API for your deployment.

## Docker Compose

| Mode | Files | Command |
|---|---|---|
| **Production-shaped Core** | `infra/docker-compose/compose.yml` | `npm run infra:core:up` / `npm run infra:core:down`; verify with `npm run infra:core:verify` and `npm run e2e:production-core` |
| **Development** | `compose.yml` + `compose.development.yml` (+ `compose.identity-demo.yml` for Keycloak, `compose.mfe-demo.yml` to serve the MFEs) | `npm run identity:up && npm run infra:up && npm run mfe:up` |
| **E2E (auth suite)** | development files + `compose.e2e-auth-core.yml`, then `compose.e2e-auth.yml` | `npm run e2e:auth:build && npm run e2e:auth:prepare && npm run e2e:auth:core:up && npm run e2e:auth:up && npm run e2e:auth:verify` |

`compose.yml` starts only `auth-db, openfga-db, openfga-migrate, openfga, redis,
authorization-service, aurevia-bff, nginx`. `compose.development.yml` adds `demo-fixture-init`
(fixtures), `mock-hr/finance/legacy/oauth`, `operation-gateway`, the `dev` Spring profile and the
relaxed local network/secret settings. `compose.e2e-auth.yml` adds `test-sso-service`,
`test-legacy-service`, `mf-test-sso`, `mf-test-legacy`; the E2E runner registers their panels,
manifests, targets and routes through the Admin API at run time. CI validates both the Core file and
the Core + development combination (`.gitlab-ci.yml`).

Transport hardening (mTLS, TLS-only Redis, Swagger off) is the orthogonal Spring `prod` profile
(`application-prod.yml`); it is configuration, not data, and unchanged by this separation.

## Build

| Target | Command | Contents |
|---|---|---|
| Core | `npm run build:core` | shell, `mfe-admin`, `mfe-reports` (generic Superset report viewer; a product module) |
| Demo | `npm run build:demo` | `mfe-hr`, `mfe-finance` |
| E2E | `npm run build:e2e` (`e2e:auth:build`) | `mf-test-sso`, `mf-test-legacy` |
| Everything (CI) | `npm run build` | all workspaces |

Production images: `aurevia/authorization-service`, `aurevia/superapp-bff` (both from `compose.yml`)
plus static hosting for `apps/shell/dist`, `apps/mfe-admin/dist` (and `apps/mfe-reports/dist` if
used). `test-sso-service`, `test-legacy-service` and the demo/test MFEs are never required.

## Tests

- `./mvnw -pl services/authorization-service test` with `AUREVIA_TEST_JDBC_URL` (+ OpenFGA/Redis
  variables for the Spring suites): includes `CoreBaselineInstallationIntegrationTest` (production
  cleanliness + development fixture + chain equivalence). `PermissionLifecycleIntegrationTest` pins
  the historical chain because it exercises the demo catalog.
- `npm run e2e:production-core` on a Core-only stack: baseline-only history, no demo rows/services,
  Admin Panel works, dynamic registration of a real micro frontend + OpenFGA grant → visible in
  `/api/me/context`.
- `npm run infra:verify` (development) / `npm run infra:core:verify` (Core): zero OpenFGA drift,
  bootstrapped administrator effective, Admin manifest contract.
