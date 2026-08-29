# Contributing to Aurevia

## Workflow

1. Branch from an up-to-date `main` using `feature/<topic>`, `fix/<topic>`, or `docs/<topic>`.
2. Keep commits focused and use Conventional Commits, for example `feat(authz): add group role projection`.
3. Never commit `.env`, credentials, tokens, certificates, database dumps, or generated `dist`/`target` files.
4. Add or update tests and documentation with behavioral changes.
5. Run the local quality gate before opening a pull request.

```powershell
npm ci
npm run typecheck
npm test
npm run build
.\mvnw.cmd clean verify
docker compose -f infra/docker-compose/compose.yml config --quiet
git diff --check
```

## Authorization changes

Authorization changes require an explicit resource, action, subject type, default-deny behavior, audit impact, OpenFGA projection impact, migration strategy, and tests. Never enforce security only in the UI. Existing Flyway migrations are immutable; add a new numbered migration.

## Pull requests

PRs must explain the user-visible outcome, security impact, migrations, rollback, and verification evidence. Changes to security, authorization, IAM, proxying, or network boundaries require CODEOWNER review. Merge using squash for a small single-purpose PR or rebase for a curated multi-commit change; do not create merge commits on `main`.

## Releases

Tags use SemVer (`vMAJOR.MINOR.PATCH`). Release artifacts must come from CI, include an SBOM, and reference the exact commit SHA. Production secrets are supplied by the deployment platform, never repository defaults.
