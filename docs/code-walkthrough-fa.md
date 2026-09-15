# راهنمای خواندن کد به ترتیب اجرا

این سند معادل نگهداری‌پذیر «توضیح خط‌به‌خط» است: هر فایل دست‌نویس از نقطه ورود تا خروج، به ترتیب اجرای بلوک‌ها توضیح داده می‌شود. importها فقط dependency را معرفی می‌کنند و فایل‌های `*.d.ts`, `dist`, `target` و lockfile منطق runtime ندارند.

## یک درخواست از مرورگر

1. `infra/nginx/nginx.conf` headerهای امنیتی را اضافه، asset را سرو و مسیرهای API را فقط به BFF forward می‌کند.
2. `SecurityConfig` route عمومی را جدا، session را الزام و mutation را با CSRF محافظت می‌کند.
3. controller مناسب فقط داده مورد نیاز را دریافت می‌کند؛ مقصد دلخواه از کاربر قبول نمی‌شود.
4. `AuthorizationServiceClient` با workload credential به سرویس مجوزدهی وصل می‌شود.
5. `RouteResolutionController` operation ثبت‌شده را پیدا و resource/action/limits را برمی‌گرداند.
6. `AuthorizationController` action را به permission تبدیل و adapter رابطه‌ای را صدا می‌زند.
7. `OpenFgaRelationshipAdapter` هر exception را deny یا failure کنترل‌شده می‌کند.
8. `OperationalProxyController` token vault، اندازه، timeout، header و پاسخ upstream را کنترل می‌کند.

## BFF: فایل‌به‌فایل و بلوک‌به‌بلوک

### `SuperappBffApplication`

annotation برنامه Spring Boot و `main` context reactive را راه می‌اندازند. هیچ business logic در entry point قرار ندارد.

### `security/SecurityConfig`

matcher مربوط به tunnel استثنای CSRF اختصاصی را محدود می‌کند. `authorizeExchange` health/login را public و بقیه را authenticated می‌کند. handler موفقیت login به flow سفارشی vault وصل است و logout رکورد vault را پاک می‌کند.

### `security/OidcLoginSuccessHandler`

نوع authentication را OIDC کنترل می‌کند، authorized client را از repository server-side می‌خواند، access/refresh/expiry را به record داخلی تبدیل می‌کند، claimها را به identity DTO امن تبدیل می‌کند، sync هویت را پیش از ایجاد session نهایی انجام می‌دهد، handle قبلی را حذف، token جدید را ذخیره، session fixation را با `changeSessionId` خنثی و در پایان redirect می‌کند.

### `security/TokenVaultCrypto` و `TokenVaultService`

Crypto کلید Base64 و key-id را validate و برای هر ciphertext nonce تازه AES-GCM می‌سازد. Service فقط ciphertext را serialize می‌کند، TTL را از expiry می‌گیرد، handle UUID تصادفی می‌سازد و نام Redis را با namespace جدا می‌کند. read ابتدا handle را validate و سپس decrypt می‌کند. token منقضی نوشته نمی‌شود.

### `security/RefreshCoordinator` و `TokenRefreshService`

Coordinator برای هر handle یک `Mono.cache` مشترک نگه می‌دارد تا چند request هم‌زمان فقط یک refresh بسازند و در `doFinally` پاک شوند. Service فقط نزدیک expiry refresh می‌کند، refresh token نبودن را خطا می‌داند، پاسخ IAM را validate و vault را atomically با token تازه overwrite می‌کند.

### `api/OperationalProxyController`

متد `proxy` path/method را normalize و resolve می‌کند، پیش از تماس upstream مجوز می‌گیرد، session handle را استخراج و token تازه را آماده می‌کند. `forward` content-length و سقف عددی را کنترل می‌کند. `readBody` حتی requestهای chunked را bounded جمع می‌کند. `call` فقط bearer، Accept، Content-Type و correlation را forward و timeout ثبت‌شده را اعمال می‌کند. 401 فقط یک refresh/retry دارد. `writeResponse` اندازه و headerهای خروجی را allowlist می‌کند.

### سایر controllerها

- `MeController`: principal را نمایش و manifest را بدون token browser-facing برمی‌گرداند.
- `CsrfController`: نام header و token تولیدشده Spring را به frontend می‌دهد.
- `AdminProxyController`: method/path/query/body را به registry داخلی می‌برد و `X-Actor` را از principal می‌سازد.
- `ReportsController`: فهرست گزارش را برای subject جاری می‌گیرد.
- `OperationSupersetProxyController`: flow مستقل گزارش است و در این تکمیل تغییر داده نشده است.

## Authorization Service: فایل‌به‌فایل و بلوک‌به‌بلوک

### `config/SecurityConfig`

health بدون credential قابل probe است؛ سایر endpointها Basic workload authentication می‌خواهند. CSRF فقط برای API داخلی non-browser ignore است. user داخلی با password encoder استاندارد ساخته می‌شود.

### `config/AdminAuthorizationInterceptor`

از `X-Actor` استفاده می‌کند، کاربر ACTIVE و grant مستقیم ACTIVE/غیرمنقضی `application:aurevia/admin` را در یک query بررسی می‌کند و در نبود آن پیش از controller پاسخ 403 می‌دهد.

### `api/IdentitySyncController`

user با کلید `(issuer, external_id)` upsert می‌شود. membership قبلی در همان transaction حذف و claim فعلی idempotent درج می‌شود. path گروه slash یکسان، prefix `/` و بدون slash تکراری می‌شود.

### `api/AuthorizationController`

`check` subject/resource/action را validate، action business را به permission مدل تبدیل و نتیجه را با reason/decision id بسته‌بندی می‌کند. `manifest` role مستقیم، role حاصل از group، grant مستقیم user و grant مستقیم group را union می‌کند؛ expiry و status در هر شاخه اعمال و permission map deterministic ساخته می‌شود.

### `api/RouteResolutionController`

path خام باید absolute و بدون traversal/backslash باشد. query فقط target/route/operation فعال، method یکسان، مرز prefix درست و pattern نسبی مطابق را قبول و longest prefix را انتخاب می‌کند. نبود route برابر 404 است.

### `api/AccessAdminController`

resource/action/user و اتصال resource-action را مدیریت می‌کند. grant نوع subject را محدود، شناسه fallback قدیمی `userId` را پشتیبانی و رکورد grant، audit و outbox را transactionally می‌نویسد. payload outbox subject نوع‌دار، relation استاندارد و object OpenFGA بدون prefix تکراری می‌سازد. revoke ابتدا payload delete نسخه بعد را ثبت و سپس archive می‌کند.

### `sync/OutboxReconciler`

scheduler batchهای آماده را با lock غیرمسدودکننده claim می‌کند. event پنل را complete، event grant را به tuple تبدیل و write/delete می‌کند. موفقیت `processed_at` را پر و failure attempts، زمان retry با backoff محدود و پیام redacted/کوتاه را ذخیره می‌کند.

### policy

`StructuredPolicyEvaluator` ساختار JSON را فقط با allowlist ارزیابی می‌کند؛ هیچ eval عمومی ندارد. `OperationalRules` scope سازمانی و maker-checker را به‌صورت تابع مستقل و تست‌پذیر اعمال می‌کند.

## Frontend

`apps/shell/src/index.tsx` manifest را می‌گیرد، 401/redirect را به OIDC می‌برد، پنل اول مجاز را انتخاب و remote را داخل error boundary mount می‌کند. خود manifest هر panel را با `application:aurevia/<slug>/can_view` در OpenFGA فیلتر می‌کند. `remote-loader.ts` فقط remote entry ثبت‌شده در manifest را بارگذاری و shared scope را initialize می‌کند.

`packages/sh-core-ui` manifest را در context نگه می‌دارد. نبودن، منقضی‌بودن یا unknown permission همگی deny نمایشی‌اند. `SHCan`, `SHRouteGuard` و `SHAction` به‌ترتیب render، fallback و hide/disable/read-only را کنترل می‌کنند.

Admin MFE برای mutation ابتدا CSRF می‌گیرد. صفحه‌ها registry پنل، resource/action، grant و identity/role را مدیریت می‌کنند. HR و Finance رابط نمونه‌اند؛ هر اتصال داده باید فقط از prefixهای عملیاتی BFF استفاده کند.

## Infra و migration

Compose dependency و health order را تعریف می‌کند. Keycloak realm client محرمانه و mapper گروه دارد. OpenFGA model relation و permission مشتق‌شده را تعریف و `model-tests.yaml` invariantهای allow/deny را اثبات می‌کند. Flyway تنها مسیر تغییر schema است؛ V1 پایه، V2 تا V8 catalog و constraintهای افزایشی و V9 مجوز panel و operationهای کامل نمونه را اضافه می‌کند. migration اجراشده هرگز ویرایش نمی‌شود.

برای جزئیات تصمیم، failure mode و production controls به [معماری Authorization Engine](authorization-engine-fa.md) و برای مسئولیت هر فایل به [مرجع کد](code-reference-fa.md) مراجعه کنید.
