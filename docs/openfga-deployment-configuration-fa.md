# راهنمای اتصال Java BFF، Authorization Service و OpenFGA در انتشار

این سند محل تعریف آدرس‌ها، پورت‌ها، Store و Authorization Model را برای Local،
Staging و Production مشخص می‌کند. اصل معماری این است که Java BFF مستقیماً
OpenFGA را فراخوانی نمی‌کند و آدرس OpenFGA هرگز وارد Frontend، Manifest یا Route
میکروفرانت نمی‌شود.

## نسخه و نوع OpenFGA مورد استفاده

این مخزن از توزیع رسمی و کامل OpenFGA استفاده می‌کند؛ نسخه Lite، emulator یا
پیاده‌سازی جایگزین OpenFGA در معماری وجود ندارد. در محیط Compose، image رسمی
`openfga/openfga:v1.18.1` برای migration و runtime به‌صورت صریح pin شده و داده‌ها
در PostgreSQL اختصاصی OpenFGA نگهداری می‌شوند.

غیرفعال بودن `OPENFGA_PLAYGROUND_ENABLED` فقط رابط Playground را می‌بندد و به
معنای استفاده از نسخه Lite یا محدودشدن قابلیت‌های authorization engine نیست.
Authorization Service از Java SDK رسمی، Store ID و Model ID منتشرشده برای اجرای
checkها و مدیریت tupleها استفاده می‌کند؛ Redis نیز صرفاً cache کوتاه‌مدت تصمیم‌هاست
و جایگزین OpenFGA محسوب نمی‌شود.

## Topology

```text
Browser / Micro Frontend
          │ same-origin session
          ▼
Java BFF / Java Proxy
          │ AUTHORIZATION_URL
          │ Production: HTTPS + mTLS
          ▼
Authorization Service
          │ OPENFGA_URL + STORE_ID + MODEL_ID
          ▼
OpenFGA private API
          │
          ▼
OpenFGA PostgreSQL
```

مسئولیت هر hop:

| مؤلفه | مقصد مجاز | اطلاعات اتصال |
|---|---|---|
| Frontend | فقط BFF same-origin | هیچ OpenFGA URL یا credential ندارد |
| Java BFF | Authorization Service | `AUTHORIZATION_URL` و هویت workload |
| Authorization Service | OpenFGA | `OPENFGA_URL`, `OPENFGA_STORE_ID`, `OPENFGA_MODEL_ID` |
| OpenFGA | PostgreSQL خودش | `OPENFGA_DATASTORE_URI` |

Operation Gateway و سرویس‌های HR/Finance نیز نباید برای تصمیم‌گیری مستقیم به
OpenFGA متصل شوند. Authorization Service تنها owner ارتباط runtime و tupleهاست.

## ارتباط Java BFF با Authorization Service

BFF برای Manifest، authorization check، route resolution، مدیریت Resource/Role
و کنترل Superset از WebClient داخلی Authorization Service استفاده می‌کند.

تنظیم اصلی:

```env
AUTHORIZATION_URL=https://authorization-service.internal.example:8082
```

نگاشت در BFF:

```yaml
aurevia:
  authorization-service:
    base-url: ${AUTHORIZATION_URL}
```

### Local

Compose از DNS داخلی Docker استفاده می‌کند:

```env
AUTHORIZATION_URL=http://authorization-service:8082
AUTH_INTERNAL_USER=bff
AUTH_INTERNAL_PASSWORD=<local-secret>
```

Basic Auth داخلی فقط bootstrap محیط Local است.

### Production

با `SPRING_PROFILES_ACTIVE=prod`، حالت ارتباط Authorization Service روی mTLS
قرار می‌گیرد. BFF باید این مقادیر را از Secret Store یا volume فقط‌خواندنی دریافت
کند:

```env
SPRING_PROFILES_ACTIVE=prod
AUTHORIZATION_URL=https://authorization-service.authorization.svc.cluster.local:8082
AUTH_MTLS_KEYSTORE=/run/secrets/bff-authz-client.p12
AUTH_MTLS_KEYSTORE_PASSWORD=<secret>
AUTH_MTLS_TRUSTSTORE=/run/secrets/authz-ca.p12
AUTH_MTLS_TRUSTSTORE_PASSWORD=<secret>
```

Authorization Service نیز در profile تولید TLS و client certificate را اجباری
می‌کند و هویت certificate مورد قبول BFF با `AUTH_BFF_CERT_IDENTITY` مشخص می‌شود.

## ارتباط Authorization Service با OpenFGA

Authorization Service این متغیرها را می‌خواند:

```env
OPENFGA_URL=http://openfga:8080
OPENFGA_STORE_ID=<environment-store-id>
OPENFGA_MODEL_ID=<approved-model-id>
OPENFGA_CACHE_NAMESPACE=aurevia:openfga:check
OPENFGA_CACHE_TTL=5s
```

`OpenFgaConfiguration` با URL، Store ID و Model ID یک `OpenFgaClient` می‌سازد.
`OpenFgaRelationshipAdapter` عملیات `check`، tuple write و tuple delete را از طریق
همین client انجام می‌دهد.

Redis فقط cache کوتاه‌عمر تصمیم‌ها و graph epoch را نگه می‌دارد. اگر Redis برای
read در دسترس نباشد، check مستقیم OpenFGA انجام می‌شود. اگر OpenFGA نیز خطا بدهد،
نتیجه `DENY` است. در mutation، شکست invalidation امن Redis باعث توقف write می‌شود
تا تصمیم inherited قدیمی باقی نماند.

## اجرای OpenFGA

نمونه Local موجود در Compose:

```env
OPENFGA_DATASTORE_ENGINE=postgres
OPENFGA_DATASTORE_URI=postgres://openfga:<password>@openfga-db:5432/openfga
OPENFGA_HTTP_ADDR=0.0.0.0:8080
OPENFGA_PLAYGROUND_ENABLED=false
```

در Local فقط mapping زیر وجود دارد:

```text
127.0.0.1:8080 -> openfga:8080
```

این mapping برای توسعه است. در Production، OpenFGA باید ClusterIP/DNS یا endpoint
خصوصی داشته باشد و نباید با Public Ingress، LoadBalancer عمومی یا NodePort در
دسترس مرورگر قرار گیرد.

نمونه DNS داخلی Kubernetes:

```text
openfga.authorization.svc.cluster.local:8080
```

آدرس واقعی وابسته به زیرساخت است؛ همان آدرس در `OPENFGA_URL` Authorization Service
اعلام می‌شود، نه در BFF.

## Store و Model چگونه ایجاد می‌شوند؟

تنظیم URL به‌تنهایی کافی نیست. قبل از شروع Authorization Service باید:

1. migration دیتابیس OpenFGA اجرا شود؛
2. OpenFGA سالم و قابل دسترسی باشد؛
3. Store مخصوص محیط ایجاد یا resolve شود؛
4. مدل [infra/openfga/model.fga](../infra/openfga/model.fga) به Store منتشر شود؛
5. شناسه Store و شناسه Model در Config/Secret محیط ثبت شوند؛
6. Authorization Service با همان شناسه‌ها شروع شود؛
7. reconciliation، tupleهای PostgreSQL control plane را در OpenFGA بازسازی کند.

Storeها باید بین محیط‌ها جدا باشند:

```text
aurevia-development
aurevia-staging
aurevia-production
```

شناسه‌ها نمونه placeholder نیستند. مقدار زیر در `.env.example` باید قبل از اجرا
با شناسه واقعی bootstrap جایگزین شود:

```env
OPENFGA_STORE_ID=created-by-bootstrap
OPENFGA_MODEL_ID=<created-by-model-deployment>
```

`OPENFGA_MODEL_ID` در Production باید صریح و pin‌شده باشد. خالی گذاشتن آن به client
اجازه می‌دهد مدل فعال/آخر Store را مصرف کند و rollout و rollback را غیرقطعی
می‌کند.

## ترتیب Pipeline انتشار

ترتیب پیشنهادی Jobها:

```text
1. OpenFGA database migration
2. OpenFGA deployment and health check
3. Idempotent store bootstrap/lookup
4. model.fga validation and model publish
5. export STORE_ID and MODEL_ID to environment config
6. Authorization DB Flyway migration
7. Authorization Service deployment
8. OpenFGA dry-run reconciliation
9. OpenFGA repair reconciliation
10. Java BFF deployment
11. allow/deny smoke tests
```

Job bootstrap باید idempotent باشد: اگر Store همان محیط وجود دارد، Store جدید
نسازد؛ مدل را validate کند و فقط نسخه تأییدشده را منتشر کند. IDهای خروجی نباید با
ویرایش دستی داخل image قرار گیرند و باید در سامانه پیکربندی deployment ثبت شوند.

## نمونه تنظیم Production

### Authorization Service

```env
SPRING_PROFILES_ACTIVE=prod
AUTHORIZATION_PORT=8082

OPENFGA_URL=https://openfga.authorization.svc.cluster.local:8443
OPENFGA_STORE_ID=<production-store-id>
OPENFGA_MODEL_ID=<production-model-id>

OPENFGA_CACHE_NAMESPACE=aurevia:prod:openfga:check
OPENFGA_CACHE_TTL=5s

AUTH_TLS_KEYSTORE=/run/secrets/authz-server.p12
AUTH_TLS_KEYSTORE_PASSWORD=<secret>
AUTH_TLS_TRUSTSTORE=/run/secrets/workload-ca.p12
AUTH_TLS_TRUSTSTORE_PASSWORD=<secret>
AUTH_BFF_CERT_IDENTITY=aurevia-bff
```

### Java BFF

```env
SPRING_PROFILES_ACTIVE=prod
AUTHORIZATION_URL=https://authorization-service.authorization.svc.cluster.local:8082
AUTH_MTLS_KEYSTORE=/run/secrets/bff-authz-client.p12
AUTH_MTLS_KEYSTORE_PASSWORD=<secret>
AUTH_MTLS_TRUSTSTORE=/run/secrets/authz-ca.p12
AUTH_MTLS_TRUSTSTORE_PASSWORD=<secret>
```

Portهای `8082` و `8443` در این مثال قراردادی‌اند. مقدار نهایی باید با Service و
TLS termination زیرساخت منطبق باشد. `OPENFGA_URL` باید شامل scheme، host و port
کامل باشد.

## جریان Check و Write

برای یک درخواست عملیاتی:

```text
BFF
  -> Authorization Service /internal/v1/authorize/check
  -> normalize action to permission
  -> OpenFGA Check(user, relation, object)
  -> structured policy evaluation
  -> ALLOW or DENY + decision audit
```

برای Grant یا Revoke:

```text
Admin request
  -> PostgreSQL transaction
       authorization_grant
       audit_event
       outbox_event
  -> OutboxReconciler
  -> OpenFGA tuple write/delete
```

Controller مدیریت مستقیماً OpenFGA را write نمی‌کند؛ Outbox امکان retry و recovery
را فراهم می‌کند. بعد از restore یا ساخت Store جدید، Reconciliation باید projection
را از PostgreSQL بازسازی کند.

## شکاف امنیتی فعلی

Profile تولید، ارتباط BFF به Authorization Service را با mTLS سخت‌گیری می‌کند،
اما `OpenFgaConfiguration` فعلی فقط `apiUrl`، `storeId` و `modelId` را روی SDK
تنظیم می‌کند و credential یا TLS client identity اختصاصی OpenFGA در کد ندارد.

بنابراین صرف تنظیم `OPENFGA_URL` برای ادعای Production-ready کافی نیست. تا زمان
افزوده‌شدن adapter احراز هویت اختصاصی، یکی از این مرزها باید در زیرساخت enforce
شود:

- Service Mesh با mTLS و Workload Identity میان Authorization Service و OpenFGA؛
- Internal API Gateway/sidecar امن که فقط workload Authorization Service را بپذیرد؛
- شبکه خصوصی و NetworkPolicy سخت‌گیرانه همراه با TLS termination مورد اعتماد.

OpenFGA بدون این مرز نباید روی شبکه عمومی یا شبکه مشترک غیرقابل اعتماد منتشر شود.

## NetworkPolicy و Secretها

- فقط Authorization Service حق egress به OpenFGA API داشته باشد.
- فقط OpenFGA حق دسترسی به دیتابیس OpenFGA داشته باشد.
- BFF فقط به Authorization Service وصل شود و route مستقیم OpenFGA نداشته باشد.
- Frontend و Operation Gateway هیچ دسترسی شبکه‌ای به OpenFGA نداشته باشند.
- datastore URI، certificate password و workload credential در Secret Store باشند.
- Store ID و Model ID محرمانه نیستند، ولی configuration-controlled و environment
  specific هستند.
- Playground در Production غیرفعال باشد.

## Health و Smoke Test انتشار

پس از rollout این موارد باید بررسی شوند:

```text
[ ] OpenFGA health از شبکه Authorization Service موفق است.
[ ] STORE_ID متعلق به محیط درست است.
[ ] MODEL_ID دقیقاً نسخه تأییدشده pipeline است.
[ ] Authorization Service health موفق است.
[ ] BFF با certificate معتبر به Authorization Service متصل می‌شود.
[ ] کاربر مجاز برای یک Application نتیجه ALLOW می‌گیرد.
[ ] کاربر بدون relation برای همان Application نتیجه DENY می‌گیرد.
[ ] خطای OpenFGA نتیجه fail-closed می‌دهد.
[ ] Grant جدید به Outbox و سپس tuple OpenFGA می‌رسد.
[ ] Revoke پس از cache invalidation به DENY تبدیل می‌شود.
[ ] backlog، retry، dead-letter و drift metric/alert دارند.
```

## مرجع فایل‌ها

| موضوع | فایل |
|---|---|
| URL و mTLS سمت BFF | `services/superapp-bff/.../AuthorizationWebClientConfiguration.java` |
| تنظیم Production BFF | `services/superapp-bff/src/main/resources/application-prod.yml` |
| تنظیم OpenFGA | `services/authorization-service/src/main/resources/application.yml` |
| TLS سرور Authorization | `services/authorization-service/src/main/resources/application-prod.yml` |
| ساخت OpenFGA SDK client | `services/authorization-service/.../OpenFgaConfiguration.java` |
| check/write/delete و Redis cache | `services/authorization-service/.../OpenFgaRelationshipAdapter.java` |
| projection Outbox | `services/authorization-service/.../OutboxReconciler.java` |
| reconciliation | `services/authorization-service/.../OpenFgaReconciliationService.java` |
| مدل canonical | `infra/openfga/model.fga` |
| اجرای Local | `infra/docker-compose/compose.yml` |
