# Aurevia Super App

> راهنمای مرزهای اعتماد، جریان توکن، کنترل دسترسی و الزامات انتشار Production: [Enterprise Production Readiness (FA)](docs/enterprise-production-readiness-fa.md)

Production-shaped, Persian-first enterprise super-app monorepo. The browser talks only to the same-origin BFF; tokens remain encrypted on the server. Authorization is evaluated by the Authorization Service and OpenFGA. Token Exchange is deliberately absent.

## Documentation

- [English complete guide](docs/README-en.md)
- [راهنمای جامع فارسی](docs/README-fa.md)
- [راهنمای آموزشی صفر تا تسلط تیم فنی](docs/technical-team-zero-to-production-fa.md)
- [Architecture](docs/architecture.md)
- [Access-control model (فارسی)](docs/access-control-fa.md)
- [Micro Frontend access for users, roles and groups (فارسی)](docs/access-control-fa.md#دسترسی-micro-frontend-به-کاربر-گروه-و-نقش)
- [Code reference (فارسی)](docs/code-reference-fa.md)
- [Authorization Engine architecture (فارسی)](docs/authorization-engine-fa.md)
- [Complete architecture and OpenFGA reference (فارسی)](docs/architecture-openfga-complete-fa.md)
- [Dynamic Proxy Route management guide (فارسی)](docs/dynamic-proxy-routing-fa.md)
- [Legacy service self-service and authentication guide (فارسی)](docs/legacy-service-authentication-fa.md#تعریف-یک-micro-app-از-نوع-legacy-بدون-انتشار-نسخه)
- [Resource Catalog and Manifest architecture (فارسی)](docs/resource-catalog-manifest-architecture-fa.md)
- [HR/Finance ERP and OpenFGA resource-tree demo (فارسی)](docs/hr-finance-erp-openfga-demo-fa.md)
- [Superset routing, access and in-MFE embedding guide (فارسی)](docs/superset-routing-and-embedding-fa.md)
- [Git governance and repository access (فارسی)](docs/git-governance-fa.md)
- [Operations and troubleshooting (فارسی)](docs/operations-fa.md)

Repository collaboration rules are in [CONTRIBUTING.md](CONTRIBUTING.md) and security reporting is in [SECURITY.md](SECURITY.md).

## Pinned toolchain

- Node.js 22.15.0+ (22.x) / npm 10.9.2
- Java 21
- Spring Boot 3.5.5
- Webpack 5 Module Federation
- PostgreSQL 17, Redis 8, OpenFGA 1.18 (container tags are pinned in Compose)

## Repository

```text
apps/                         shell and four independently built MFEs
packages/                     UI authorization, contracts, translations
services/                     Java BFF and Authorization Service
infra/                        Compose, proxies, IAM, policy and databases
tests/                        end-to-end, security and contract tests
docs/                         ADRs, diagrams, threat model and runbooks
```

## Local commands

Copy `.env.example` to `.env` and replace every `change-me` value before startup.

```bash
npm ci
npm run infra:up
./mvnw verify
npm run build && npm test
```

The microfrontends are served independently from the Shell. With Docker Compose,
their default Remote Entry URLs are:

- Admin: `http://localhost:3001/remoteEntry.js`
- HR: `http://localhost:3002/remoteEntry.js`
- Finance: `http://localhost:3003/remoteEntry.js`
- Reports: `http://localhost:3004/remoteEntry.js`

The Administration panel accepts a complete `http://` or `https://` Remote Entry
URL. For local webpack development, run `dev:mfe:admin`, `dev:mfe:hr`,
`dev:mfe:finance`, and `dev:mfe:reports` in separate terminals. Use an HTTPS
Remote Entry URL when the Shell itself is deployed over HTTPS.

On Windows use `mvnw.cmd verify`. No real credentials or external deployment are needed. See [architecture](docs/architecture.md) for boundaries and request flows.

## Security invariants

- The browser receives only an opaque, Secure, HttpOnly session cookie.
- Browser applications use relative same-origin URLs and never receive bearer tokens.
- The BFF forwards the unchanged Public IAM access token to the operational gateway over mTLS.
- Only Authorization Service writes OpenFGA relationships.
- OpenFGA is the authorization source of truth; Redis only caches check decisions for a short TTL and tuple writes invalidate the matching entry.
- Access and refresh tokens are encrypted in the Redis-backed server-side Token Vault and never stored in the browser.
- Missing route/action/session/policy information denies access.
- Public and Operation Superset are separate; Operation Superset has no direct browser/network route and is reachable only through the authorized BFF tunnel.

## Manifest developer guide

The effective manifest is the frontend contract for navigation and presentation-level
authorization. It is not an API authorization credential: every protected backend request
is checked again by the BFF and Authorization Service.

### Fetching the manifest

After the OAuth2 Authorization Code login has completed, call the same-origin endpoint:

```http
GET /api/v1/me/manifest HTTP/1.1
Accept: application/json
X-Correlation-ID: <uuid>
Cookie: AUREVIA_SESSION=<opaque-session-id>
```

Frontend code must include the browser session and must not attach a bearer token:

```ts
import type { EffectiveManifest } from '@aurevia/contracts';

export async function fetchManifest(): Promise<EffectiveManifest> {
  const response = await fetch('/api/v1/me/manifest', {
    credentials: 'same-origin',
    redirect: 'manual',
    headers: {
      Accept: 'application/json',
      'X-Correlation-ID': crypto.randomUUID(),
    },
  });
  if (response.status === 401 || response.status === 302 || response.type === 'opaqueredirect') {
    window.location.assign('/oauth2/authorization/public-iam');
    throw new Error('AUTH_REDIRECT');
  }
  if (!response.ok) throw new Error(`Manifest HTTP ${response.status}`);
  return response.json() as Promise<EffectiveManifest>;
}
```

### Response contract

```json
{
  "version": "manifest-a1b2c3",
  "expiresAt": "2026-08-29T20:00:00Z",
  "panels": [
    {
      "id": "uuid",
      "code": "HR",
      "slug": "mfe-hr",
      "nameFa": "منابع انسانی",
      "nameEn": "Human Resources",
      "remoteEntry": "http://localhost:3002/remoteEntry.js",
      "exposedModule": "./bootstrap",
      "routeBasePath": "/hr",
      "semanticVersion": "0.1.0",
      "contractVersion": "1"
    }
  ],
  "permissions": {
    "business:hr.employee": ["view", "update"]
  },
  "resourceTree": [
    {
      "id": "uuid",
      "parent_id": null,
      "resource_key": "application:aurevia",
      "type": "APPLICATION",
      "name_fa": "آرویا",
      "name_en": "Aurevia",
      "actions": []
    }
  ]
}
```

Field semantics:

| Field | Meaning |
|---|---|
| `version` | Content-derived version suitable for change detection |
| `expiresAt` | Refresh deadline; the current service TTL is 60 seconds |
| `panels` | Active panels for which OpenFGA returned `can_view` |
| `remoteEntry` | Complete allowlisted `http://` or `https://` Module Federation URL |
| `permissions` | Effective active USER, GROUP and ROLE actions keyed by canonical resource key |
| `resourceTree` | Authorized nodes plus ancestors required to render the hierarchy |
| `presentation` | Optional `hide`, `disable` or `readOnly` UI policy when supplied |

The response is `Cache-Control: no-cache` and includes an ETag. Clients should refresh it
at `expiresAt`, after an administrative grant change, and after a new login. They must fail
closed when the response is unavailable or expired; retaining an expired manifest must not
enable actions.

### Consuming the manifest in Shell and MFEs

The Shell wraps remotes in `SHManifestProvider` and passes the same immutable snapshot to
the remote `mount` contract:

```ts
export interface RemoteModule {
  contractVersion: '1';
  mount(element: HTMLElement, context: {
    locale: 'fa-IR' | 'en-US';
    manifest: EffectiveManifest;
    correlationId: () => string;
  }): () => void;
}
```

Use canonical resource/action keys for presentation guards:

```tsx
<SHCan resource="business:hr.employee" action="view">
  <EmployeeList />
</SHCan>

<SHAction resource="business:hr.employee" action="update" mode="disable">
  <Button>ویرایش</Button>
</SHAction>

<SHRouteGuard resource="module:hr" action="view">
  <HrRoutes />
</SHRouteGuard>
```

`SHCan` hides by default, `SHAction` supports `hide`, `disable`, and `readOnly`, and
`SHRouteGuard` renders an access-denied state. These controls improve UX only. Never infer
that an API call is authorized because its button or route was visible.

### Adding a new manifest-controlled feature

1. Add a forward-only migration for the canonical resource and action.
2. Attach the action through `resource_action`.
3. Register operational routes with the same resource/action where applicable.
4. Add OpenFGA action-to-relation and action-to-permission mappings if the action is new.
5. Grant USER, GROUP, or ROLE access through the Admin MFE.
6. Wait for and monitor the transactional outbox projection.
7. Use the exact resource/action keys in the MFE guard.
8. Test direct, group, role, inherited, revoked, expired, and unrelated-user cases.
9. Verify the backend returns 403 even when a user manually bypasses the frontend guard.

The complete Persian reference, including all seven resource types, OpenFGA relations,
inheritance, Redis caching, outbox behavior, and known gaps, is in
[architecture-openfga-complete-fa.md](docs/architecture-openfga-complete-fa.md).

## Current production readiness

The topology is production-shaped, but the repository defaults are not production-ready.
Before production, close the documented authorization gaps, execute structured policies in
the runtime check path, persist decision logs, replace internal Basic Auth with workload
identity/mTLS, configure TLS and secure cookies, move secrets to a secret manager, add Redis
and PostgreSQL HA/backups, use a production Superset WSGI deployment, configure CSP and a
shared rate-limit backend, and add outbox/OpenFGA drift monitoring. Local `change-me` values,
development servers, and example data must never be promoted to production.

## Superset demo

`npm run infra:up` enables the `superset` profile. The one-shot init container migrates the
metadata database, creates the local administrator, initializes roles, and loads the official
Superset example datasets and dashboards when `SUPERSET_LOAD_EXAMPLES=yes`. Only this init
container receives temporary bootstrap egress because the examples are downloaded; the
runtime Operation Superset remains on the internal operation network with no published port.

Open the full Superset UI through the authenticated tunnel:

```text
http://localhost:8443/reports-runtime/superset/welcome/
```

Set `SUPERSET_LOAD_EXAMPLES=no` outside local/demo environments.
