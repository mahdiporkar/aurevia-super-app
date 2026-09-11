# معماری External Identity Provider در Aurevia

این سند مرز Authentication و Authorization، Registry چند IdP، هویت canonical، اجرای Demo و قیود Production را توضیح می‌دهد.

## وضعیت قبل از این تغییر

- `infra/docker-compose/compose.yml` سرویس‌های `keycloak-db` و `keycloak` را همیشه اجرا می‌کرد و `aurevia-bff.depends_on` مستقیماً به `keycloak` وابسته بود.
- BFF فقط registration ثابت `public-iam` داشت. endpointهای مرورگر به `localhost:8180` و endpointهای backend به hostname داخلی `keycloak:8080` اشاره می‌کردند.
- Spring Security از Authorization Code + OIDC استفاده می‌کرد، توکن‌ها را پس از Login از authorized-client store خارج و رمز‌شده در Redis Token Vault نگه می‌داشت. مرورگر فقط cookie امن و HttpOnly داشت.
- Login Sync، کاربر را با `issuer + sub` می‌ساخت و گروه‌ها/claimهای LDAP را به Authorization Service می‌فرستاد.
- OpenFGA Subject یک هش deterministic از `issuer + sub` بود. این مقدار collision-safe بود، اما با تعویض IdP تغییر می‌کرد و در نتیجه identity واقعاً provider-independent نبود.
- LDAP/AD در Authorization Service اختیاری بود، اما مقادیر پیش‌فرض Compose آن به Samba و issuer محلی Keycloak اشاره می‌کرد.

## معماری جدید

```text
Browser
  | Secure + HttpOnly AUREVIA_SESSION (opaque)
  v
Aurevia BFF
  |-- provider routing (code / tenant / domain)
  |-- dynamic Spring ClientRegistration
  |-- encrypted server-side Token Vault
  v
OIDC Identity Provider (Keycloak / Entra ID / Okta / Auth0 / Google Workspace)
  |
  | validated ID token: signature + JWKS + issuer + audience + time + nonce
  v
Authorization Service
  | (provider_code, issuer, sub) -> external_identity -> canonical_user_id
  v
OpenFGA: user:<canonical_user_id>
```

Authentication فقط در IdP انجام می‌شود. Role، Permission و Relation در IdP تعریف نمی‌شوند. Authorization Service و OpenFGA تنها مرجع تصمیم مجوز هستند.

## مدل داده

Migration `V59__external_identity_providers.sql` دو بخش اصلی دارد:

- `identity_provider`: کد، نوع، issuer، endpointهای استاندارد OIDC، client ID، ارجاع secret، tenant، domainهای routing، claim mapping، وضعیت اتصال و آخرین health check.
- `external_identity`: نگاشت چند `issuer + subject` به یک ردیف `app_user`.
- `app_user.canonical_user_id`: شناسه پایدار و provider-independent که Subject واقعی OpenFGA است.

مقدار client secret هرگز در Registry ذخیره یا از API مدیریت برگردانده نمی‌شود؛ فقط reference مانند `secret://identity/bank-a` ذخیره می‌شود.

## اجرای Core بدون Keycloak

```bash
npm run infra:up
```

Compose اصلی فقط Core، PostgreSQL، Redis، OpenFGA، BFF، Authorization Service و اجزای عملیاتی Demo را اجرا می‌کند. BFF برای startup هیچ metadata، JWKS یا container مربوط به IdP را فراخوانی نمی‌کند. اگر هیچ provider فعالی ثبت نشده باشد، health سرویس سالم می‌ماند و فقط Login با پاسخ fail-closed مواجه می‌شود.

بررسی ایزولیشن Compose:

```bash
npm run infra:config:test
```

## Keycloak Demo اختیاری

```bash
npm run identity:up
npm run infra:up
```

ترتیب این دو فرمان الزامی نیست؛ Core بدون Demo بالا می‌آید. fixture توسعه در `infra/demo/integration-catalog.sql` provider با کد `public-iam` را ثبت می‌کند و فقط هنگام Login به Keycloak نیاز است.

خاموش‌کردن Demo:

```bash
npm run identity:down
```

Demo LDAP/Samba نیز جدا و اختیاری است:

```bash
docker compose --env-file .env -f infra/docker-compose/compose.identity-demo.yml --profile directory up -d
```

در این حالت LDAP روی `localhost:1389` منتشر می‌شود. برای sync سمت Core، متغیرهای `DIRECTORY_LDAP_URL`، `DIRECTORY_BASE_DN`، `DIRECTORY_BIND_DN`، `DIRECTORY_BIND_PASSWORD` و `DIRECTORY_ISSUER` را تنظیم و `DIRECTORY_SYNC_ENABLED=true` کنید. LDAP همچنان داده سازمانی و OU را تأمین می‌کند؛ Authentication و OpenFGA را جایگزین نمی‌کند.

## ثبت Keycloak خارجی

ابتدا secret را خارج از Database ایجاد کنید. در Production، برای reference زیر فایل
`$IDENTITY_PROVIDER_SECRET_ROOT/identity/bank-a.json` باید روی BFF به‌صورت read-only mount شود:

```json
{"clientSecret":"value-from-your-secret-manager"}
```

سپس در صفحه «هویت و نقش» پنل مدیریت، Provider را اضافه کنید یا از API داخلی حفاظت‌شده استفاده کنید:

```http
POST /internal/v1/registry/identity-providers
Content-Type: application/json
X-Actor: administrator

{
  "code": "bank-a-keycloak",
  "name": "Keycloak Bank A",
  "type": "KEYCLOAK",
  "issuerUrl": "https://sso.bank-a.com/realms/main",
  "authorizationEndpoint": "https://sso.bank-a.com/realms/main/protocol/openid-connect/auth",
  "tokenEndpoint": "https://sso.bank-a.com/realms/main/protocol/openid-connect/token",
  "jwksUri": "https://sso.bank-a.com/realms/main/protocol/openid-connect/certs",
  "userInfoEndpoint": "https://sso.bank-a.com/realms/main/protocol/openid-connect/userinfo",
  "clientId": "aurevia-bff",
  "clientSecretReference": "secret://identity/bank-a",
  "enabled": true,
  "tenantId": "bank-a",
  "domains": ["bank-a.com"],
  "scopes": ["openid", "profile", "email"],
  "audiences": ["aurevia-bff"],
  "subjectClaim": "sub",
  "usernameClaim": "preferred_username",
  "groupsClaim": "groups"
}
```

Redirect URI ثبت‌شده در IdP باید دقیقاً با endpoint عمومی BFF منطبق باشد:

```text
https://aurevia.company.com/login/oauth2/code/bank-a-keycloak
```

پس از ثبت، دکمه Health Check سند JWKS را با redirect غیرفعال، timeout محدود و سقف پاسخ بررسی می‌کند.

## Multi IdP و Routing ورود

Provider را می‌توان صریح یا بر اساس tenant/domain انتخاب کرد:

```text
GET /auth/login?provider=bank-a-keycloak
GET /auth/login?tenant=bank-a
GET /auth/login?domain=user@bank-a.com
GET /auth/providers?tenant=bank-a
```

اگر بدون selector بیش از یک Provider منطبق باشد، BFF انتخاب را حدس نمی‌زند و `409` می‌دهد تا Shell لیست `/auth/providers` را به کاربر نمایش دهد. redirect همیشه مسیر داخلی ساخته‌شده توسط سرور است و URL ورودی کاربر به‌عنوان redirect پذیرفته نمی‌شود.

## Flow کامل Login

1. Browser با `provider`، `tenant` یا `domain` به `/auth/login` می‌رود.
2. BFF انتخاب را از Registry در Authorization Service می‌گیرد.
3. Spring Security، `ClientRegistration` را در همان لحظه از issuer/endpoints/client ID می‌سازد و secret reference را در BFF resolve می‌کند.
4. Browser با state، nonce و PKCE/Authorization Code flow به authorization endpoint هدایت می‌شود.
5. Callback در BFF code را با token endpoint مبادله می‌کند.
6. ID token از طریق JWKS امضاسنجی می‌شود؛ issuer، client audience/azp، زمان، nonce و audienceهای اضافی پیکربندی‌شده بررسی می‌شوند. provider یا issuer اشتباه fail-closed است.
7. BFF claimهای مجاز را به Login Sync می‌فرستد. tokenها هرگز به Browser داده نمی‌شوند.
8. Access/refresh token با encryption در Redis Token Vault ذخیره می‌شود و Session فقط identity حداقلی و handle opaque دارد.

## Flow نگاشت Canonical Identity

```text
Keycloak A: issuer-a + sub-123 ----+
                                    +--> app_user.canonical_user_id=usr_789
Entra ID: issuer-b + oid-999 -------+                 |
                                                      v
                                              OpenFGA user:usr_789
```

Login جدید، `providerCode + issuer + subject` را با Registry تطبیق می‌دهد. alias شناخته‌شده همان کاربر canonical را update می‌کند؛ alias ناشناخته کاربر canonical جدید می‌سازد. اتصال identity متعلق به Provider جدید به کاربر موجود باید صریحاً در پنل مدیریت انجام شود؛ تطبیق خودکار بر اساس email مجاز نیست.

Migration، tupleهای موجود را از subject هش‌شده قدیمی به `canonical_user_id` بازپروژکت می‌کند. Reconciler نیز از این پس فقط شناسه canonical را به OpenFGA می‌فرستد.

## Flow Authorization

1. BFF از Session فقط `issuer` و `subject` تأییدشده را می‌خواند.
2. Authorization Service این alias را در `external_identity` resolve می‌کند.
3. Check با `user:<canonical_user_id>` و relation استاندارد به OpenFGA می‌رود.
4. در صورت ALLOW، policyهای context-aware سمت سرور ارزیابی می‌شوند؛ DENY در هر مرحله نهایی است.
5. تغییر IdP یا Subject خارجی، تا وقتی alias جدید به همان canonical user متصل باشد، tuple و Permission را تغییر نمی‌دهد.

## LDAP و Group Mapping

claim گروه، DN، objectGUID، department، title و employeeType فقط از principal امضاشده خوانده می‌شوند. Directory Sync نیز اختیاری است و با issuer مربوط به Tenant کار می‌کند. OU/LDAP به membership و سپس relationهای OpenFGA نگاشت می‌شود؛ نبود claim مورداعتماد، membership محاسباتی را fail-closed می‌کند.

## الزامات Production

- `IDENTITY_PROVIDER_ALLOW_HTTP=false` و `IDENTITY_PROVIDER_ALLOW_PRIVATE_HOSTS=false` ثابت می‌مانند.
- `IDENTITY_PROVIDER_REQUIRE_HOST_ALLOWLIST=true` است؛ همه hostهای issuer، authorization، token، JWKS و userinfo باید در `IDENTITY_PROVIDER_ALLOWED_HOSTS` باشند. wildcard محدود مثل `*.company.com` پشتیبانی می‌شود.
- secretها در volume/secret manager خارج از Registry قرار می‌گیرند و mount باید read-only و محدود به process BFF باشد.
- endpointها redirect دنبال نمی‌کنند و timeout دارند. egress firewall باید همان allowlist را enforce کند تا DNS rebinding نیز در لایه شبکه مهار شود.
- Audit eventهای create، update، issuer/client/tenant change، enable/disable، health check و link/unlink identity ثبت می‌شوند.
- بعد از هر تغییر مدیریتی IdP، cache registration در BFF invalidate می‌شود.
- Permissionها در Keycloak/Entra/Okta مدل نمی‌شوند و OpenFGA حذف یا bypass نمی‌شود.

## محدودیت‌های فعلی

- Registry endpointها را دستی دریافت می‌کند؛ OIDC Discovery (`.well-known/openid-configuration`) هنوز فقط به‌عنوان منبع انسانی استفاده می‌شود و auto-import ندارد.
- یک deployment از BFF به یک Secret Store فایل‌محور یا adapter توسعه محلی متصل است؛ adapter مستقیم Vault/KMS باید مطابق محیط سازمان افزوده شود.
- انتخاب تعاملی Provider در API آماده است، اما UX مستقل login chooser در Shell می‌تواند متناسب با برند سازمان توسعه یابد.
- Health Check فعلی JWKS و وجود signing key را می‌سنجد؛ synthetic authorization-code login به حساب تست نیاز دارد و در health check خودکار اجرا نمی‌شود.
- allowlist نرم‌افزاری جایگزین egress firewall، DNS policy و TLS inspection سازمان نیست.

