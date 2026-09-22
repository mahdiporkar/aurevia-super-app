# Primary Authentication, First Administrator Bootstrap and User Management

This document is the operator reference for how Aurevia authenticates users, how the very first
administrator receives authorization on a fresh installation, and how administrators create
additional Keycloak users from the Admin Panel. The Persian companion is
[primary-authentication-and-first-admin-bootstrap-fa.md](primary-authentication-and-first-admin-bootstrap-fa.md).

## Responsibility boundaries

```text
Keycloak                                   Authorization Service + OpenFGA
========                                   ================================
Authentication (login, password, MFA)      Roles, permissions, applications, modules,
User identity (stable user id / sub)       pages, components, reports, dashboards,
Basic user creation                        operations, policies, resource / user / group grants
```

Aurevia never stores passwords, never implements permissions as Keycloak roles and never replaces
OpenFGA. Keycloak only proves *who* the user is; Authorization Service and OpenFGA decide *what*
that user may do, keyed by the stable Keycloak user id (`sub`).

## Startup architecture

```text
DevOps -> environment / runtime configuration -> Aurevia BFF -> Keycloak (OIDC) -> Aurevia
```

The PRIMARY Keycloak connection is infrastructure configuration. It is read once at BFF startup,
validated, and turned into the Spring Security `ClientRegistration` with id `public-iam`. The BFF
never asks the Authorization Service or the `identity_provider` table for the primary provider, and
the primary client secret is never stored in the application database.

### Primary authentication configuration (BFF)

| Variable | Required | Meaning |
|---|---|---|
| `OIDC_ISSUER_URI` | yes | Keycloak realm issuer, e.g. `https://sso.example.com/realms/aurevia`. Endpoints are discovered from `{issuer}/.well-known/openid-configuration` by Spring Security (`ClientRegistrations.fromIssuerLocation`). |
| `OIDC_CLIENT_ID` | yes | Confidential login client, e.g. `aurevia-bff`. |
| `OIDC_CLIENT_SECRET` | yes | Login client secret. Inject through a Kubernetes/Docker secret, Vault, External Secrets or a cloud secret manager; never commit it. |
| `OIDC_SCOPES` | no | Comma-separated scopes, default `openid,profile,email`; `openid` is mandatory. |
| `OIDC_ENDPOINT_OVERRIDES_ENABLED` | no (default `false`) | Local Docker only. When the browser and the containers reach Keycloak through different addresses, set `true` and provide all of `OIDC_AUTHORIZATION_URI`, `OIDC_TOKEN_URI`, `OIDC_JWK_SET_URI`, `OIDC_USER_INFO_URI` (plus optional `OIDC_END_SESSION_URI`). Discovery is skipped only in this explicit mode; there is no silent fallback. |

Startup validation (fail fast): if `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID` or `OIDC_CLIENT_SECRET` is
missing the BFF stops with

```text
OIDC configuration is incomplete. Missing required configuration: OIDC_CLIENT_SECRET
```

If discovery fails (issuer unreachable, wrong TLS trust, issuer mismatch) the BFF stops with
`OIDC discovery failed. Check OIDC_ISSUER_URI, issuer reachability, TLS trust and the issuer
discovery document`. The BFF therefore never reports healthy while login is guaranteed to fail.
Secrets, tokens and remote response bodies are never written to logs.

The Authorization Service also receives `OIDC_ISSUER_URI`: login-sync for the primary provider
(`providerCode=public-iam`) accepts only identities whose issuer equals this value, and the First
Administrator Bootstrap resolves its subject under this issuer.

### Keycloak login client

| Setting | Value |
|---|---|
| Client ID | `aurevia-bff` (any name; must equal `OIDC_CLIENT_ID`) |
| Client type | OpenID Connect, confidential (client authentication ON) |
| Flows | Standard flow (authorization code) only; PKCE is used by the BFF |
| Valid redirect URIs | `https://<aurevia-host>/login/oauth2/code/public-iam` |
| Web origins | `https://<aurevia-host>` |
| Optional mapper | `groups` (group membership, full path) if directory groups should reach Authorization Service |

The local fixture `infra/keycloak/realm-aurevia.json` contains this client for
`http://localhost:8081` and `http://localhost:8443`.

### Keycloak Admin API service account (server-side only)

The BFF creates users through the Keycloak Admin REST API with a **separate** confidential client
using the client-credentials grant. It must never be the login client.

| Variable | Meaning |
|---|---|
| `KEYCLOAK_ADMIN_CLIENT_ID` | e.g. `aurevia-identity-admin` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | runtime secret; never logged, persisted or returned |
| `KEYCLOAK_ADMIN_BASE_URL` | optional container-reachable Keycloak base URL (`https://sso.example.com`) when it differs from the issuer host; default is derived from `OIDC_ISSUER_URI` |

Keycloak configuration of that client:

| Setting | Value |
|---|---|
| Client type | OpenID Connect, confidential |
| Standard flow / direct access grants | OFF |
| Service accounts roles | ON |
| Service-account role | `realm-management` → **`manage-users`** only |

`manage-users` is the minimum permission for creating users and setting their initial password
(it also allows the read used by the startup diagnostic below). Do not assign `realm-admin`.
When the service account is not configured, `POST /api/v1/admin/keycloak-users` answers
`503 KEYCLOAK_ADMIN_NOT_CONFIGURED`; everything else keeps working.

## First Administrator Bootstrap

After Keycloak and the environment are configured, authentication works, but nobody is authorized
yet. The bootstrap gives exactly one Keycloak user the existing platform-administration permission
(`admin` on `application:aurevia`, OpenFGA relation `manager`), once.

| Variable | Meaning |
|---|---|
| `AUREVIA_BOOTSTRAP_ADMIN_SUB` | The **stable Keycloak user id** (`sub`) of the initial human administrator. Never a username or e-mail. Read by the Authorization Service (grant) and by the BFF (verification only). |

Step by step:

1. In Keycloak, create the human administrator (Users → Add user), set a password there. Aurevia
   never knows this password and it must not appear in any Aurevia configuration.
2. Copy the user's **ID** from the Keycloak console (Users → the user → ID), e.g.
   `8c604f37-33d2-42e4-a982-35bd5613e974`.
3. Set `AUREVIA_BOOTSTRAP_ADMIN_SUB=<that id>` in the Authorization Service and BFF environment.
4. Start Aurevia. On startup the Authorization Service runs
   `FirstAdministratorBootstrapRunner`, which in one transaction under a Postgres advisory lock:
   - resolves or creates the canonical subject (`app_user` + `external_identity` for
     `issuer + sub`; a user who already logged in is reused, an inactive user is refused),
   - creates the grant through the ordinary `AccessAdministrationService.grant` path
     (audit entry with actor `FIRST_ADMIN_BOOTSTRAP`, outbox event projected to OpenFGA),
   - writes the durable marker `schema_version(component='first-administrator')`.
   Log lines: `First administrator bootstrap completed`, `… already completed; existing grants are
   unchanged`, or `… is not completed: configure AUREVIA_BOOTSTRAP_ADMIN_SUB …` (warning).
5. The BFF, when the admin service account is configured, looks the id up in Keycloak and logs
   `Bootstrap administrator verified in Keycloak: sub=… username=…` or an `ERROR` explaining that
   the id matches no Keycloak user (typically a username was pasted instead of the id).
6. The administrator logs in through Keycloak, `/api/me/context` reports `application:aurevia:
   [admin, view]` and the Admin Panel (including `Users`) is available.

Guarantees:

- **Once only.** The marker is written in the same transaction as the grant. Later restarts,
  upgrades or redeployments only log `already completed`; if an administrator revokes that grant
  through Access Studio it stays revoked even though the variable is still set.
- **Idempotent.** An interrupted start commits nothing; a retry converges to one subject, one
  `external_identity`, one grant and one marker. The advisory lock serialises replicas.
- **Diagnosable.** Malformed values (whitespace, control characters, > 255 chars) fail startup;
  a non-UUID value logs a warning; an inactive linked user or a missing `application:aurevia`
  `admin` action fails startup with `First administrator bootstrap failed: …`.
- **Scope.** Only `admin` on `application:aurevia` is granted. Root administrators inherit the
  deployment applications and their admin pages through the resource hierarchy; resources that are
  deliberately not inherited (for example `business_resource:public-zone-logs`) are granted later
  through the Admin Panel like any other permission.

## User Management (Admin Panel → «مدیریت کاربران»)

```text
Administrator -> Admin Panel (users page) -> POST /api/v1/admin/keycloak-users (BFF)
              -> OpenFGA check: admin on application:aurevia
              -> client-credentials token from the service account
              -> POST {keycloak}/admin/realms/{realm}/users
              -> 201 { id, username, firstName, lastName, email, enabled }
```

- Fields: username, first name, last name, e-mail, enabled, initial password (non-temporary).
- The browser never talks to the Keycloak Admin API and never sees the service-account secret or
  the admin token. The initial password travels only browser → BFF → Keycloak; it is not stored,
  not logged and not returned. Validation errors return field names only.
- The stable Keycloak id is returned as `id`. It becomes the Aurevia subject on the user's first
  login (existing lazy `login-sync` behaviour; no second subject model). Authorization is assigned
  afterwards through Access Studio / roles / groups exactly as for any other user.
- Error mapping: `409 KEYCLOAK_USER_CONFLICT` (username/e-mail exists),
  `400 KEYCLOAK_USER_REJECTED` (Keycloak rejected payload or password policy),
  `400 INVALID_USER`, `403 ACCESS_DENIED`, `502 KEYCLOAK_ADMIN_ACCESS_DENIED`
  (service account rejected: credentials or missing `manage-users`), `503 KEYCLOAK_UNAVAILABLE`,
  `504 KEYCLOAK_TIMEOUT`, `502 KEYCLOAK_INVALID_RESPONSE`, `503 AUTHORIZATION_UNAVAILABLE`.

Out of scope for this phase: Keycloak groups, roles, password reset, MFA, LDAP, identity
federation, realm/client administration, multiple-IdP management.

## Additional identity providers

The `identity_provider` registry, the Admin Panel card «ارائه‌دهندگان هویت اضافی» and the
`secret://` reference resolver remain for *additional* providers (partner SSO, Azure AD, a second
Keycloak). They can no longer define, edit or shadow the primary provider: the code `public-iam`
and the primary issuer are refused by the Authorization Service (`409`), and `/auth/providers`
returns the primary provider from runtime configuration.

## Local development vs. production

| | Local demo (`.env.example`, `infra/keycloak/realm-aurevia.json`) | Production |
|---|---|---|
| Keycloak users | imported fixtures with password `local-change-me`; `administrator` has the fixed id `8c604f37-33d2-42e4-a982-35bd5613e974` | created by DevOps in Keycloak; Aurevia never knows passwords |
| `AUREVIA_BOOTSTRAP_ADMIN_SUB` | the fixture id above | the real administrator's id |
| Client secrets | fixture values (`local-change-me`, `local-identity-admin-only`) | injected secrets |
| Endpoint overrides | `OIDC_ENDPOINT_OVERRIDES_ENABLED=true` (localhost vs host.docker.internal) | `false`, single HTTPS issuer |
| `demo-catalog-init` | development catalog only; it seeds **no** identity provider and **no** administrator grant | not deployed |

Migration `V78` retires the historical implicit privileges of the demo identity whose subject was
literally `administrator`; `V79` publishes Admin MF manifest 0.6.0 with the Users page.

## Verification

- Unit/integration: `./mvnw -pl services/superapp-bff test`,
  `./mvnw -pl services/authorization-service test` (set the `AUREVIA_TEST_*` variables for the
  real Postgres/OpenFGA/Redis tests, then `npm run test:permission:verify`).
- End-to-end on the Compose stack: `node tools/primary-auth-bootstrap-e2e.mjs`
  (clean install → bootstrap → login → create user → grant → restart → revoke → restart).
- `npm run infra:verify` checks the bootstrapped administrator by stable sub and the 0.6.0 manifest.
