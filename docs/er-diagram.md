# Control-plane ER diagram

```mermaid
erDiagram
  APP_USER ||--o{ USER_GROUP_MEMBERSHIP : belongs
  DIRECTORY_GROUP ||--o{ USER_GROUP_MEMBERSHIP : contains
  APP_USER ||--o{ USER_ROLE_ASSIGNMENT : assigned
  DIRECTORY_GROUP ||--o{ GROUP_ROLE_ASSIGNMENT : assigned
  APPLICATION_ROLE ||--o{ USER_ROLE_ASSIGNMENT : bundles
  APPLICATION_ROLE ||--o{ GROUP_ROLE_ASSIGNMENT : bundles
  RESOURCE ||--o{ RESOURCE : parent
  RESOURCE ||--o{ RESOURCE_ACTION : supports
  ACTION ||--o{ RESOURCE_ACTION : binds
  PANEL ||--o{ PROXY_ROUTE : owns
  SERVICE_TARGET ||--o{ PROXY_ROUTE : serves
  OUTBOUND_AUTH_PROFILE ||--o{ SERVICE_TARGET : authenticates
  PROXY_ROUTE ||--o{ ROUTE_OPERATION : maps
  RESOURCE ||--o{ ROUTE_OPERATION : protects
  ACTION ||--o{ ROUTE_OPERATION : requires
  RESOURCE ||--o{ AUTHORIZATION_GRANT : granted
  CONDITION_DEFINITION ||--o{ AUTHORIZATION_GRANT : constrains
  RESOURCE ||--o| SUPERSET_ASSET : projects
  AUTHORIZATION_GRANT ||--o{ SUPERSET_ACCESS_SYNC : synchronizes
  OUTBOX_EVENT }o--|| SCHEMA_VERSION : model_version
```

Hard deletion is restricted for resources referenced by grants, routes, audit, or external assets. Archive status preserves historical integrity. The service rejects parent changes that introduce a cycle.
