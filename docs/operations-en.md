# Operations, Testing, and Troubleshooting

## Development cycle

```powershell
npm ci
npm run build
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd verify
docker compose -f infra/docker-compose/compose.yml up -d --build
```

Rebuild an MFE and hard-refresh after frontend changes. Package the JAR and restart the relevant service after Java changes. Always run `nginx -t` before reloading Nginx.

## Health and logs

```powershell
docker compose -f infra/docker-compose/compose.yml ps
docker logs --tail 100 aurevia-bff
docker logs --tail 100 aurevia-operation-superset-1
docker logs --tail 100 aurevia-nginx-1
```

## Pre-merge verification

```powershell
npm run build
npm test
.\mvnw.cmd verify
git diff --check
```

Minimum smoke suite:

1. Successful login with no token in browser storage.
2. `/api/v1/me/manifest` returns 200.
3. Authorized active panels render.
4. Admin mutation succeeds only with CSRF.
5. Resource/action/user/grant creation and revocation work.
6. Reports list contains only permitted assets.
7. `/reports-runtime/superset/welcome/` returns non-empty HTML.
8. `/static` CSS/JS assets return 200.
9. Superset `/api/v1/me/` and `/superset/log/` return 200.

## Troubleshooting

### Login ends with `?error` or Invalid credentials

- Verify that issuer and redirect URI exactly match in Keycloak and `application.yml`.
- Restart the BFF after installing or changing Java.
- Clear stale cookies or use a private browser window.
- Compare BFF and Keycloak logs by time/correlation.

### CSRF 500 or mutation 403

- Check `GET /api/v1/csrf` first.
- The browser must send both session cookie and CSRF header.
- Admin mutation without CSRF should return 403.
- The Superset tunnel is the exception because Superset owns its CSRF validation.

### `remoteEntry.js` has `text/plain` MIME

- Ensure the MFE build exists.
- Ensure Nginx includes `mime.types`.
- Do not let the remote entry fall back to SPA `index.html`.
- Hard-refresh after rebuilding; remote entries are intentionally no-cache.

### Superset returns 500

- Look for `no host in upstream ":8081"` in Nginx logs.
- `$bff` must be assigned before a `rewrite ... break` directive.
- Verify BFF, Operation Gateway, and Operation Superset health.

### Superset returns 200 but displays an empty page

Measure the body size. A zero-byte HTML response means the WebClient response was released before subscription. The controller must consume the body with `writeWith` inside `exchangeToMono`.

### FontAwesome or Select2 returns 404

The public image must contain both `/app/superset/static` and Flask-AppBuilder assets from:

```text
/app/.venv/lib/python3.10/site-packages/flask_appbuilder/static/appbuilder
```

### CSP blocks an inline script

`/reports-runtime` has a dedicated CSP because Superset 5 emits an inline bootstrap. The normal shell retains the stricter `script-src 'self'` policy.

### Superset `/api/v1/me/` returns 404 or `/superset/log/` returns 405

These are root-relative Superset endpoints. The runtime document uses `Referrer-Policy: same-origin`, allowing Nginx to distinguish Superset `/api/v1` calls from Super App APIs. `/superset/*` always uses the BFF tunnel.

### A report is not visible

- The asset must be published.
- The current user must have an active, non-expired direct `view` grant on its backing resource.
- Verify the effective USER/GROUP/ROLE permissions and backing-resource grants.

## Backup and recovery

- Authorization PostgreSQL: control-plane source of truth.
- Operation Superset PostgreSQL: dashboards, charts, datasets, and metadata.
- Keycloak database: identities and clients.
- OpenFGA: rebuildable projection, but backup reduces recovery time.
- Redis: transient; loss logs users out.

Restore source-of-truth databases first, rebuild the OpenFGA projection, then start application services.

## Secure release checklist

- No real secret is committed.
- Images and dependencies are pinned and scanned.
- New migrations are backward-compatible.
- Verify the administrator actor has an active `application:aurevia/admin` grant and `X-Actor` propagation.
- Operation Superset has no public published port.
- Public Superset has no DWH credentials.
