# Architecture

> Document status: canonical living architecture, synchronized with the source tree on
> 2026-09-15. See [current implementation status (FA)](current-state-fa.md) for the complete
> feature inventory and document precedence rules.

## Runtime boundaries

```mermaid
flowchart LR
  Browser[Browser: Shell + MFEs] -->|same-origin HTTP| Nginx[Nginx ingress]
  Nginx -->|static Shell| Browser
  Nginx --> BFF[Java WebFlux BFF]
  BFF --> Redis[(Redis session, encrypted token vault, decision cache)]
  BFF --> Authz[Authorization Service]
  Authz --> AuthDB[(Authorization PostgreSQL)]
  Authz --> FGA[(OpenFGA)]
  BFF -->|registered route| Gateway[Operation Gateway]
  Gateway --> Services[Modern and legacy services]
  BFF -->|registered URL + SSRF/TLS policy| Superset[External Superset]
  BFF -->|dynamic OIDC registration| IdP[External Identity Provider]
```

The browser never receives an OAuth access or refresh token and never connects directly to
Authorization Service, OpenFGA, operational services, or a registered Superset destination.
It receives only the opaque `AUREVIA_SESSION` cookie and uses relative URLs. Nginx is the only
browser ingress; authorization and destination selection happen in Java.

## Independent lifecycles

The base `infra/docker-compose/compose.yml` is the local Core: PostgreSQL, OpenFGA, Redis,
Authorization Service, BFF, Nginx, Operation Gateway, catalog initialization, and operational
mocks. It contains no Identity Provider, MFE artifact server, LDAP server, or Superset runtime.

- `compose.identity-demo.yml` owns optional Keycloak and directory fixtures.
- `compose.mfe-demo.yml` owns the four independently served MFE artifacts.
- `compose.superset-demo.yml` owns the optional local Superset demo.
- E2E and native-Superset overlays are test or connector configuration, not Core ownership.

Production providers, MFEs, service targets, and Superset instances are registered by URL and
policy. Their availability is not a Core startup dependency.

## Login and effective context

```mermaid
sequenceDiagram
  participant U as Browser
  participant B as BFF
  participant I as Registered OIDC IdP
  participant A as Authorization Service
  participant R as Redis
  U->>B: GET /auth/login (optional code/tenant/domain)
  B->>A: resolve active IdP
  B->>I: Authorization Code + state/nonce/PKCE
  I-->>B: callback code
  B->>I: server-side code redemption
  B->>A: POST /internal/v1/identity/login-sync
  B->>R: encrypted tokens + token handle
  B-->>U: opaque Secure/HttpOnly AUREVIA_SESSION
  U->>B: GET /api/me/context
  B->>A: manifest + allowed Superset integrations
  B-->>U: identity, effective catalog, routes, navigation, permissions
```

`GET /api/me/context` is the canonical single-fetch Shell contract. The older
`GET /api/v1/me/manifest` and focused `GET /api/ui/catalog` endpoints remain compatibility
projections. The BFF rewrites registered MFE artifact URLs to `/api/mfe/{moduleKey}/...`, so the
browser loads only authorized same-origin artifacts.

## Operational API flow

```mermaid
sequenceDiagram
  participant U as Browser MFE
  participant B as BFF
  participant A as Authorization Service
  participant F as OpenFGA
  participant V as Redis token vault
  participant G as Operation Gateway
  participant S as Target service
  U->>B: /{panelSlug}/... + session (+ CSRF for mutation)
  B->>A: resolve normalized path and HTTP method
  A-->>B: target, auth mode, resource/action, limits
  B->>A: authorization check
  A->>F: relationship check
  F-->>A: allow or deny
  A-->>B: decision and obligations
  B->>V: obtain current user or legacy service token
  B->>G: bounded request + correlation ID
  G->>S: routed request
  S-->>B: bounded response
  B-->>U: allowlisted status, headers and body
```

`service_target`, `proxy_route`, and `route_operation` are configuration data, never
caller-supplied destinations. Resolution enforces a path-segment boundary, allowed HTTP methods,
registered resource/action, request/response limits, and default deny. Modern routes forward the
current Public-IAM bearer token; Legacy routes resolve an approved connection and obtain/cache a
server-side service token without exposing its credential or value.

## Superset flow

Superset is an external integration with an independent lifecycle. The current connector does
not route Superset through Operation Gateway and does not require a public asset-only Superset.

```mermaid
sequenceDiagram
  participant U as Browser
  participant B as BFF Superset proxy
  participant A as Authorization Service
  participant S as Registered Superset
  participant D as Analytical data source
  U->>B: /api/integrations/superset/{code}/...
  B->>A: resolve integration + authorize subject/asset
  A-->>B: fixed base URL, policy and access result
  B->>S: server-side HTTP(S), optional mTLS
  S->>D: query under Superset policy
  S-->>B: HTML/API/static response
  B-->>U: same-origin rewritten response
```

Compatibility tunnels under `/api/v1/superset/**` and `/api/v1/superset-instances/**` are still
implemented. Superset owns CSRF for its tunnel; every other state-changing BFF endpoint retains
Spring CSRF. Network policy rejects unapproved schemes, hosts and address classes.

## Authorization control plane

```mermaid
flowchart TB
  Admin[Admin MFE /api/v1/admin] --> BFFAdmin[BFF admin facade]
  BFFAdmin --> Authz[Authorization Service /internal/v1/registry]
  Authz --> DB[(PostgreSQL source of truth)]
  DB --> Outbox[(Transactional outbox)]
  Outbox --> Reconciler[Idempotent reconciler]
  Reconciler --> OpenFGA[(Relationship projection)]
  Runtime[Runtime check] --> Authz
  Authz --> OpenFGA
  DB --> Context[Effective manifest and UI catalog]
  Context --> Shell[Shell and MFE presentation guards]
```

PostgreSQL owns identity projections, resources, actions, grants, policies, registries, audit,
and outbox state. OpenFGA owns relationship decisions at runtime. Redis is transient and may
cache checks for a short TTL; writes invalidate matching entries. UI visibility improves UX but
never authorizes an API by itself.

## MFE governance

An MFE publishes two independent contracts:

- `mf-manifest.json` owns runtime integration, routes, and navigation defaults.
- `resource-manifest.json` owns authorization resources and actions.

The Panel registry stores canonical MFE identity and source URLs. MF sync creates immutable,
idempotent artifact revisions. Resource import creates a versioned draft and diff; only explicit
publish changes the effective catalog and emits OpenFGA outbox work. Navigation overlays can
change presentation without inventing authorization resources. Shell routing is built only from
the backend-filtered `uiCatalog` in `/api/me/context`.

## Deployment properties

BFF and Authorization Service are stateless apart from Redis/PostgreSQL and can be replicated.
Outbox consumers coordinate with `FOR UPDATE SKIP LOCKED`. Production profiles disable Swagger
and demo mutation/data, require integrity for MFE artifacts, reject HTTP destinations, require
mTLS between BFF and Authorization Service/Gateway, and enable TLS with client authentication on
Authorization Service. The base Compose remains a development environment; production still
requires platform-provided secret management, certificate rotation, HA/PITR, image governance,
rate limiting, monitoring, and tested recovery procedures.
