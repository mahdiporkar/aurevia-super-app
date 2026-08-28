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
    Nginx --> PublicSuperset[Public Superset]
  end
  subgraph Operation
    Gateway[Operational API Gateway] --> Services[HR / Finance / legacy services]
    Services --> OperationSuperset[Operation Superset]
  end
  BFF -->|mTLS + unchanged Public IAM bearer| Gateway
```

There is no browser workload in the Operation zone. Authorization Service and OpenFGA are temporarily placed in Public, but their adapters and base URLs are configuration boundaries so relocation does not alter domain or frontend code.

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

