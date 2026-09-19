# راهنمای عملیاتی Proxy Routing (FORWARD_USER_TOKEN و LEGACY_SERVICE_TOKEN)

این سند راهنمای کاربردی تعریف، آزمایش و عیب‌یابی مسیرهای پراکسی رجیستری‌محور است. یک موتور مسیریابی وجود دارد؛ تفاوت «مدرن» و «Legacy» فقط در **Outbound Auth Profile** است.

---

## A. معماری

```
مرورگر
  │  fetch('/api/proxy/hr/employees/42')  (cookie نشست، بدون Bearer)
  ▼
Nginx عمومی  (/api/ → BFF ؛ /<slug>-micro/ → BFF)
  ▼
BFF (OperationalProxyController)
  ├─ 1. Route Resolution  GET authz:/internal/v1/routes/resolve?path&method
  ├─ 2. OpenFGA check     POST authz:/internal/v1/authorize/check  (اگر authorizationRequired)
  ├─ 3. Token Vault       خواندن/تازه‌سازی توکن Public IAM کاربر (سمت سرور)
  ├─ 4. Outbound Auth     FORWARD_USER_TOKEN → همان توکن کاربر
  │                       LEGACY_SERVICE_TOKEN → توکن سرویس Legacy (کش Redis)
  ▼
Operation Gateway (Nginx خصوصی)
  │  Legacy: Authorization ← X-Internal-Legacy-Authorization ؛ هدر داخلی حذف می‌شود
  ▼
سرویس مقصد
```

ترتیب ۱→۲→۳→۴ ثابت است: **هیچ Secret یا توکن Legacy قبل از تأیید OpenFGA خوانده نمی‌شود.**

---

## B. تعاریف

| مفهوم | نقش | endpoint |
|---|---|---|
| **Outbound Connection** | آدرس پایهٔ یک سرویس توکن Legacy (`baseUrl`, `tlsRequired`) با یک `connectionRef` پایدار | `/internal/v1/registry/outbound-connections` |
| **Outbound Auth Profile** | «چطور احراز هویت کنیم»: `authMode` (`FORWARD_USER_TOKEN` یا `LEGACY_SERVICE_TOKEN`)، برای Legacy: connection، مسیر token endpoint، ارجاع Secret، pointerهای پاسخ، skew انقضا | `/internal/v1/registry/outbound-auth-profiles` |
| **Service Target** | مقصد شبکه: `gatewayBaseUrl` (origin تأییدشدهٔ BFF)، `upstreamBasePath`، health path، timeoutها، سقف پاسخ | `/internal/v1/registry/service-targets` |
| **Proxy Route** | اتصال Panel + Target + Auth Profile به یک `pathPrefix` عمومی؛ `stripPrefix`, `priority`, `allowedMethods`, rewrite, retry | `/internal/v1/registry/proxy-routes` |
| **Route Operation** | یک endpoint واقعی زیر Route: `httpMethod` + `pathPattern` نسبی + `resourceKey`/`actionKey` + `authorizationRequired` + `maxBodyBytes` | `/internal/v1/registry/proxy-routes/{routeId}/operations` |

یک میکروفرانت می‌تواند چند Route با Target، Prefix و Auth Profile متفاوت داشته باشد (بخش E).

---

## C. FORWARD_USER_TOKEN — قدم‌به‌قدم

سناریو: میکروی `hr`، API عمومی `/api/proxy/hr/employees`, مقصد سرویس مدرن HR پشت Gateway در مسیر `/hr-service`.

1. **Auth Profile** (یک‌بار برای همهٔ سرویس‌های مدرن کافی است):
```json
POST /internal/v1/registry/outbound-auth-profiles
{ "code": "forward-user", "name": "Forward user token", "authMode": "FORWARD_USER_TOKEN",
  "requestFormat": "NONE", "tokenResponsePointer": "/access_token",
  "expiresInResponsePointer": "/expires_in", "tokenTypeResponsePointer": "/token_type",
  "authorizationScheme": "Bearer", "credentialTransport": "USER_AUTHORIZATION_HEADER",
  "expirySkewSeconds": 30, "connectTimeoutMs": 2000, "responseTimeoutMs": 5000,
  "maxTokenResponseSize": 65536, "active": true }
```
2. **Service Target**:
```json
POST /internal/v1/registry/service-targets
{ "code": "hr-service", "name": "HR service", "gatewayBaseUrl": "http://operation-gateway",
  "upstreamBasePath": "/hr-service", "environment": "local", "healthCheckPath": "/health",
  "connectTimeoutMs": 2000, "responseTimeoutMs": 10000, "maxResponseSize": 1048576,
  "outboundAuthProfileId": "<forward-user profile id>", "active": true }
```
3. **Proxy Route**:
```json
POST /internal/v1/registry/proxy-routes
{ "code": "hr-api", "panelId": "<HR panel id>", "serviceTargetId": "<hr-service id>",
  "outboundAuthProfileId": "<forward-user profile id>", "serviceSlug": "hr",
  "pathPrefix": "/api/proxy/hr", "stripPrefix": 3, "priority": 0,
  "allowedMethods": ["GET","POST"], "preserveHost": false, "retryEnabled": false,
  "maxRetries": 0, "active": true }
```
4. **Operations**:
```json
POST /internal/v1/registry/proxy-routes/{routeId}/operations
{ "httpMethod": "GET", "pathPattern": "/employees/{id}", "resourceKey": "page:hr.employees",
  "actionKey": "view", "authorizationRequired": true, "active": true, "maxBodyBytes": 0 }

{ "httpMethod": "POST", "pathPattern": "/employees", "resourceKey": "page:hr.employees",
  "actionKey": "create", "authorizationRequired": true, "active": true, "maxBodyBytes": 65536 }
```
5. آزمایش: `POST /internal/v1/registry/proxy-routes/resolve-test` با `{"method":"GET","path":"/api/proxy/hr/employees/42"}` باید Route را با `upstreamPath: "/hr-service/employees/42"` و `resourceObject: "resource:page/hr.employees"` برگرداند.

نتیجه: سرویس مقصد `Authorization: Bearer <توکن Public IAM کاربر>` دریافت می‌کند و **باید خودش آن را اعتبارسنجی کند**؛ هدرهای `X-Aurevia-Subject`/`X-Aurevia-Issuer` اطلاعاتی‌اند، نه جایگزین اعتبارسنجی.

---

## D. LEGACY_SERVICE_TOKEN — قدم‌به‌قدم

1. **Outbound Connection**:
```json
POST /internal/v1/registry/outbound-connections
{ "connectionRef": "connection://legacy/hr", "name": "Legacy HR token service",
  "baseUrl": "http://legacy-hr.internal:8080", "tlsRequired": false, "active": true, "version": 0 }
```
2. **Secret**: مقدار Secret هرگز از API عبور نمی‌کند. ارجاع مثل `secret://legacy/hr` باید در BFF resolve شود (`FileSecretResolver`: فایل JSON `{ "username": "...", "password": "...", "version": "v1" }` زیر مسیر پیکربندی‌شده).
3. **Auth Profile**:
```json
POST /internal/v1/registry/outbound-auth-profiles
{ "code": "legacy-hr", "name": "Legacy HR service token", "authMode": "LEGACY_SERVICE_TOKEN",
  "tokenConnectionRef": "connection://legacy/hr", "tokenEndpointPath": "/oauth/token",
  "requestFormat": "FORM_PASSWORD", "credentialSecretRef": "secret://legacy/hr",
  "tokenResponsePointer": "/access_token", "expiresInResponsePointer": "/expires_in",
  "tokenTypeResponsePointer": "/token_type", "authorizationScheme": "Bearer",
  "credentialTransport": "INTERNAL_LEGACY_HEADER", "expirySkewSeconds": 60,
  "connectTimeoutMs": 2000, "responseTimeoutMs": 5000, "maxTokenResponseSize": 65536, "active": true }
```
`POST .../outbound-auth-profiles/{id}/token-test` یک توکن واقعی می‌گیرد ولی کش نمی‌کند؛ `.../connection-test` فقط اتصال را می‌سنجد.
4. **Service Target / Route / Operations**: مانند بخش C، با `outboundAuthProfileId` این پروفایل و مثلاً `upstreamBasePath: "/legacy-service"`.

**رفتار دو-توکنی:** BFF به Gateway می‌فرستد:
```
Authorization: Bearer <توکن Public IAM کاربر>
X-Internal-Legacy-Authorization: Bearer <توکن سرویس Legacy>
```
Gateway (`infra/mock-operation/gateway.conf`) برای مسیرهای Legacy:
```
proxy_set_header Authorization $http_x_internal_legacy_authorization;
proxy_set_header X-Internal-Legacy-Authorization "";
```
یعنی سرویس Legacy **فقط** توکن Legacy را می‌بیند و توکن Public IAM هرگز به آن نمی‌رسد. مورد E2E `SECURITY-RESPONSES`/`LEGACY-SSO-CREDENTIAL` این را اثبات می‌کند.

چرخهٔ توکن Legacy: cache در Redis با کلید profile+version → پیش از انقضا (`expirySkewSeconds`) تازه‌سازی → تازه‌سازی همزمان با قفل (`LegacyTokenRefreshCoordinator`) → پاسخ 401 مقصد ⇒ invalidate + یک بار دریافت مجدد (حداکثر `maxRetries`) → `POST .../outbound-auth-profiles/{id}/invalidate-token` برای ابطال دستی.

---

## E. میکروی ترکیبی (یک میکرو، دو حالت)

میکروی `mixed`:

| Route | pathPrefix | Target | Auth Profile | Operation |
|---|---|---|---|---|
| `mixed-modern` | `/api/proxy/mixed/modern` | `orders-service` (`/orders-service`) | `forward-user` | `GET /orders` → `page:mixed.orders:view` |
| `mixed-legacy` | `/api/proxy/mixed/legacy` | `legacy-crm` (`/legacy-service`) | `legacy-crm` | `GET /customer` → `page:mixed.customer:view` |

هر دو با `stripPrefix: 4`. درخواست `GET /api/proxy/mixed/modern/orders` ⇒ Gateway `/orders-service/orders` با توکن کاربر؛ `GET /api/proxy/mixed/legacy/customer` ⇒ `/legacy-service/customer` با توکن Legacy. پیشوند بلندتر همیشه بر `priority` مقدم است، پس Route عمومی‌تر `/api/proxy/mixed` (اگر وجود داشته باشد) هرگز این دو را نمی‌دزدد. کش توکن Legacy به شناسهٔ Auth Profile وابسته است و بین Routeها/پروفایل‌ها مشترک نمی‌شود. حالت `AUTH-DUAL-*` و `BROWSER-DUAL` در `tools/e2e-auth` دقیقاً همین را روی مرورگر واقعی اجرا می‌کند.

---

## F. پردازش مسیر

ترتیب: `stripPrefix` → (`rewritePattern`/`rewriteReplacement`) **یا** `upstreamBasePath`. اگر Rewrite تعریف شده باشد، `upstreamBasePath` اعمال **نمی‌شود** (Replacement باید مسیر کامل مقصد باشد). این قاعده در `UpstreamPathPolicy` یک‌جا پیاده شده و «پیش‌نمایش» و «زمان اجرا» از همان استفاده می‌کنند.

| مسیر ورودی | strip | rewrite | upstreamBasePath | مسیر مقصد |
|---|---|---|---|---|
| `/api/proxy/hr/employees/42` | 3 | — | `/hr-api` | `/hr-api/employees/42` |
| `/api/proxy/hr/employees/42` | 3 | — | — یا `/` | `/employees/42` |
| `/api/proxy/hr/employees/42` | 0 | — | `/hr-api` | `/hr-api/api/proxy/hr/employees/42` |
| `/api/proxy/hr/employees/42` | 0 | `^/api/proxy/hr` → `/svc/v1` | (نادیده) | `/svc/v1/employees/42` |
| `/api/proxy/hr/employees/42` | 3 | `^/employees` → `/people` | (نادیده) | `/people/42` |
| `/api/proxy/hr` | 3 | — | `/hr-api` | `/hr-api` |
| `/api/proxy/hr/` | 3 | — | — | `/` |
| `/api/proxy/hr/a/b/c` | 2 | — | — | `/hr/a/b/c` |

قواعد اعتبارسنجی: `stripPrefix` ≤ تعداد segmentهای `pathPrefix` (`STRIP_PREFIX_EXCEEDS_PATH_PREFIX`)؛ Rewrite که پس از strip اعمال نشود در پیش‌نمایش `400 REWRITE_PREFIX_NOT_FOUND_AFTER_STRIP` و در زمان اجرا `422` می‌دهد (نه ارسال مسیر ناقص).

**زبان الگوی Operation** (نسبت به Prefix): بخش ثابت `[A-Za-z0-9._~-]`، متغیر `{name}`، `*` (یک segment)، `**` فقط در انتها (صفر یا چند segment)، و `/` به‌تنهایی برای ریشهٔ Prefix. غیرمجاز: `..`, `//`, `\`, `?`, `#`, هر کدگذاری `%`, regex.
**مسیر زمان اجرا**: کدگذاری‌های سالم مثل `%20` یا حروف فارسی به‌صورت خام مقایسه و عیناً ارسال می‌شوند؛ `%2F %5C %2E %3F %23 %25` و `%` ناقص ⇒ `400`.

---

## G. مجوز سطح Operation

هر Operation یک `resourceKey` + `actionKey` دارد. BFF پیش از هر ارسال، `check` را با **شیء canonical محاسبه‌شده توسط Authorization Service** (`resourceObject`) صدا می‌زند؛ بنابراین منبع می‌تواند Page، Module، Application یا حتی منبع خارجی باشد.

مثال دو Operation روی یک Route و یک منبع:

| Operation | action | کاربر با فقط `view` |
|---|---|---|
| `GET /employees/{id}` | `view` | ALLOW |
| `POST /employees` | `create` | **DENY (403)** |

`authorizationRequired=false` فقط برای endpointهای واقعاً عمومی (health/متادیتا) است؛ در این حالت هیچ check انجام نمی‌شود ولی نشست معتبر همچنان لازم است (401 بدون نشست).

---

## H. رویهٔ Admin UI

مدیریت → پراکسی:
1. **Outbound Connections**: اتصال توکن Legacy (فقط برای Legacy).
2. **Outbound Auth Profiles**: پروفایل؛ برای Legacy «تست اتصال» و «تست توکن» را بزنید.
3. **Service Targets**: مقصد + «تست اتصال» (health).
4. **Proxy Routes**: Panel، Target، Profile، Prefix؛ فرم `stripPrefix` را از Prefix پیشنهاد می‌دهد. «اعتبارسنجی» (`/validate`) پیش از ذخیره.
5. **Operations**: متد + الگو + منبع/Action. «Match test» (`/operations/match-test`) یک مسیر نمونه را با Operationها تطبیق می‌دهد.
6. «پیش‌نمایش» (`/preview`) مسیر مقصد نهایی را نشان می‌دهد؛ «Resolve test» (`/resolve-test`) کل انتخاب زمان اجرا را.

خطاهای اعتبارسنجی به فارسی ترجمه می‌شوند و کد اصلی داخل پرانتز می‌آید (مثلاً `RETRY_REQUIRES_SAFE_METHODS`).

---

## I. نمونه‌های API کامل

```
POST /internal/v1/registry/proxy-routes/validate      → { "valid": true, "normalizedPathPrefix": "/api/proxy/hr/" }
POST /internal/v1/registry/proxy-routes/preview       { "routeId": "…", "path": "/api/proxy/hr/employees/42" }
                                                      → { "incomingPath": "...", "upstreamPath": "/hr-service/employees/42", "routeId": "…" }
POST /internal/v1/registry/proxy-routes/resolve-test  { "method": "GET", "path": "/api/proxy/hr/employees/42" }
                                                      → ResolvedRoute (routeId, operationId, authMode, resourceObject, upstreamPath, …)
PATCH /internal/v1/registry/proxy-routes/{id}/status?version=<n>   { "active": false }
POST /internal/v1/registry/proxy-routes/{routeId}/operations/match-test { "method": "POST", "path": "/employees" }
```
همهٔ درخواست‌ها هدر `X-Actor` و از طریق BFF هدرهای actor را دارند؛ ایجاد/تغییر نیازمند `can_manage` روی `resource:proxy.route` / `resource:proxy.operation` / `resource:proxy.target` است.

---

## J. جریان یک درخواست

`fetch('/api/proxy/hr/employees/42')` از میکروی HR:

| # | Forward | Legacy |
|---|---|---|
| 1 | Nginx: `/api/` → BFF | همان |
| 2 | BFF: resolve → Route `hr-api`, Operation `GET /employees/{id}` | Route `hr-legacy` |
| 3 | OpenFGA `can_view resource:page/hr.employees` ⇒ ALLOW/403 | همان — **قبل از هر کار Legacy** |
| 4 | Token Vault: توکن کاربر (تازه‌سازی در صورت نیاز) | همان (برای هدر Authorization هاپ خصوصی) |
| 5 | credential = توکن کاربر | credential = توکن Legacy از کش/دریافت |
| 6 | → Gateway `/hr-service/employees/42`, `Authorization: Bearer <user>` | → Gateway `/legacy-service/employees/42`, `Authorization: Bearer <user>`, `X-Internal-Legacy-Authorization: Bearer <legacy>` |
| 7 | Gateway → سرویس با همان Authorization | Gateway: Authorization ← توکن Legacy؛ هدر داخلی حذف |
| 8 | 401 مقصد ⇒ (اگر retry) یک‌بار تازه‌سازی توکن کاربر و تکرار | 401 مقصد ⇒ invalidate کش + دریافت مجدد + تکرار |
| 9 | 403 مقصد ⇒ عیناً برگردانده می‌شود، بدون تازه‌سازی | همان |

هدرهای ارسالی به مقصد فقط: `Authorization`, (`X-Internal-Legacy-Authorization`), `Accept`, `Content-Type`, `X-Correlation-ID`, `X-Aurevia-Subject`, `X-Aurevia-Issuer`, و `Host` فقط با `preserveHost`. Cookie، هدرهای مرورگر و `Authorization` تزریق‌شده از مرورگر هرگز ارسال نمی‌شوند. از پاسخ فقط `Content-Type` و `Content-Disposition` عبور می‌کند.

**Retry**: فقط وقتی `retryEnabled` است و Route فقط متدهای امن (`GET, HEAD, OPTIONS`) دارد (`RETRY_REQUIRES_SAFE_METHODS`). حتی آن‌گاه، تنها محرک retry پاسخ **401** است (تازه‌سازی credential)؛ خطای شبکه/timeout هرگز تکرار نمی‌شود. بنابراین هیچ POST/PATCH/DELETE به‌صورت خودکار تکرار نمی‌شود.

**سقف‌ها**: بدنهٔ بزرگ‌تر از `maxBodyBytes` ⇒ `413` قبل از ارسال؛ پاسخ بزرگ‌تر از `maxResponseSize` ⇒ `502`؛ عدم پاسخ در `responseTimeoutMs` ⇒ `504`؛ عدم اتصال ⇒ `502`.

**فضای نام عمومی**: Nginx فقط `/api/...` و `^/[a-z][a-z0-9-]*-micro(/|$)` را به BFF می‌فرستد. `pathPrefix` باید زیر یکی از این‌ها باشد (`/api/proxy/<slug>/...` توصیه می‌شود). افزودن Route جدید نیازی به تغییر کد Java، Nginx عمومی یا rebuild ندارد؛ فقط Gateway عملیاتی باید مسیر مقصد (`upstreamBasePath`) را بشناسد.

---

## K. عیب‌یابی

| علامت | علت / بررسی |
|---|---|
| `404 Proxy route resolution rejected` | Route/Operation فعال با این Prefix+متد+الگو نیست، یا Panel/Target/Profile غیرفعال است. `resolve-test` بزنید. |
| `409 Ambiguous active route operations` | دو Operation با Prefix، priority و specificity برابر. یکی را حذف یا priority بدهید. |
| `400 Invalid route path` | مسیر شامل `//`, `..`, `%2F`, `?`… است. |
| `422 Route path transformation failed` | Rewrite پس از strip اعمال نمی‌شود؛ پیش‌نمایش را بزنید. |
| `403` با `NO_RELATIONSHIP` | OpenFGA برای `resourceObject` مجوز ندارد → سند مجوزها، بخش G. |
| `401 Token vault session missing` | نشست BFF منقضی/خارج شده؛ ورود مجدد. |
| `401` از مقصد (Forward) | سرویس مدرن توکن کاربر را رد کرده (issuer/audience). `SSO-EXPIRED`/`SSO-INVALID` در E2E. |
| `401` از مقصد (Legacy) | توکن Legacy نامعتبر؛ پس از یک retry، Secret/endpoint را با `token-test` بررسی کنید. |
| `502 Outbound authentication transport does not match` | `credentialTransport` پروفایل با `authMode` ناسازگار است (`USER_AUTHORIZATION_HEADER` برای Forward، `INTERNAL_LEGACY_HEADER` برای Legacy). |
| `502 Legacy authentication unavailable` | token endpoint/Secret در دسترس نیست (`LEGACY-TOKEN-ENDPOINT`, `LEGACY-MISSING-SECRET`). |
| مسیر مقصد اشتباه / دوبار prefix | `stripPrefix` یا `upstreamBasePath`; جدول بخش F و `preview`. |
| Health check ناموفق | `gatewayBaseUrl` باید origin تأییدشدهٔ BFF (`aurevia.gateway.approved-base-urls`) باشد؛ `healthCheckPath` روی Gateway وجود داشته باشد. |
| `413` | بدنه > `maxBodyBytes` عملیات. |
| `502 Operation response exceeded` | پاسخ > `maxResponseSize` هدف. |
| `504` | مقصد در `responseTimeoutMs` پاسخ نداد. |

اجرای E2E کامل (Keycloak + سرویس‌های آزمایشی SSO/Legacy + مرورگر): `npm run identity:up && npm run e2e:auth:build && npm run e2e:auth:prepare && npm run e2e:auth:core:up && npm run e2e:auth:up && npm run e2e:auth:verify` — گزارش در `target/e2e-auth/results.json` بدون هیچ توکنی.
