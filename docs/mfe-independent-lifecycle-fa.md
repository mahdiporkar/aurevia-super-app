# چرخهٔ مستقل Core و Micro Frontend در Aurevia

در معماری فعلی، Aurevia Core فقط چرخهٔ سرویس‌های پلتفرم را مدیریت می‌کند: Shell،
SuperApp BFF، Authorization Service، OpenFGA، Keycloak، دیتابیس‌ها و زیرساخت عملیاتی.
چهار MFE نمونه بخشی از Compose اصلی نیستند و خاموش‌بودن یک یا همهٔ آن‌ها مانع startup یا
health سرویس‌های Core نمی‌شود.

## فرمان‌های اجرا

Core بدون هیچ MFE:

```bash
npm run infra:up
npm run infra:down
```

چهار MFE نمونه با lifecycle مستقل:

```bash
npm run mfe:build
npm run mfe:up
npm run mfe:down
```

فایل این چرخه `infra/docker-compose/compose.mfe-demo.yml` و نام Compose project آن
`aurevia-mfe-demo` است. اجرای یکی از MFEها با webpack dev server نیز مستقل است:

```bash
npm run dev:mfe:admin
npm run dev:mfe:hr
npm run dev:mfe:finance
npm run dev:mfe:reports
```

مثلاً `npm run dev:mfe:hr` فقط HR را روی پورت 3002 اجرا می‌کند. Core محلی برای دسترسی
از داخل container به هر پورت loopback ثبت‌شده از bridge عمومی توسعه
`host.docker.internal` استفاده می‌کند؛ هیچ نگاشت ثابت `3001 -> mfe-admin` یا مشابه آن وجود ندارد.

## Registry، منبع یگانهٔ آدرس

رکورد `panel` و revision فعال `ui_module_artifact` منبع حقیقت runtime هستند. فیلدهای اصلی
شامل slug/module key، `remoteEntry`، `remoteName`، `exposedModule`، `mfManifestUrl`،
`resourceManifestUrl`، integrity و active هستند. Compose و Shell کپی دیگری از URL تولید
نمی‌کنند.

ثبت از UI «مدیریت میکروفرانت‌ها» روش توصیه‌شده است. نمونهٔ معادل از façade همان‌origin
مدیریت (با session مدیر و CSRF معتبر):

```http
POST /api/v1/admin/panels
Content-Type: application/json
X-CSRF-TOKEN: <csrf-token>

{
  "code": "CRM",
  "nameFa": "ارتباط با مشتری",
  "nameEn": "CRM",
  "description": "CRM independently deployed by Company A",
  "slug": "crm",
  "serviceSlug": "crm",
  "remoteName": "company_a_crm",
  "defaultRouteId": "home",
  "remoteEntry": "https://crm.company-a.com/assets/remoteEntry.js",
  "exposedModule": "./plugin",
  "routeBasePath": "/crm",
  "semanticVersion": "1.0.0",
  "contractVersion": "1.0",
  "integrity": "sha384-<base64-digest>",
  "resourceDefinitionMode": "MANIFEST",
  "classification": "REAL",
  "mfManifestUrl": "https://crm.company-a.com/mf-manifest.json",
  "resourceManifestUrl": "https://crm.company-a.com/resource-manifest.json",
  "active": true,
  "sortOrder": 50
}
```

پس از ثبت، MF Manifest را sync کنید تا revision معتبر ساخته و فعال شود. Resource Manifest
مسیر draft/preview/publish مستقل خود را دارد. تغییر URL یا ثبت MFE جدید نیازمند ویرایش Compose،
`.env`، `UI_ARTIFACT_ALLOWED_ORIGINS`، build یا restart Core نیست. متغیر
`UI_ARTIFACT_ALLOWED_ORIGINS` حذف شده است.

## جریان کامل runtime و authorization

1. Shell با session کاربر `GET /api/me/context` را فراخوانی می‌کند.
2. BFF، Authorization Service و OpenFGA فقط moduleهای مجاز را در Effective UI Catalog
   برمی‌گردانند.
3. BFF آدرس واقعی revision را در پاسخ مرورگر به
   `/api/mfe/{moduleKey}/remoteEntry.js` تبدیل می‌کند.
4. Shell فقط همین URL همان‌origin را load می‌کند.
5. هر درخواست artifact دوباره catalog مؤثر همان کاربر را دریافت می‌کند. module غایب با 404
   رد می‌شود؛ مخفی‌کردن menu جایگزین این کنترل backend نیست.
6. BFF آدرس واقعی revision فعال را از همان catalog می‌گیرد، policy شبکه را اعمال و artifact را
   proxy می‌کند.

در نتیجه مرورگر topology واقعی MFE را نمی‌بیند و CORS بین Browser و host خارجی لازم نیست.
درخواست مستقیم یک کاربر غیرمجاز به `/api/mfe/hr/remoteEntry.js` نیز artifact را افشا نمی‌کند.

## اعتبارسنجی URL و SSRF

ذخیرهٔ تنظیمات، بخش offline-safe اعتبارسنجی را انجام می‌دهد: URL مطلق، scheme مجاز، hostname
ساختاری معتبر، نبود username/password، query و fragment، نبود traversal کدشده، پسوند `.js`
برای Remote Entry، پسوند `.json` برای manifest و ساختار SRI. در production، HTTPS و SRI
اجباری‌اند.

DNS و policy آدرس شبکه بلافاصله پیش از هر fetch در Authorization Service و BFF بررسی می‌شود.
این تفکیک اجازه می‌دهد یک configuration معتبر را هنگام downtime موقت MFE ذخیره کنیم، ولی
هیچ درخواست end-user یا sync به مقصد حل‌نشده یا ممنوع ارسال نشود. redirect دنبال نمی‌شود؛
manifest حداکثر 1 MiB و artifact به‌صورت پیش‌فرض حداکثر 8 MiB است؛ connect/response timeout
و content type فایل‌های JavaScript، JSON و CSS کنترل می‌شود.

مقصدهای unspecified، link-local، multicast، reserved و metadata شناخته‌شده در همهٔ محیط‌ها
مسدودند. سه mode سراسری وجود دارد:

| Policy | کاربرد | private network | loopback |
|---|---|---:|---:|
| `DEVELOPMENT` | workstation و Docker محلی | مجاز | مجاز یا rewrite به development host |
| `INTERNAL_ENTERPRISE` | شبکهٔ خصوصی سازمان | فقط CIDRهای سراسری مصوب | مسدود |
| `PRODUCTION_INTERNET` | MFE عمومی production | مسدود | مسدود |

برای hostهایی مانند `http://10.20.30.40:8080` از `INTERNAL_ENTERPRISE` و یک محدودهٔ مصوب
مانند `10.20.30.0/24` در `UI_ARTIFACT_ALLOWED_PRIVATE_CIDRS` استفاده کنید. این یک
policy محیطی است، نه allowlist هر MFE. HTTP فقط در محیط توسعه/غیرproduction که صریحاً مجاز
شده قابل استفاده است؛ همان مقصد در profile تولید باید HTTPS ارائه کند. کنترل egress
شبکه/Firewall باید در production نیز به‌عنوان لایهٔ دفاعی مستقل فعال باشد.

تنظیمات سراسری:

```env
UI_ARTIFACT_NETWORK_POLICY=PRODUCTION_INTERNET
UI_ARTIFACT_ALLOW_HTTP=false
UI_ARTIFACT_REQUIRE_INTEGRITY=true
UI_ARTIFACT_DEVELOPMENT_HOST=
UI_ARTIFACT_ALLOWED_PRIVATE_CIDRS=10.20.30.0/24,10.40.0.0/16
MFE_PROXY_CONNECT_TIMEOUT_MS=3000
MFE_PROXY_RESPONSE_TIMEOUT_MS=10000
MFE_PROXY_MAX_RESPONSE_BYTES=8388608
```

profile production مقدار HTTP را false، SRI را true و development host را خالی نگه می‌دارد.
`MFE_PROXY_LOOPBACK_TARGETS` نیز حذف شده و production هیچ وابستگی به نام یا پورت MFEهای Demo
ندارد.

## رفتار خرابی و audit

sync مانیفست فقط با اقدام مدیر انجام می‌شود و در startup اجرا نمی‌شود. startup guard صرفاً
اعتبار ساختاری revisionهای فعال را بدون تماس شبکه بررسی می‌کند. بنابراین MFE خاموش، Core را
خراب نمی‌کند؛ درخواست همان MFE با 502 یا 504 شکست می‌خورد و سایر moduleها مستقل می‌مانند.

رویدادهای موجود `UI_REGISTRY` تغییرات `remoteEntryUrl`، `mfManifestUrl`،
`resourceManifestUrl`، وضعیت active، publish/sync و activation را با before/after امن ثبت
می‌کنند. سیستم audit جداگانه‌ای برای این قابلیت ساخته نشده است.
