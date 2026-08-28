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
