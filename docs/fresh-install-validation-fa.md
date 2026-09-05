# راهنمای نصب کاملاً تازه و اعتبارسنجی دسترسی‌های Aurevia

این Runbook برای ماشینی است که هیچ volume، دیتابیس، Store یا Model قبلی ندارد. هدف فقط
«بالا آمدن containerها» نیست؛ در پایان، دسترسی administrator به مدیریت میکروفرانت‌ها،
اعطا/لغو مجوز در درخت Manifest و مشاهده API/Audit Log با OpenFGA واقعی آزموده می‌شود.

## علت رایج Access Denied در نصب تازه

`.env.example` عمداً Store/Model واقعی ندارد. PostgreSQL و Flyway می‌توانند با موفقیت بالا
بیایند، اما OpenFGA بدون Store و مدل canonical هیچ tupleای را نمی‌پذیرد. در این حالت eventهای
outbox retry و سپس dead-letter می‌شوند و این نشانه‌ها دیده می‌شود:

- پنل مدیریت در Manifest نمایش داده نمی‌شود؛
- درخواست‌های `/api/v1/admin/**` پاسخ 403 می‌گیرند؛
- دکمه اعطا خطا می‌دهد یا grant فقط در PostgreSQL دیده می‌شود؛
- تب Audit با وجود ورود administrator پاسخ 403 می‌گیرد.

از این نسخه به بعد `npm run infra:up` قبل از Compose وجود artifactهای frontend و اعتبار واقعی
Store/Model را بررسی می‌کند و به‌جای اجرای نیمه‌سالم، با پیام قابل اقدام متوقف می‌شود.

## ۱. پیش‌نیازها

- Git؛
- Docker Engine + Docker Compose v2؛
- Node.js 22 و npm 10؛
- Java 21 (برای اجرای تست‌های Maven)؛
- OpenFGA CLI با فرمان `fga` یا امکان اجرای image رسمی و pin‌شده آن در Docker.

نسخه‌های image در Compose و bootstrap pin شده‌اند. اگر CLI میزبان نصب باشد، bootstrap از همان
استفاده می‌کند؛ در غیر این صورت `openfga/cli:v0.7.20` را در Docker اجرا می‌کند. در محیط
Production artifact CLI را از registry داخلی و با checksum تأییدشده وارد کنید؛ دانلود لحظه‌ای
در زمان انتشار مجاز نیست.

## ۲. Clone، تنظیم secret و Build

```bash
git clone <REPOSITORY_URL> aurevia-super-app
cd aurevia-super-app
cp .env.example .env
```

تمام `change-me`ها را با secret مستقل جایگزین کنید. برای `TOKEN_VAULT_KEY_BASE64` یک کلید
۳۲بایتی Base64 بسازید. Store/Model فعلاً می‌تواند placeholder بماند؛ مرحله بعد آن را اصلاح
می‌کند.

```bash
npm ci
npm run build
./mvnw verify                 # Windows: mvnw.cmd verify
npm test
```

فایل‌های `apps/*/dist` باید قبل از Nginx ساخته شده باشند؛ این directoryها داخل Git نیستند.

## ۳. Bootstrap قطعی OpenFGA

```bash
npm run openfga:bootstrap
```

این فرمان به‌ترتیب کارهای زیر را انجام می‌دهد:

1. فقط PostgreSQL مربوط به OpenFGA، migration و سرور OpenFGA را بالا می‌آورد؛
2. منتظر `/healthz` می‌ماند؛
3. Store با نام `aurevia-local` را پیدا می‌کند یا می‌سازد؛
4. مدل [model.fga](../infra/openfga/model.fga) را با CLI منتشر می‌کند؛
5. آخرین `STORE_ID` و `MODEL_ID` را از API می‌خواند و در `.env` می‌نویسد.

نام و endpoint قابل تغییر است:

```bash
FGA_STORE_NAME=aurevia-demo FGA_API_URL=http://127.0.0.1:8080 npm run openfga:bootstrap
```

در Production این script provisioning خودکار محسوب نمی‌شود. Store باید توسط pipeline کنترل‌شده
ساخته، مدل تست و approval شود و شناسه‌های immutable در secret/config manager قرار گیرند.

## ۴. اجرای Stack

```bash
npm run infra:up
```

preflight موارد زیر را کنترل می‌کند:

- Docker Engine در دسترس است؛
- dist مربوط به Shell و چهار MFE وجود دارد؛
- شناسه‌ها placeholder نیستند؛
- endpoint، Store و Model واقعاً در OpenFGA موجودند.

سپس Compose با profile کامل Superset و `--build` اجرا می‌شود. وضعیت را ببینید:

```bash
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml ps
```

تا healthy شدن `authorization-service` و `aurevia-bff` صبر کنید. Flyway در اولین اجرای
Authorization Service تمام migrationها را به‌ترتیب اجرا می‌کند؛ SQL دستی روی دیتابیس نزنید.
در Compose محلی، `OPENFGA_RECONCILE_ON_STARTUP=true` است: پس از آماده‌شدن برنامه، DB به‌عنوان
source of truth با OpenFGA repair و بلافاصله dry-run می‌شود و اگر drift باقی بماند startup شکست
می‌خورد. این گزینه پیش‌فرض برنامه `false` است و در Production باید reconciliation مطابق change
management و runbook عملیاتی زمان‌بندی شود.

## ۵. آزمون ماشینی migration، outbox و OpenFGA

پس از اینکه outbox فرصت پردازش پیدا کرد اجرا کنید:

```bash
npm run infra:verify
```

این آزمون fail-closed است و موارد زیر را مستقیماً می‌سنجد:

- containerهای در حال اجرا unhealthy نباشند؛
- Flyway حداقل تا آخرین migration شناخته‌شده رسیده باشد؛
- outbox pending یا dead-letter نداشته باشد؛
- dry-run reconciliation هیچ tuple گمشده یا غیرمنتظره‌ای نداشته باشد؛
- artifact فعال ADMIN نسخه `0.2.0`، قرارداد `1.0` و هر ۱۸ route نسبی را داشته باشد؛
- `administrator` روی `application:aurevia/admin` دارای `can_view` باشد؛
- روی `application:aurevia` دارای `can_manage` باشد (شرط اعطا/ویرایش)؛
- از زنجیره V49 روی `proxy.target`، `proxy.route`، `proxy.operation` و
  `integration.auth-profile` دارای `can_manage` باشد؛
- روی `resource:business_resource/public-zone-logs` دارای `can_view` و `can_manage` باشد.

اگر فقط pending گزارش شد، چند ثانیه بعد دوباره اجرا کنید. dead-letter را با restart پنهان نکنید؛
ابتدا `last_error` را بررسی و سپس reconciliation کنترل‌شده انجام دهید.

## ۶. آزمون مرورگر از نگاه کاربر صفر

1. `http://localhost:8443/` را باز کنید.
2. با `administrator / local-change-me` وارد شوید (فقط داده دموی local).
3. پنل «مدیریت» باید در Manifest دیده شود.
4. تب «میکروفرانت‌ها» باید لیست panelها را نمایش دهد.
5. در «استودیوی دسترسی»، یک resource و یک کاربر را انتخاب و یک action را اعطا کنید.
6. پیام queued شدن را ببینید و بعد از حداکثر چند ثانیه `npm run infra:verify` را تکرار کنید.
7. در «لاگ‌ها»، هر دو تب API Logs و Audit Logs را باز کنید.

مرورگر فقط cookie تصادفی `AUREVIA_SESSION` دارد. BFF هویت `administrator` را به Authorization
Service می‌فرستد و check نهایی در OpenFGA انجام می‌شود؛ نقش Keycloak به‌تنهایی مجوز admin نیست.

## ۷. مسیر داده‌ای سه قابلیت مسئله‌دار

| قابلیت | شرط نمایش/اجرا | tuple دموی لازم |
|---|---|---|
| ورود به MFE مدیریت | manifest check | `user:administrator viewer application:aurevia/admin` |
| اعطا در درخت Manifest | interceptor روی mutation | `user:administrator manager application:aurevia` |
| API Log | check اختصاصی controller | `viewer` روی `resource:business_resource/public-zone-logs` |
| Audit Log | check اختصاصی controller | `manager` روی همان resource |

Migration `V49` اپ‌های `admin`، `hr`، `finance` و `reports` را به‌صورت persisted زیر
`application:aurevia` قرار می‌دهد. بنابراین مدیر ریشه از مسیر رسمی مدل OpenFGA به منابع فرزند
دسترسی می‌گیرد و این دسترسی به tuple موقتی یا seed خارج از درخت وابسته نیست.

منبع حقیقت catalog/grant در PostgreSQL است، تغییرات با outbox به OpenFGA projection می‌شوند و
OpenFGA منبع تصمیم runtime است. وجود grant در جدول بدون tuple متناظر، دسترسی مؤثر ایجاد نمی‌کند.

## ۸. عیب‌یابی دقیق

### بررسی Flyway

```bash
docker compose --env-file .env -f infra/docker-compose/compose.yml exec -T auth-db \
  psql -U aurevia -d aurevia_auth -c \
  "select installed_rank,version,description,success from flyway_schema_history order by installed_rank;"
```

### بررسی outbox

```bash
docker compose --env-file .env -f infra/docker-compose/compose.yml exec -T auth-db \
  psql -U aurevia -d aurevia_auth -c \
  "select event_type,attempts,processed_at,dead_lettered_at,last_error from outbox_event order by created_at desc limit 50;"
```

`store not found` یعنی `OPENFGA_STORE_ID` اشتباه است. `authorization model not found` یعنی
Model مربوط به Store دیگری است. خطای relation/type معمولاً اختلاف مدل deploy‌شده با
`infra/openfga/model.fga` است.

### Drift و repair

ابتدا dry-run:

```http
POST /api/v1/admin/operations/openfga-reconcile?repair=false
```

خروجی missing/unexpected را بازبینی و فقط با approval عملیاتی اجرا کنید:

```http
POST /api/v1/admin/operations/openfga-reconcile?repair=true
```

این endpoint session و CSRF معتبر administrator می‌خواهد. repair مستقیم SQL یا حذف volume
راه‌حل قابل قبول نیست.

### Keycloak import

Realm فقط هنگام ایجاد دیتابیس تازه import می‌شود. اگر volume قدیمی دارید، تغییر فایل JSON
کاربر قبلی را خودکار اصلاح نمی‌کند. برای آزمون «واقعاً از صفر»، از project/volume جدا استفاده
کنید؛ volume محیط دارای داده را برای آزمایش حذف نکنید.

## ۹. چک‌لیست تحویل Production

- `SPRING_PROFILES_ACTIVE=prod` و OpenFGA روی HTTPS؛
- Store و Model صریح، pin‌شده و حاصل pipeline مدل؛
- secretها خارج Git و Compose، با rotation؛
- Keycloak production mode، TLS و hostname نهایی؛
- PostgreSQL/Redis HA، backup و restore drill؛
- alert برای outbox pending/dead-letter، deny-rate و latency OpenFGA؛
- اجرای model tests، Maven، frontend tests و smoke test در CI/CD؛
- dry-run reconciliation قبل و بعد rollout؛
- عدم استفاده از `local-change-me` و کاربران دموی realm.

راهنمای تفصیلی محیط دمو در [deployment-demo-linux-fa.md](deployment-demo-linux-fa.md) و الزامات
محیط واقعی در [deployment-production-linux-fa.md](deployment-production-linux-fa.md) قرار دارد.
