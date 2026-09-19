# راهنمای ثبت و چرخهٔ حیات میکروفرانت (MFE)

راهنمای گام‌به‌گام برای راهبر/توسعه‌دهنده: چگونه یک میکروفرانت جدید — در هر مکان شبکه‌ای — ثبت، همگام، فعال و در Shell اجرا می‌شود. افزودن میکروی جدید **هیچ تغییری در کد، Nginx یا rebuild** نیاز ندارد؛ همه‌چیز دادهٔ رجیستری است.

---

## A. معماری

```
Panel (رجیستری میکرو: code, slug, تنظیمات استقرار)
  └─ UI Artifact (نسخهٔ فعال: remoteEntry, remoteName, exposedModule, contract, SRI, snapshot MF Manifest)
       ├─ MF Manifest        (runtime + routes + navigation)   ← از mfManifestUrl همگام می‌شود
       └─ Resource Manifest  (کاتالوگ مجوز: منابع/Actionها)    ← از resourceManifestUrl به Draft و سپس Publish
BFF  /api/mfe/{moduleKey}/manifest.json      ← مانیفست مؤثر برای کاربر مجاز
     /api/mfe/{moduleKey}/remoteEntry.js     ← پراکسی remoteEntry (مرورگر هرگز origin واقعی را نمی‌بیند)
     /api/mfe/{moduleKey}/{chunk}            ← chunkهای فدرال، نسبت به مسیر remoteEntry
Shell  loadRemote(remoteName, '/api/mfe/<key>/remoteEntry.js', exposedModule, integrity)
```

مرورگر فقط با origin سامانه صحبت می‌کند. BFF با استفاده از **همان سیاست شبکه‌ای که در زمان ثبت اعمال شده** (`ui-artifact-security/UiArtifactUriPolicy`) artifact را از مقصد ثبت‌شده می‌گیرد.

---

## B. فیلدهای لازم (Panel)

| فیلد | معنا | مثال |
|---|---|---|
| `code` | کد یکتا (حروف بزرگ) | `HR` |
| `slug` | شناسهٔ پایدار Shell و مسیر `/api/mfe/<slug>` | `hr` |
| `nameFa` / `nameEn` | نام نمایشی | `منابع انسانی` / `Human Resources` |
| `routeBasePath` | پیشوند مسیر در Shell | `/hr` |
| `serviceSlug` | پیشوند پراکسی API (`/api/proxy/<serviceSlug>`) | `hr` |
| `remoteEntry` | URL مطلق `remoteEntry.js` — **تنظیم مؤثر استقرار** | `http://192.168.10.25:3000/remoteEntry.js` |
| `remoteName` | نام container در Module Federation (باید با bundle یکی باشد) | `aurevia_hr_ui_0_1_0` |
| `exposedModule` | ماژول expose‌شده | `./plugin` |
| `contractVersion` | قرارداد Shell/MFE | `1.0` |
| `semanticVersion` | نسخهٔ اولیهٔ artifact | `0.2.0` |
| `integrity` | SRI اختیاری/اجباری (بخش C) | `sha384-…` |
| `mfManifestUrl` | URL مطلق `mf-manifest.json` | `http://192.168.10.25:3000/mf-manifest.json` |
| `resourceManifestUrl` | URL مطلق `resource-manifest.json` | `http://192.168.10.25:3000/resource-manifest.json` |
| `resourceDefinitionMode` | `MANIFEST` / `MANUAL` / `HYBRID` | `HYBRID` |
| `classification` | `REAL` یا `DEMO` (DEMO فقط با `DEMO_DATA_ENABLED=true` دیده می‌شود) | `REAL` |
| `active` | فعال بودن ثبت | `true` |

**قاعدهٔ override (قطعی):** مقادیر runtime داخل MF Manifest (`runtime.remoteEntry/remoteName/exposedModule/contractVersion`) **فقط اطلاعاتی** هستند؛ آنچه BFF واقعاً fetch می‌کند همیشه تنظیمات Panel است. اختلاف فقط یک هشدار در نتیجهٔ sync است. نسخهٔ artifact (`microfrontend.version`) **تغییرناپذیر** است: همان نسخه با محتوای متفاوت یا تنظیمات استقرار متفاوت رد می‌شود؛ نسخه را بالا ببرید.

### SRI (Subresource Integrity)
- `UI_ARTIFACT_REQUIRE_INTEGRITY=false` (پیش‌فرض محلی): SRI اختیاری؛ اگر داده شود باید معتبر باشد (`sha256|sha384|sha512-<base64>`).
- `=true` (توصیهٔ تولید): ثبت/فعال‌سازی بدون SRI رد می‌شود با پیام `SRI is required for UI artifacts`.
- محاسبه: `openssl dgst -sha384 -binary remoteEntry.js | openssl base64 -A` و پیشوند `sha384-`. با هر build جدید مقدار عوض می‌شود؛ نسخهٔ artifact را هم بالا ببرید.

---

## C. حالت‌های شبکه (`UI_ARTIFACT_NETWORK_POLICY`)

هر پنج متغیر `UI_ARTIFACT_*` را **هر دو سرویس** (Authorization و BFF) می‌خوانند؛ `compose.yml` آن‌ها را از `.env` عبور می‌دهد. پس هر URL که در ثبت پذیرفته شود، در زمان اجرا هم پذیرفته می‌شود.

| حالت | loopback | خصوصی (10/172.16/192.168, fc00::) | عمومی | نکته |
|---|---|---|---|---|
| `DEVELOPMENT` | ✔ و به `UI_ARTIFACT_DEVELOPMENT_HOST` (مثلاً `host.docker.internal`) بازنویسی می‌شود | ✔ | ✔ | پیش‌فرض Compose |
| `INTERNAL_ENTERPRISE` | ✘ | فقط داخل `UI_ARTIFACT_ALLOWED_PRIVATE_CIDRS` | ✔ | |
| `PRODUCTION_INTERNET` | ✘ | ✘ | ✔ | پیش‌فرض پروفایل prod |
| `UNRESTRICTED` | ✔ (بدون بازنویسی) | ✔ | ✔ | هیچ محدودیت کلاس آدرس؛ IPv4/IPv6/DNS |

**قاعدهٔ امنیتی جداگانه (در همهٔ حالت‌ها، حتی UNRESTRICTED):** آدرس‌های metadata ابری (`169.254.169.254`, `metadata.google.internal`, …)، link-local (`169.254/16`, `fe80::/10`), multicast، `0.0.0.0`/`::` و رنج‌های reserved همیشه رد می‌شوند. این یک قاعدهٔ SSRF است نه توپولوژی، و به‌عمد قابل خاموش‌کردن نیست. همچنین در هر حالت: URL بدون credential/query/fragment، بدون traversal کدگذاری‌شده، remoteEntry باید `.js` و manifest باید `.json` باشد؛ `http` فقط با `UI_ARTIFACT_ALLOW_HTTP=true`.

نکتهٔ توپولوژی: BFF داخل Docker است. `localhost` در UNRESTRICTED به خود کانتینر اشاره می‌کند؛ برای MFEِ روی همان ماشین از IP شبکهٔ میزبان (`192.168.x.x`) یا `host.docker.internal` استفاده کنید. `UI_ARTIFACT_DEVELOPMENT_HOST` خارج از DEVELOPMENT نادیده گرفته می‌شود (خطا نمی‌دهد).

---

## D. مثال کامل: میکروی خارج از Docker

MFE روی سرور دیگری در LAN با آدرس `http://192.168.10.25:3000/` سرو می‌شود (`remoteEntry.js`, `mf-manifest.json`, `resource-manifest.json`, chunkها در همان مسیر).

1. در `.env` سامانه: `UI_ARTIFACT_NETWORK_POLICY=UNRESTRICTED` (یا `INTERNAL_ENTERPRISE` + `UI_ARTIFACT_ALLOWED_PRIVATE_CIDRS=192.168.0.0/16`) و `UI_ARTIFACT_ALLOW_HTTP=true`؛ سپس `docker compose up -d authorization-service aurevia-bff`.
2. Panel را ثبت کنید (بخش E.1) با `remoteEntry: http://192.168.10.25:3000/remoteEntry.js`.
3. «همگام‌سازی MF Manifest» (E.2) ← artifact ایجاد و فعال می‌شود.
4. «دریافت Resource Manifest» (E.4) ← Draft؛ Diff را ببینید؛ «انتشار» (E.5).
5. به کاربر/گروه مجوز `view` روی یک Page یا Application میکرو بدهید (سند مجوزها).
6. ورود کاربر ← `/api/me/context` ماژول را با `remote.remoteEntryUrl = /api/mfe/hr/remoteEntry.js` برمی‌گرداند ← Shell آن را از BFF می‌گیرد ← BFF از `192.168.10.25:3000` می‌خواند.

آزمون خودکار همین سناریو: `npm run e2e:mfe:external` (سرور ایستا روی IP میزبان می‌سازد، Panel HR را با Admin API به آن می‌برد، sync/fetch/preview، بارگذاری remoteEntry و chunk از طریق BFF، رندر در Shell با Chrome واقعی، و بازگردانی). نیاز: Keycloak (`npm run identity:up`) و `UI_ARTIFACT_NETWORK_POLICY=UNRESTRICTED`.

---

## E. نمونه‌های Admin API

همهٔ مسیرها از طریق BFF با نشست راهبر: `/api/v1/admin/...` ⇄ `/internal/v1/registry/...`.

**E.1 ایجاد Panel** — `POST /internal/v1/registry/panels`
```json
{ "code": "HR", "nameFa": "منابع انسانی", "nameEn": "Human Resources", "description": "",
  "slug": "hr", "serviceSlug": "hr", "remoteName": "aurevia_hr_ui_0_1_0", "defaultRouteId": "employee-list",
  "remoteEntry": "http://192.168.10.25:3000/remoteEntry.js", "exposedModule": "./plugin",
  "routeBasePath": "/hr", "semanticVersion": "0.2.0", "contractVersion": "1.0", "integrity": null,
  "resourceDefinitionMode": "HYBRID", "classification": "REAL",
  "mfManifestUrl": "http://192.168.10.25:3000/mf-manifest.json",
  "resourceManifestUrl": "http://192.168.10.25:3000/resource-manifest.json",
  "active": true, "sortOrder": 20 }
```
ویرایش: `PUT /internal/v1/registry/panels/{id}?version=<version>` با همان بدنه.

**E.2 همگام‌سازی MF Manifest** — `POST /internal/v1/registry/panels/{panelId}/frontend-manifests/sync` (بدنهٔ `{}`)
```json
{ "artifactId": "…", "status": "SUCCESS", "idempotent": false, "routesAdded": 5, "routesUpdated": 0,
  "routesRemoved": 0, "navigationAdded": 5, "runtimeChanged": true,
  "warnings": ["Administrator deployment settings override MF manifest runtime defaults"] }
```
artifact جدید همان‌جا **فعال** می‌شود.

**E.3 انتشار دستی artifact / فعال‌سازی نسخهٔ دیگر**
```
POST /internal/v1/registry/panels/{panelId}/artifacts
{ "artifactVersion": "0.2.1", "remoteEntryUrl": "http://192.168.10.25:3000/remoteEntry.js",
  "remoteName": "aurevia_hr_ui_0_1_0", "exposedModule": "./plugin", "contractVersion": "1.0",
  "integrity": "sha384-…", "manifest": "<JSON کامل MF Manifest به صورت رشته>" }
POST /internal/v1/registry/panels/{panelId}/artifacts/{artifactId}/activate?version=<panel version>
GET  /internal/v1/registry/panels/{panelId}/artifacts
```

**E.4 دریافت Resource Manifest** — `POST /internal/v1/registry/panels/{panelId}/resource-manifests/fetch` ← `201 { "draftId": "…", "workflowStatus": "DRAFT", ... }`
پیش‌نمایش: `GET /internal/v1/registry/panels/{panelId}/resource-manifests/drafts/{draftId}`
ورود دستی JSON: `POST /internal/v1/registry/panels/{panelId}/resource-manifests/drafts` با `{ "manifest": { ... } }`.

**E.5 انتشار** — `POST /internal/v1/registry/panels/{panelId}/resource-manifests/drafts/{draftId}/publish` ← `{ "workflowStatus": "PUBLISHED", "created": 9, "updated": 0, "deprecated": 2 }`
منابعی که مانیفست دیگر اعلام نمی‌کند **Deprecated** می‌شوند؛ قبل از انتشار Diff را ببینید.

---

## F. نمونهٔ مانیفست‌ها

**MF Manifest** (`mf-manifest.json`):
```json
{ "schemaVersion": "1.0",
  "microfrontend": { "key": "hr", "name": "Human Resources", "version": "0.2.0" },
  "runtime": { "remoteEntry": "http://192.168.10.25:3000/remoteEntry.js", "remoteName": "aurevia_hr_ui_0_1_0",
               "exposedModule": "./plugin", "contractVersion": "1.0", "apiBasePath": "/api/proxy/hr" },
  "defaultRouteKey": "employee-list",
  "routes": [
    { "key": "employee-list", "path": "personal", "requiredResource": "page:hr.employee.list", "requiredAction": "view", "title": "پرسنل" },
    { "key": "employee-details", "path": "personal/:id", "requiredResource": "page:hr.employee.detail", "requiredAction": "view", "title": "اطلاعات پرسنل" }
  ],
  "navigation": [
    { "key": "hr-people", "type": "PAGE", "routeKey": "employee-list", "title": "پرسنل", "icon": "team", "order": 10 }
  ] }
```
نباید کلید `resources` داشته باشد.

**Resource Manifest** (`resource-manifest.json`):
```json
{ "schemaVersion": "1.0", "application": { "key": "hr", "resourceKey": "application:aurevia/hr", "nameFa": "منابع انسانی", "nameEn": "Human Resources" },
  "resources": [
    { "resourceKey": "module:hr", "type": "MODULE", "parent": "application:aurevia/hr", "nameFa": "ماژول HR", "nameEn": "HR module", "actions": ["view"] },
    { "resourceKey": "page:hr.employee.list", "type": "PAGE", "parent": "module:hr", "nameFa": "فهرست پرسنل", "nameEn": "Employees", "actions": ["view","export"] },
    { "resourceKey": "page:hr.employee.detail", "type": "PAGE", "parent": "module:hr", "nameFa": "جزئیات پرسنل", "nameEn": "Employee detail", "actions": ["view","update"] }
  ] }
```
هیچ راز/آدرس استقرار در Resource Manifest نیست. مانیفست دستی (E.4 Draft با JSON) و مانیفست fetch‌شده به یک وضعیت رجیستری می‌رسند.

---

## G. عیب‌یابی

| علامت | معنا / اقدام |
|---|---|
| `Remote Entry hostname is invalid` / `Invalid Remote Entry URL` | URL بد؛ باید مطلق، بدون credential/query/fragment، با `.js` |
| `Remote Entry must use HTTPS` | `http` با `UI_ARTIFACT_ALLOW_HTTP=false` |
| `… private target is blocked by PRODUCTION_INTERNET policy` | حالت PRODUCTION_INTERNET؛ به UNRESTRICTED/INTERNAL_ENTERPRISE بروید |
| `… private target is outside UI_ARTIFACT_ALLOWED_PRIVATE_CIDRS` | CIDR را اضافه کنید |
| `… loopback target is blocked` | localhost خارج از DEVELOPMENT/UNRESTRICTED |
| `… target is blocked by the UI artifact network policy` | metadata/link-local/multicast — همیشه مسدود |
| `… hostname cannot be resolved` (DNS) | DNS از داخل کانتینر BFF/Authorization حل نمی‌شود؛ از IP یا نام قابل حل در Docker استفاده کنید |
| `manifest connection was refused or unreachable` / `manifest fetch timed out` / `manifest TLS handshake failed` | مقصد از داخل Docker قابل دسترسی نیست (فایروال، پورت، localhost به‌جای IP میزبان) |
| `manifest endpoint returned HTTP 404` / `must return JSON` | مسیر یا Content-Type فایل مانیفست |
| `MF manifest version is immutable…` | نسخهٔ `microfrontend.version` را بالا ببرید |
| `SRI is required for UI artifacts` / `Invalid UI artifact SRI` | بخش B |
| ثبت موفق ولی `502 MFE target rejected: …` در زمان اجرا | متغیرهای `UI_ARTIFACT_*` دو سرویس یکسان نیستند؛ هر دو را از `.env` بگیرید و هر دو را ری‌استارت کنید |
| `502 MFE artifact host could not be resolved (DNS)` / `connection was refused` / `504 timed out` | شبکهٔ BFF تا مقصد |
| chunk 404 | chunk باید زیر همان مسیر remoteEntry باشد (`publicPath: 'auto'`)؛ traversal/origin دیگر رد می‌شود |
| `502 … unexpected content type` | سرور MFE برای `.js` باید `application/javascript` برگرداند |
| میکرو در Shell نیست | artifact فعال ندارد (`NO_ACTIVE_ARTIFACT`)، `validation_status≠VALID`، Panel غیرفعال، یا کاربر مجوز هیچ route را ندارد → endpoint تشخیص مجوز |

---

## H. چک‌لیست تولید برای میکروی جدید

1. bundle با `publicPath: 'auto'`، `remoteName` یکتا، `remoteEntry.js` + `mf-manifest.json` + `resource-manifest.json` در یک مسیر، Content-Type درست.
2. HTTPS (یا `ALLOW_HTTP=true` آگاهانه)؛ SRI محاسبه و ثبت شود اگر `REQUIRE_INTEGRITY=true`.
3. حالت شبکهٔ مناسب و یکسان روی هر دو سرویس؛ مقصد از داخل Docker قابل حل/دسترسی باشد.
4. Panel → Sync MF Manifest → بررسی هشدارها → Fetch Resource Manifest → Diff → Publish.
5. مجوز حداقلی به کاربر آزمایشی؛ `/api/me/context` و `/api/mfe/<slug>/remoteEntry.js` را با نشست او ببینید.
6. `npm run e2e:mfe:external` (یا سناریوی معادل با آدرس واقعی) را قبل از اعلام آماده بودن اجرا کنید.
