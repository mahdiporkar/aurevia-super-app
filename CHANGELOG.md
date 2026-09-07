# Changelog

All notable changes follow [Semantic Versioning](https://semver.org/).

## 0.1.0 — 2026-09-08

### Added

- Relationship-based authorization with OpenFGA, transactional outbox and drift reconciliation.
- Secure BFF session/token-vault flow and governed Legacy/OAuth2 proxy routes.
- Manifest-driven Shell with independently loaded Admin, HR, Finance and Reports frontends.
- Eighteen dedicated Admin governance pages with field and contract coverage.
- Fresh-database, token-proxy and release-runtime verification gates.
- GitHub Pages product showcase and reproducible tag-based release workflow.

### Fixed

- Fresh-install race between bootstrap outbox replay and OpenFGA startup reconciliation.
- PostgreSQL manifest-tree action aggregation that caused server-side fetch to return 500.
- GitHub Pages repository enablement that caused the public product URL to return 404.

### Release constraints

Version 0.1.0 is the first source release. Production deployment still requires environment-owned
TLS certificates, secret manager integration, HA databases/Redis/OpenFGA, hardened Keycloak and
successful operational disaster-recovery evidence described in the production readiness guide.
