# استاندارد اجباری Manifest برای Micro Frontendها

| مشخصه | مقدار |
|---|---|
| وضعیت | هنجاری و الزامی (Normative) |
| نسخه قرارداد | `1.0` |
| تاریخ آخرین بازبینی | `2026-09-09` |
| دامنه | همه Micro Frontendهای متصل به Aurevia Super App |
| قراردادها | `resource-manifest.json` و `mf-manifest.json` |

این سند مرجع نهایی طراحی، بازبینی، انتشار و عملیات Manifestهای Micro Frontend است. هر
Micro Frontend جدید یا موجود **باید** از دو فایل مستقل استفاده کند:

```text
resource-manifest.json  -> تعریف Resource و Actionهای قابل مجوزدهی
mf-manifest.json        -> runtime، routeهای محلی و navigation پیش‌فرض
```

قرار دادن اطلاعات frontend در Resource Manifest یا تعریف Resource در MF Manifest ممنوع است.
اسناد معماری دیگر توضیح تکمیلی‌اند؛ در صورت تعارض، این سند برای قرارداد نسخه `1.0` اولویت دارد.

## ۱. واژگان الزام

- **باید** و **نباید**: شرط الزامی برای قبول شدن در review و انتشار است.
- **توصیه می‌شود**: عدول از آن فقط با دلیل ثبت‌شده در Pull Request مجاز است.
- **سازگاری قدیمی**: فقط برای خواندن revisionهای موجود است و برای فایل جدید مجاز نیست.
- **کلید پایدار**: شناسه‌ای است که با تغییر عنوان، ترجمه، URL یا چیدمان تغییر نمی‌کند.
- **Registration**: رکورد فنی Micro Frontend در جدول `panel` و پنل راهبری است.
- **Manifest-owned**: داده‌ای با `source=MANIFEST` که فقط از انتشار Manifest تغییر می‌کند.
- **Admin-owned**: داده‌ای با `source=ADMIN` که راهبر ایجاد و نگهداری می‌کند.
- **Effective Context**: خروجی فیلترشده backend برای کاربر جاری؛ تنها ورودی مجاز Shell است.

## ۲. مرز مالکیت و منبع حقیقت

| داده | منبع حقیقت | فایل MFE حق تعریف دارد؟ |
|---|---|---|
| `slug` فنی و `route_base_path` سراسری | Registration | خیر |
| URL دو Manifest | Registration | خیر |
| remote مؤثر، SRI و contract اجرایی | Registration و revision فعال | فقط مقدار پیش‌فرض |
| route محلی و navigation پیش‌فرض | `mf-manifest.json` | بله |
| عنوان، icon، order و hidden مدیریتی | Navigation Overlay | خیر؛ فقط default می‌دهد |
| Resource، hierarchy و Action قابل حفاظت | `resource-manifest.json` یا راهبر بر اساس mode | بله |
| انتساب دسترسی به user/group/role | OpenFGA و Access Administration | خیر |
| route نهایی و منوی قابل مشاهده کاربر | Effective Context | خیر؛ backend می‌سازد |

هیچ Manifestی نباید نام کاربر، گروه، نقش، tenant، token، secret یا grant محیط خاص را در خود
داشته باشد. Manifest «چه چیزی قابل حفاظت یا نمایش است» را تعریف می‌کند، نه «چه کسی مجاز است».

## ۳. الزامات مشترک انتشار فایل‌ها

هر MFE متصل باید هر دو فایل را در ریشه source خود نگه دارد و build آن‌ها را کنار
`remoteEntry.js` منتشر کند. حتی در حالت `MANUAL` وجود یک Resource Manifest معتبر و خالی در
repository برای یکسان ماندن قالب تیم‌ها الزامی است؛ در آن mode فایل import نمی‌شود.

الزامات endpoint فایل:

- JSON معتبر با encoding `UTF-8` و پسوند `.json` باشد.
- پاسخ موفق `2xx` بدهد و `Content-Type` شامل `json` باشد.
- حداکثر اندازه هر فایل `1 MiB` است.
- redirect دنبال نمی‌شود؛ URL نهایی باید مستقیم باشد.
- URL باید absolute، بدون credentials، query و fragment و از origin موجود در allowlist باشد.
- در Production فقط HTTPS مجاز است. HTTP تنها با `aurevia.ui-artifacts.allow-http=true` برای
  محیط محلی مجاز است.
- فایل نباید secret یا تنظیم محرمانه محیط را شامل شود.
- objectها نباید فیلد تعریف‌نشده در این سند داشته باشند. پذیرش احتمالی یک فیلد اضافی توسط
  parser به معنی پشتیبانی قراردادی از آن نیست.

Fetcher سرور timeout اتصال `5s` و timeout درخواست `10s` دارد. در دسترس نبودن endpoint، پاسخ
غیر JSON، redirect یا عبور از سقف اندازه، sync را بدون تغییر revision فعال متوقف می‌کند.

## بخش اول: قرارداد Resource Manifest

## ۴. هدف و قالب پایه `resource-manifest.json`

این فایل فقط catalog مجوزدهی را توصیف می‌کند. قالب canonical آن چنین است:

```json
{
  "schemaVersion": "1.0",
  "module": {
    "key": "hr",
    "name": "Human Resources",
    "nameFa": "منابع انسانی",
    "nameEn": "Human Resources",
    "version": "1.2.0"
  },
  "resources": []
}
```

### ۴.۱. فیلدهای سطح ریشه

| فیلد | نوع | الزام | قاعده |
|---|---|---|---|
| `schemaVersion` | string | باید | در قرارداد جاری دقیقاً `1.0` |
| `module` | object | باید | هویت مالک Resourceها |
| `resources` | array | باید | برای MFE بدون capability اختصاصی یا حالت `MANUAL` می‌تواند خالی باشد |

این فیلدها در ریشه Resource Manifest ممنوع‌اند:

```text
routes, navigation, menus, runtime, remoteEntry, remoteEntryUrl,
exposedModule, routePrefix, slug, component, components, icon, iconKey
```

### ۴.۲. فیلدهای `module`

| فیلد | نوع | الزام | قاعده |
|---|---|---|---|
| `key` | string | باید | دقیقاً برابر `panel.slug` و مطابق regex زیر |
| `name` | string | باید | نام عمومی و غیرخالی |
| `nameFa` | string | باید در Production | نام فارسی قابل نمایش |
| `nameEn` | string | باید در Production | نام انگلیسی قابل نمایش |
| `version` | string | باید | SemVer و immutable برای همان محتوا |

الگوی `module.key`:

```regex
^[a-z][a-z0-9-]{1,79}$
```

الگوی نسخه پذیرفته‌شده:

```regex
^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$
```

هر تغییر محتوایی باید همراه افزایش `module.version` باشد. ارسال محتوای متفاوت با نسخه تکراری
رد می‌شود؛ ارسال همان محتوا با همان نسخه idempotent است.

## ۵. قرارداد هر Resource

```json
{
  "key": "page:hr.employee.detail",
  "type": "PAGE",
  "parentKey": "module:hr",
  "name": "Employee Details",
  "nameFa": "جزئیات کارمند",
  "nameEn": "Employee Details",
  "ownerDomain": "hr",
  "classification": "INTERNAL",
  "actions": ["view"],
  "metadata": {}
}
```

| فیلد | نوع | الزام | معنا و قاعده |
|---|---|---|---|
| `key` | string | باید | کلید global، پایدار، lowercase و منطبق با prefix نوع |
| `type` | enum | باید | یکی از انواع بخش ۶؛ ورودی هنگام normalize uppercase می‌شود |
| `parentKey` | string/null | وابسته | کلید parent؛ نام canonical |
| `parent` | string/null | سازگاری | alias قدیمی `parentKey`؛ در فایل جدید استفاده نشود |
| `name` | string | اختیاری | fallback مشترک نام‌ها |
| `nameFa` | string | باید در Production | عنوان فارسی |
| `nameEn` | string | باید در Production | عنوان انگلیسی |
| `ownerDomain` | string | اختیاری | در نبود آن `panel.slug` اعمال می‌شود |
| `classification` | string | اختیاری | در نبود آن `INTERNAL` اعمال می‌شود |
| `actions` | string[] | باید | actionهای پشتیبانی‌شده؛ بدون blank و duplicate |
| `metadata` | object | اختیاری | metadata دامنه؛ بدون اطلاعات frontend |
| `provider` | string | برای external | نام provider منبع بیرونی |
| `externalType` | string | برای external | نوع شیء در provider |
| `externalId` | string | برای external | شناسه شیء در provider |

اگر نام‌ها حذف شوند backend می‌تواند از `name` و سپس `key` fallback بگیرد، اما این رفتار فقط
برای سازگاری است. MFE تولیدی باید `nameFa` و `nameEn` معنادار داشته باشد.

الگوی کلید Resource:

```regex
^[a-z][a-z0-9_-]*:[a-z0-9][a-z0-9._/-]*$
```

تغییر `key` یا `type` ویرایش محسوب نمی‌شود؛ migration هویتی جداگانه لازم دارد. key نباید
بر اساس ترجمه، label، URL یا نام React component ساخته شود.

## ۶. انواع Resource، prefix و parent مجاز

Backend برای هر MFE دو گره synthetic می‌سازد:

```text
application:aurevia/{panel.slug}  (APPLICATION)
└── module:{module.key}            (MODULE)
```

فایل جدید نباید این دو گره را دوباره تعریف کند. Resource بدون `parentKey` به‌طور پیش‌فرض زیر
`module:{module.key}` قرار می‌گیرد. با این حال برای hierarchyهای چندسطحی باید parent صریح باشد.

| `type` | prefix الزامی | parentهای مجاز |
|---|---|---|
| `APPLICATION` | `application:` | بدون parent |
| `MODULE` | `module:` | `APPLICATION` |
| `PAGE` | `page:` | `MODULE` |
| `UI_COMPONENT` | `component:` | `PAGE`, `UI_COMPONENT` |
| `FIELD` | `field:` | `UI_COMPONENT` |
| `BUSINESS_RESOURCE` | `business:` | `APPLICATION`, `MODULE`, `BUSINESS_RESOURCE` |
| `EXTERNAL_RESOURCE` | `external_resource:` | `APPLICATION`, `MODULE`, `PAGE`, `BUSINESS_RESOURCE` |
| `API_RESOURCE` | `api:` | `APPLICATION`, `MODULE`, `BUSINESS_RESOURCE` |
| `DATA_RESOURCE` | `data:` | `APPLICATION`, `MODULE`, `BUSINESS_RESOURCE`, `DATA_RESOURCE` |
| `DATA_GOVERNANCE_RESOURCE` | `governance:` | `APPLICATION`, `MODULE`, `DATA_RESOURCE` |

تعریف parent مفقود، parent از نوع نامعتبر، self-parent یا cycle کل draft را رد می‌کند.

معنای انواع:

- `PAGE`: صفحه‌ای که route می‌تواند به آن ارجاع دهد.
- `UI_COMPONENT`: ناحیه منطقی قابل حفاظت داخل صفحه؛ نه نام implementation یا React component.
- `FIELD`: فیلد حساس زیر یک `UI_COMPONENT`.
- `BUSINESS_RESOURCE`: مفهوم دامنه مانند Employee یا Invoice.
- `EXTERNAL_RESOURCE`: شناسه شیء بیرونی همراه binding کامل provider.
- `API_RESOURCE`: capability منطقی API؛ نباید URL یا HTTP method را در key مدل کند.
- `DATA_RESOURCE`: dataset، stream یا دارایی داده.
- `DATA_GOVERNANCE_RESOURCE`: policy/capability حاکمیتی مرتبط با داده.

برای `EXTERNAL_RESOURCE` هر سه فیلد `provider`، `externalType` و `externalId` الزامی‌اند.

## ۷. Action و تفاوت آن با Resource

`actions` فقط نام actionهای از قبل ثبت‌شده در Action Registry است. Manifest action جدید ایجاد
نمی‌کند. نمونه‌های رایج عبارت‌اند از `view`، `create`، `update`، `delete`، `export` و
`download`؛ معتبر بودن نهایی هر نام به registry محیط مقصد وابسته است.

این الگوها ممنوع‌اند:

- ساخت Resource برای دکمه‌هایی مانند `create-button`، `delete_button` یا `approve-button`؛
- قراردادن URL با `/api/` در key؛
- شروع key با HTTP method مانند `GET-...` یا `POST-...`؛
- استفاده از route، menu یا نام component به‌عنوان metadata مجوز.

دکمه «ویرایش کارمند» با Resource و Action مدل می‌شود:

```text
Resource = business:hr.employee
Action   = update
```

نه با یک Resource جدید به نام دکمه.

## ۸. metadata مجاز و ممنوع

`metadata` فقط برای داده دامنه‌ای غیرمحرمانه است؛ مانند data steward، retention class یا code
سیستم کسب‌وکار. فیلدهای زیر هم در خود Resource و هم در `metadata` ممنوع‌اند:

```text
route, path, menu, navigation, icon, component, remoteEntry,
remoteEntryUrl, exposedModule, routePrefix, slug, label, iconKey
```

کلید API و اطلاعات runtime نیز نباید با نام دیگری در metadata پنهان شوند. اگر اطلاعاتی برای
render یا navigation لازم است جای آن `mf-manifest.json` است.

## ۹. نمونه کامل Resource Manifest

نمونه زیر hierarchy صفحه، component، field، business، external، API، data و governance را
پوشش می‌دهد. actionهای نمونه باید پیش از انتشار در Action Registry محیط موجود باشند.

```json
{
  "schemaVersion": "1.0",
  "module": {
    "key": "hr",
    "name": "Human Resources",
    "nameFa": "منابع انسانی",
    "nameEn": "Human Resources",
    "version": "1.2.0"
  },
  "resources": [
    {
      "key": "page:hr.employee.list",
      "type": "PAGE",
      "nameFa": "فهرست کارکنان",
      "nameEn": "Employee List",
      "classification": "INTERNAL",
      "actions": ["view"]
    },
    {
      "key": "page:hr.employee.detail",
      "type": "PAGE",
      "nameFa": "جزئیات کارمند",
      "nameEn": "Employee Details",
      "classification": "INTERNAL",
      "actions": ["view"]
    },
    {
      "key": "component:hr.employee.salary",
      "type": "UI_COMPONENT",
      "parentKey": "page:hr.employee.detail",
      "nameFa": "اطلاعات حقوق",
      "nameEn": "Salary Information",
      "classification": "RESTRICTED",
      "actions": ["view"]
    },
    {
      "key": "field:hr.employee.salary.amount",
      "type": "FIELD",
      "parentKey": "component:hr.employee.salary",
      "nameFa": "مبلغ حقوق",
      "nameEn": "Salary Amount",
      "classification": "RESTRICTED",
      "actions": ["view"]
    },
    {
      "key": "business:hr.employee",
      "type": "BUSINESS_RESOURCE",
      "nameFa": "کارمند",
      "nameEn": "Employee",
      "actions": ["view", "create", "update", "export"]
    },
    {
      "key": "api:hr.employee-record",
      "type": "API_RESOURCE",
      "parentKey": "business:hr.employee",
      "nameFa": "سرویس پرونده کارمند",
      "nameEn": "Employee Record API",
      "actions": ["view", "update"]
    },
    {
      "key": "data:hr.employee-record",
      "type": "DATA_RESOURCE",
      "parentKey": "business:hr.employee",
      "nameFa": "داده پرونده کارمند",
      "nameEn": "Employee Record Data",
      "actions": ["view", "export"]
    },
    {
      "key": "governance:hr.employee-retention",
      "type": "DATA_GOVERNANCE_RESOURCE",
      "parentKey": "data:hr.employee-record",
      "nameFa": "سیاست نگهداری داده کارکنان",
      "nameEn": "Employee Data Retention",
      "actions": ["view", "update"],
      "metadata": { "retentionClass": "HR-7Y" }
    },
    {
      "key": "external_resource:hr.payroll.employee",
      "type": "EXTERNAL_RESOURCE",
      "parentKey": "business:hr.employee",
      "nameFa": "پرونده حقوق در سامانه بیرونی",
      "nameEn": "External Payroll Employee",
      "actions": ["view"],
      "provider": "payroll-core",
      "externalType": "employee",
      "externalId": "employee-record"
    }
  ]
}
```

قالب repository برای MFE کاملاً دستی:

```json
{
  "schemaVersion": "1.0",
  "module": {
    "key": "legacy-portal",
    "name": "Legacy Portal",
    "nameFa": "پرتال قدیمی",
    "nameEn": "Legacy Portal",
    "version": "1.0.0"
  },
  "resources": []
}
```

## ۱۰. حالت‌های مالکیت Resource

`resourceDefinitionMode` فقط مالکیت Resource Catalog را تعیین می‌کند و هیچ اثری بر sync
route، navigation یا runtime ندارد.

| mode | URL Resource Manifest | import/publish | ایجاد دستی Resource | کاربرد |
|---|---|---|---|---|
| `MANIFEST` | الزامی | مجاز و مسیر اصلی | ممنوع | مالکیت کامل تیم MFE |
| `MANUAL` | اختیاری | غیرفعال | مجاز | سیستم قدیمی یا مدیریت کامل راهبر |
| `HYBRID` | اختیاری؛ برای sync لازم | مجاز | مجاز با key جدا | ترکیب منابع محصول و محلی |

قواعد مالکیت:

- در `MANIFEST` راهبر نمی‌تواند Resource دستی جدید برای آن MFE بسازد.
- در `MANUAL` هر نوع import Resource Manifest رد می‌شود.
- در `HYBRID` Manifest نمی‌تواند Resource با key متعلق به `ADMIN` را تصاحب کند و راهبر نیز
  نمی‌تواند تعریف Resource متعلق به `MANIFEST` را بازنویسی کند.
- `key`، `type`، `source` و مالکیت `panel` پس از ایجاد immutable هستند.
- در Production، hierarchy، metadata و supported actions منبع `MANIFEST` فقط با publish
  نسخه جدید عوض می‌شوند. راهبر همچنان می‌تواند visibility مجاز را مدیریت کند.
- تغییر mode مالکیت داده موجود را خودکار جابه‌جا نمی‌کند.

## ۱۱. چرخه Draft، Diff و Publish منابع

```text
Fetch/Import
    ↓
Parse + Contract Validation + Normalize
    ↓
Version/Checksum Idempotency Check
    ↓
Immutable Draft + Diff
    ↓
Operator Review
    ↓
Atomic Publish
    ↓
Resource Catalog + Outbox parent events -> OpenFGA
```

`fetch` و `import draft` هیچ تغییری در catalog فعال یا OpenFGA نمی‌دهند. Diff یکی از وضعیت‌های
زیر را برای هر Resource ثبت می‌کند:

| وضعیت | معنا |
|---|---|
| `CREATE` | key جدید Manifest |
| `UPDATE` | تعریف Manifest-owned تغییر کرده است |
| `UNCHANGED` | تفاوت مؤثر ندارد |
| `DEPRECATE` | Resource متعلق به Manifest در نسخه جدید حذف شده است |
| `CONFLICT` | تصادم مالکیت یا تغییر type غیرمجاز |

وجود `CONFLICT` انتشار را متوقف می‌کند. Resource حذف‌شده hard-delete نمی‌شود؛ `DEPRECATED`
می‌شود تا grant و audit history از بین نرود. Publish باید transactional باشد: ایجاد/ویرایش،
جایگزینی actionها، deprecate و ثبت وضعیت draft یا همگی موفق می‌شوند یا هیچ‌کدام.

تکرار همان نسخه و checksum idempotent است. همان نسخه با checksum متفاوت ممنوع است. checksum
روی payload Manifest محاسبه می‌شود؛ بنابراین تغییر whitespace پس از parse اثر معنایی ندارد،
اما تغییر مقدار یا ترتیب array می‌تواند revision متفاوت بسازد.

## بخش دوم: قرارداد MF Manifest، Route و Menu

## ۱۲. هدف و قالب پایه `mf-manifest.json`

این فایل قرارداد frontend مستقل از مجوزهاست، اما routeها با reference به Resource/Action موجود
محافظت می‌شوند.

```json
{
  "schemaVersion": "1.0",
  "microfrontend": {
    "key": "hr",
    "name": "Human Resources",
    "version": "1.2.0"
  },
  "runtime": {
    "remoteEntry": "https://cdn.example.com/hr/remoteEntry.js",
    "remoteName": "aurevia_hr_ui",
    "exposedModule": "./plugin",
    "contractVersion": "1.0",
    "integrity": "sha384-YWJjZA==",
    "apiBasePath": "/api/proxy/hr"
  },
  "defaultRouteKey": "employee-list",
  "routes": [],
  "navigation": []
}
```

| فیلد | نوع | الزام | قاعده |
|---|---|---|---|
| `schemaVersion` | string | باید | دقیقاً `1.0` |
| `microfrontend` | object | باید | هویت و نسخه artifact |
| `runtime` | object | باید | defaultهای اتصال remote |
| `defaultRouteKey` | string | اختیاری | باید key یکی از routeها باشد |
| `routes` | array | باید | routeهای محلی و قابل حفاظت |
| `navigation` | array | باید | درخت منوی پیش‌فرض |

وجود `resources` در هر سطح ریشه MF Manifest ممنوع است. برای فایل جدید استفاده از نام‌های قدیمی
`moduleKey`، `module`، `defaultRouteId`، `menus`، route `id/resource/action` یا navigation
`id/pageKey/routeId` ممنوع است؛ parser فقط برای revisionهای قبلی آن‌ها را می‌خواند.

### ۱۲.۱. `microfrontend`

| فیلد | الزام | قاعده |
|---|---|---|
| `key` | باید | دقیقاً برابر `panel.slug` |
| `name` | باید | نام غیرخالی |
| `version` | باید | SemVer، immutable برای محتوا و deployment settings همان sync |

نسخه پذیرفته‌شده MF Manifest prerelease را می‌پذیرد، اما build metadata با `+` در revision UI
پذیرفته نمی‌شود:

```regex
^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?$
```

### ۱۲.۲. `runtime`

| فیلد | الزام | قاعده |
|---|---|---|
| `remoteEntry` | باید | URL absolute فایل `.js` از origin مجاز |
| `remoteName` | باید | `^[A-Za-z][A-Za-z0-9_]*$` و یکتا بین Panelها |
| `exposedModule` | باید | با `./` شروع شود؛ مانند `./plugin` |
| `contractVersion` | باید | دقیقاً `1.0` |
| `integrity` | Production | SRI با `sha256`، `sha384` یا `sha512` |
| `apiBasePath` | توصیه می‌شود | مسیر same-origin BFF/Proxy با `/` ابتدایی |

`apiBasePath` نباید با `//` شروع شود و نباید شامل `://`، `..`، backslash، query یا fragment
باشد. MFE نباید برای API عملیاتی hostname مستقیم یا token مستقل hard-code کند.

مقادیر runtime داخل فایل default توسعه‌دهنده‌اند. در sync، تنظیمات امن Registration شامل
`remoteEntry`، `remoteName`، `exposedModule`، `contractVersion` و `integrity` بر آن‌ها اولویت
دارند. اختلاف به‌صورت warning گزارش می‌شود. اگر یک version قبلاً با deployment settings دیگری
sync شده باشد، باید version Manifest افزایش یابد.

## ۱۳. قرارداد Route

```json
{
  "key": "employee-details",
  "path": "personal/:id",
  "title": "اطلاعات پرسنل",
  "requiredResource": "page:hr.employee.detail",
  "requiredAction": "view"
}
```

| فیلد | نوع | الزام | قاعده |
|---|---|---|---|
| `key` | string | باید | کلید پایدار و یکتا در همان MFE |
| `path` | string | باید | local path؛ رشته خالی برای root مجاز است |
| `title` | string | توصیه می‌شود | عنوان route، نه منبع authorization |
| `requiredResource` | string | باید | key موجود و فعال Resource |
| `requiredAction` | string | اختیاری | پیش‌فرض `view`؛ باید روی Resource پشتیبانی شود |

الگوی route key:

```regex
^[a-z][a-z0-9._-]{1,99}$
```

`component` و هر جزئیات import/render در route ممنوع است. پیاده‌سازی component داخل remote
و contract plugin باقی می‌ماند.

### ۱۳.۱. قواعد local path

- path با `/` شروع یا تمام نمی‌شود؛ `personal` صحیح و `/personal` غلط است.
- path خالی `""` route ریشه MFE است.
- segment ثابت فقط از `A-Z a-z 0-9 . _ ~ -` استفاده می‌کند.
- parameter به شکل `:id` است و نام آن با حرف شروع و حداکثر ۶۴ کاراکتر دارد.
- wildcard فقط `*` و فقط آخرین segment است؛ مانند `reports/*`.
- `//`، `..`، `://`، backslash، `%`، query، fragment و control character ممنوع‌اند.
- keyها و شکل مؤثر path باید یکتا باشند.
- نام parameter جزو شکل route نیست؛ بنابراین `employee/:id` و `employee/:code` با هم conflict
  دارند.
- literalها برای تشخیص conflict بدون حساسیت به بزرگی حروف مقایسه می‌شوند.

### ۱۳.۲. اتصال route محلی به route سراسری

`route_base_path` را راهبر در Registration تعیین می‌کند و در Manifest تکرار نمی‌شود:

| `route_base_path` | `path` | مسیر نهایی |
|---|---|---|
| `/hr` | `""` | `/hr` |
| `/hr` | `personal` | `/hr/personal` |
| `/hr` | `personal/:id` | `/hr/personal/:id` |
| `/hr` | `reports/*` | `/hr/reports/*` |

تغییر `route_base_path` محیطی نباید باعث تغییر فایل MFE شود. prefix باید در سطح Registration
یکتا، lowercase kebab-case و خارج از prefixهای رزروشده Shell باشد.

### ۱۳.۳. مرجع مجوز route

پیش از sync کردن MF Manifest، `requiredResource/requiredAction` باید در Resource Catalog موجود
و فعال باشد. sync هیچ Resource یا Actionی ایجاد نمی‌کند. ترتیب انتشار نسخه جدیدی که route آن به
Resource جدید وابسته است:

1. Resource Manifest را fetch/import کنید.
2. Diff را بازبینی و draft را publish کنید.
3. پس از آماده شدن Resource/Action، MF Manifest را sync کنید.

Shell نیز route را guard می‌کند، اما این guard فقط تجربه کاربر است. سرویس backend باید همان
مجوز را مستقل از UI enforce کند؛ پنهان کردن route یا menu مرز امنیتی نیست.

## ۱۴. قرارداد Navigation و Menu

واژه canonical در فایل `navigation` است. «Menu» نمایش مؤثر همین درخت در Shell است.

```json
{
  "key": "employees-menu",
  "type": "PAGE",
  "parentKey": "hr.nav.root",
  "routeKey": "employee-list",
  "title": "پرسنل",
  "description": "مدیریت کارکنان",
  "icon": "user",
  "order": 10
}
```

| فیلد | نوع | الزام | قاعده |
|---|---|---|---|
| `key` | string | باید | پایدار، یکتا و مطابق regex route key |
| `type` | enum | باید | `GROUP`, `PAGE`, `EXTERNAL_LINK` |
| `parentKey` | string | اختیاری | اگر موجود است باید به `GROUP` معتبر اشاره کند |
| `routeKey` | string | برای PAGE | key یکی از routeهای همان Manifest |
| `title` | string | باید | عنوان پیش‌فرض غیرخالی |
| `description` | string | اختیاری | توضیح presentation |
| `icon` | string | اختیاری | key از icon system مورد توافق UI |
| `order` | integer | اختیاری | ترتیب پیش‌فرض؛ عدد کمتر زودتر نمایش داده می‌شود |
| `externalUrl` | string | برای external | HTTPS absolute امن |

قواعد هر نوع:

| نوع | `routeKey` | `externalUrl` | فرزندپذیری |
|---|---|---|---|
| `GROUP` | ممنوع | ممنوع | بله |
| `PAGE` | الزامی | ممنوع | خیر؛ parent باید GROUP باشد |
| `EXTERNAL_LINK` | ممنوع | الزامی | خیر؛ parent باید GROUP باشد |

`EXTERNAL_LINK` فقط URL مطلق HTTPS بدون credentials و fragment می‌پذیرد. parent باید در همان
درخت وجود داشته، از نوع `GROUP` باشد و hierarchy بدون cycle بماند.

Navigation نباید `requiredResource` یا `requiredAction` داشته باشد. مجوز یک `PAGE` همواره از
route ارجاع‌شده ارث می‌رسد تا دو policy متفاوت برای menu و route ایجاد نشود. `GROUP` فقط وقتی
مؤثر است که پس از فیلتر حداقل یک فرزند قابل نمایش داشته باشد.

## ۱۵. نمونه کامل MF Manifest با route و menu تو‌در‌تو

```json
{
  "schemaVersion": "1.0",
  "microfrontend": {
    "key": "hr",
    "name": "Human Resources",
    "version": "1.2.0"
  },
  "runtime": {
    "remoteEntry": "https://cdn.example.com/hr/remoteEntry.js",
    "remoteName": "aurevia_hr_ui",
    "exposedModule": "./plugin",
    "contractVersion": "1.0",
    "integrity": "sha384-YWJjZA==",
    "apiBasePath": "/api/proxy/hr"
  },
  "defaultRouteKey": "employee-list",
  "routes": [
    {
      "key": "employee-list",
      "path": "personal",
      "title": "پرسنل",
      "requiredResource": "page:hr.employee.list",
      "requiredAction": "view"
    },
    {
      "key": "employee-details",
      "path": "personal/:id",
      "title": "اطلاعات پرسنل",
      "requiredResource": "page:hr.employee.detail",
      "requiredAction": "view"
    },
    {
      "key": "employee-reports",
      "path": "reports/*",
      "title": "گزارش‌های کارکنان",
      "requiredResource": "business:hr.employee",
      "requiredAction": "export"
    }
  ],
  "navigation": [
    {
      "key": "hr.nav.root",
      "type": "GROUP",
      "title": "منابع انسانی",
      "icon": "team",
      "order": 10
    },
    {
      "key": "employees-menu",
      "type": "PAGE",
      "parentKey": "hr.nav.root",
      "routeKey": "employee-list",
      "title": "پرسنل",
      "icon": "user",
      "order": 10
    },
    {
      "key": "employee-reports-menu",
      "type": "PAGE",
      "parentKey": "hr.nav.root",
      "routeKey": "employee-reports",
      "title": "گزارش‌ها",
      "icon": "bar-chart",
      "order": 20
    },
    {
      "key": "hr-help",
      "type": "EXTERNAL_LINK",
      "parentKey": "hr.nav.root",
      "title": "راهنمای منابع انسانی",
      "externalUrl": "https://docs.example.com/hr",
      "order": 30
    }
  ]
}
```

## ۱۶. Default، Overlay و Navigation مؤثر

Navigation سه لایه دارد:

```text
MF Manifest defaults
        +
Navigation Overlay راهبر
        +
Authorization filter کاربر
        =
Effective Navigation در /api/me/context
```

برای گره `MANIFEST`، راهبر فقط فیلدهای presentation زیر را override می‌کند:

```text
title, icon, order, hidden
```

راهبر حق تغییر `type`، `parentKey`، `routeKey` یا `externalUrl` گره Manifest-owned را ندارد.
برای تغییر structure باید MF Manifest جدید با version جدید sync شود.

راهبر می‌تواند گره مستقل `ADMIN` بسازد و در آن `nodeType`، `parentKey`، `pageKey` و
`externalUrl` را تعیین کند. قواعد type، parent، route و cycle برای گره Admin نیز همان قواعد
Manifest است. title برای گره Admin الزامی است. گره Admin نمی‌تواند key یک گره Manifest را تصاحب
کند و حذف parent دارای فرزند Admin ممنوع است.

Overlay بر اساس key پایدار ذخیره می‌شود و با sync نسخه جدید باقی می‌ماند. اگر گره Manifest از
نسخه جدید حذف شود، overlay آن برای audit با `REMOVED_FROM_SOURCE` نگهداری می‌شود، اما در
navigation مؤثر ظاهر نمی‌شود. `hidden=true` صرفاً presentation است و grant را حذف نمی‌کند.

ترتیب مؤثر از `override.order` و سپس `default.order` می‌آید. عنوان و icon مؤثر نیز ابتدا
override و سپس default را انتخاب می‌کنند.

## ۱۷. Sync و revisionهای immutable

### ۱۷.۱. تنظیمات لازم Registration

راهبر برای هر MFE این مقادیر را ثبت می‌کند:

- `code`، `slug`، نام‌های نمایشی و `classification`؛
- `routeBasePath` و `serviceSlug`؛
- `remoteEntry`، `remoteName`، `exposedModule`، `contractVersion` و `integrity` مؤثر؛
- `mfManifestUrl` و `resourceManifestUrl`؛
- `resourceDefinitionMode`، وضعیت `active` و `sortOrder`.

`mfManifestUrl` برای اجرای sync frontend الزامی است. `resourceManifestUrl` در mode
`MANIFEST` هنگام ثبت الزامی و در `HYBRID` برای fetch لازم است.

### ۱۷.۲. APIهای راهبری

مسیر داخلی Authorization Service و مسیر مرورگر از BFF چنین‌اند:

| عملیات | Authorization Service | BFF/Admin MFE |
|---|---|---|
| sync frontend | `POST /internal/v1/registry/panels/{panelId}/frontend-manifests/sync` | `POST /api/v1/admin/panels/{panelId}/frontend-manifests/sync` |
| فهرست artifactها | `GET /internal/v1/registry/panels/{panelId}/artifacts` | `GET /api/v1/admin/panels/{panelId}/artifacts` |
| فهرست draftهای Resource | `GET /internal/v1/registry/panels/{panelId}/resource-manifests` | `GET /api/v1/admin/panels/{panelId}/resource-manifests` |
| fetch Resource | `POST /internal/v1/registry/panels/{panelId}/resource-manifests/fetch` | `POST /api/v1/admin/panels/{panelId}/resource-manifests/fetch` |
| import draft | `POST /internal/v1/registry/panels/{panelId}/resource-manifests/drafts` | `POST /api/v1/admin/panels/{panelId}/resource-manifests/drafts` |
| preview draft | `GET /internal/v1/registry/panels/{panelId}/resource-manifests/drafts/{draftId}` | `GET /api/v1/admin/panels/{panelId}/resource-manifests/drafts/{draftId}` |
| publish Resource | `POST /internal/v1/registry/panels/{panelId}/resource-manifests/drafts/{draftId}/publish` | `POST /api/v1/admin/panels/{panelId}/resource-manifests/drafts/{draftId}/publish` |
| navigation مؤثر راهبری | `GET /internal/v1/registry/panels/{panelId}/navigation-definitions` | `GET /api/v1/admin/panels/{panelId}/navigation-definitions` |
| ثبت overlay | `PUT /internal/v1/registry/panels/{panelId}/navigation-overrides/{key}` | `PUT /api/v1/admin/panels/{panelId}/navigation-overrides/{key}` |
| حذف overlay | `DELETE /internal/v1/registry/panels/{panelId}/navigation-overrides/{key}` | `DELETE /api/v1/admin/panels/{panelId}/navigation-overrides/{key}` |

درخواست‌های mutation مرورگر باید از session و CSRF استاندارد BFF استفاده کنند. `X-Actor` در
مسیر داخلی برای audit اجباری است و کلاینت عمومی نباید آن را جعل کند.

پاسخ sync frontend شامل این اطلاعات است:

```text
artifactId, status, idempotent,
routesAdded, routesUpdated, routesRemoved,
navigationAdded, navigationUpdated, navigationRemoved,
runtimeChanged, warnings
```

sync رکورد Registration را lock، فایل را fetch و validate، checksum و diff را محاسبه، revision
immutable را ذخیره و آن را اتمیک فعال می‌کند. خطا revision فعال قبلی را حفظ می‌کند. فعال‌سازی
دستی artifact قدیمی نیز optimistic version صحیح Panel را لازم دارد.

## ۱۸. Effective Context و مسئولیت Shell

مرورگر نباید دو Manifest خام را fetch یا با overlay و grant ادغام کند. جریان صحیح:

```text
Resource Catalog + OpenFGA grants
                 +
active MF artifact + Navigation Overlay
                 ↓
Authorization Service filtering
                 ↓
BFF: GET /api/me/context
                 ↓
Shell route guard + menu rendering + remote loading
```

Effective Context برای هر module اطلاعاتی مانند `moduleKey`، `routePrefix`، remote مؤثر،
`runtime.apiBasePath`، routeهای مجاز و navigation مؤثر را دارد. Backend routeهایی را که کاربر
`requiredResource/requiredAction` آن‌ها را ندارد حذف می‌کند؛ PAGEهای وابسته و GROUPهای خالی نیز
حذف می‌شوند. Shell فقط همین خروجی را مصرف می‌کند و نباید policy یا overlay را بازسازی کند.

برای deep link، Shell پیش از load کردن remote باید route مؤثر را resolve کند. route ناشناخته یا
غیرمجاز نباید remote را load کند و باید به رفتار 404/403 مورد توافق برسد. با این حال همه APIهای
عملیاتی باید authorization مستقل سمت سرور داشته باشند.

## ۱۹. سازگاری نسخه `1.0` و migration

- `schemaVersion` و `contractVersion` ناشناخته رد می‌شوند؛ مصرف‌کننده نباید major جدید را حدس
  بزند.
- revisionهای UI قدیمی با `moduleKey`، route `id/resource/action` و `menus` فقط برای read و
  migration قابل استفاده‌اند.
- draft قدیمی Resource که همزمان frontend fields دارد read-only است و تا تبدیل به دو فایل جدا
  publish نمی‌شود.
- endpoint قدیمی `resource-definition-manifests/{application}` payload legacy را مستقیم apply
  نمی‌کند؛ آن را به draft جدید تبدیل می‌کند.
- همه تغییرات جدید باید فقط از نام‌های canonical این سند استفاده کنند.

تغییر ناسازگار نیازمند `schemaVersion` جدید، validator جدید، migration داده، پشتیبانی همزمان
مصرف‌کننده و برنامه حذف نسخه قدیمی است. تغییر label، icon یا افزودن metadata اختیاری سازگار،
تغییر contract major نیست؛ تغییر معنای key/type/path یا requiredness فیلدها ناسازگار است.

## ۲۰. ضدالگوها و علت رد شدن

| ضدالگو | نمونه | علت |
|---|---|---|
| route در Resource Manifest | `"route": "/hr"` | اختلاط authorization و frontend |
| Resource در MF Manifest | `"resources": [...]` | مالکیت دوگانه catalog |
| path سراسری در route | `"path": "/hr/personal"` | prefix متعلق به Registration است |
| component در route | `"component": "EmployeePage"` | نشت implementation |
| مجوز روی menu | `"requiredAction": "view"` | مجوز باید از route ارث برسد |
| دکمه به‌عنوان Resource | `component:hr.delete-button` | Action است، Resource نیست |
| URL به‌عنوان API Resource | `api:/api/employees` | key باید capability پایدار باشد |
| external ناقص | بدون `provider/externalType/externalId` | binding مبهم |
| نسخه تکراری با محتوا جدید | `1.2.0` با checksum دیگر | نقض immutability |
| دو route هم‌شکل | `x/:id` و `x/:code` | resolve مبهم |
| parent منو از نوع PAGE | PAGE زیر PAGE | فقط GROUP می‌تواند parent باشد |
| HTTP خارجی | `http://docs...` | EXTERNAL_LINK فقط HTTPS |

## ۲۱. مراحل استاندارد توسعه و انتشار

### ۲۱.۱. توسعه‌دهنده MFE

1. keyهای Resource، route و navigation را قبل از کدنویسی پایدار طراحی کند.
2. `resource-manifest.json` را فقط با capabilityهای authorization تکمیل کند.
3. routeها را در `mf-manifest.json` با local path و Resource/Action موجود تعریف کند.
4. navigation را با reference به route بسازد؛ مجوز تکراری اضافه نکند.
5. هر دو نسخه را مطابق تغییر افزایش دهد.
6. build تولیدی MFE را اجرا کند تا هر دو validator webpack اجرا و فایل‌ها emit شوند.
7. فایل خروجی و `remoteEntry.js` را روی origin مجاز و immutable منتشر کند.

### ۲۱.۲. راهبر محیط

1. Registration و دو URL را ثبت و originها را allowlist کند.
2. deployment overrideها و SRI واقعی `remoteEntry.js` را ثبت کند.
3. Resource Manifest را fetch/import و Diff را review کند.
4. draft بدون conflict را publish و رسیدن outbox را پایش کند.
5. MF Manifest را sync و counts/warnings را review کند.
6. Navigation Overlay لازم را بدون تغییر structure Manifest-owned اعمال کند.
7. با کاربر مجاز و غیرمجاز، menu، deep link و API را تست کند.

### ۲۱.۳. CI و Pull Request

حداقل کنترل‌های اجباری:

- parse موفق هر دو JSON؛
- اجرای validatorهای `ResourceManifestWebpackPlugin` و
  `MicroFrontendManifestWebpackPlugin` در build؛
- نبود فیلدهای frontend در Resource Manifest و نبود `resources` در MF Manifest؛
- uniqueness و pattern تمام keyها؛
- صحت prefix/type/parent و نبود cycle؛
- صحت local path و نبود route shape تکراری؛
- resolve شدن تمام Resource/Action و route referenceها در محیط آزمون؛
- افزایش version در هر تغییر محتوایی؛
- تست route مستقیم، refresh، منوی تو‌در‌تو، override و کاربر بدون مجوز.

نمونه‌های جاری repository:

```text
apps/mfe-admin/resource-manifest.json
apps/mfe-admin/mf-manifest.json
apps/mfe-finance/resource-manifest.json
apps/mfe-finance/mf-manifest.json
apps/mfe-hr/resource-manifest.json
apps/mfe-hr/mf-manifest.json
apps/mfe-reports/resource-manifest.json
apps/mfe-reports/mf-manifest.json
```

validator build در `tools/resource-manifest-webpack-plugin.cjs` قرار دارد. موفق شدن validator
سمت build جای validation سرور هنگام draft/sync را نمی‌گیرد؛ هر دو لایه الزامی‌اند.

## ۲۲. چک‌لیست نهایی پذیرش

### Resource Manifest

- [ ] `schemaVersion=1.0` است.
- [ ] `module.key` با `panel.slug` برابر است.
- [ ] version جدید و SemVer است.
- [ ] هیچ runtime، route، menu، icon یا component وجود ندارد.
- [ ] key هر Resource با type و prefix آن هماهنگ است.
- [ ] parentها موجود، از نوع مجاز و بدون cycle هستند.
- [ ] actionها موجود، غیرخالی و بدون duplicate هستند.
- [ ] external binding کامل است.
- [ ] Diff بدون `CONFLICT` review شده است.
- [ ] حذف‌ها آگاهانه به `DEPRECATE` منجر می‌شوند.

### MF Manifest، Route و Menu

- [ ] `schemaVersion` و `contractVersion` برابر `1.0` هستند.
- [ ] `microfrontend.key` با `panel.slug` برابر است.
- [ ] runtime URL، allowlist و SRI معتبرند.
- [ ] routeها local، یکتا، غیرمبهم و بدون `component` هستند.
- [ ] همه Resource/Action referenceها قبلاً publish شده‌اند.
- [ ] `defaultRouteKey` موجود است.
- [ ] navigation keyها پایدار و یکتا هستند.
- [ ] هر PAGE به route معتبر اشاره می‌کند.
- [ ] parentها فقط GROUP و درخت بدون cycle است.
- [ ] navigation هیچ Resource/Action مستقلی تعریف نمی‌کند.
- [ ] اختلاف deployment override و warningهای sync بررسی شده‌اند.
- [ ] Effective Context، deep link و API enforcement تست شده‌اند.

با تکمیل این دو چک‌لیست، قرارداد یک MFE از نظر تفکیک authorization، runtime، route و menu برای
نسخه `1.0` آماده انتشار است.
