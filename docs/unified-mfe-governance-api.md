# Unified micro frontend governance contract

This document records the compatibility layer that completes the existing panel, immutable UI
artifact, resource-manifest, navigation-overlay, OpenFGA, and policy-engine implementation. It does
not introduce a second registry or authorization model.

## Ownership and persistence

`panel` remains the canonical `MicroFrontendRegistry`; `resource_manifest_import` is the immutable
`ManifestRegistry`; `resource` and `resource_action` are the resource/action registries;
`ui_menu_override` is the navigation catalog; existing policy, assignment, OpenFGA outbox, and audit
tables remain authoritative. Flyway migrations
`V51__microfrontend_governance_catalog.sql` and
`V52__immutable_resource_manifest_versions.sql` evolve these tables without destructive renames.

The MFE owns capability definitions, routes, and default navigation. A manifest contains no user,
role, group, or permission assignments. Import is draft-first; publish validates schema, ownership,
hierarchy and actions, then creates/updates resources and deprecates missing definitions in one
transaction. Grants are never deleted by manifest import.

## Effective context API

`GET /api/me/context` is the canonical browser startup call. Authentication is the BFF session.
The response is private/no-cache and contains:

```json
{
  "contractVersion": "1.0",
  "identity": {"issuer":"...","subject":"...","username":"..."},
  "tenant": {"id":"default"},
  "organizations": [],
  "allowedApplications": ["finance"],
  "allowedMicros": [],
  "dynamicRoutes": [],
  "navigation": [],
  "resources": [],
  "actions": {"finance.invoice":["view","approve"]},
  "policies": {},
  "uiCatalog": {},
  "version": "manifest-sha256-...",
  "expiresAt": "..."
}
```

The existing `/api/v1/me`, `/api/v1/me/manifest`, and `/api/ui/catalog` contracts remain available.
The shell now makes one context request and derives routes, navigation, identity, and SDK state from
that response.

## Secured artifacts

The effective context replaces upstream artifact locations with same-origin, session-authorized URLs:

- `GET /api/mfe/{moduleKey}/manifest.json`
- `GET /api/mfe/{moduleKey}/remoteEntry.js`

The BFF resolves `moduleKey` only from the user's already-filtered effective catalog. Unknown or
unauthorized modules return 404, avoiding disclosure. The upstream URL is taken only from the
validated registry; callers cannot supply a target URL. Deployments must expose upstream artifact
origins only to the BFF/proxy network so their raw `/manifest.json` and `/remoteEntry.js` paths are
not Internet-accessible.

## Frontend SDK and backend evaluation

`@aurevia/authorization-sdk` stores the effective context in memory and exposes:

```tsx
authorization.can('finance.invoice', 'approve')

<Can resource="finance.invoice" action="approve">
  <ApproveButton />
</Can>
```

Checks fail closed when context is missing or expired. They are UX hints only. Dynamic ABAC checks
use `POST /api/v1/authorize/evaluate` with `{resource, action, context}` and CSRF protection. The BFF
always replaces caller identity with the authenticated session identity, and the authorization
service evaluates OpenFGA plus the policy engine. Business APIs must continue to enforce their own
resource/action checks independently of frontend state.
