# Authorization Fix — Checkpoint

Source of truth: current uncommitted working tree (`git status` / `git diff`). Nothing committed. Last updated: 2026-09-19.

## Baseline
- Branch `main` @ `e06c792`. All work below is **uncommitted**.
- Tests: backend 300 (authz 194, bff 98, ui-artifact-security 8), frontend 65 — all green.
- Docker Compose stack (12 services) starts healthy; startup reconciliation converges (180/180).

## Root causes identified
| # | Cause | Status |
|---|---|---|
| A1 | Hardcoded Reports discoverability (`"reports"`, `application:aurevia/reports`, superset regex) in `AuthorizationDecisionService` | Fixed (V71 + generic `discoveryGuard`) |
| A2 | No projection success/failure counters (only a Timer that also counts failures) | Fixed |
| A3 | No context diagnostics (§18) | Implemented |
| A4 | `expectedTuples()` (reconciliation delete-set) had no real-DB test | Covered |
| A5 | Diagnostics passed `user:`-prefixed principal into SQL matching bare `canonical_user_id` (own bug, found live) | Fixed |
| A6 | Demo `viewer` held V9-seeded broad `application:aurevia/reports:view`, masking asset-only path | Fixed (V72) |

Already correct before this work (verified by reading, not to re-audit): all 4 subject types in grant projection and reconciliation; outbox ordering/backoff/dead-letter; projection state via outbox lateral join; Redis-optional check with fail-closed epoch bump; configured `subjectClaim` used by both login-sync and `SessionIdentity`; directory groups namespaced `group:directory/<uuid>`; multi-instance `GET /api/v1/reports`.

## Two sets of uncommitted changes (keep separate at commit time)
**Pre-existing (earlier session, not reviewed in isolation):** `apps/mfe-admin/src/AccessStudio.tsx`, `apps/mfe-admin/src/grant-projection.{ts,d.ts,test.ts}`, `access/AccessModels.java`, `access/JdbcAccessRepository.java`, `identity/*` (6 files), `sync/JdbcOpenFgaReconciliationRepository.java`, `bff/api/ReportsController.java`, `bff/security/SessionIdentity.java`, bff tests (`ReportsControllerTest`, `OidcLoginSuccessHandlerTest`, `TokenFreeSecurityContextRepositoryTest`), `tools/register-superset-native.mjs`, `test/.../support/PostgresFixture.java`, `test/.../identity/IdentityAdministrationServiceTest.java`, migrations V69, V70.

**This session:**
- `authorization/AuthorizationDecisionService.java` — removed Reports branch; `discoveryGuard`, `descendsFrom`, `DeclaredRoute`, `RouteGuard`; landing-route fallback only when no declared route is authorized.
- `authorization/AuthorizationQueryRepository.java`, `JdbcAuthorizationQueryRepository.java` — `PanelRecord.discoveryResourceKey` (additive).
- `sync/OutboxReconciler.java` — counters `aurevia.openfga.projection.applied` / `.failed`.
- `diagnostics/` (new): `AuthorizationDiagnostics`, `AuthorizationDiagnosticsRepository`, `JdbcAuthorizationDiagnosticsRepository`, `AuthorizationDiagnosticsService`.
- `api/AuthorizationDiagnosticsController.java` (new) — `GET /internal/v1/registry/diagnostics/authorization?issuer&subject&resource&action`.
- `config/AdminAuthorizationInterceptor.java` — `/diagnostics/` requires `can_manage`.
- `docs/ApiDocumentationCatalog.java` — Persian summary for new endpoint.
- Migrations V71, V72.

## Migrations
| V | Purpose |
|---|---|
| 69 (pre-existing) | Project directory groups as `group:directory/<uuid>` scoped by (issuer, external_id); never project raw claim |
| 70 (pre-existing) | Repair roles disabled before status changes emitted projection events; keep assignments for re-enable |
| 71 | `panel.discovery_resource_key` FK→`resource(resource_key)`; REPORTS→`external_resource:superset-public`. Grants nothing; drives MFE discoverability declaratively |
| 72 | Archive demo `viewer`'s `application:aurevia/reports:view` + enqueue `GRANT_DELETE` |

## Tests added this session
- `AuthorizationDecisionServiceManifestTest` — 9→12 (discovery subtree, no-subtree hidden, declared routes suppress fallback, held-action fallback, deterministic guard).
- `OutboxReconcilerTest` — +1 (counters separate applied/failed).
- `diagnostics/AuthorizationDiagnosticsServiceTest` — 9 (mocks use distinct bare id vs `user:` principal).
- `diagnostics/AuthorizationDiagnosticsRepositoryIntegrationTest` — 11 (real PG, all subject types, ancestor grants, PENDING/APPLIED/FAILED, expiry, role deactivation, V71 applied).
- `sync/OpenFgaReconciliationRepositoryIntegrationTest` — 11 (real PG: 4 grant subject types, 3 role paths, 2 memberships, page→module→application, stale removal, idempotence).

**Skipped unless `AUREVIA_TEST_JDBC_URL` set** (+ `_USER`/`_PASSWORD`, default postgres/postgres): both `*IntegrationTest` classes. Run with a throwaway `postgres:16-alpine` (`PostgresFixture` uses disposable schemas). CI currently does not set it.

## E2E scenarios
Verified live against compose stack (manual): **H** (scratch user with only dashboard 7 → Reports visible, permissions contain only the asset, dashboard 7 ALLOW, dashboard 1 / chart 100 DENY; fixture removed), **J** (restart: 180 legit tuples kept, 1 stale removed). Diagnostics endpoint verified live (viewer → `ALLOWED_BY_GRANT` via `superset-viewer` role).

Missing as automated login→`/api/me/context` tests: **A, B, C, D, E, F, G, I, K, L, M**. Harness: `tools/e2e-auth/` + `mock-oauth`.

## Known risks
1. Scenarios above not automated; acceptance not all independently demonstrated.
2. Integration tests silently skip in CI without the env var.
3. `viewer` still reaches Reports via `superset-viewer` role (legitimate; asset-only path proven only by scratch user).
4. Pre-existing uncommitted work not reviewed in isolation; only the combined tree is tested.
5. Nothing committed.

## Next tasks (priority order)
1. Review pre-existing diff; commit as two commits on a branch (pre-existing work, then this session's) — on user's instruction only.
2. Add PostgreSQL service + `AUREVIA_TEST_JDBC_URL` to `.gitlab-ci.yml`.
3. Automate E2E: start with E (role disable/re-enable), K/L (outbox RETRYING/FAILED visible in Access Studio), M (custom `subjectClaim`); then A–D, F, G, I.
4. Access Studio UI for the diagnostics endpoint (mfe-admin).
5. Optional: drop `viewer`'s `superset-viewer` assignment so shipped demo proves asset-only path.

## Operational notes (avoid rediscovery)
- authz service listens on **8082** in container; internal basic auth `bff:$AUTH_INTERNAL_PASSWORD`; admin endpoints need `X-Actor-Issuer`/`X-Actor-Subject` (administrator has `can_manage`).
- auth-db creds from `.env`: `aurevia` / `aurevia_auth`.
- OpenFGA store `01M2TR7FJZHN29JNWDAPPRCY4A`, URL `http://openfga:8080` (inside network).
- `npm run infra:up` preflight needs OpenFGA already up; use `docker compose --env-file .env -f infra/docker-compose/compose.yml up -d --build` directly.
- No python on host; use perl/node for scripted edits.

## Session 2 — permission task COMPLETE (2026-09-19)
Baseline commit `2e9fbcc`. Work below is **uncommitted** on top of it.

### New root causes (confirmed by real-stack test, all fixed)
| # | Cause | Fix |
|---|---|---|
| A7 | `expires_at` never enforced at runtime; expired grants/role assignments stayed effective until restart | `sync/ExpirationSweeper` + `ExpirationRepository`/`JdbcExpirationRepository` (30s sweep, reuses `GRANT_DELETE` / `ROLE_ASSIGNMENT_DELETE` projection; metrics `aurevia.authorization.expired.*`) |
| A8 | `JdbcAccessRepository.createGrant` and `JdbcIdentityRepository.upsertRoleAssignment` bound `java.time.Instant` directly → PG "Can't infer the SQL type"; **any grant/assignment with `expiresAt` failed** | bind as `OffsetDateTime` |
| A9 | `RuntimePolicyService.loadResource` rebuilt the registry key with `/`→`:`; Superset assets use slash keys → every `/authorize/check` on a Superset asset returned `POLICY_EVALUATION_ERROR` even when OpenFGA allowed | `RuntimePolicyRepository.activeResource(key, registryKey, canonicalObject)` also matches the computed canonical object |

### Files this session
`.gitlab-ci.yml` (PG/Redis/OpenFGA services + env + guard), `tools/verify-permission-integration-tests.mjs`, `package.json` (`test:permission:verify`), `docs/permission-definition-and-operation-fa.md` (new), `docs/current-state-fa.md` (stale V68 marker → V72, pre-existing docs:verify failure), `access/JdbcAccessRepository.java`, `identity/JdbcIdentityRepository.java`, `policy/{RuntimePolicyRepository,JdbcRuntimePolicyRepository,RuntimePolicyService}.java`, `sync/{ExpirationRepository,JdbcExpirationRepository,ExpirationSweeper}.java`, `test/support/PermissionStack.java`, `test/sync/PermissionLifecycleIntegrationTest.java` (13 scenarios), `test/resources/openfga/authorization-model.json`.

### Test results (real PostgreSQL 16 + OpenFGA + Redis 7 via docker)
authz 207 / bff 98 / ui-artifact-security 8 — 313 run, 0 fail, 0 err, **0 skip**. Permission integration classes executed: Lifecycle 13, Reconciliation 11, Diagnostics 11. `npm run docs:verify` PASS.
Run locally: start `postgres:16-alpine` (55432), `redis:7-alpine --requirepass testpass` (56379), `openfga/openfga run` (58080); export `AUREVIA_TEST_JDBC_URL=jdbc:postgresql://localhost:55432/postgres AUREVIA_TEST_JDBC_USER=postgres AUREVIA_TEST_JDBC_PASSWORD=postgres AUREVIA_TEST_OPENFGA_URL=http://127.0.0.1:58080 AUREVIA_TEST_REDIS_HOST=localhost AUREVIA_TEST_REDIS_PORT=56379 AUREVIA_TEST_REDIS_PASSWORD=testpass`; `./mvnw -o test`; `npm run test:permission:verify`.

### Remaining (permission)
- Browser-level login → `/api/me/context` E2E not automated (BFF hop covered by existing BFF unit tests; authz side covered end-to-end by `PermissionLifecycleIntegrationTest`).
- MFE discoverability of Reports asserted by unit tests + live compose check, not in the lifecycle test (fresh DB has no active Reports artifact).
- Nothing committed.

**Next task: prompt 2 (MFE registration / network policy).** Permission scope is closed.

## Session 3 — Proxy Routing task COMPLETE (2026-09-20)
Uncommitted, on top of the (also uncommitted) session-2 permission work. Prompt 2 (MFE registration/network policy) still **not started**.

Routing bugs found/fixed: R1 BFF hardcoded `resource:` when authorizing (denied APPLICATION/EXTERNAL_RESOURCE-bound operations) → authz now returns `resourceObject`; R2 BFF/preview divergence on path transformation (non-matching rewrite silently dropped base path at runtime) → single `UpstreamPathPolicy`, authz returns `upstreamPath`, runtime 422 on misapplied rewrite; R3 no `stripPrefix ≤ prefix segments` validation → `STRIP_PREFIX_EXCEEDS_PATH_PREFIX`; R4 any `%`-encoded runtime path was 400 → safe encodings kept opaque, structural ones rejected; R5 upstream timeout/connection failure surfaced as 500 → 504/502; R6 root `/` operation pattern rejected → accepted; R7 E2E harness race (polled only dual user) → polls all.
Files: `routing/{UpstreamPathPolicy(new),RoutePathPolicy,RouteResolutionService,RouteResolutionRepository,JdbcRouteResolutionRepository,ResolvedRoute,ProxyRouteAdministrationService}.java`, tests `routing/{RouteResolutionServiceTest(new,12),UpstreamPathPolicyTest(new,14),RoutePathPolicyTest(+3)}`, BFF `api/{OperationalProxyController,RouteResolution}.java`, tests `api/{OperationalProxyForwardingTest(new,10),OperationalProxyAuthorizationTest}`, `apps/mfe-admin/src/ProxyRoutes.tsx` (+`describeError`, `proxy-route-errors.test.ts`), `tools/e2e-auth/verify.mjs`, `docs/proxy-routing-legacy-forward-fa.md` (new).
E2E: `tools/e2e-auth` **41/41 PASS** incl. BROWSER-* (Keycloak via `identity:up`; jars via `./mvnw -Pe2e-sso-legacy -pl services/test-legacy-service,services/test-sso-service -am package -DskipTests`). Stacks left running: core compose, identity-demo, e2e-auth.
Backend: authz 236 (35 skipped = permission integration classes without env), bff 108, ui-artifact-security 8. Frontend typecheck clean; mfe-admin 35 tests.
