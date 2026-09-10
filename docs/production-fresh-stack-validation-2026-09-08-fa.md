# گزارش اعتبارسنجی Production از دیتابیس خالی — ۲۰۲۶-۰۹-۰۸

## حکم نهایی

نصب کاملاً تازه و جریان‌های اصلی سیستم **موفق** هستند، اما Compose فعلی هنوز برای استقرار
Production تأیید نمی‌شود. علت، شکست تست کاربردی نیست؛ چند تنظیم صریحاً توسعه‌ای و چند کنترل
زیرساختی حل‌نشده در بخش «موانع انتشار» ثبت شده‌اند. بنابراین این گزارش بین «سلامت نرم‌افزار روی
fresh install» و «آمادگی استقرار Production» تفکیک قائل می‌شود.

## روش اجرای آزمون

- پروژه Compose مجزا با نام `aurevia_prod_e2e` ساخته شد؛ هیچ volume متعلق به محیط قبلی حذف نشد.
- PostgreSQL احراز هویت، Keycloak، OpenFGA، Redis و PostgreSQL عملیاتی از volumeهای خالی آغاز شدند.
- OpenFGA Store و Authorization Model جدید ساخته شد.
- تمام migrationهای Flyway تا `V53` روی دیتابیس خالی اجرا شدند.
- Shell، چهار MFE، BFF، Authorization Service، Keycloak، OpenFGA، Gateway و Superset اجرا شدند.
- پس از آزمون، فقط پنج volume دارای label پروژه آزمایشی حذف شد؛ `.env` اصلی با SHA-256 قبلی
  بازگردانده و stack اصلی دوباره healthy شد.

## نتایج قابل بازتولید

| Gate | نتیجه | مدرک/دامنه |
|---|---:|---|
| `npm audit --audit-level=high` | Pass | صفر vulnerability گزارش‌شده |
| `mvnw.cmd -B clean verify` | Pass | ۱۳۶ تست Backend؛ صفر failure/error |
| lint، typecheck، Node test و build | Pass | ۳۶ تست Frontend/SDK/E2E و build همه workspaceها |
| Fresh Flyway | Pass | تمام migrationها تا V53 |
| OpenFGA projection | Pass | expected=actual و drift صفر |
| Outbox | Pass | pending و dead-letter صفر پس از bootstrap |
| ورود واقعی | Pass | کاربر `administrator` و cookie مبهم `AUREVIA_SESSION` |
| عدم افشای token | Pass | token در session سمت کلاینت و پاسخ‌ها وجود نداشت |
| Admin manifest | Pass | قرارداد 1.0، artifact نسخه 0.4.0، تعداد ۱۸ route نسبی |
| سطوح دسترسی | Pass | دسترسی administrator به Admin و مدیریت ریشه و منابع Proxy |
| Manifest governance | Pass | Fetch سمت سرور، Diff/Draft/Preview و عدم تغییر tree پیش از Publish |
| Proxy Legacy | Pass | cache miss و cache hit با `LEGACY_SERVICE_TOKEN` |
| Proxy OAuth2 | Pass | تزریق `KEYCLOAK_ACCESS_TOKEN` و پاسخ ۲۰۰ |
| Superset | Pass کاربردی | catalog پاسخ ۲۰۰ و runtime عملیاتی قابل دسترس |
| Deep link صفحات Admin | Pass قراردادی/HTTP | صفحه مستقل و پاسخ HTML برای root و ۱۸ مسیر |
| OpenAPI | Pass | ۴۲ operation در BFF، ۱۱۴ operation در Authorization و نمونه فارسی |

فرمان‌های بازتولید اصلی:

```powershell
.\mvnw.cmd -B clean verify
npm run lint
npm run typecheck
npm test
npm run build
npm audit --audit-level=high
npm run openfga:bootstrap
npm run infra:up
npm run infra:verify
npm run infra:verify:token-proxy
```

## ایرادهای کشف‌شده و اصلاح‌شده

### ۱. Race نصب تازه میان Outbox و OpenFGA reconciliation

در اولین اجرای واقعی، scheduler هم‌زمان با startup reconciliation در حال replay بود و برنامه با
۳۶ tuple غیرمنتظره متوقف می‌شد. startup اکنون قبل از repair/verify، outbox bootstrap را به‌صورت
محدود و fail-closed تخلیه می‌کند و اگر pending/dead-letter باقی بماند startup شکست می‌خورد.
آزمون regression برای حالت drain موفق و outbox غیرقابل‌تخلیه اضافه شد.

### ۲. SQL ناسازگار با PostgreSQL در ساخت درخت Manifest

Fetch سمت سرور با خطای `resource_key must appear in GROUP BY` پاسخ ۵۰۰ می‌داد. تجمیع actionها
به lateral subquery مستقل منتقل شد تا هر resource دقیقاً یک ردیف داشته باشد و query به استنتاج
کلید اصلی در recursive CTE وابسته نباشد. همان جریان واقعی Fetch/Diff/Draft/Preview پس از rebuild
روی PostgreSQL تازه با موفقیت اجرا شد.

### ۳. تحمل‌پذیری bootstrap OpenFGA

پنجره انتظار health از ۴۰ به ۶۰ تلاش افزایش یافت و هر fetch timeout دوثانیه‌ای دارد؛ در نتیجه
کندی Docker به hang نامحدود یا شکست زودرس تبدیل نمی‌شود.

## پوشش فرم‌ها و حالت‌ها

قرارداد E2E موجود، حضور ۱۸ صفحه مستقل Admin و ۱۱۸ فیلد تعریف‌شده را کنترل می‌کند. تست‌های این
اجرا همچنین مسیرهای امنیتی و integration بالا را به‌صورت runtime پوشش دادند. با این حال عبارت
«تک‌تک حالت‌های ممکن» از نظر ریاضی نامتناهی/ترکیبی است و نباید به‌عنوان ادعای کیفی استفاده شود.
برای هر فیلد باید حداقل ماتریس زیر در CI مرورگر واقعی اجرا شود:

1. مقدار معتبر حداقل، معمول و حداکثر؛
2. خالی/null، whitespace، طول بیشتر از حد، Unicode/RTL و کاراکترهای کنترلی؛
3. duplicate، reference ناموجود، optimistic-concurrency و retry؛
4. مجاز و غیرمجاز برای هر نقش، tenant و مالک resource؛
5. خطای 4xx، 5xx، timeout، شبکه قطع، refresh و ارسال دوباره؛
6. Create، Read، Update، Delete/Deactivate و rollback/audit متناظر.

در این اجرا browser runner داخلی به‌دلیل نبود sandbox policy قابل راه‌اندازی نبود. بنابراین
پوشش ۱۱۸ فیلد فعلاً **contract-level** است، نه شواهد click/type واقعی برای تک‌تک boundaryها.
تا اضافه‌شدن Playwright/Cypress مستقل به CI، این مورد یک شکاف release gate محسوب می‌شود.

## موانع انتشار Production

موارد زیر باید قبل از هر تأیید Production بسته شوند:

- `SPRING_PROFILES_ACTIVE=dev` و Keycloak با `start-dev`؛
- اجازه HTTP داخلی و نبود TLS/mTLS و مدیریت certificate در این Compose؛
- `LEGACY_LOCAL_SECRETS_ENABLED=true` و token-evidence logging؛
- secretهای فایل `.env` به‌جای secret manager و rotation کنترل‌شده؛
- هشدار صریح Superset برای CSP تعریف‌نشده؛
- rate limiter حافظه‌ای Superset به‌جای backend مشترک مانند Redis؛
- فعال بودن seed/demo catalog و امکان `SUPERSET_LOAD_EXAMPLES=yes`؛
- bundleهای بزرگ Webpack (Admin/Shell حدود 1.44 MiB vendor و Finance حدود 1 MiB)؛
- نبود تست مرورگری field-level در CI و نبود evidence برای accessibility/visual regression؛
- OpenAPI در profile فعلی فعال است و باید exposure آن در Production تصمیم‌گیری و محدود شود.

## معیار تأیید نهایی انتشار

انتشار تنها زمانی قابل امضا است که Compose/Helm مخصوص Production هیچ flag توسعه‌ای نداشته باشد،
secretها خارج از repository مدیریت شوند، TLS/CSP/rate-limit فعال باشد، seed نمونه خاموش باشد،
browser matrix برای ۱۱۸ فیلد سبز شود و سپس هر دو فرمان `infra:verify` و
`infra:verify:token-proxy` روی محیط ephemeral صفر اجرا و artifact شواهد در CI نگهداری شود.

## تکمیل gateهای Release نسخه 0.1.0

در ادامه همان ممیزی، موارد زیر نیز در ۲۰۲۶-۰۹-۰۸ اجرا و ثبت شدند:

- GitHub Pages که در repository غیرفعال بود با `build_type=workflow` فعال شد؛ workflow شماره
  `34168464015` موفق و root عمومی، `styles.css` و `app.js` همگی ۲۰۰ شدند.
- `npm run release:verify:runtime` اضافه شد: شش header/رفتار امنیتی، fail-closed بدون session،
  تمام ۱۸ deep-link و assetهای Live را کنترل می‌کند.
- دو اجرای probe هم‌زمان ۲۰۰ درخواستی، هر دو صفر failure داشتند؛ P95 به‌ترتیب حدود ۱۷۱۰ و
  ۱۳۲۴ میلی‌ثانیه بود. این یک release smoke محدود است و جای load/soak ظرفیت‌سنجی را نمی‌گیرد.
- Authorization Service عمداً restart شد، پس از ۳۵ ثانیه healthy شد و سپس migration، outbox،
  OpenFGA drift و دسترسی administrator دوباره موفق بودند.
- جریان کامل token proxy دوباره Login، session بدون token، Manifest، Legacy miss/hit، OAuth2،
  Superset و Draft/Preview را با موفقیت تأیید کرد.
- نسخه Maven از `0.1.0-SNAPSHOT` به `0.1.0` تبدیل شد و workflow tag-based برای تولید archive،
  JAR، npm CycloneDX SBOM، SHA-256، build provenance و GitHub Release اضافه شد.

### وضعیت انتشار

کد و artifactهای نسخه `0.1.0` یک **release source قابل تکرار** هستند. tag نهایی Production نباید
پیش از ارائه certificate/secret manager و شواهد HA/PITR/restore محیط مقصد ایجاد شود. هشدارهای
bundle بزرگ Webpack نیز همچنان debt کارایی‌اند؛ build موفق است اما performance budget نهایی نیست.
