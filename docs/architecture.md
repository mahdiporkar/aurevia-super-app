# Architecture

## Trust zones

```mermaid
flowchart LR
  Browser[Browser: Shell + MFEs] -->|same origin| Nginx
  subgraph Public
    Nginx --> BFF
    BFF --> Keycloak[Public Keycloak]
    BFF --> Redis[(Session + encrypted token vault)]
    BFF --> Authz[Authorization Service]
    Authz --> FGA[OpenFGA]
    Authz --> AuthDB[(Authorization PostgreSQL)]
    Nginx -->|React / JS / CSS only| PublicSuperset[Public Superset]
  end
  subgraph Operation
    Gateway[Operational API Gateway :80] --> Services[HR / Finance / legacy services]
    Gateway --> OperationSuperset[Operation Superset]
    OperationSuperset --> DWH[(DWH / analytical sources)]
  end
  BFF -->|Java proxy; port 80 locally| Gateway
```

There is no browser workload in the Operation zone. Authorization Service and OpenFGA are temporarily placed in Public, but their adapters and base URLs are configuration boundaries so relocation does not alter domain or frontend code.

## Superset request flow

Public Superset is a presentation artifact host. The browser may fetch only its compiled
React, JavaScript, CSS, fonts, and images through `/static/`. It does not own dashboards,
execute chart queries, or connect to the DWH.

```mermaid
sequenceDiagram
  participant U as Browser
  participant N as Public Nginx
  participant P as Public Superset
  participant B as Java BFF Proxy
  participant G as Operation API Gateway :80
  participant O as Operation Superset
  participant D as DWH
  U->>N: GET /static/assets/...
  N->>P: presentation assets only
  U->>N: /reports-runtime/*
  N->>B: /api/v1/superset/*
  B->>G: /superset/*
  G->>O: dashboard, chart, API, login
  O->>D: query analytical data
```

Nginx has no network route to Operation Superset. All dynamic Superset traffic therefore
crosses the authenticated Java proxy and the Operation Gateway. Superset retains its own
CSRF validation on that tunnel; Spring CSRF remains enabled for every other BFF mutation.

## Operational API request flow

```mermaid
sequenceDiagram
  participant U as Browser MFE
  participant N as Nginx
  participant B as BFF
  participant A as Authorization Service
  participant F as OpenFGA
  participant R as Redis Token Vault
  participant G as Operation Gateway
  U->>N: /hr-micro or /finance-micro
  N->>B: same-origin request + session + CSRF
  B->>A: resolve(path, method)
  A-->>B: resource, action, size and timeout limits
  B->>A: authorize(subject, resource, action)
  A->>F: relationship check
  F-->>A: allow/deny
  A-->>B: decision + reason
  B->>R: read/refresh encrypted token
  B->>G: original bearer + correlation id over configured mTLS
  G-->>B: bounded response
  B-->>U: allowlisted status, headers, and body
```

Route definitions are data, not user-provided destinations. `service_target` owns an allowlisted base URL; `proxy_route` owns a path prefix; `route_operation` binds HTTP method/pattern to a resource/action and request limit. Resolution requires a path-segment boundary and chooses the longest matching prefix.

## Authorization control and data planes

```mermaid
flowchart TB
  Admin[Admin MFE] --> BFFAdmin[BFF admin proxy]
  BFFAdmin --> Guard[Admin actor interceptor]
  Guard --> DB[(PostgreSQL source of truth)]
  DB --> Outbox[(Transactional outbox)]
  Outbox --> Reconciler
  Reconciler --> OpenFGA[(Runtime relationship graph)]
  Request[Operational request] --> Check[Authorization check]
  Check --> OpenFGA
  DB --> Manifest[Effective manifest: USER + GROUP + ROLE]
  Manifest --> Shell[Shell presentation guards]
```

The control plane is eventually consistent with OpenFGA through idempotent outbox events. Runtime relationship failures deny access. UI manifest permissions improve presentation but never authorize an operational API by themselves. See [Authorization Engine architecture](authorization-engine-fa.md) for the complete model and failure semantics.

## Login sequence

```mermaid
sequenceDiagram
  participant U as Browser
  participant B as BFF
  participant K as Public Keycloak
  participant A as Authorization Service
  participant R as Redis
  U->>B: GET /auth/login
  B->>K: Authorization Code + state/nonce/PKCE
  K-->>B: callback code
  B->>K: server-side code redemption
  B->>A: identity/login-sync
  B->>R: encrypted tokens + tokenHandle session
  B-->>U: opaque Secure HttpOnly session cookie
```

## Source-of-truth boundaries

PostgreSQL owns control-plane metadata, identity projections, audit, policy definitions, synchronization state, and the transactional outbox. OpenFGA is the runtime relationship projection. Redis owns transient sessions and encrypted token records in separate namespaces. Panel is deployment metadata only; resource permissions are never stored in it.

## Deployment and scaling

BFF and Authorization Service are stateless apart from Redis/PostgreSQL and may be horizontally replicated. Outbox consumers coordinate with `FOR UPDATE SKIP LOCKED`. Redis must be shared by all BFF replicas. Database migrations run once before rolling application instances. Nginx is the only browser ingress; Operation Gateway and data services have no public browser route. Production requires TLS at ingress, verified mTLS to operational workloads, secret-manager injection, database backup/PITR, Redis HA, OpenFGA persistence, and metrics/alerts described in the runbooks.
