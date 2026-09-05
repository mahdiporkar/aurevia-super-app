# راهنمای حرفه‌ای Swagger و OpenAPI سرویس‌های Backend

این سند مرجع اجرای Swagger، احراز هویت، CSRF، نمونه درخواست و سیاست نگهداری قرارداد API در Aurevia است. توضیح endpointها، پارامترها، schemaها، پاسخ‌های خطا و نمونه payloadهای پاک‌سازی‌شده مستقیماً از کد تولید می‌شوند.

## آدرس پرتال

پس از اجرای stack، آدرس زیر را باز کنید:

```text
http://localhost:8443/swagger-ui.html
```

اگر نشست ندارید، BFF شما را وارد Authorization Code Flow در Keycloak می‌کند. success handler فعلی پس از ورود به `/` بازمی‌گردد؛ در این حالت آدرس Swagger را یک‌بار دیگر باز کنید. سپس در dropdown بالای Swagger یکی از دو قرارداد را انتخاب کنید:

| قرارداد | کاربرد | مسیر JSON |
|---|---|---|
| `1 - BFF عمومی سوپر اپ` | قرارداد مرورگر، session، manifest، admin proxy، route proxy و Superset | `/v3/api-docs` |
| `2 - سرویس مجوزدهی` | کاربران، OU، گروه، نقش، منبع، route، Legacy، Superset، log و OpenFGA | `/api/v1/docs/authorization/openapi` |

authorization-service مستقیماً به اینترنت منتشر نمی‌شود. BFF در محیط غیر production، JSON قرارداد و اجرای Try it out آن را با WebClient داخلی ارائه می‌کند؛ Basic password یا گواهی mTLS سرویس داخلی هرگز به browser یا Swagger UI داده نمی‌شود. اجرای endpointهای این قرارداد علاوه بر نشست و CSRF به مجوز `manage` روی `application:aurevia/admin` نیاز دارد.

سند Authorization Service بزرگ‌تر از سقف پیش‌فرض ۲۵۶KB WebClient است. فقط façade توسعه مستندات
سقف bounded مستقل `aurevia.documentation.max-openapi-bytes` (پیش‌فرض ۲MB، بازه مجاز ۲۵۶KB تا
۸MB) دارد؛ WebClientهای عملیاتی یا profile تولید به این دلیل بزرگ‌تر نشده‌اند.

## مدل امنیتی Swagger

Swagger استثنای امنیتی ایجاد نمی‌کند:

- `AUREVIA_SESSION` یک شناسه opaque با ویژگی‌های `HttpOnly`، `Secure` و `SameSite=Lax` است و حاوی token نیست.
- access token و refresh token کاربر به‌صورت رمز‌شده در Redis Token Vault باقی می‌مانند.
- درخواست Swagger همانند Shell به BFF می‌رسد. BFF token لازم را server-side می‌خواند.
- credential سرویس Legacy در API، مستندات یا دیتابیس رجیستری ثبت نمی‌شود؛ فقط `credentialSecretRef` مستند می‌شود.
- درخواست‌های تغییردهنده به CSRF token همان نشست نیاز دارند.
- در profile `prod` هم `/v3/api-docs` و هم Swagger UI غیرفعال‌اند.

## اجرای Try it out

### عملیات فقط‌خواندنی

پس از Login، endpoint موردنظر را باز کنید، `Try it out` و سپس `Execute` را بزنید. cookie نشست توسط مرورگر و به‌صورت same-origin ارسال می‌شود. مقدار cookie را در Authorize کپی نکنید.

### عملیات POST، PUT، PATCH و DELETE

1. در قرارداد BFF، `GET /api/v1/csrf` را اجرا کنید.
2. مقدار فیلد `token` پاسخ را کپی کنید.
3. دکمه `Authorize` را بزنید و مقدار را برای `csrfToken` وارد کنید.
4. عملیات تغییردهنده را اجرا کنید.

نام header از فیلد `headerName` پاسخ CSRF مشخص می‌شود و در پیکربندی فعلی `X-CSRF-TOKEN` است. token ضد-CSRF با token OAuth2 تفاوت دارد و افشای access token محسوب نمی‌شود.

## نمونه‌های حرفه‌ای

تمام نمونه‌های زیر در خود Swagger نیز کنار schema درخواست قابل انتخاب‌اند. UUIDها، hostnameها و hashها نمونه هستند.

### بررسی دسترسی

```http
POST /api/v1/docs/authorization/execute/internal/v1/authorize/check
Content-Type: application/json
X-CSRF-TOKEN: <csrf-of-current-session>
X-Correlation-ID: 5e4ddf32-1e7e-4e20-a9f3-64de1c938f97

{
  "subjectId": "8e3a7fd6-demo-user",
  "issuer": "http://localhost:8180/realms/aurevia",
  "resource": "resource:page/finance.payments",
  "action": "approve",
  "context": {
    "ip": "192.0.2.10",
    "branch": "TEH-01"
  },
  "correlationId": "5e4ddf32-1e7e-4e20-a9f3-64de1c938f97"
}
```

پاسخ نمونه:

```json
{
  "result": "ALLOW",
  "reasonCode": "OPENFGA_ALLOW",
  "modelVersion": "01H...",
  "decisionId": "d4ae6f4d-ef30-4ba0-826a-20d98d1c4a8f",
  "obligations": {}
}
```

`subjectId` بدون `issuer` هویت یکتا نیست. `resource` و `action` باید دقیقاً با Resource Manifest ثبت‌شده منطبق باشند. برای هر درخواست یک `correlationId` جدید بسازید.

### تعریف مقصد و Route مدرن

ابتدا service target ساخته می‌شود:

```json
{
  "code": "finance-operation",
  "name": "Finance operation service",
  "description": "مقصد ثابت در Operation Gateway",
  "gatewayBaseUrl": "http://operation-gateway:80",
  "upstreamBasePath": "/finance",
  "environment": "OPERATION",
  "healthCheckPath": "/finance/actuator/health",
  "connectTimeoutMs": 3000,
  "responseTimeoutMs": 30000,
  "maxResponseSize": 10485760,
  "active": true
}
```

سپس Route ساخته می‌شود:

```json
{
  "code": "finance-api",
  "panelId": "2b6a0a84-da5b-4795-b9e7-e4fd8a93a180",
  "serviceTargetId": "3691d12f-253f-4bce-924c-e23dc8ff6b37",
  "serviceSlug": "finance-micro",
  "pathPrefix": "/finance-micro/api",
  "stripPrefix": 1,
  "priority": 100,
  "allowedMethods": ["GET", "POST", "PUT"],
  "preserveHost": false,
  "retryEnabled": false,
  "maxRetries": 0,
  "active": true
}
```

در پایان برای هر عملیات `httpMethod + pathPattern`، منبع و action ثبت می‌شود. Route بدون operation مجاز باعث عبور بدون تصمیم دسترسی نمی‌شود.

### تعریف Route از نوع Legacy

تعریف Legacy سه جزء مستقل دارد:

1. `outbound-connection`: آدرس پایه مقصد/token endpoint و الزام TLS؛
2. `outbound-auth-profile`: روش دریافت و parse token و فقط reference راز؛
3. `service-target`: اتصال route به `outboundAuthProfileId`.

نمونه پروفایل:

```json
{
  "code": "legacy-crm-client-credentials",
  "name": "توکن CRM عملیاتی",
  "authMode": "LEGACY_SERVICE_TOKEN",
  "tokenConnectionRef": "connection://legacy/crm",
  "tokenEndpointPath": "/oauth/token",
  "requestFormat": "OAUTH_CLIENT_CREDENTIALS",
  "credentialSecretRef": "secret://legacy/crm-oauth",
  "scope": "crm.read crm.write",
  "audience": "crm-api",
  "tokenResponsePointer": "/access_token",
  "expiresInResponsePointer": "/expires_in",
  "tokenTypeResponsePointer": "/token_type",
  "authorizationScheme": "Bearer",
  "credentialTransport": "INTERNAL_LEGACY_HEADER",
  "expirySkewSeconds": 30,
  "connectTimeoutMs": 3000,
  "responseTimeoutMs": 10000,
  "maxTokenResponseSize": 65536,
  "active": true
}
```

مقدار واقعی client id/password/secret داخل `credentialSecretRef` نیست؛ آن reference به secret file یا secret manager اشاره می‌کند. پاسخ API نیز هیچ‌گاه token را برنمی‌گرداند. endpointهای `token-test` و `cache-status` فقط نتیجه، latency یا وجود مقدار cache‌شده را نشان می‌دهند.

### همگام‌سازی Login، LDAP و OU

```json
{
  "issuer": "http://localhost:8180/realms/aurevia",
  "subject": "8e3a7fd6-demo-user",
  "username": "ali.rezaei",
  "displayName": "علی رضایی",
  "email": "ali.rezaei@example.test",
  "distinguishedName": "CN=Ali Rezaei,OU=Sales,DC=aurevia,DC=local",
  "ouExternalId": "OU=Sales,DC=aurevia,DC=local",
  "directoryExternalId": "object-guid-demo",
  "groups": [
    {"externalId": "sales-users", "path": "/Sales/Users", "displayName": "کاربران فروش"}
  ],
  "attributes": {"department": "Sales", "employeeNumber": "10042"}
}
```

این API توسط BFF در پایان Login اجرا می‌شود، نه توسط فرم ادمین. endpoint مستقیمی برای create/update/delete OU مستند نشده است. ادمین تنها OU کشف‌شده را می‌خواند و به rule گروه دسترسی متصل می‌کند.

### تعریف Superset عمومی و عملیاتی

برای هر instance مقدار `zone` برابر `PUBLIC` یا `OPERATION` تعریف می‌شود. سپس Mapping دارای `publicInstanceId`، `operationInstanceId` و `publicPath` ساخته می‌شود. UI فقط نام عمومی را می‌بیند؛ BFF مقصد عملیاتی را resolve و دسترسی asset را کنترل می‌کند.

```json
{
  "code": "operation-default",
  "name": "Superset عملیاتی",
  "zone": "OPERATION",
  "baseUrl": "http://operation-superset:8088",
  "connectionRef": "connection://superset/operation-default",
  "authMode": "REMOTE_USER",
  "tlsRequired": false,
  "active": true,
  "version": 0
}
```

در production باید URL از allowlist، TLS و Connection امن استفاده کند؛ `allow-http` فقط برای local demo است.

## پاسخ‌ها و خطاها

هر operation پاسخ‌های متعارف زیر را مستند می‌کند:

| Status | معنا | اقدام |
|---|---|---|
| `400` | validation یا مقدار نامعتبر | schema، enum، required و محدودیت عددی را بررسی کنید |
| `401` | نشست یا هویت داخلی نامعتبر | دوباره Login کنید یا mTLS سرویس را بررسی کنید |
| `403` | مجوز یا CSRF رد شده | Manifest/Grant و CSRF همان نشست را بررسی کنید |
| `409` | optimistic lock یا تعارض داده | رکورد را دوباره بخوانید و `version` جدید بفرستید |
| `502` | خطای مقصد/Authorization/Legacy | correlationId و لاگ downstream را بررسی کنید |
| `504` | timeout مقصد | timeout و سلامت target را بررسی کنید |

هیچ پیام خطایی نباید token، password، secret یا پاسخ خام احراز هویت Legacy را نمایش دهد.

## قرارداد و جلوگیری از مستندات قدیمی

- منبع حقیقت endpointها annotationهای Spring MVC/WebFlux و DTOهای واقعی‌اند.
- metadata فارسی و مثال‌ها در packageهای `authz.docs` و `bff.docs` نگهداری می‌شوند تا controller و service آلوده به منطق مستندسازی نشوند.
- تست پوشش، اضافه‌شدن endpoint بدون summary فارسی یا request body بدون example را رد می‌کند.
- snapshot دستی YAML نگهداری نمی‌شود تا قرارداد دوم و قدیمی شکل نگیرد. برای هر release، JSON runtime را از Swagger دانلود و به artifact همان build پیوست کنید.
- پس از هر تغییر API، تست‌ها و دریافت `/v3/api-docs` باید در CI انجام شود.

## چک‌لیست انتشار

- profile فعال دقیقاً `prod` است.
- `/swagger-ui.html`، `/swagger-ui/**` و `/v3/api-docs` در production پاسخ مستندات نمی‌دهند.
- authorization-service مستقیماً در ingress منتشر نشده است.
- مسیر توسعه‌ای `/api/v1/docs/authorization/execute/**` به علت `@Profile("!prod")` وجود ندارد.
- هیچ example شامل credential یا token واقعی نیست.
- JSON هر دو قرارداد بدون operation فاقد summary، schema یا پاسخ امنیتی است.
- `npm run infra:verify:token-proxy` هر دو JSON runtime را می‌خواند و OpenAPI 3، متن فارسی،
  حداقل inventory عملیات‌ها و وجود sample درخواست را کنترل می‌کند.
