# تعریف صحیح میکروفرانت از صفر؛ مثال ops-acc

این راهنما مسیر آماده‌سازی، ثبت، انتشار، فعال‌سازی و تست دسترسی یک میکروفرانت در Aurevia را توضیح می‌دهد. مثال، میکروی «حسابداری عملیات» با شناسه `ops-acc` و کاربر آزمایشی `demo-hr-only` است.

## ۱. اجزایی که باید آماده باشند

ثبت یک ردیف در «مدیریت میکروفرانت‌ها» به‌تنهایی برای نمایش برنامه کافی نیست:

| جزء | وظیفه |
|---|---|
| Panel | هویت و تنظیمات استقرار میکرو؛ نام، مسیر و نشانی Remote Entry |
| Resource Catalog | تعریف منابع قابل کنترل و عملیات مجاز آن‌ها |
| Resource Manifest | فایل تعریف منابع؛ پس از Draft و Publish وارد کاتالوگ می‌شود |
| MF Manifest | تعریف runtime، مسیرهای رابط کاربری و منوها؛ به منابع موجود ارجاع می‌دهد |
| Artifact | نسخهٔ معتبر و مشخص فرانت‌اند همراه با Snapshot مانیفست |
| Active Artifact | نسخه‌ای که برای بارگذاری و ساخت کاتالوگ انتخاب شده است |
| Grant | اعطای عملیات یک منبع به کاربر، نقش یا گروه |

ترتیب پیشنهادی: آماده‌سازی فایل‌های میکرو ← ثبت Panel ← انتشار منابع ← انتشار و فعال‌سازی Artifact ← اعطای دسترسی ← تست با کاربر محدود.

## ۲. آماده‌سازی خود فرانت‌اند

میکرو باید واقعاً ساخته و روی وب‌سرور اجرا شده باشد. وجود URL در پنل، فایل یا برنامه ایجاد نمی‌کند. در این مثال فرض می‌کنیم میکرو روی پورت `3232` کامپیوتر توسعه اجرا می‌شود.

برای قرارداد `1.0`، ماژول exposeشده باید `contractVersion` و کامپوننت `App` را صادر کند. نمونهٔ حداقلی فایل `src/bootstrap.tsx` در پروژهٔ میکرو:

```tsx
import React from 'react';
import type { MicroFrontendProps } from '@aurevia/contracts';

export const contractVersion = '1.0' as const;

export function App({ runtime }: MicroFrontendProps) {
  return <section dir={runtime.theme.direction}>
    <h1>حسابداری عملیات</h1>
    <p>صفحهٔ اصلی میکروی ops-acc</p>
  </section>;
}
```

این نمونه فقط قرارداد کامپوننت است، نه یک پروژهٔ مستقل کامل. پروژه باید وابستگی‌ها، TypeScript و build خود را داشته باشد. نمونهٔ موجود در مخزن: [میکروی HR](../apps/mfe-hr/webpack.config.cjs).

تنظیمات Module Federation میکروی شما باید با ثبت پنل یکسان باشد:

```js
new ModuleFederationPlugin({
  name: 'opsacc',
  filename: 'remoteEntry.js',
  exposes: { './bootstrap': './src/bootstrap.tsx' },
  shared: {
    react: { singleton: true },
    'react-dom': { singleton: true },
    'react-router-dom': { singleton: true }
  }
})
```

نسخهٔ وابستگی‌های مشترک را با Shell سازگار نگه دارید. برای chunkها از `output.publicPath: 'auto'` استفاده کنید. کامپوننت embedded نباید یک `BrowserRouter` یا `createRoot` مستقل روی Shell بسازد. در صورت استفاده از API، از `runtime.http` استفاده کنید؛ توکن به مرورگر یا کد میکرو داده نمی‌شود.

این سه فایل باید از وب‌سرور میکرو قابل دریافت باشند:

```text
http://localhost:3232/remoteEntry.js
http://localhost:3232/mf-manifest.json
http://localhost:3232/resource-manifest.json
```

فایل‌های JSON باید در خروجی build کپی یا توسط وب‌سرور ارائه شوند. پاسخ HTML صفحهٔ اصلی به جای JSON نشانهٔ اشتباه در مسیر یا fallback وب‌سرور است.

در Docker Desktop، Authorization Service و BFF هم باید به میزبان میکرو دسترسی داشته باشند. سیاست توسعهٔ پروژه نشانی loopback را هنگام fetch به میزبان توسعه نگاشت می‌کند؛ مقدار پیش‌فرض آن `host.docker.internal` است. اگر اتصال برقرار نشد، bind وب‌سرور روی `0.0.0.0`، فایروال و تنظیمات شبکهٔ توسعه را بررسی کنید. `localhost` در مرورگر دستگاه دیگر به همان دستگاه اشاره می‌کند. در استقرار شبکه‌ای از نشانی قابل دسترس واقعی استفاده کنید. سیاست Production به HTTPS، مقصد مجاز و تنظیمات integrity وابسته است؛ قواعد توسعه را به Production تعمیم ندهید.

## ۳. ثبت Panel در پنل راهبری

با حساب راهبر وارد شوید و در «مدیریت میکروفرانت‌ها» روی «میکرو جدید» بزنید. اگر `ops-acc` را قبلاً ساخته‌اید، همان ردیف را «ویرایش» کنید؛ ردیف تکراری نسازید.

| عنوان فرم | مقدار نمونه |
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
| رده Micro Frontend | `REAL` |
| MF Manifest URL | `http://localhost:3232/mf-manifest.json` |
| Resource Manifest URL | `http://localhost:3232/resource-manifest.json` |
| فعال | انتخاب‌شده |

`Remote Name` نام container در Module Federation است؛ نام سرویس Docker نیست. Route Prefix مسیر Shell است؛ مسیر routeهای داخل مانیفست محلی و بدون این پیشوند نوشته می‌شود. `Service Slug` به‌تنهایی API یا Proxy Route ایجاد نمی‌کند؛ اگر میکرو API دارد، اتصال backend و مجوزهای آن جداگانه ثبت می‌شوند.

## ۴. تعریف و انتشار منابع

فایل `resource-manifest.json` پیشنهادی:

```json
{
  "schemaVersion": "1.0",
  "module": {
    "key": "ops-acc",
    "name": "Operations Accounting",
    "nameFa": "حسابداری عملیات",
    "nameEn": "Operations Accounting",
    "version": "0.1.0"
  },
  "resources": [
    {
      "key": "page:ops-acc.home",
      "type": "PAGE",
      "name": "Home",
      "nameFa": "صفحه اصلی",
      "nameEn": "Home",
      "actions": ["view"]
    }
  ]
}
```

در ردیف میکرو «Artifact و Catalog» را باز کنید. در بخش «Resource Manifest — Draft / Diff / Approval» یکی از این دو روش را انجام دهید:

1. اگر فایل را روی سرور قرار داده‌اید، «Fetch از URL ثبت‌شده» را بزنید.
2. در غیر این صورت JSON بالا را در کادر «Resource Manifest JSON» قرار دهید و «Validate و ایجاد Draft» را بزنید.

سپس تغییرات Draft را بررسی کنید و برای همان Draft «Publish» را بزنید. وضعیت باید `PUBLISHED` شود. دریافت یا ایجاد Draft به‌تنهایی منابع را منتشر نمی‌کند.

اگر `page:ops-acc.home` را قبلاً دستی ساخته‌اید، ابتدا وجود عملیات `view` و فعال‌بودن آن را در درخت بررسی کنید. برای همان منبع لازم نیست دوباره Manifest منتشر کنید. اگر تصمیم دارید مالکیت آن را به Manifest منتقل کنید، تعارض‌های Draft را بررسی و رفع کنید؛ منبع یا مجوز موجود را صرفاً برای عبور از خطا حذف نکنید. حالت `MANUAL` برای ورود Resource Manifest نیست؛ برای این مثال `HYBRID` انتخاب شده است.

## ۵. تعریف MF Manifest

فایل `mf-manifest.json` پیشنهادی:

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

نکات تطبیق:

- `routes[].key`، مقدار `defaultRouteKey`، مقدار `navigation[].routeKey` و «Default Route ID» همگی در این مثال `index` هستند.
- `path: ""` یعنی صفحهٔ اصلی میکرو زیر `/ops-acc`. برای صفحهٔ فاکتورها مثلاً `path: "invoices"` بنویسید، نه `/ops-acc/invoices`.
- `requiredResource` باید دقیقاً همان منبع منتشرشده باشد و عملیات `view` روی آن تعریف شده باشد.
- در MF Manifest آرایهٔ `resources` نگذارید؛ تعریف منابع در Resource Manifest جداست.
- مجوز Navigation از route گرفته می‌شود؛ روی گره Navigation، `requiredResource` یا `requiredAction` قرار ندهید.
- نمونهٔ پیش‌فرض فرم ممکن است `application:aurevia/ops-acc` را پیشنهاد کند. برای تست مشخص این راهنما آن را با `page:ops-acc.home` مطابق JSON بالا جایگزین کنید.

## ۶. انتشار و فعال‌سازی فرانت‌اند

### روش پیشنهادی: همگام‌سازی از URL

بعد از انتشار منابع، در «Artifact و Catalog» روی «Sync Frontend Manifest» بزنید. سرور فایل را از `MF Manifest URL` می‌خواند، اعتبارسنجی می‌کند و نسخه را ثبت/فعال می‌کند. تنظیمات استقرار ثبت‌شده در پنل بر پیش‌فرض‌های runtime مانیفست اولویت دارند؛ هر دو را سازگار نگه دارید.

در جدول نسخه‌ها باید `Validation = VALID` و نشان «فعال» را ببینید. موفقیت ذخیرهٔ Panel جای این بررسی را نمی‌گیرد.

### روش جایگزین: انتشار دستی

اگر نمی‌خواهید فایل MF Manifest را روی URL ارائه دهید، در بخش «انتشار دستی Artifact immutable (پیشرفته)» این مقادیر را وارد کنید:

| فیلد | مقدار |
|---|---|
| نسخه | `0.1.0` |
| Remote Entry URL | `http://localhost:3232/remoteEntry.js` |
| Remote Name | `opsacc` |
| Exposed Module | `./bootstrap` |
| Contract | `1.0` |
| MF Manifest Snapshot | کل JSON بخش ۵ |

«Validate و Publish» را بزنید. سپس در جدول نسخه‌ها، روی «Activate / Rollback» همان نسخه بزنید تا نشان «فعال» ظاهر شود. در روش دستی، Publish و Activate دو مرحلهٔ جدا هستند. برای تغییر یک نسخهٔ immutable، نسخهٔ تازه مانند `0.1.1` منتشر کنید.

## ۷. اعطای دسترسی محدود

در «استودیوی دسترسی OpenFGA»:

1. درخت میکروی `ops-acc` و منبع `page:ops-acc.home` را انتخاب کنید.
2. نوع هویت را «کاربر» و کاربر را `demo-hr-only` انتخاب کنید.
3. مقابل عملیات `view` روی «اعطا» بزنید.
4. وضعیت همگام‌سازی اعطا را بررسی کنید؛ وجود ردیف با خطای projection به معنی اعمال موفق در OpenFGA نیست.

در منطق فعلی، مجوز یک PAGE که route میکرو به آن ارجاع دارد برای ورود آن میکرو به کاتالوگ کافی است؛ لازم نیست برای حل مشکل نمایش، مجوز کلی همهٔ منابع یا دسترسی راهبر بدهید. مجوز صفحه، مجوز عملیات API، فیلدها و سایر صفحات را خودبه‌خود تضمین نمی‌کند.

## ۸. تست با کاربر واقعی

یک پنجرهٔ Incognito/InPrivate تازه باز کنید و وارد `http://localhost:8443` شوید. برای محیط دموی محلی این مخزن:

```text
username: demo-hr-only
password: local-change-me
```

این اطلاعات مخصوص دمو است. اگر رمز محیط تغییر کرده، از رمز همان محیط استفاده کنید. خروج فعلی برنامه نشست SSO در Keycloak را خاتمه نمی‌دهد؛ پنجرهٔ خصوصی تازه مانع ورود ناخواسته با حساب راهبر قبلی می‌شود.

در همان مرورگر، پاسخ `GET /api/me/context` را بررسی کنید. برای APIهای دیگر یا curl باید نشست معتبر خود درخواست موجود باشد. خروجی مورد انتظار:

- در `permissions`، کلید `page:ops-acc.home` شامل `view` باشد.
- در `uiCatalog.modules`، یک عضو با `moduleKey: "ops-acc"` باشد.
- همان عضو route با `id: "index"` و resource برابر `page:ops-acc.home` داشته باشد.
- در `panels`، ردیفی با `slug: "ops-acc"` دیده شود.
- منوی «حسابداری عملیات» و مسیر `/ops-acc` باز شوند و Remote Entry و chunkها خطای بارگذاری نداشته باشند.

ممکن است `remoteEntryUrl` مؤثر به شکل مسیر BFF مانند `/api/mfe/ops-acc/remoteEntry.js` نمایش داده شود؛ با URL ثبت‌شدهٔ upstream الزاماً یکسان نیست.

برای تست منفی، مجوز همین صفحه را از همان کاربر لغو و context را بازخوانی کنید. اگر مجوز دیگری از نقش/گروه یا منبع دیگری برای کشف همین میکرو ندارد، میکرو نباید در کاتالوگ قابل دسترس او بماند. کنترل backend باید مستقل از پنهان‌شدن منو برقرار باشد.

## ۹. عیب‌یابی

| علامت | بررسی لازم |
|---|---|
| Permission هست، ولی Panel و UI Catalog نیست | Active Artifact وجود دارد؟ نسخه `VALID` و پنل فعال است؟ route به منبع/عملیات مجاز اشاره دارد؟ |
| `active_artifact_id = null` | نسخه هنوز فعال نشده؛ Sync یا Publish سپس Activate انجام دهید |
| `undeclared resource/action` | ابتدا منبع و عملیات را منتشر کنید؛ کلیدها را دقیق تطبیق دهید |
| دکمهٔ Sync غیرفعال است | `MF Manifest URL` در ویرایش Panel ثبت نشده است |
| Fetch موفق، ولی منبع در درخت نیست | Draft هنوز Publish نشده یا conflict دارد |
| میکرو در کاتالوگ هست، ولی منو نیست | `navigation.routeKey`، hidden overlay و ساختار والد Navigation را بررسی کنید |
| منو هست، ولی میکرو بارگذاری نمی‌شود | شبکه، Remote Name، Exposed Module، قرارداد صادرات App و chunkها را بررسی کنید |
| پاسخ JSON در واقع HTML است | فایل Manifest منتشر نشده یا fallback وب‌سرور مسیر نادرست را به index برمی‌گرداند |
| دسترسی بیش از انتظار است | حساب جاری، نقش‌ها، گروه‌ها، مجوزهای دیگر و وضعیت SSO را بررسی کنید |
| خطای تعارض نسخه در Activate | پنجره را بازخوانی و با نسخهٔ جدید ردیف دوباره اقدام کنید |

در بررسی `ops-acc` در تاریخ ۲۰۲۶-۰۹-۲۰، Panel فعال و مجوز `page:ops-acc.home/view` موجود بود، اما `active_artifact_id` و URLهای Manifest خالی بودند. نبود Active Artifact علت حذف آن از کاتالوگ بود؛ این مشاهده وضعیت دائمی محیط نیست.

## ۱۰. مراجع پیاده‌سازی

- [فرم‌ها و دکمه‌های مدیریت میکرو](../apps/mfe-admin/src/Panels.tsx)
- [اعتبارسنجی، Sync و فعال‌سازی Artifact](../services/authorization-service/src/main/java/com/aurevia/authz/ui/UiPluginRegistryService.java)
- [شرط Active Artifact معتبر برای ورود به کاتالوگ](../services/authorization-service/src/main/java/com/aurevia/authz/authorization/JdbcAuthorizationQueryRepository.java)
- [فیلتر مجوز routeها و ساخت مانیفست مؤثر](../services/authorization-service/src/main/java/com/aurevia/authz/authorization/AuthorizationDecisionService.java)
- [چرخهٔ مستقل میکروفرانت و سیاست شبکه](mfe-independent-lifecycle-fa.md)
- [راهنمای فیلدهای پنل راهبری](operator-admin-form-field-guide-fa.md)
