# Aurevia Super App

Production-shaped, Persian-first enterprise super-app monorepo. The browser talks only to the same-origin BFF; tokens remain encrypted on the server. Authorization is evaluated by the Authorization Service and OpenFGA. Token Exchange is deliberately absent.

## Documentation

- [English complete guide](docs/README-en.md)
- [راهنمای جامع فارسی](docs/README-fa.md)
- [Architecture](docs/architecture.md)
- [Access-control model (فارسی)](docs/access-control-fa.md)
- [Code reference (فارسی)](docs/code-reference-fa.md)
- [Authorization Engine architecture (فارسی)](docs/authorization-engine-fa.md)
- [Git governance and repository access (فارسی)](docs/git-governance-fa.md)
- [Operations and troubleshooting (فارسی)](docs/operations-fa.md)

Repository collaboration rules are in [CONTRIBUTING.md](CONTRIBUTING.md) and security reporting is in [SECURITY.md](SECURITY.md).

## Pinned toolchain

- Node.js 22.14.0 / npm 10.9.2
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
- Public and Operation Superset are separate; Operation Superset has no browser route.
