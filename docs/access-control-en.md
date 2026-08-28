# Access-Control and Authorization Model

## Vocabulary

| Concept | Meaning | Storage/model |
|---|---|---|
| User | A person projected from an external issuer | `app_user` / `user` |
| Group | Organizational group and membership | `directory_group` / `group` |
| Role | A reusable permission bundle | `application_role` / `role` |
| Resource | Anything protected by authorization | `resource` |
| Action | An operation on a resource | `action` |
| Grant | Subject + resource + action assignment | `authorization_grant` |
| Relation | OpenFGA relationship such as viewer or manager | OpenFGA tuple |
| Condition | Structured contextual rule | `condition_definition` |
| Obligation | Output restriction such as masking or row filters | `data_policy` |

## Resource tree

Resources may point to a parent and use one of these types: `APPLICATION`, `MODULE`, `PAGE`, `UI_COMPONENT`, `BUSINESS_RESOURCE`, or `EXTERNAL_RESOURCE`.

```text
application:aurevia
├── module:hr
│   └── business_resource:employee
├── module:finance
│   └── business_resource:payment
└── external_resource:superset-public
    └── external_resource:superset-public:dashboard:welcome-dashboard
```

PostgreSQL stores the hierarchy. The current manifest query does not automatically inherit parent grants; inheritance must be explicitly modeled in OpenFGA or an evaluator.

## Relationships and derived permissions

| Object | Relation | Derived permission |
|---|---|---|
| application | `viewer` | `can_view` |
| application | `manager` | `can_view`, `can_manage` |
| resource | `viewer` | `can_view` |
| resource | `creator` | `can_create` |
| resource | `editor` | `can_view`, `can_edit` |
| resource | `deleter` | `can_delete` |
| resource | `manager` | all resource permissions |
| external_resource | `viewer` | `can_view` |
| external_resource | `editor` | `can_view`, `can_edit` |
| external_resource | `sharer` | `can_share` |
| external_resource | `exporter` | `can_export` |
| external_resource | `manager` | all external-resource permissions |

Users can receive relationships directly, through group membership, or through a role assigned to a user/group.

## Runtime decision

`AuthorizationController.check` sends `user:<subject>`, action, and resource to `RelationshipAuthorizationPort`. The OpenFGA adapter evaluates the configured model and returns `ALLOW` or `DENY` with a reason code, model version, decision ID, and obligations. Missing or invalid data must fail closed.

## UI manifest

`GET /api/v1/me/manifest` returns active panel metadata and a permission map:

```json
{
  "version": "manifest-...",
  "expiresAt": "...",
  "panels": [],
  "permissions": {
    "business_resource:employee": ["view", "update"]
  }
}
```

The shell may hide routes and controls using this manifest. UI hiding is not a security boundary; APIs must enforce the same decision independently.

## Assigning access to a user

1. Create or select the user.
2. Select a resource from the tree.
3. Select an action attached to that resource.
4. Choose the relation.
5. Optionally set `expiresAt`.
6. Admin MFE sends `POST /api/v1/admin/grants` with CSRF.
7. BFF forwards to `/internal/v1/registry/grants`.
8. Authorization Service stores the grant and audit event.

```json
{
  "userId": "UUID",
  "resourceId": "UUID",
  "actionId": "UUID",
  "relation": "viewer",
  "expiresAt": null
}
```

`DELETE /api/v1/admin/grants/{id}` archives the active grant instead of physically deleting it.

## Assigning a Superset report or dashboard

1. Admin MFE loads dashboards and charts from the live Operation Superset API.
2. Register the selected asset with `POST /api/v1/admin/superset-assets`.
3. The service creates an `EXTERNAL_RESOURCE` below `external_resource:superset-public`.
4. It attaches `view`, `update`, and `admin` actions.
5. UI levels map to `viewer/view`, `editor/update`, and `manager/admin`.
6. `/api/v1/reports` accepts any of these active levels for a published asset.
7. Opening the asset uses the secured Operation Superset tunnel.

## Structured policy

Allowed fields are `ownerId`, `orgUnit`, `branch`, `classification`, `request.ipClass`, and `time`. Allowed operators are `eq`, `in`, `before`, and `after`.

Allowed obligations are `rowFilters`, `allowedColumns`, `maskedColumns`, `maximumRows`, `exportAllowed`, `printAllowed`, and `watermark`. Unknown fields/operators/obligations, missing context, and parsing errors produce DENY.

`OperationalRules` enforces organizational row scope and maker-checker separation for payment approval.

## Current implementation gaps before production

1. `AdminProxyController` authenticates users but does not explicitly require the `admin` permission.
2. The manifest currently returns every active panel instead of applying panel-level authorization.
3. The report query supports direct USER grants only, not Role or Group grants.
4. Grant/revoke operations do not yet create a complete OpenFGA synchronization outbox flow.
5. OpenFGA drift, retry, and reconciliation require production monitoring.
6. Internal APIs use shared Basic Auth; production requires mTLS, rotation, and network policies.
7. UI authorization must never replace API enforcement.
