# ثبت میکرو بدون فایل mf-manifest.json

بله؛ میکرو می‌تواند بدون ارائهٔ فایل `mf-manifest.json` روی وب‌سرور خودش، با مجوز درخت منابع در `GET /api/me/context` نمایش داده شود. شرط آن، ثبت دستی اطلاعات رابط کاربری در رجیستری، فعال‌بودن Panel، داشتن Artifact معتبر و فعال و مجوز route مربوط است.

در این روش «فایل خارجی Manifest» نداریم، اما «Snapshot اطلاعات مسیرها و منوها» را در پنل راهبری ثبت می‌کنیم. فقط ثبت Remote Entry و اعطای دسترسی کافی نیست.

## ۱. آماده‌سازی میکرو

مثال این راهنما `ops-acc` است. میکرو باید روی وب‌سرور خودش اجرا شود و این فایل و chunkهای وابسته‌اش قابل دریافت باشند:

```text
http://localhost:3232/remoteEntry.js
```

در این روش لازم نیست `mf-manifest.json` یا `resource-manifest.json` روی سرور وجود داشته باشد. منابع را دستی تعریف می‌کنیم.

مشخصات واقعی Module Federation میکرو را از توسعه‌دهنده بگیرید. در مثال ما:

```text
name: opsacc
exposes: ./bootstrap
contractVersion: 1.0
```

ماژول exposeشده باید با قرارداد Shell سازگار باشد؛ برای قرارداد `1.0`، `contractVersion` و کامپوننت `App` را export می‌کند. نبود فایل Manifest، برنامهٔ مستقل یا ناسازگار را خودکار به میکروی قابل بارگذاری تبدیل نمی‌کند. نمونهٔ قرارداد و تنظیمات build در [راهنمای جامع](microfrontend-registration-step-by-step-fa.md) آمده است.

پورت و URL نمونه‌اند؛ آدرس واقعی میکروی خودتان را جایگزین کنید. BFF هم باید بتواند فایل‌ها را دریافت کند. برای Docker Desktop، سیاست توسعه و دسترسی به `host.docker.internal` را بررسی کنید. برای محیط غیرمحلی از میزبان واقعی و سیاست شبکهٔ مناسب آن محیط استفاده کنید.

## ۲. ثبت میکرو در پنل راهبری

با حساب راهبر وارد «مدیریت میکروفرانت‌ها» شوید و «میکرو جدید» را بزنید. اگر `ops-acc` موجود است، همان را ویرایش کنید.

| فیلد | مقدار مثال |
|---|---|
| کد | `OPS-ACC` |
| Slug | `ops-acc` |
| نام فارسی | حسابداری عملیات |
| نام انگلیسی | Operations Accounting |
| Service Slug | `ops-acc` |
| Remote Name | `opsacc` |
| آدرس کامل Remote Entry | `http://localhost:3232/remoteEntry.js` |
| Exposed Module | `./bootstrap` |
| Route Prefix | `/ops-acc` |
| Default Route ID | `index` |
| نسخه | `0.1.0` |
| نسخه قرارداد | `1.0` |
| روش تعریف Resource | `HYBRID` |
| رده | `REAL` |
| MF Manifest URL | خالی |
| Resource Manifest URL | خالی |
| فعال | بله |

`HYBRID` اجازه می‌دهد اکنون منابع با مالکیت `ADMIN` بسازید و بعداً منابع Manifest را با بررسی تعارض اضافه کنید. `MANUAL` هم برای منابع کاملاً دستی قابل استفاده است؛ روش تعریف Resource تعیین‌کنندهٔ نحوهٔ انتشار Artifact نیست. برای این سناریو `MANIFEST` را انتخاب نکنید، چون برای منابع، URL مانیفست می‌خواهد.

## ۳. ساخت منبع صفحه در درخت

در «استودیوی دسترسی OpenFGA»، درخت میکروی `ops-acc` را پیدا کنید. اگر Application یا Module از قبل ساخته شده، آن را تکرار نکنید. ساختار هدف:

```text
application:aurevia/ops-acc
└── module:ops-acc
    └── page:ops-acc.home
```

زیر Module با «افزودن فرزند»، منبعی با نوع `PAGE`، کلید `page:ops-acc.home` و نام «صفحه اصلی» تعریف کنید و عملیات `view` را برای آن در نظر بگیرید. منبع باید به همین میکرو تعلق داشته، فعال و نمایش آن در Catalog فعال باشد. اگر صفحه از قبل وجود دارد، فقط این مشخصات و عملیات را بررسی کنید.

این مرحله باید قبل از انتشار Artifact انجام شود: اعتبارسنجی route، وجود منبع و عملیات ارجاع‌شده را کنترل می‌کند. منوی رابط کاربری را به‌عنوان منبع مجوز جداگانه نسازید؛ مجوز منو از صفحه گرفته می‌شود.

## ۴. ثبت دستی Snapshot رابط کاربری

به «مدیریت میکروفرانت‌ها» برگردید و مقابل میکرو «Artifact و Catalog» را باز کنید. از بخش «انتشار دستی Artifact immutable (پیشرفته)» استفاده کنید؛ دکمهٔ «Sync Frontend Manifest» برای این روش نیست و با URL خالی غیرفعال است.

مقادیر نسخه، Remote Entry URL، Remote Name، Exposed Module و Contract را مطابق مرحلهٔ ۲ وارد کنید. در کادر «MF Manifest Snapshot» کل JSON زیر را قرار دهید:

```json
{
  "schemaVersion": "1.0",
  "microfrontend": {
    "key": "ops-acc",
    "name": "Operations Accounting",
    "version": "0.1.0"
  },
  "runtime": {
    "remoteEntry": "http://localhost:3232/remoteEntry.js",
    "remoteName": "opsacc",
    "exposedModule": "./bootstrap",
    "contractVersion": "1.0",
    "apiBasePath": "/api/proxy/ops-acc"
  },
  "defaultRouteKey": "index",
  "routes": [
    {
      "key": "index",
      "path": "",
      "title": "صفحه اصلی حسابداری عملیات",
      "requiredResource": "page:ops-acc.home",
      "requiredAction": "view"
    }
  ],
  "navigation": [
    {
      "key": "ops-acc.nav.home",
      "type": "PAGE",
      "routeKey": "index",
      "title": "حسابداری عملیات",
      "order": 10
    }
  ]
}
```

این JSON فقط در رجیستری ذخیره می‌شود؛ لازم نیست آن را به پروژه یا وب‌سرور میکرو اضافه کنید. `path: ""` زیر پیشوند پنل، مسیر `/ops-acc` را می‌سازد. نام route برابر `index` است، ولی کلید مجوز `page:ops-acc.home` است؛ این دو مفهوم متفاوت‌اند.

مقدار پیش‌فرض فرم ممکن است `application:aurevia/ops-acc` باشد. در این مثال، `requiredResource` را دقیقاً با صفحه‌ای که به کاربر مجوز می‌دهید تطبیق دهید. `apiBasePath` نیز فقط نشانی پایه است؛ اگر میکرو API استفاده می‌کند، Proxy Route، backend و مجوزهای API باید جداگانه پیکربندی شوند.

## ۵. انتشار و فعال‌سازی

1. «Validate و Publish» را بزنید.
2. در جدول نسخه‌ها، نسخه باید `VALID` باشد.
3. برای همان نسخه «Activate / Rollback» را بزنید.
4. مطمئن شوید نشان «فعال» روی همان نسخه ظاهر شده است.

انتشار دستی به‌تنهایی نسخه را فعال نمی‌کند. `active_artifact_id` پنل باید به نسخهٔ انتخاب‌شده اشاره کند. Artifact immutable است؛ اگر `0.1.0` قبلاً منتشر شده و باید محتوایش عوض شود، نسخهٔ جدید مثلاً `0.1.1` را هم در فرم و هم در Snapshot ثبت کنید. انتشار دوبارهٔ همان نسخه می‌تواند خطای `409` بدهد.

### مانع شناخته‌شده در نسخهٔ فعلی

در بررسی ۲۰۲۶-۰۹-۲۰، پس از انتشار اولین Artifact برای پنلی بدون نسخهٔ فعال، endpoint لیست Artifactها خطای `400` داده است. در `JdbcUiPluginRepository.artifacts`، مقایسهٔ شناسه با `active_artifact_id = null` نتیجهٔ SQL برابر `null` می‌دهد، در حالی که `ArtifactView.active` از نوع `boolean` است.

اگر جدول نسخه‌ها بعد از Publish بارگذاری نشد، به معنی نامعتبر بودن فایل یا نداشتن مجوز کاربر نیست. این ایراد باید در سرویس رفع شود تا نسخه و دکمهٔ Activate قابل نمایش باشند. راه‌حل کدی، تبدیل نتیجهٔ nullable به `false` است؛ نیازی به حذف شرط نسخهٔ فعال یا اعطای دسترسی گسترده نیست. این سند به‌تنهایی آن باگ را اصلاح یا نسخهٔ محیط را فعال نمی‌کند.

## ۶. اعطای دسترسی به کاربر

در «استودیوی دسترسی OpenFGA»:

1. منبع `page:ops-acc.home` را انتخاب کنید.
2. نوع هویت «کاربر» و کاربر `demo-hr-only` را انتخاب کنید.
3. مقابل `view`، «اعطا» را بزنید.
4. وضعیت همگام‌سازی مجوز را بررسی کنید؛ خطای projection باید رفع شود.

در منطق فعلی، وقتی route به همین صفحه اشاره می‌کند، مجوز صفحه برای کشف میکروی مالک در کاتالوگ کافی است. برای نمایش این صفحه لازم نیست به کاربر نقش راهبر یا مجوز تمام Application بدهید. عملیات سایر صفحات، APIها و فیلدها همچنان مجوزهای خودشان را دارند.

## ۷. نتیجهٔ مورد انتظار در context

در پنجرهٔ خصوصی تازه با کاربر محدود وارد شوید. در محیط دموی پیش‌فرض، نام کاربر `demo-hr-only` و رمز `local-change-me` است؛ در محیط با رمز تغییرکرده از همان رمز جدید استفاده کنید. پنجرهٔ خصوصی را برای جلوگیری از استفادهٔ دوباره از نشست SSO حساب راهبر باز می‌کنیم.

در Network مرورگر، پاسخ `GET /api/me/context` را باز کنید. باید این موارد را ببینید:

| بخش | انتظار |
|---|---|
| `permissions` | `page:ops-acc.home` شامل `view` |
| `uiCatalog.modules` | عضوی با `moduleKey = ops-acc` |
| route همان ماژول | `id = index` و `resource = page:ops-acc.home` |
| `panels` | عضوی با `slug = ops-acc` |
| منو | حسابداری عملیات |

نمایش در context و موفقیت بارگذاری دو بررسی جدا هستند. حتی با مجوز صحیح، سرور خاموش یا Remote Name اشتباه می‌تواند هنگام بازکردن میکرو خطا بدهد. لینک `remoteEntryUrl` مؤثر ممکن است به مسیر BFF مانند `/api/mfe/ops-acc/remoteEntry.js` تبدیل شده باشد.

اگر فقط Permission وجود دارد، ابتدا نسخهٔ فعال و معتبر، فعال‌بودن Panel و تطبیق resource/action مسیر را بررسی کنید؛ لاگین دوباره جای فعال‌سازی Artifact را نمی‌گیرد.

## ۸. اضافه‌کردن فایل Manifest در آینده

بعداً توسعه‌دهنده می‌تواند همین ساختار رابط کاربری را در `mf-manifest.json` ارائه کند. در ویرایش Panel، `MF Manifest URL` را ثبت کنید و با حفظ کلیدهای پایدار صفحه و route، «Sync Frontend Manifest» را اجرا کنید. Sync نسخه را اعتبارسنجی و ثبت/فعال می‌کند؛ بعد از آن دوباره context و منوها را آزمایش کنید.

برای اضافه‌کردن Resource Manifest، مالکیت منابع دستی و تعارض‌های Draft را جداگانه بررسی کنید. انتشار Manifest منابع نباید بدون بررسی، منابع یا دسترسی‌های موجود را حذف یا جایگزین کند.

## مراجع

- [راهنمای جامع ثبت میکرو](microfrontend-registration-step-by-step-fa.md)
- [فرم انتشار دستی](../apps/mfe-admin/src/Panels.tsx)
- [اعتبارسنجی Artifact](../services/authorization-service/src/main/java/com/aurevia/authz/ui/UiPluginRegistryService.java)
- [ساخت context و فیلتر مجوز routeها](../services/authorization-service/src/main/java/com/aurevia/authz/authorization/AuthorizationDecisionService.java)
- [لیست Artifactها و وضعیت فعال](../services/authorization-service/src/main/java/com/aurevia/authz/ui/JdbcUiPluginRepository.java)
