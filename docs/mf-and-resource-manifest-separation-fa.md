# تفکیک MF Manifest از Resource Manifest

> قرارداد هنجاری و چک‌لیست اجباری همه تیم‌ها در
> [استاندارد Manifest برای Micro Frontendها](microfrontend-manifest-contract-standard-fa.md)
> آمده است. این سند نمای معماری و جزئیات migration را توضیح می‌دهد.

این سند قرارداد مرجع نسخه `1.0` پس از migration شماره `V55` است. هویت ثبت‌شده‌ی هر
Micro Frontend همچنان جدول `panel` است و رجیستری یا موتور مجوزدهی موازی ساخته نشده است.

```text
resource-manifest.json -> resource_manifest_import -> resource/resource_action -> Outbox -> OpenFGA
mf-manifest.json       -> ui_module_artifact       -> ui_menu_override       -> Effective UI Catalog
                                                               + OpenFGA checks
                                                                      |
                                                                      v
                                                             GET /api/me/context
                                                                      |
                                                                      v
                                                                    Shell
```

## مالکیت داده

| داده | منبع حقیقت |
|---|---|
| technical key و route prefix سراسری | registration در `panel.slug` و `panel.route_base_path` |
| URL دو manifest | `panel.mf_manifest_url` و `panel.resource_manifest_url` |
| runtime، route محلی و navigation پیش‌فرض | revision immutable در `ui_module_artifact` |
| label/icon/order/hidden مدیریتی | `ui_menu_override` |
| Resource و Action قابل حفاظت | `resource` و `resource_action` |
| تصمیم مجوز | OpenFGA به‌علاوه policyهای backend |
| context مرورگر | Authorization Service، سپس BFF در `/api/me/context` |

`resource_definition_mode` با مقادیر `MANIFEST`، `MANUAL` و `HYBRID` فقط مالکیت Resource
Catalog را تعیین می‌کند و هیچ اثری بر sync route یا navigation ندارد.

## Resource Manifest

این فایل فقط قرارداد منابع مجوزدهی است. `routes`، `navigation`، `menus`، `runtime`،
`remoteEntry`، `exposedModule` و `routePrefix` در آن رد می‌شوند.

```json
{
  "schemaVersion": "1.0",
  "module": {
    "key": "hr",
    "name": "Human Resources",
    "nameFa": "منابع انسانی",
    "nameEn": "Human Resources",
    "version": "0.2.0"
  },
  "resources": [
    {
      "key": "page:hr.employee.list",
      "type": "PAGE",
      "nameFa": "لیست کارکنان",
      "nameEn": "Employee List",
      "actions": ["view"]
    },
    {
      "key": "field:hr.employee.salary-amount",
      "type": "FIELD",
      "parentKey": "component:hr.employee.salary-information",
      "nameFa": "مبلغ حقوق",
      "nameEn": "Salary Amount",
      "actions": ["view"]
    }
  ]
}
```

Fetch فقط Draft و Diff می‌سازد. Publish منابع را transactionally اعمال می‌کند، موارد حذف‌شده
را `DEPRECATED` می‌کند و تغییر parent را از outbox موجود به OpenFGA می‌رساند. Grantها حذف
نمی‌شوند. revisionهای مخلوط قدیمی برای مشاهده قابل خواندن‌اند، ولی Draft قدیمی باید با قرارداد
تفکیک‌شده دوباره stage شود.

## MF Manifest

این فایل integration فنی و defaultهای presentation را تعریف می‌کند و فقط به Resource اشاره
می‌کند؛ Resource جدید ایجاد نمی‌کند.

```json
{
  "schemaVersion": "1.0",
  "microfrontend": {
    "key": "hr",
    "name": "Human Resources",
    "version": "0.2.0"
  },
  "runtime": {
    "remoteEntry": "https://static.example/hr/remoteEntry.js",
    "remoteName": "aurevia_hr",
    "exposedModule": "./plugin",
    "contractVersion": "1.0",
    "apiBasePath": "/api/proxy/hr"
  },
  "defaultRouteKey": "employee-list",
  "routes": [
    {
      "key": "employee-list",
      "path": "employees",
      "requiredResource": "page:hr.employee.list",
      "requiredAction": "view"
    },
    {
      "key": "employee-details",
      "path": "employees/:id",
      "requiredResource": "page:hr.employee.detail",
      "requiredAction": "view"
    }
  ],
  "navigation": [
    {
      "key": "hr.nav.employees",
      "type": "PAGE",
      "routeKey": "employee-list",
      "title": "Employees",
      "order": 10
    }
  ]
}
```

routeها local و بدون `/` ابتدایی‌اند. مسیر مؤثر از registration ساخته می‌شود:

```text
route_base_path=/hr                  + employees = /hr/employees
route_base_path=/human-resources     + employees = /human-resources/employees
```

تغییر prefix نیاز به تغییر source یا MF Manifest ندارد. `panel.slug` technical key پایدار است؛
در مدل موجود نامی که در سناریوها «slug نمایشی» خوانده می‌شود همان `route_base_path` است.

## Sync و precedence

دو عملیات مستقل‌اند:

| عملیات | API داخلی | اثر |
|---|---|---|
| Sync Frontend Manifest | `POST /internal/v1/registry/panels/{id}/frontend-manifests/sync` | fetch، validate، diff، ثبت revision و activate |
| Sync Resource Manifest | `POST /internal/v1/registry/panels/{id}/resource-manifests/fetch` | ساخت Draft؛ بدون تغییر catalog تا Publish |

Frontend sync با lock ردیف `panel`، version immutable و checksum idempotent است. revision قبلی
حذف نمی‌شود؛ بنابراین rollback و audit ممکن می‌ماند. overrideهای `ui_menu_override` در sync
نوشته یا پاک نمی‌شوند. precedence runtime چنین است:

```text
Admin deployment registration > MF Manifest runtime defaults
```

این precedence برای `remoteEntry`، `remoteName`، `exposedModule`، contract و SRI اعمال می‌شود و
اختلاف با default به صورت warning گزارش می‌شود. چون Artifact immutable است، تغییر deployment
برای یک نسخهٔ قبلاً syncشده نیازمند نسخهٔ جدید MF Manifest است. label/icon/order/hidden مؤثر از
default manifest به‌علاوه override محاسبه می‌شود و مقدار مؤثر هرگز روی default نوشته نمی‌شود.

## Effective context و Shell

Authorization Service routeهایی را که `requiredResource + requiredAction` آنها در OpenFGA مجاز
نیست حذف می‌کند. Navigation نوع `PAGE` شرط مجوز مستقل را تکرار نمی‌کند و مجوز route مرجع را
به ارث می‌برد. BFF همان catalog مؤثر را با URLهای same-origin در `/api/me/context` تحویل می‌دهد.

Shell manifestهای خصوصی را fetch یا policy را evaluate نمی‌کند. تنها routeهای موجود در context را
mount می‌کند؛ deep link پارامتری پشتیبانی می‌شود و URL داخل prefix که با هیچ route مجازی match
نشود پاسخ UX از نوع `403` می‌گیرد. این guard فقط UX است و APIهای backend همچنان مستقل از Shell
مجوز را enforce می‌کنند.

## نمونه‌های قابل اجرا

نمونه‌های source در مسیرهای زیر هستند و Webpack هر دو فایل را کنار `remoteEntry.js` emit می‌کند:

- `apps/mfe-admin/{mf-manifest.json,resource-manifest.json}`
- `apps/mfe-hr/{mf-manifest.json,resource-manifest.json}`
- `apps/mfe-finance/{mf-manifest.json,resource-manifest.json}`
- `apps/mfe-reports/{mf-manifest.json,resource-manifest.json}`
