# راهنمای تست سرتاسری Legacy و OAuth2

این سند روش اجرای دو مسیر واقعی و قابل‌تکرار را توضیح می‌دهد:

1. سرویس Modern که access token جاری Keycloak را فقط در Java BFF دریافت می‌کند؛
2. سرویس Legacy که BFF با یک Secret Reference، token سرویس را دریافت، رمزگذاری و در Redis cache می‌کند.

در هر دو مسیر مرورگر فقط Cookie ناماتریال `AUREVIA_SESSION` را می‌فرستد. هیچ access token، refresh token، ID token، نام کاربری Legacy یا رمز Legacy در JavaScript، Local Storage، پاسخ API یا لاگ امن نمایش داده نمی‌شود.

## اجزای سناریو

| جزء | مسئولیت | دسترسی شبکه |
|---|---|---|
| `mock-legacy` | endpoint دریافت token و API حفاظت‌شده‌ی Legacy | API از gateway؛ endpoint token فقط از شبکه محدود BFF |
| `mock-oauth` | API حفاظت‌شده با JWT صادرشده توسط Keycloak | فقط از operation gateway |
| `operation-gateway` | هدایت درخواست به fixture مقصد | بدون host port |
| `aurevia-bff` | بازیابی token، authorization و proxy | تنها backend قابل فراخوانی از nginx عمومی |
| `authorization-service` | resolve کردن route/operation و کنترل resource/action | شبکه داخلی |
| `redis` | Session بدون token و vault رمز‌شده | شبکه داده‌ی داخلی |
| `demo-catalog-init` | ثبت idempotent داده‌ی نمونه پس از Flyway | one-shot و فقط Compose توسعه |

داده‌های نمونه در [integration-catalog.sql](../infra/demo/integration-catalog.sql) تعریف شده‌اند و در Production migration وارد نمی‌شوند.

## داده‌ی ثبت‌شده در میکرو راهبری

### مسیر Legacy

- Connection: `connection://demo/legacy`
- Auth profile: `legacy-demo-password`
- Service target: `legacy-demo`
- Route: `/api/proxy/legacy-demo`
- Operation: `GET /ping`
- Resource/action: `api:integration.legacy-demo` + `view`
- Secret reference: `secret://demo/legacy`

### مسیر OAuth2

- Auth profile: `public-iam-forward`
- Service target: `oauth2-demo`
- Route: `/api/proxy/oauth2-demo`
- Operation: `GET /ping`
- Resource/action: `api:integration.oauth2-demo` + `view`

پروفایل OAuth2 توکن جدیدی تولید یا exchange نمی‌کند؛ همان access token جاری کاربر از vault رمز‌شده خوانده و به upstream تأییدشده ارسال می‌شود.

## اجرای خودکار از صفر

پیش‌نیازها و ساخت اولیه در [راهنمای نصب تازه](fresh-install-validation-fa.md) آمده است. پس از آماده شدن `.env` و bootstrap شدن OpenFGA:

```powershell
npm ci
npm run build
npm run openfga:bootstrap
npm run infra:up
```

رمز کاربر نمونه را فقط در متغیر محیطی همان process قرار دهید و تست را اجرا کنید:

```powershell
$env:AUREVIA_DEMO_PASSWORD = '<local-demo-password>'
npm run infra:verify:token-proxy
Remove-Item Env:AUREVIA_DEMO_PASSWORD
```

اسکریپت [verify-token-proxy.mjs](../tools/verify-token-proxy.mjs) این موارد را fail-closed بررسی می‌کند:

- اجرای واقعی Authorization Code با صفحه Login Keycloak؛
- صدور Cookie opaque و عدم شباهت آن به JWT؛
- resolve شدن Cookie به Session سمت سرور؛
- وجود فقط `SessionIdentity` کمینه در Session Redis؛
- نبود `OidcIdToken`، `OidcUserAuthority`، authorized client، refresh token یا JWT در Session؛
- درخواست اول Legacy به‌عنوان cache miss؛
- درخواست دوم Legacy به‌عنوان cache hit؛
- موفقیت مسیر OAuth2/Keycloak؛
- نبود هر نوع token یا Authorization header در پاسخ برگشتی به کلاینت.

خروجی موفق باید شامل HTTP 200 برای `legacy-miss`، `legacy-hit` و `oauth2` و دو مقدار زیر باشد:

```json
{
  "serverSessionContainsTokenMaterial": false,
  "tokenMaterialReturnedToClient": false
}
```

## تست از رابط میکرو راهبری

پس از Login با مدیر سیستم، صفحه «آزمایش یکپارچه‌سازی» را در MFE راهبری باز کنید:

- «اجرای Legacy» یک درخواست واقعی را اجرا می‌کند؛
- «اجرای OAuth2 / Keycloak» مسیر Modern را اجرا می‌کند؛
- «Legacy ×2» امکان مشاهده‌ی رفتار cache را می‌دهد؛
- جدول نتیجه فقط نام سرویس، نوع credential، HTTP status و Correlation ID را نشان می‌دهد.

برای هر خطا ابتدا Ready بودن Target، Route و Auth profile را در همان صفحه بررسی کنید و سپس Correlation ID را در لاگ BFF جست‌وجو کنید.

## شواهد امن در محیط توسعه

در Compose توسعه، `TOKEN_EVIDENCE_LOGGING_ENABLED=true` است. رویداد `DEV_TOKEN_EVIDENCE` فقط fingerprint کوتاه، نوع credential، cache status و Correlation ID را ثبت می‌کند؛ خود token هرگز log نمی‌شود.

```powershell
docker compose --env-file .env -f infra/docker-compose/compose.yml logs aurevia-bff
```

در `application-prod.yml` فعال‌سازی این قابلیت ممنوع است و guard راه‌اندازی، Production را در صورت تلاش برای فعال‌سازی متوقف می‌کند.

## قواعد امنیتی Legacy برای Production

- مقدار username/password در جدول route، target یا auth profile ذخیره نشود؛ فقط `credentialSecretRef` مجاز است.
- resolver واقعی باید به Vault/KMS سازمان متصل شود؛ resolver محلی JSON مخصوص توسعه است.
- endpoint دریافت token باید در `outbound_connection` با host/port allowlist و TLS اجباری ثبت شود.
- token cache با AES-GCM و key-id نسخه‌دار رمزگذاری شود؛ کلید رمزنگاری خارج از دیتابیس و Redis بماند.
- rotation با active key جدید و previous key محدود انجام شود.
- timeout، حداکثر اندازه پاسخ و JSON Pointerهای token/expiry صریح تعریف شوند.
- response، exception، metric label و audit log نباید credential material داشته باشند.

جزئیات تمام فیلدها در [راهنمای فرم‌های راهبری](operator-admin-form-field-guide-fa.md) و طراحی credential در [راهنمای احراز هویت Legacy](legacy-service-authentication-fa.md) است.

## ارتقای Session امن

namespace فعلی Spring Session، `aurevia:session:v2` است. این نسخه authorityهای OIDC را نیز به نام ساده‌ی مجوز تبدیل می‌کند؛ زیرا `OidcUserAuthority` در صورت serialize شدن ID token را همراه خود نگه می‌دارد.

هنگام ارتقا از نسخه‌ی قدیمی:

1. ابتدا image جدید BFF را منتشر کنید؛
2. ترافیک را فقط به instanceهای جدید ببرید؛
3. Sessionهای namespace قدیمی `aurevia:session:sessions:*` را revoke کنید؛ این کار کاربران را مجبور به Login مجدد می‌کند؛
4. namespace جدید را با تست خودکار بالا بررسی کنید؛
5. کلیدهای vault token را حذف نکنید؛ logout و TTL چرخه‌ی عمر آن‌ها را مدیریت می‌کنند.

Rollback به نسخه‌ای که authority حامل ID token را serialize می‌کند مجاز نیست. در rollback عملیاتی باید image امن قبلی و namespace مستقل دیگری استفاده شود.

## عیب‌یابی سریع

| نشانه | بررسی |
|---|---|
| 401 پس از Login | issuer/redirect URI، Cookie Secure و سلامت Redis |
| 403 در هر دو سناریو | grant منبع `view` و تخلیه outbox/OpenFGA |
| 404 route | `serviceSlug`، `pathPrefix` و active بودن operation |
| 502 در Legacy | allowlist connection، Secret Reference، token endpoint و شبکه `bff-egress` |
| Legacy اول موفق و دوم ناموفق | expiry/skew، رمز vault و schema پاسخ token |
| OAuth2 با 401 upstream | issuer/audience/JWK fixture و access token منقضی‌شده |
| `serverSessionContainsTokenMaterial` true | انتشار را متوقف کنید؛ BFF یا namespace قدیمی است |

