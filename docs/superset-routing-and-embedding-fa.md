# راهنمای کامل Superset عمومی، عملیاتی، Route و نمایش در MFE Reports

این سند مرجع فنی تعریف دو محیط Superset، مالکیت routeها، ثبت داشبورد، کنترل OpenFGA و نمایش گزارش داخل Micro Frontend گزارشات است.

## اصل معماری

Aurevia دو جزء جداگانه با مسئولیت‌های متفاوت دارد:

| جزء | مسئولیت | داده و داشبورد | دسترسی مستقیم مرورگر |
|---|---|---|---|
| Public Superset | فقط ارائه JS، CSS، font و image کامپایل‌شده Superset | ندارد | فقط از `/static/*` |
| Operation Superset | ساخت و اجرای dashboard/chart و اتصال به منبع تحلیلی | دارد | ندارد؛ فقط از BFF و Operation Gateway |

داشبوردهای تست، عملیاتی و Production همگی در **Operation Superset** ساخته می‌شوند. Public Superset نباید database تحلیلی، dashboard runtime یا credential منبع داده داشته باشد.

## محل تعریف هر بخش

### سرویس‌ها و تنظیمات محیط

- `infra/docker-compose/compose.yml`: سرویس‌های `public-superset`، `operation-superset`، دیتابیس و init.
- `.env`: secret و تنظیمات local مانند `OPERATION_SUPERSET_SECRET_KEY` و `SUPERSET_LOAD_EXAMPLES`.
- `infra/superset-operation/superset_config.py`: authentication از نوع Remote User، session cookie و تنظیمات runtime.
- `infra/superset-public/Dockerfile`: ساخت image عمومی فقط برای static assetها.

در محیط Production همین topology باید در manifest استقرار Kubernetes/VM تعریف شود؛ Compose این مخزن فقط مرجع local است.

### Route فایل‌های عمومی

در `infra/nginx/nginx.conf` مسیر زیر فقط به Public Superset می‌رود:

```text
/static/* -> public-superset:8088
```

هیچ مسیر dashboard یا API نباید به Public Superset اضافه شود.

### Route امن runtime عملیاتی

مرز عمومی در `infra/nginx/nginx.conf` است:

```text
/reports-runtime/* -> /api/v1/superset/* در Java BFF
/superset/*        -> /api/v1/superset/superset/* در Java BFF
```

مرز Java در `OperationSupersetProxyController` است:

```text
/api/v1/superset/** -> OpenFGA check -> Operation Gateway /superset/**
```

و route داخلی Gateway در `infra/mock-operation/gateway.conf` است:

```text
/superset/* -> operation-superset:8088
```

جریان نهایی:

```text
Browser
  -> Public Nginx
  -> Java BFF / OperationSupersetProxyController
  -> Authorization Service / OpenFGA
  -> Operation Gateway
  -> Operation Superset
  -> DWH
```

Operation Superset نباید host port یا network route عمومی داشته باشد. header هویت `X-Aurevia-Subject` فقط در BFF تولید و فقط روی شبکه خصوصی Gateway پذیرفته می‌شود.

## تفاوت Proxy Route و Superset Route

بخش «راهبری Proxy» پنل ادمین برای APIهای HR، Finance و سرویس‌های business/legacy است. route تخصصی Superset در حال حاضر از `OperationSupersetProxyController` عبور می‌کند و در جدول‌های `service_target`، `proxy_route` و `route_operation` ساخته نمی‌شود.

برای Superset route عمومی دیگری نسازید؛ route موازی ممکن است check اختصاصی دارایی Superset را دور بزند یا با endpointهای root-relative نسخه ۵ تداخل پیدا کند.

## URL استاندارد گزارش

URL ذخیره‌شده در catalog باید relative و same-origin باشد:

```text
Dashboard: /superset/dashboard/{dashboardId}/
Chart:     /explore/?slice_id={chartId}
```

نمونه:

```text
/superset/dashboard/1/
```

این URLها ممنوع‌اند:

```text
http://operation-superset:8088/...
http://localhost:8088/...
http://<operation-host>/...
```

زیرا BFF، audit و OpenFGA را دور می‌زنند و نام داخلی شبکه را افشا می‌کنند.

## ثبت dashboard و تخصیص دسترسی

1. مدیر گزارش dashboard را در Operation Superset ایجاد و publish می‌کند.
2. در Super App وارد `مرکز مدیریت Aurevia` می‌شود.
3. تب `گزارش‌ها و داشبوردها` را باز می‌کند.
4. `دریافت مجدد از API` فهرست زنده Operation Superset را دریافت می‌کند.
5. با `افزودن به درخت`، asset در registry ثبت می‌شود.
6. Authorization Service در یک transaction رکورد `superset_asset`، resource خارجی، actionهای مجاز و outbox event والد را می‌سازد.
7. از `سطوح دسترسی`، سطح مشاهده/ویرایش/مدیریت به USER، GROUP یا ROLE داده می‌شود.
8. outbox relation متناظر را در OpenFGA می‌نویسد.
9. `GET /api/v1/reports` فقط assetهای publish‌شده‌ای را برمی‌گرداند که OpenFGA برای کاربر `can_view` داده است.
10. هر درخواست runtime نیز جداگانه با endpoint `superset-access` کنترل می‌شود؛ مخفی‌کردن کارت در UI کنترل امنیتی محسوب نمی‌شود.

نمونه metadata یک dashboard:

```json
{
  "externalId": "dashboard:1",
  "assetType": "DASHBOARD",
  "title": "World Bank Dashboard",
  "urlPath": "/superset/dashboard/1/",
  "ownerExternalId": null,
  "published": true
}
```

نکته: کلید والد فعلی `external_resource:superset-public` نام دارد، ولی runtime واقعاً در Operation اجرا می‌شود. تغییر آن به `superset-operation` نیازمند migration هم‌زمان PostgreSQL، outbox و tupleهای OpenFGA است و نباید با update دستی انجام شود.

## نمایش داخل MFE Reports

پیاده‌سازی فعلی `apps/mfe-reports/src/bootstrap.tsx` گزارش را با `target="_blank"` باز می‌کند. طراحی هدف برای نمایش داخل خود میکرو، یک حالت catalog/viewer است:

```text
ReportsCatalog
  -> انتخاب کارت مجاز
  -> ReportsViewer
       -> iframe same-origin
       -> بازگشت، refresh و تمام‌صفحه
```

نمونه قرارداد viewer:

```tsx
<iframe
  src={report.url_path}
  title={report.title}
  style={{ width: '100%', height: 'calc(100vh - 180px)', border: 0 }}
/>
```

قواعد الزامی:

- `src` فقط از `url_path` برگشتی BFF و با prefix مجاز `/superset/` یا `/reports-runtime/` ساخته شود.
- URL از query string دلخواه کاربر یا host خارجی ساخته نشود.
- iframe و requestهای آن same-origin و دارای cookie نشست باشند.
- BFF برای navigation، APIهای chart/dashboard، log و queryهای runtime همچنان `superset-access` را check کند.
- CSP محیط باید `frame-src 'self'` و Superset باید embedding همان origin را مجاز کند.
- fallback «بازکردن در صفحه جدید» برای accessibility و عیب‌یابی حفظ شود.
- unmount کردن MFE باید viewer را نیز حذف کند و listener سراسری باقی نگذارد.

نمایش iframe به‌تنهایی authorization نیست. کاربر حتی با دانستن URL باید در BFF/OpenFGA DENY شود.

## endpointهای root-relative در Superset 5

Superset 5 بخشی از درخواست‌ها را به `/api/v1/*` و `/superset/*` می‌فرستد. Nginx با referrer همان‌origin تشخیص می‌دهد کدام `/api/v1` متعلق به Superset است. cookie مستقل `AUREVIA_OPERATION_SUPERSET` نیز باید path `/` داشته باشد.

به همین علت `APPLICATION_ROOT` در Operation Superset برابر `/` است و prefix عمومی در Nginx/BFF مدیریت می‌شود. قرار دادن مستقیم Superset 5 زیر `APPLICATION_ROOT=/reports-runtime` می‌تواند router SPA را مختل کند.

## تنظیم یک محیط واقعی

برای جایگزینی containerهای local با سرویس واقعی:

1. Public asset host را به image/version دقیق همان Superset عملیاتی pin کنید.
2. Operation Gateway را به DNS خصوصی Operation Superset متصل کنید.
3. هیچ route عمومی مستقیم برای Operation Superset ایجاد نکنید.
4. ارتباط BFF→Gateway را با mTLS و trust store صریح فعال کنید.
5. headerهای هویت ورودی اینترنت را در Gateway حذف و فقط هویت workload تأییدشده BFF را بپذیرید.
6. secret، database URL و credentialها را از Vault/KMS تزریق کنید.
7. CSP را با originهای واقعی محیط تولید کنید؛ مقادیر localhost متعلق به local هستند.
8. health، login، catalog، dashboard مجاز، dashboard غیرمجاز و revoke را در smoke/E2E آزمایش کنید.

## چک‌لیست تست

```text
[ ] /static/* از Public Superset پاسخ 200 می‌دهد.
[ ] Public Superset هیچ dashboard/API runtime عمومی ندارد.
[ ] Operation Superset host port عمومی ندارد.
[ ] /superset/dashboard/{id}/ برای کاربر مجاز نمایش داده می‌شود.
[ ] همان URL برای کاربر بدون grant پاسخ 403 می‌دهد.
[ ] مدیر application به dashboard دسترسی مدیریتی دارد.
[ ] revoke پس از invalidation کش OpenFGA اعمال می‌شود.
[ ] iframe هیچ URL مستقیم Operation را مصرف نمی‌کند.
[ ] /api/v1/me/، chart data و /superset/log/ از tunnel عبور می‌کنند.
[ ] token، cookie و header داخلی در log ثبت نمی‌شوند.
```

## فایل‌های مرجع

| مسئولیت | فایل |
|---|---|
| تعریف سرویس‌های local | `infra/docker-compose/compose.yml` |
| ingress و rewrite عمومی | `infra/nginx/nginx.conf` |
| route خصوصی Gateway | `infra/mock-operation/gateway.conf` |
| پیکربندی Operation Superset | `infra/superset-operation/superset_config.py` |
| tunnel و check runtime | `services/superapp-bff/.../OperationSupersetProxyController.java` |
| فهرست گزارش کاربر | `services/superapp-bff/.../ReportsController.java` |
| registry و OpenFGA check | `services/authorization-service/.../SupersetAssetController.java` |
| UX مدیریت asset/grant | `apps/mfe-admin/src/SupersetAssets.tsx` |
| catalog و viewer گزارش | `apps/mfe-reports/src/bootstrap.tsx` |
