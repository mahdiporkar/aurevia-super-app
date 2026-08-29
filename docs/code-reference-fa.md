# مرجع کد، فایل‌به‌فایل

این سند کد دست‌نویس پروژه را بر اساس مسئولیت و بلوک منطقی توضیح می‌دهد. فایل‌های تولیدشده `*.d.ts` محصول build هستند و منطق اجرایی مستقلی ندارند.

## ریشه مخزن

| فایل | مسئولیت |
|---|---|
| `pom.xml` | reactor Maven و نسخه‌های مشترک سرویس‌های Java |
| `package.json` | workspaceها، build/test و orchestration فرانت‌اند |
| `package-lock.json` | قفل دقیق dependencyهای npm |
| `tsconfig.base.json` | قواعد مشترک TypeScript |
| `.nvmrc` | نسخه Node.js |
| `.env.example` | قرارداد متغیرهای محیطی بدون secret واقعی |

## Shell

### `apps/shell/src/index.tsx`

- style و تنظیم RTL را وارد می‌کند.
- manifest کاربر را از BFF می‌خواند.
- منو و routeهای MFE را از metadata می‌سازد.
- remote را هنگام نیاز بارگذاری می‌کند؛ بنابراین failure یک MFE کل Shell را متوقف نمی‌کند.

### `remote-loader.ts`

- script مربوط به `remoteEntry.js` را inject می‌کند.
- container مربوط به Module Federation را پیدا می‌کند.
- shared scope را initialize و ماژول expose‌شده را resolve می‌کند.
- خطا را به caller برمی‌گرداند تا UI fallback نمایش دهد.

## Micro Frontendها

### Admin

- `bootstrap.tsx`: mount مستقل Admin MFE و اتصال صفحه‌ها.
- `Panels.tsx`: دریافت registry پنل‌ها، loading، error، retry، فرم و نمایش وضعیت فعال.
- `SupersetAssets.tsx`: تعریف asset گزارش/داشبورد و اتصال آن به resource داخلی.

Admin APIها از prefix `/api/v1/admin` استفاده می‌کنند. mutationها ابتدا CSRF را از `/api/v1/csrf` دریافت می‌کنند.

### Reports

`apps/mfe-reports/src/bootstrap.tsx`:

- `GET /api/v1/reports` را اجرا می‌کند.
- گزارش‌های مجاز را به‌صورت card/list نشان می‌دهد.
- `url_path` را زیر `/reports-runtime` باز می‌کند.
- خطا و empty state را مدیریت می‌کند.

### HR و Finance

فعلاً نمونه‌های کوچک برای اثبات استقلال build و Module Federation هستند. منطق عملیاتی باید از route registry و BFF عبور کند و نباید مستقیم gateway را فراخوانی کند.

## packageهای مشترک

- `packages/contracts`: typeهای Panel، Manifest، Permission و قراردادهای مشترک.
- `packages/i18n`: ترجمه فارسی/انگلیسی.
- `packages/sh-core-ui`: componentهای UI و guard نمایشی مبتنی بر permission.

## BFF

### `SuperappBffApplication.java`

نقطه شروع Spring Boot است.

### `security/SecurityConfig.java`

- health، root و routeهای login/callback را public می‌کند.
- سایر routeها را authenticated می‌کند.
- Spring CSRF را برای mutationها فعال نگه می‌دارد.
- فقط `/api/v1/superset/**` را از Spring CSRF خارج می‌کند، چون Superset CSRF مستقل دارد.
- logout را به `VaultLogoutHandler` متصل می‌کند.

### token vault

- `TokenVaultCrypto`: رمزگذاری/رمزگشایی AES-GCM و key id.
- `TokenVaultService`: ذخیره token record با TTL در Redis و نگهداری handle در session.
- `RefreshCoordinator`: هماهنگ‌کردن refresh هم‌زمان برای جلوگیری از چند refresh موازی.
- `TokenRefreshService`: refresh پیش‌دستانه، single-flight و یک retry کنترل‌شده پس از 401.
- `OidcLoginSuccessHandler`: sync هویت/گروه، ذخیره vault، تعویض session id و redirect نهایی.
- `VaultLogoutHandler`: حذف token vault هنگام logout.

### Controllerها

| کلاس | route | مسئولیت |
|---|---|---|
| `CsrfController` | `/api/v1/csrf` | ساخت/برگرداندن token CSRF |
| `MeController` | `/api/v1/me`, `/api/v1/me/manifest` | هویت و manifest |
| `AdminProxyController` | `/api/v1/admin/**` | proxy کنترل‌پلین به Authorization Service |
| `ReportsController` | `/api/v1/reports` | assetهای گزارش مجاز کاربر |
| `OperationSupersetProxyController` | `/api/v1/superset/**` | tunnel امن Superset به Gateway عملیاتی |
| `OperationalProxyController` | `/hr-micro/**`, `/finance-micro/**` | route resolution، check، token refresh و proxy محدودشده عملیاتی |

### `OperationSupersetProxyController.java` به ترتیب اجرا

1. `REQUEST_HEADERS` فقط headerهای مورد نیاز را allowlist می‌کند.
2. `RESPONSE_HEADERS` headerهای امن/ضروری پاسخ را انتخاب می‌کند.
3. constructor آدرس gateway را با `RouteNormalizer.allowlistedTarget` بررسی می‌کند.
4. redirect خودکار WebClient خاموش است تا `Location` به مرورگر برگردد، نه اینکه BFF داخل شبکه آن را دنبال کند.
5. path با `normalizePath` پاک‌سازی می‌شود.
6. query string بدون decode/re-encode غیرضروری منتقل می‌شود.
7. subject احراز‌شده در `X-Aurevia-Subject` قرار می‌گیرد.
8. prefix و host اصلی forward می‌شوند.
9. فقط POST/PUT/PATCH body upstream می‌گیرند؛ GET receiver اضافی ایجاد نمی‌کند.
10. status و headerهای upstream روی پاسخ WebFlux قرار می‌گیرند.
11. body درون lifecycle همان WebClient response stream می‌شود؛ خارج‌کردن Flux از `exchangeToMono` باعث body صفر بایت می‌شد.
12. `Location`های root-relative زیر `/reports-runtime` بازنویسی می‌شوند.

### proxy helpers

- `RouteNormalizer`: جلوگیری از path traversal و مقصد خارج از allowlist.
- `ProxyRetryPolicy`: retry محدود برای عملیات idempotent و خطاهای transient.
- `AuthorizationServiceClient`: client داخلی manifest/authorization.
- `GatewayWebClientConfiguration`: allowlist مقصد و پیکربندی اختیاری PKCS12/mTLS برای gateway.

## Authorization Service

### Security

`config/SecurityConfig.java` health را public و همه `/internal/**` را با Basic Auth داخلی محافظت می‌کند. CSRF برای API داخلی ignore شده، چون این API browser-facing نیست.

### `AuthorizationController`

- `/authorize/check`: OpenFGA relationship check و Decision.
- `/authorize/check-batch`: اجرای چند check.
- `/subjects/{id}/manifest`: ترکیب permissionهای USER، GROUP و ROLE فعال، ETag و TTL یک دقیقه.

### identity و route registry

- `IdentitySyncController`: upsert هویت OIDC و جایگزینی idempotent snapshot عضویت گروه‌ها در login.
- `IdentityAdminController`: فهرست گروه/نقش، ساخت نقش و assign/revoke نقش برای USER/GROUP.
- `RouteResolutionController`: longest-prefix resolution با مرز path segment و اتصال method/pattern به resource/action.
- `AdminAuthorizationInterceptor`: الزام grant فعال admin برای تمام registry endpointهای مدیریتی.
- `WebMvcConfiguration`: نصب interceptor و تعریف استثناهای صریح endpointهای subject-facing.

### `RegistryController`

- CRUD پنل‌های MFE.
- optimistic locking با query parameter `version`.
- archive منطقی به‌جای delete فیزیکی.
- ثبت outbox برای تغییرات panel.
- مشاهده audit با limit کنترل‌شده.

### `AccessAdminController`

- CRUD منبع و action.
- اتصال action به resource.
- ثبت کاربر.
- grant مستقیم و revoke منطقی.
- audit تغییرات مدیریتی.

grant برای USER/GROUP/ROLE ساخته می‌شود، action به relation نگاشت می‌شود و write/delete در outbox قرار می‌گیرد. رفتار کامل در [access-control-fa.md](access-control-fa.md) توضیح داده شده است.

### `SupersetAssetController`

- فهرست assetهای Superset.
- فهرست assetهای publish‌شده دارای grant مستقیم user.
- ساخت هم‌زمان resource خارجی، اتصال action `view` و رکورد `superset_asset` در یک transaction.

### Policy و OpenFGA

- `StructuredPolicyEvaluator`: allowlist field/operator/obligation و default deny.
- `OperationalRules`: org scope و maker-checker.
- `RelationshipAuthorizationPort`: مرز domain با engine رابطه‌ای.
- `OpenFgaRelationshipAdapter`: پیاده‌سازی HTTP/OpenFGA.
- `OpenFgaConfiguration`: ساخت client.
- `OutboxReconciler`: پردازش دوره‌ای eventهای pending.

## migrationهای دیتابیس

- `V1__control_plane.sql`: همه typeها و tableهای پایه، index، audit و outbox.
- `V2__bootstrap_catalog.sql`: چهار panel، actionها و resourceهای پایه.
- `V3__development_users_and_actions.sql`: کاربران توسعه و اتصال actionها.
- `V4__superset_report_catalog.sql`: dashboard نمونه و grantهای اولیه.
- `V5__superset_access_levels.sql`: سطح‌های گزارش.
- `V6__active_grant_uniqueness.sql`: یکتایی grant فعال.
- `V7__groups_roles_and_admin_grant.sql`: گروه‌ها، نقش‌ها و bootstrap مجوز admin.
- `V8__operational_route_catalog.sql`: resource/action و routeهای عملیاتی HR/Finance.
- `V9__panel_authorization_and_route_operations.sql`: resource مجزای هر panel، فیلتر manifest، operationهای کامل HR/Finance و bootstrap outbox.

ترتیب migrationها قرارداد است؛ migration اجراشده را ویرایش نکنید، migration جدید بسازید.

## Infra

### `infra/nginx/nginx.conf`

- Shell و MFEها را سرو می‌کند.
- CSP و headerهای امنیتی را می‌گذارد.
- `remoteEntry.js` را no-cache می‌کند.
- `/static` را فقط به public asset host می‌دهد.
- `/api`, `/auth`, `/oauth2`, `/login` را به BFF می‌دهد.
- `/reports-runtime` و endpointهای root-relative Superset را به tunnel BFF می‌دهد.

### دو Superset

- `infra/superset-public/Dockerfile`: فقط assetهای Superset و Flask-AppBuilder را به Nginx کپی می‌کند.
- `infra/superset-public/nginx.conf`: فقط `/static` و `/health`؛ بقیه 404.
- `infra/superset-operation/superset_config.py`: DB، Remote User، cookie مستقل، ProxyFix، خاموش‌کردن telemetry.
- `infra/mock-operation/gateway.conf`: endpoint داخلی `/superset/` روی پورت 80.

### هویت و مجوز

- `infra/keycloak/realm-aurevia.json`: realm، client و کاربران توسعه.
- `infra/openfga/model.fga`: typeها، relationها و permissionهای مشتق‌شده.
- `infra/openfga/model-tests.yaml`: اثبات grant گروه/Role، مستقیم و default deny.

## تست‌ها

- تست crypto از round-trip و خرابی ciphertext محافظت می‌کند.
- تست RouteNormalizer مسیر مجاز/نامجاز را بررسی می‌کند.
- تست ProxyRetryPolicy retry ایمن را بررسی می‌کند.
- تست CSRF قرارداد endpoint را پوشش می‌دهد.
- تست policy حالت allow، deny و خطای context را پوشش می‌دهد.
- تست OperationalRules جداسازی وظایف را بررسی می‌کند.
