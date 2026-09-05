# قرارداد مؤثر UI Catalog و مسیریابی Admin MFE

این سند مرجع نهایی جریان manifest رابط کاربری و صفحه‌های route-based میکروفرانت مدیریت است. هدف این طراحی آن است که Shell هیچ تصمیم دسترسی نسازد و هیچ مسیر داخلی MFE را hardcode نکند.

## جریان معماری نهایی

```text
Panel registration (routePrefix)
        +
Published immutable MFE artifact (relative routes, menus, remote, runtime)
        +
OpenFGA decisions for the current user
        │
        ▼
Authorization Service: effective uiCatalog
        │
        ▼ unchanged
SuperApp BFF: GET /api/v1/me/manifest
        │
        ▼
Shell: route/menu/remote loading
        │
        ▼
MFE: internal relative router
```

مالک تصمیم دسترسی زمان اجرا فقط OpenFGA است. PostgreSQL مشخصات registration، artifact، resource و mapping را نگه می‌دارد، اما یک موتور مجوزدهی موازی نمی‌سازد.

## مالکیت داده و مسئولیت اجزا

| جزء | مسئولیت |
|---|---|
| Panel registration | `routePrefix`، نام نمایشی، ترتیب، icon و اتصال panel به artifact فعال |
| Published MFE artifact | routeهای نسبی، menuها، remote metadata، نسخه قرارداد و `runtime.apiBasePath` |
| Authorization Service | بررسی مجوز module و تک‌تک pageها، حذف route/menu غیرمجاز، انتخاب default مجاز و تولید `uiCatalog` |
| BFF | عبور بدون تغییر manifest و افزودن cache header/ETag؛ بدون بازسازی business rule |
| Shell | مصرف مستقیم `uiCatalog.modules`، ساخت مسیر و menu و بارگذاری remote |
| Admin MFE | render صفحه داخلی از روی route نسبی؛ بدون اطلاع از prefix ثبت‌شده در Super App |

`panels`، `permissions` و `resourceTree` فعلاً برای سازگاری، administration و debug در پاسخ باقی مانده‌اند. Shell برای تشخیص module یا صفحه قابل‌مشاهده به ترکیب این مدل‌های قدیمی وابسته نیست.

## الگوریتم effective uiCatalog

Authorization Service برای هر درخواست کاربر این ترتیب را اجرا می‌کند:

1. panelهای فعال با artifact معتبر را می‌خواند.
2. برای هر panel، `can_view` را روی application متناظر در OpenFGA بررسی می‌کند.
3. permission candidateهای کاتالوگ را به OpenFGA batch check می‌فرستد.
4. routeهای artifact را با زوج `resource + action` فیلتر می‌کند.
5. menuهایی را که route مجاز ندارند حذف می‌کند و override معتبر را اعمال می‌کند.
6. اگر هیچ route مجازی باقی نماند، خود module را از `uiCatalog` حذف می‌کند.
7. `defaultRouteId` را از میان routeهای باقی‌مانده انتخاب می‌کند؛ اگر default منتشرشده مجاز نباشد، اولین route مجاز انتخاب می‌شود.
8. `routePrefix` را فقط از registration و مسیرهای داخلی را فقط از artifact می‌گیرد.

این فیلتر frontend یک قابلیت UX است. endpointهای backend همچنان مستقل از URL و UI، مجوز OpenFGA را enforce می‌کنند؛ دانستن URL یک صفحه مجوز دسترسی ایجاد نمی‌کند.

## نمونه نهایی پاسخ BFF

```json
{
  "manifestType": "EFFECTIVE_USER_MANIFEST",
  "subject": {
    "type": "user",
    "issuer": "https://iam.example.com/realms/public",
    "id": "9d52a6dd-5ddb-4c55-82da-b82015c471d8"
  },
  "version": "manifest-sha256-...",
  "expiresAt": "2026-09-05T20:31:00Z",
  "panels": [],
  "permissions": {
    "application:aurevia": ["admin"],
    "proxy.route": ["admin"]
  },
  "resourceTree": [],
  "uiCatalog": {
    "catalogVersion": "manifest-sha256-...",
    "generatedAt": "2026-09-05T20:30:00Z",
    "contractVersion": "1.0",
    "modules": [
      {
        "registrationId": "11111111-1111-1111-1111-111111111111",
        "moduleKey": "admin",
        "displayName": "مدیریت",
        "displayNameEn": "Administration",
        "description": "مرکز راهبری سوپر اپ",
        "icon": "control",
        "order": 10,
        "routePrefix": "management",
        "defaultRouteId": "operator-guide",
        "remote": {
          "remoteEntryUrl": "https://static.example.com/mfe/admin/remoteEntry.js",
          "remoteName": "aurevia_admin",
          "exposedModule": "./bootstrap",
          "contractVersion": "1.0",
          "artifactVersion": "0.2.0"
        },
        "runtime": {
          "apiBasePath": "/api/v1/admin"
        },
        "routes": [
          {
            "id": "operator-guide",
            "path": "operator-guide",
            "title": "راهنمای فرم‌ها",
            "resource": "application:aurevia",
            "action": "admin"
          },
          {
            "id": "proxy-routes",
            "path": "proxy-routes/routes",
            "title": "Proxy Routes",
            "resource": "proxy.route",
            "action": "admin"
          }
        ],
        "menus": [
          {
            "id": "operator-guide-menu",
            "routeId": "operator-guide",
            "title": "راهنمای فرم‌ها",
            "icon": "book",
            "order": 10
          },
          {
            "id": "proxy-routes-menu",
            "routeId": "proxy-routes",
            "title": "Proxy Routes",
            "icon": "branches",
            "order": 51
          }
        ]
      }
    ]
  }
}
```

نمونه بالا عمداً تنها routeهای مجاز همان کاربر را نشان می‌دهد. اگر کاربر هیچ route مجازی در یک MFE نداشته باشد، آن MFE در `modules` ظاهر نمی‌شود؛ اگر هیچ MFE مجازی نباشد، مقدار `modules` برابر `[]` است.

## routePrefix و deep link

تعریف داخلی Admin برای مثال این است:

```text
proxy-routes/routes
```

registration اول:

```text
routePrefix = management
runtime URL = /management/proxy-routes/routes
```

registration دوم بدون تغییر MFE:

```text
routePrefix = governance
runtime URL = /governance/proxy-routes/routes
```

در standalone همان تعریف داخلی به صورت `/proxy-routes/routes` کار می‌کند. `BrowserRouter` و `historyApiFallback` refresh و deep link را نگه می‌دارند. در حالت embedded، Shell مسیر `/{routePrefix}/*` را می‌سازد و `HostRuntime.navigation` prefix را به navigation داخلی اضافه می‌کند. Back، Forward، bookmark و بازکردن در tab جدید از history واقعی router استفاده می‌کنند، نه state شماره تب.

## تب‌های واقعی کشف‌شده و route جدید

| بخش قبلی | تب تو‌در‌تو قبلی | route داخلی جدید |
|---|---|---|
| راهنمای فرم‌ها | — | `/operator-guide` |
| دسترسی مبتنی بر OU | OUهای سازمانی | `/ou-access/ous` |
| دسترسی مبتنی بر OU | Access Groupها | `/ou-access/groups` |
| دسترسی مبتنی بر OU | دسترسی Microfrontend | `/ou-access/applications` |
| دسترسی مبتنی بر OU | بررسی دسترسی User | `/ou-access/explain` |
| استودیوی دسترسی | — | `/access-studio` |
| میکروفرانت‌ها | — | `/panels` |
| راهبری Proxy | Service Targets | `/proxy-routes/targets` |
| راهبری Proxy | Proxy Routes | `/proxy-routes/routes` |
| راهبری Proxy | Route Operations | `/proxy-routes/operations` |
| اتصال‌های Legacy | — | `/outbound-connections` |
| پروفایل‌های احراز هویت سرویس‌ها | — | `/outbound-auth` |
| آزمایشگاه اتصال | — | `/integration-test` |
| محیط‌های Superset | — | `/superset-instances` |
| گروه‌ها و نقش‌ها | — | `/identity` |
| لاگ‌ها | API Logs | `/logs/api` |
| لاگ‌ها | Audit Logs | `/logs/audit` |
| گزارش‌ها و داشبوردها | — | `/superset` |

تمام content، فرم، table، modal، action و API قبلی در page متناظر حفظ شده است. ظاهر tab-like برای navigation باقی مانده، اما `location` و route منبع حقیقت صفحه فعال هستند.

با `routePrefix=management` مسیرهای نمونه زیر می‌شوند:

```text
/management/ou-access/groups
/management/proxy-routes/operations
/management/logs/audit
/management/superset
```

## منبع واحد route در Admin MFE

`ADMIN_PAGE_ROUTES` در `apps/mfe-admin/src/admin-route-catalog.ts` منبع routeهای داخلی است و برای این موارد استفاده می‌شود:

- router صفحه‌ها؛
- navigation بالایی و زیرصفحه‌ها؛
- metadata منتشرشدنی `publishedManifest.routes`؛
- metadata منو در `publishedManifest.menus`؛
- تست mapping و relative بودن مسیرها.

این مدل هیچ `routePrefix` ندارد. migration نسخه `V48` snapshot اولیه artifact `0.2.0` را در registry موجود bootstrap می‌کند؛ نسخه‌های بعدی باید از `publishedManifest` همان build از طریق API فعلی UI Registry منتشر و سپس activate شوند.

## page-level authorization

نمونه route منتشرشده:

```json
{
  "id": "proxy-operations",
  "path": "proxy-routes/operations",
  "resource": "proxy.operation",
  "action": "admin"
}
```

Authorization Service زوج بالا را با semantics موجود به permission محاسباتی OpenFGA تبدیل می‌کند. اگر نتیجه false باشد، هم route و هم menu متناظر حذف می‌شوند. Admin router نیز فقط route IDهای برگشتی در module مؤثر را mount می‌کند؛ route ناشناخته یا غیرمجاز fallback «صفحه مدیریت یافت نشد» می‌گیرد.

منابع قبلی دوباره استفاده شده‌اند: `application:aurevia`، `proxy.target`، `proxy.route`، `proxy.operation`، `integration.auth-profile`، `business_resource:public-zone-logs` و `module:admin.superset-catalog`. معنای مجوز جدید یا موتور دسترسی دوم ایجاد نشده است.

## default و مسیر نامعتبر

- `/` به `defaultRouteId` می‌رود فقط اگر آن route در پاسخ مؤثر وجود داشته باشد.
- در غیر این صورت اولین route مجاز انتخاب می‌شود؛ بنابراین کاربری که فقط Superset را دارد به `/superset` می‌رود.
- `/ou-access`، `/proxy-routes` و `/logs` به اولین child مجاز همان بخش می‌روند.
- مسیر ناشناخته به صفحه نامرتبط redirect نمی‌شود و fallback مشخص دریافت می‌کند.

## سازگاری و کد legacy باقی‌مانده

- `panels/permissions/resourceTree` تا پایان مهاجرت مصرف‌کننده‌های مدیریتی و debug باقی مانده‌اند.
- export قدیمی `mount` در Admin MFE برای مصرف‌کننده‌ای که هنوز bootstrap مستقیم دارد باقی مانده، ولی قرارداد اصلی remote اکنون `1.0` و `App` است.
- تصمیم Shell فقط بر اساس `uiCatalog` است؛ fallback permission در Admin فقط برای manifest قدیمیِ بدون `uiCatalog` نگه داشته شده و مسیر توسعه جدید نیست.

## تست و پذیرش

پوشش خودکار شامل این موارد است:

- MFE غیرمجاز در catalog نیست؛
- MFE مجاز و remote metadata آن موجود است؛
- route غیرمجاز و menu وابسته حذف می‌شوند؛
- module بدون route مجاز حذف می‌شود؛
- routeها نسبی و `routePrefix` ثبت‌شده مستقل است؛
- default مجاز انتخاب می‌شود؛
- BFF همان manifest را بدون business-rule جدید عبور می‌دهد؛
- Shell از `uiCatalog` route و menu می‌سازد؛
- هر ۱۸ صفحه قبلی route دارد؛
- deep linkهای nested resolve می‌شوند؛
- prefixهای `management` و `governance` تعریف داخلی یکسانی دارند؛
- مسیر نامعتبر match نمی‌شود و fallback می‌گیرد.

فرمان‌های پذیرش:

```bash
npm run typecheck
npm test
npm run build
./mvnw test
```

برای تست زنده migration، login و refresh باید Docker Engine فعال باشد و سپس `npm run infra:up` و سناریوهای `tools/fresh-install-verify.mjs` اجرا شوند.
