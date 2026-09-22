# وضعیت جاری معماری و قابلیت‌های Aurevia

> وضعیت سند: مرجع canonical و جاری. آخرین ممیزی با source tree: ۲۰۲۶-۰۹-۱۷.
> اگر یک گزارش تاریخ‌دار، راهنمای قدیمی یا فایل evidence با این سند تعارض داشت، کد همان commit
> و این سند برای وضعیت جاری معتبرند؛ گزارش تاریخ‌دار فقط شاهد اجرای زمان خودش است.

این صفحه فهرست کوتاه و قابل‌آزمون وضعیت فعلی مخزن است. جزئیات هر حوزه در سند تخصصی لینک‌شده
آمده است، اما نام سرویس‌ها، مرزهای معماری، APIهای canonical و قابلیت‌های موجود باید با این صفحه
هماهنگ بمانند.

## منبع حقیقت و ترتیب حل تعارض

1. controller، DTO، validation، migration و configuration اجرایی همان commit؛
2. تست‌های Java/TypeScript/OpenFGA و verifierهای `tools/`؛
3. این صفحه و [معماری جاری](architecture.md)؛
4. راهنماهای living؛
5. گزارش‌ها و evidenceهای تاریخ‌دار که عمداً بازنویسی نمی‌شوند.

`docs:verify` inventoryهای زیر و لینک‌های محلی Markdown را مستقیماً با source tree مقایسه می‌کند.

## inventory اجرایی

<!-- sync:apps=mf-test-legacy,mf-test-sso,mfe-admin,mfe-finance,mfe-hr,mfe-reports,shell -->
<!-- sync:packages=authorization-sdk,contracts,http-client,i18n,sh-core-ui -->
<!-- sync:java-services=authorization-service,superapp-bff,test-legacy-service,test-sso-service,ui-artifact-security -->
<!-- sync:core-services=aurevia-bff,auth-db,authorization-service,nginx,openfga,openfga-db,openfga-migrate,redis -->
<!-- sync:latest-migration=V79 -->
<!-- sync:admin-version=0.6.0;admin-routes=19 -->
<!-- sync:swagger-specs=/api/v1/docs/admin/openapi,/api/v1/docs/authorization/openapi,/v3/api-docs -->
<!-- sync:resource-types=APPLICATION,MODULE,PAGE,UI_COMPONENT,FIELD,BUSINESS_RESOURCE,EXTERNAL_RESOURCE,API_RESOURCE,DATA_RESOURCE,DATA_GOVERNANCE_RESOURCE -->

| بخش | وضعیت جاری |
|---|---|
| Browser applications | `shell`، چهار MFE اصلی Admin/HR/Finance/Reports و دو MFE تست SSO/Legacy |
| shared packages | contracts، HTTP client با CSRF، authorization SDK، UI guard و i18n |
| Java | BFF، Authorization Service، policy مشترک UI artifact و دو سرویس تست اختیاری |
| داده | PostgreSQL کنترل‌پلین، OpenFGA برای graph تصمیم runtime، Redis برای session/token vault/cache |
| schema | ۷۷ migration ترتیبی؛ آخرین migration `V77__isolate_hr_user_superset_assets.sql` |
| Admin MFE | قرارداد `0.5.0` با ۱۸ route راهبری و navigation متناظر |
| Resource Catalog | ۱۰ نوع معتبر از `APPLICATION` تا `DATA_GOVERNANCE_RESOURCE` مطابق validation سرویس |

## topology و lifecycle

`infra/docker-compose/compose.yml` هستهٔ محلی را اجرا می‌کند: دیتابیس‌های Authorization و
OpenFGA، Redis، Authorization Service، BFF، Nginx، Operation Gateway و mockهای عملیاتی.
این فایل هیچ Keycloak، LDAP، MFE server یا Superset runtime ایجاد نمی‌کند.

اجزای اختیاری lifecycle مستقل دارند:

- `compose.identity-demo.yml`: Keycloak و دیتابیس آن؛ Samba AD فقط با profile `directory`؛
- `compose.mfe-demo.yml`: چهار artifact server مستقل روی پورت‌های 3001 تا 3004؛
- `compose.superset-demo.yml`: دموی Superset مستقل از Core؛
- `compose.e2e-auth*.yml` و `compose.pages-e2e.yml`: fixtureهای آزمون، نه topology تولید؛
- `compose.superset-native-core.yml`: فقط policy/TLS اتصال به Superset خارج از Docker.

مرورگر فقط با Nginx/BFF هم‌مبدأ صحبت می‌کند. BFF مقصد MFE، route عملیاتی، روش احراز هویت
و Superset را از Registry می‌گیرد. MFEها و Superset lifecycle وابسته به startup هسته ندارند.

## قراردادهای browser و Swagger

| قرارداد | وضعیت |
|---|---|
| `GET /api/me/context` | قرارداد canonical و single-fetch شِل؛ identity، مجوزها، `uiCatalog`، route، navigation و integrationهای مجاز |
| `GET /api/v1/me/manifest` | alias سازگاری برای مصرف‌کننده‌های قدیمی؛ شِل جاری از آن استفاده نمی‌کند |
| `GET /api/ui/catalog` | projection فقط UI catalog برای مصرف‌کننده تخصصی |
| `/api/mfe/{moduleKey}/...` | proxy امن manifest و artifact؛ URL واقعی Registry به browser افشا/مصرف مستقیم نمی‌شود |
| `/api/v1/admin/**` | façade عمومی کنترل‌پلین؛ به `/internal/v1/registry/**` نگاشت می‌شود |
| `/{panelSlug}/**` | proxy پویا برای routeهای ثبت‌شدهٔ Modern یا Legacy |
| `/api/v1/superset/**` و `/api/v1/superset-instances/**` | tunnelهای سازگاری Superset |
| `/api/integrations/superset/**` | مسیر canonical integration ثبت‌شدهٔ Superset |

Swagger در profile غیر production سه قرارداد دارد: BFF عمومی، Admin عمومی و Authorization
Service داخلی. قرارداد Admin snapshot دستی نیست؛ در runtime از OpenAPI سرویس Authorization
ساخته و پیشوند Registry به URL واقعی BFF تبدیل می‌شود. در profile `prod`، UI و JSON Swagger
غیرفعال‌اند. جزئیات در [راهنمای Swagger](swagger-openapi-fa.md) است.

## قابلیت‌های پیاده‌سازی‌شده

| حوزه | قابلیت موجود در کد |
|---|---|
| Login و identity | OIDC Authorization Code، IdP پویا از Registry، routing بر پایه code/tenant/domain، canonical issuer+subject، external identity alias و login sync |
| session و token | cookie مات `AUREVIA_SESSION`، Spring Session روی Redis، AES-GCM token vault، refresh هماهنگ، پاک‌سازی logout؛ بدون token در JavaScript |
| OU، گروه و نقش | sync اختیاری LDAP، OU خواندنی، Access Group با EXACT/SUBTREE و ANY_OF/ALL_OF، membership قابل توضیح، role و assignment |
| authorization | resource/action/grant، inheritance، check تکی و batch، structured policy/obligation، default deny و cache کوتاه‌عمر |
| OpenFGA | تنها writer از Authorization Service، transactional outbox، retry/dead-letter، startup reconcile و drift verification |
| MFE governance | Panel registry، MF Manifest مستقل، Resource Manifest مستقل، fetch/draft/diff/publish، artifact revision immutable، navigation overlay و effective catalog |
| operational proxy | Target شبکه‌ای و Route/Auth مستقل؛ تعداد نامحدود Route ترکیبی Forward/Legacy برای هر MFE، longest-prefix/priority resolution، محدودیت method/path/size/timeout و Legacy token cache |
| Superset | instance/integration registry، asset/grant، public→operation mapping سازگار، direct BFF connector با SSRF policy و TLS/mTLS اختیاری، health و same-origin tunnel |
| observability | correlation ID، API log و audit log پاک‌سازی‌شده، retention، outbox state و evidence runnerهای محلی |

## ۱۸ route پنل Admin

`operator-guide`، چهار صفحه OU (`ous`، `groups`، `applications`، `explain`)، Access Studio،
Microfrontend registry، سه صفحه Proxy (`targets`، `routes`، `operations`)، Outbound Connection،
Outbound Auth، Integration Test، Superset Instances، Identity/Role، API Logs، Audit Logs و
Superset Assets. فهرست دقیق path/resource/action از `apps/mfe-admin/mf-manifest.json` می‌آید.

## مرزهای Production

کد profile تولید fail-closed است: Swagger/demo data و mutation توسعه خاموش، HTTP مقصد ممنوع،
integrity artifact الزامی، ارتباط BFF↔Authorization و BFF↔Gateway با mTLS و TLS سرویس
Authorization اجباری است. بااین‌حال فایل Compose پایه یک محیط local/demo است و به‌تنهایی
deployment تولید نیست. secret manager، certificate issuance/rotation، HA/PITR دیتابیس، Redis
HA، image registry، WAF/rate limit و alerting عملیاتی باید در پلتفرم مقصد تأمین و با runbookها
آزمون شوند.

## فرمان‌های نگهداری

```powershell
npm run docs:verify
npm run typecheck
npm test
./mvnw.cmd test
docker compose --env-file .env -f infra/docker-compose/compose.yml config --quiet
```

برای آزمون runtime پس از روشن بودن Docker و آماده‌بودن IdP از `npm run swagger:verify`،
`npm run infra:verify` و `npm run infra:verify:token-proxy` استفاده کنید.
