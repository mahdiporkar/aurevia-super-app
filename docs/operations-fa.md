# عملیات، تست و عیب‌یابی

## چرخه توسعه

```powershell
npm ci
npm run build
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd verify
docker compose -f infra/docker-compose/compose.yml up -d --build
```

پس از تغییر MFE، build آن و reload سخت مرورگر لازم است. پس از تغییر Java، JAR را package و سرویس مربوطه را restart کنید. پس از تغییر Nginx ابتدا `nginx -t` اجرا شود.

## کنترل سلامت

```powershell
docker compose -f infra/docker-compose/compose.yml ps
docker logs --tail 100 aurevia-bff
docker logs --tail 100 aurevia-operation-superset-1
docker logs --tail 100 aurevia-nginx-1
```

## تست‌های لازم قبل از merge

```powershell
npm run build
npm test
.\mvnw.cmd verify
git diff --check
```

حداقل smoke test:

1. login موفق و عدم مشاهده token در DevTools storage.
2. `/api/v1/me/manifest` برابر 200.
3. نمایش چهار panel فعال مطابق دسترسی.
4. CSRF و mutation مدیریت.
5. ایجاد resource/action/user/grant و revoke.
6. فهرست گزارش فقط برای کاربر مجاز.
7. `/reports-runtime/superset/welcome/` با HTML غیرخالی.
8. CSS/JSهای `/static` برابر 200.
9. `/api/v1/me/` و `/superset/log/` مربوط به Superset برابر 200.

## عیب‌یابی

### Login با `?error` یا Invalid credentials

- issuer و redirect URI در Keycloak و `application.yml` باید دقیقاً یکسان باشند.
- Java/BFF باید بعد از نصب JDK restart شده باشد.
- کوکی قدیمی را پاک یا پنجره private امتحان کنید.
- لاگ BFF و Keycloak را با correlation زمانی مقایسه کنید.

### CSRF برابر 500 یا mutation برابر 403

- ابتدا `GET /api/v1/csrf` را کنترل کنید.
- cookie نشست و header CSRF باید هر دو ارسال شوند.
- mutationهای Admin بدون CSRF باید 403 شوند.
- tunnel Superset استثناست و CSRF خودش را دارد.

### `remoteEntry.js` با MIME `text/plain`

- build مربوط به MFE باید وجود داشته باشد.
- Nginx باید `mime.types` را include کند.
- remoteEntry نباید به SPA fallback یعنی `index.html` تبدیل شود.
- header آن no-cache است؛ پس از build، `Ctrl+F5` بزنید.

### Superset برابر 500

- لاگ Nginx را برای `no host in upstream ":8081"` ببینید.
- در location دارای `rewrite ... break`، مقداردهی `$bff` باید قبل از rewrite باشد.
- BFF، Operation Gateway و Operation Superset باید هر سه running باشند.

### Superset صفحه خالی با status 200

- اندازه body را بررسی کنید؛ HTML صفر بایت نشانه آزادشدن زودهنگام WebClient response است.
- body باید درون callback `exchangeToMono` با `writeWith` مصرف شود.

### CSSهای fontawesome/select2 برابر 404

public image باید علاوه بر `/app/superset/static`، مسیر Flask-AppBuilder زیر را نیز داشته باشد:

```text
/app/.venv/lib/python3.10/site-packages/flask_appbuilder/static/appbuilder
```

### CSP inline script

صفحات `/reports-runtime` CSP جداگانه دارند، چون Superset 5 bootstrap inline تولید می‌کند. CSP عمومی Shell همچنان `script-src 'self'` سخت‌گیرانه دارد.

### `/api/v1/me/` برابر 404 یا `/superset/log/` برابر 405

این‌ها endpointهای root-relative Superset هستند. سند runtime باید `Referrer-Policy: same-origin` داشته باشد تا Nginx درخواست `/api/v1` گزارش را از API خود Super App تشخیص دهد. `/superset/*` همیشه از BFF tunnel عبور می‌کند.

### گزارش برای کاربر دیده نمی‌شود

- asset باید `published=true` باشد.
- user باید grant مستقیم active برای action `view` روی resource همان asset داشته باشد.
- `expires_at` نباید گذشته باشد.
- در نسخه فعلی query گزارش Role/Group را لحاظ نمی‌کند؛ بخش شکاف‌ها در سند دسترسی را ببینید.

## backup و بازیابی

داده‌های حیاتی:

- PostgreSQL Authorization: metadata، grant، audit و outbox.
- PostgreSQL Superset Operation: dashboard/chart/dataset metadata.
- Keycloak DB: هویت و client configuration.
- OpenFGA: projection قابل بازسازی، ولی backup برای recovery سریع مفید است.
- Redis: transient است؛ از دست‌رفتن آن کاربران را logout می‌کند.

restore باید ابتدا DBهای منبع حقیقت، سپس OpenFGA projection و در پایان سرویس‌ها را بالا بیاورد.

## انتشار امن

- secret واقعی در Git قرار نگیرد.
- imageها با digest/tag قطعی ساخته شوند.
- migration جدید backward-compatible باشد.
- SBOM و dependency scan اجرا شود.
- Admin authorization gap پیش از Production بسته شود.
- Operation Superset نباید port publish عمومی داشته باشد.
- public Superset نباید DB/DWH credential داشته باشد.
