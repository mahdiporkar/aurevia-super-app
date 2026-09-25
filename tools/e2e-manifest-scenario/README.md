# Manifest / proxy-route E2E scenario

A 38-step scripted walkthrough of the hybrid-panel lifecycle against a running Compose stack:
manifest drafting and publish, artifact activation, rollback/roll-forward, SSO and legacy proxy
routes, Superset instance isolation, and the failure paths for each.

## Prerequisites

- The core stack, identity stack and MFE stack up under distinct Compose project names
  (`npm run infra:up`, `npm run identity:up`, `npm run mfe:up`).
- `%TEMP%/admpw.txt` and `%TEMP%/userbpw.txt` holding the `administrator` and `e2e-user-b`
  passwords. Step 04 creates user B and writes the latter.

## Running

Steps are numbered and stateful — run them in order from the repo root:

```
node tools/e2e-manifest-scenario/01-create-panel.mjs
...
node tools/e2e-manifest-scenario/38-final.mjs
```

Each step appends the ids it creates to the state file, so later steps can find them.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `AUREVIA_SCENARIO_BASE` | `http://localhost:8443` | BFF base URL |
| `AUREVIA_SCENARIO_STATE` | `.tmp/e2e-manual/state.json` | Ids created across steps |
| `AUREVIA_SCENARIO_IDS` | `.tmp/e2e-manual/ids.json` | Resource ids used by `grant.mjs` |

The state and ids files are environment-specific and stay out of version control.

## Compose overrides

`compose.override.yml` adds the SSO/legacy test services and their micro-frontends;
`compose.superset.override.yml` adds the two Superset instances used by steps 24-32.
The `mf/` fixture is the micro-frontend served for the test panel.
