# TEST_MULTI_MODULE

نمونهٔ آزمایشی یک Micro Frontend با یک remote و سه Resource MODULE؛ فاقد دادهٔ واقعی منابع انسانی.
در این تغییر فقط فایل‌ها ساخته شده‌اند؛ ثبت در سرویس در حال اجرا انجام نشده است.

```text
Panel TEST_MULTI_MODULE (slug: test-multi-module)
├── active artifact → aurevia_test_multi_module / ./plugin / remoteEntry.js
└── APPLICATION application:aurevia/test-multi-module
    ├── MODULE Employee   [module:test-multi-module]
    │   ├── PAGE employee-list
    │   └── PAGE employee-detail
    ├── MODULE Payroll    [module:test-multi-module.payroll]
    │   ├── PAGE salary
    │   └── PAGE payroll-report
    └── MODULE Attendance [module:test-multi-module.attendance]
        ├── PAGE daily-attendance
        └── PAGE attendance-report
```

کلید Employee عمداً همان کلید MODULE پیش‌فرض واردکننده است. `normalize` و
`syntheticEquivalent` در `ResourceManifestService` اجازهٔ بازتعریف عنوان و actions همین منبع را می‌دهند.
اگر کلید را به `module:test-multi-module.employee` تغییر دهید، MODULE پیش‌فرض باقی می‌ماند و تعداد چهار می‌شود.
این محدودیت قرارداد فعلی است؛ نمونه تغییر معماری یا کلید مخفی اضافی ندارد.

## ساخت و اجرا

از ریشهٔ مخزن:

```powershell
npm run typecheck --workspace=@aurevia/mf-test-multi-module
npm run build --workspace=@aurevia/mf-test-multi-module
npm run dev --workspace=@aurevia/mf-test-multi-module
```

پورت: `3013`. خروجی build شامل `remoteEntry.js`، `mf-manifest.json` و `resource-manifest.json` است.
حالت standalone از `/api/me/context` واقعی استفاده می‌کند؛ کاربر یا مجوز جعلی داخل UI ساخته نمی‌شود.
`AUREVIA_BFF_URL` در dev-server به‌صورت پیش‌فرض `http://localhost:8443` است.

## ثبت و انتشار از Admin

1. سرویس‌ها، ورود کاربر مدیر و dev-server نمونه باید فعال باشند.
2. یک Panel با مقادیر [panel-registration.json](panel-registration.json) ایجاد کنید.
   `REAL` صرفاً برای عبور از فیلتر demo در این نمونه انتخاب شده است؛ داده‌ها آزمایشی هستند.
   اگر backend داخل Docker است، آدرس‌های fetch/remote در ثبت پنل باید از دید backend قابل دسترسی باشند
   (مثلاً `host.docker.internal:3013` در محیط توسعهٔ تنظیم‌شده).
3. در پنل Artifact / Manifest ابتدا Resource Manifest را Fetch یا JSON آن را Import کنید؛ Draft ساخته می‌شود.
4. MF Manifest را Sync کنید؛ Artifact مرتبط با Draft نسخهٔ `1.0.0` به وضعیت staged می‌رود.
5. Draft را Publish کنید؛ درخت و Artifact با هم فعال می‌شوند. تا پایان پردازش outbox ممکن است دسترسی بسته باشد.
6. در Access Studio سه MODULE هم‌سطح و شش PAGE را بررسی کنید.
7. برای A، فقط `view` روی Employee و دو PAGE آن؛ برای B روی Payroll و دو PAGE آن؛ برای C روی Attendance و دو PAGE آن بدهید.
   `view` روی MODULE به‌تنهایی مجوز PAGEها را نمی‌دهد. `admin` روی MODULE ارث‌بری مدیریتی دارد و برای مشاهدهٔ ساده لازم نیست.
8. با هر کاربر وارد Shell شوید. هر کدام باید یک MFE و دو مسیر مجاز ببینند. مسیر مستقیم صفحات دیگر نباید mount شود.

## نسخهٔ دوم و بازگشت

`resource-manifest.v2.json` یک MODULE به نام Benefits اضافه می‌کند؛ `mf-manifest.v2.json` همان شش route را نگه می‌دارد.
نسخهٔ دوم قابلیت جدیدی در درخت دارد، ولی PAGE جدیدی ندارد و remote موجود را دوباره استفاده می‌کند.

برای آزمایش V2، فایل Resource V2 را از Import JSON به Draft تبدیل کنید؛ از فرم انتشار دستی Artifact،
نسخهٔ `2.0.0`، شناسهٔ همان Draft، همان remote و محتوای MF V2 را وارد کنید؛ سپس Draft را Publish کنید.
برای rollback روی نسخهٔ منتشرشدهٔ `1.0.0`، Activate / Rollback را بزنید.
Benefits در DB برای تاریخچه باقی می‌ماند، ولی `DEPRECATED` شده و درخت مؤثر دوباره سه MODULE دارد.

## تست‌ها

```powershell
.\mvnw.cmd -pl services/authorization-service -am '-Dtest=MultiModuleManifestTest,ManifestReleaseIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`MultiModuleManifestTest` خود فایل نمونه را به parser/validator واقعی می‌دهد و شمارش normalize را بررسی می‌کند.
`ManifestReleaseIntegrationTest.testMultiModuleThreePrincipalsRemainIsolatedAcrossPublishAndRollback`
از PostgreSQL، Redis و OpenFGA واقعی با DB/store اختصاصی استفاده می‌کند. متغیرهای زیر باید به سرویس آزمایشی اشاره کنند:

```text
AUREVIA_TEST_JDBC_URL
AUREVIA_TEST_JDBC_USER
AUREVIA_TEST_JDBC_PASSWORD
AUREVIA_TEST_OPENFGA_URL
AUREVIA_TEST_REDIS_HOST
AUREVIA_TEST_REDIS_PORT
AUREVIA_TEST_REDIS_PASSWORD
```

کاربر DB باید اجازهٔ ساخت database آزمایشی داشته باشد. بدون محیط OpenFGA این کلاس skip می‌شود؛ skip نتیجهٔ موفق integration نیست.
گزارش بررسی معماری: [multi-module-verification-fa.md](../../docs/multi-module-verification-fa.md).
