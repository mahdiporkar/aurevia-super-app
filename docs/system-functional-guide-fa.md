# راهنمای عملکردی سامانه (Functional Guide)

این سند توضیح می‌دهد پلتفرم **همان‌طور که امروز کار می‌کند** چگونه استفاده می‌شود. برای جزئیات هر حوزه به اسناد تخصصی ارجاع داده شده است:
- مجوزها: `docs/permission-definition-and-operation-fa.md`
- ثبت میکروفرانت: `docs/microfrontend-registration-and-lifecycle-fa.md`
- مسیرهای پراکسی (Forward/Legacy): `docs/proxy-routing-legacy-forward-fa.md`

هر بخش با «کجا» (صفحهٔ Admin و endpoint) و «چطور تست شده» تمام می‌شود.

---

## ۱. ثبت میکروفرانت (MFE)

**کجا:** مدیریت → میکروفرانت‌ها ← «تعریف میکروفرانت». API: `POST /internal/v1/registry/panels` (از طریق BFF: `POST /api/v1/admin/panels`).

فیلدها: `code`, `slug`, نام‌ها, `routeBasePath`, `serviceSlug`, `remoteEntry` (URL مطلق `.js` روی هر آدرس شبکه‌ای قابل دسترسی از BFF)، `remoteName` (باید با bundle یکی باشد)، `exposedModule`, `contractVersion=1.0`, `semanticVersion`, `integrity` (اختیاری)، `mfManifestUrl`, `resourceManifestUrl`, `resourceDefinitionMode` (`MANIFEST|MANUAL|HYBRID`)، `classification` (`REAL|DEMO`)، `active`.

آدرس MFE پیکربندی است، نه کد: `http://10.20.1.15:3000/remoteEntry.js`، `http://hr.internal.company:3000/remoteEntry.js`، `https://hr-ui.internal.company/remoteEntry.js` همه مجازند به شرط سازگاری با حالت شبکه (بخش ۳ سند MFE). هیچ rebuild یا تغییر Nginx لازم نیست؛ مرورگر همیشه `/api/mfe/<slug>/remoteEntry.js` را می‌گیرد و BFF به آدرس واقعی می‌رود.

**اعتبارسنجی پس از ثبت:** «همگام‌سازی MF Manifest» = دریافت + اعتبارسنجی + ساخت و فعال‌سازی Artifact. خطاها دقیق‌اند: DNS، اتصال، TLS، HTTP 404، Content-Type غیر JSON، ارجاع به منبع ناموجود، نسخهٔ تکراری با محتوای متفاوت، SRI.

**تست:** `npm run e2e:mfe:external` (MFE روی IP میزبان خارج از Docker؛ 11 مورد شامل رندر در Shell) و `services/ui-artifact-security` (62 تست ماتریس شبکه).

## ۲. Resource Manifest — ورود خودکار درخت منابع

**کجا:** میکروفرانت → «دریافت Resource Manifest» → Draft با Diff → «انتشار». API: `POST /panels/{id}/resource-manifests/fetch` → `GET .../drafts/{draftId}` → `POST .../drafts/{draftId}/publish`. ورود دستی JSON: `POST /panels/{id}/resource-manifests/drafts` با بدنهٔ مانیفست.

انتشار منابع جدید را می‌سازد، موجودها را به‌روز می‌کند و **منابعی که دیگر اعلام نشده‌اند را Deprecated می‌کند** — Diff را قبل از انتشار ببینید.

**تست:** `PermissionLifecycleIntegrationTest`, `ResourceManifestWorkflowTest`, سناریوی `enterprise-hr` (IMPORT-RESOURCE-MANIFEST).

## ۳. ساخت دستی درخت منابع

**کجا:** مدیریت → درخت منابع → «منبع جدید». API: `POST /internal/v1/registry/resources` با `resourceKey` (پیشوند نوع: `application:`, `module:`, `page:`, `component:`, `field:`, `business:`, `external_resource:`, `api:`, `data:`, `governance:`), `type`, `parentId`, `panelId`, نام‌ها؛ سپس اتصال Action: `PUT /resources/{id}/actions/{actionId}`.

سلسله‌مراتب مجاز: APPLICATION ریشه است؛ MODULE زیر APPLICATION؛ PAGE زیر MODULE؛ UI_COMPONENT زیر PAGE/UI_COMPONENT؛ … . منبع دستی و منبع مانیفست در مجوز **هیچ تفاوتی** ندارند (هر دو `resource` + `resource_action` + tuple والد).

**تست:** `manuallyCreatedMicroFrontendAndResourceTreeBehaveLikeAnImportedOne` (PG+OpenFGA واقعی) و MANUAL-RESOURCE در `enterprise-hr`.

## ۴. MF Manifest در برابر Resource Manifest

| | Resource Manifest | MF Manifest |
|---|---|---|
| پاسخ به | «چه چیزی وجود دارد و قابل مجوزدهی است؟» | «UI مجاز چطور ارائه می‌شود؟» |
| مالک | منابع، سلسله‌مراتب، Actionها | routes, navigation, runtime (remoteEntry, remoteName, exposedModule, apiBasePath) |
| ارجاع | — | هر route با `requiredResource`/`requiredAction` به کلید یک منبع موجود اشاره می‌کند؛ ارجاع ناموجود در sync رد می‌شود (`references undeclared resource/action`) |

مقادیر runtime داخل MF Manifest اطلاعاتی‌اند؛ تنظیمات استقرار Panel همیشه مؤثر است (هشدار در نتیجهٔ sync). نسخهٔ Artifact تغییرناپذیر است.

## ۵. اعطای مجوز

**کجا:** Access Studio. API یکسان برای همه: `POST /internal/v1/registry/grants` با `subjectType` ∈ `USER|GROUP|ACCESS_GROUP|ROLE`. سوژه باید وجود داشته باشد (در غیر این صورت `404 … subject does not exist`). Action باید به منبع متصل باشد (`Action is not attached to an active resource`) و نگاشت relation از `AuthorizationSemanticsRegistry` می‌آید: `view→viewer`, `create→creator`, `update→editor`, `delete→deleter`, `export/share/manage→manager` (برای APPLICATION همه غیر از view به `manager`).

- **کاربر:** `subjectType: USER`, `subjectId: app_user.id`.
- **گروه دایرکتوری:** `GROUP` + `directory_group.id`؛ عضویت در هر ورود از claim `groups` همگام می‌شود (شناسهٔ پایدار: `(issuer, external_id)`؛ claim مسیر مثل `/HR` هم به همان گروه نگاشت می‌شود).
- **گروه دسترسی (OU):** `ACCESS_GROUP` + `access_group.id`؛ عضویت محاسبه‌شده در `effective_group_membership`.
- **نقش:** `ROLE` + `application_role.id`؛ انتساب نقش با `POST /role-assignments` (`USER|DIRECTORY_GROUP|ACCESS_GROUP`)؛ غیرفعال کردن نقش همهٔ اثرش را برمی‌دارد.

**تست:** `PermissionLifecycleIntegrationTest` (15 سناریو روی PG/OpenFGA/Redis واقعی: چهار نوع سوژه، سه مسیر نقش، غیرفعال/فعال، لغو، انقضا، RETRYING/FAILED، reconciliation، subjectClaim سفارشی، Superset دو کاربره، MFE دستی).

## ۶. پروجکشن OpenFGA و وضعیت‌ها

Grant و رویداد outbox در یک تراکنش ثبت می‌شوند؛ worker (هر 5s) tuple را در OpenFGA می‌نویسد. وضعیت در Access Studio: `PENDING → APPLIED` یا `RETRYING → FAILED` با `projection_error`. فقط `APPLIED` مؤثر است. reconciliation در استارت اختلاف را می‌بندد؛ انقضا با `ExpirationSweeper` (30s) بدون ری‌استارت اعمال می‌شود. تشخیص: `GET /internal/v1/registry/diagnostics/authorization?issuer&subject&resource&action`.

## ۷. ساخت `/api/me/context`

BFF → `GET authz:/internal/v1/subjects/{subject}/manifest`: (۱) هویت canonical از `external_identity`؛ (۲) batch-check همهٔ (منبع, action)های فعال در OpenFGA → `permissions`؛ (۳) درخت `resources` = منابع مجاز + اجداد؛ (۴) برای هر Panel فعال با Artifact معتبر: routeهای MF Manifest که permission آن‌ها هست → `uiCatalog.modules[].routes/navigation`؛ اگر هیچ route مجاز نباشد ولی کاربر مجوزی در زیرمجموعهٔ `panel.discovery_resource_key` داشته باشد (مثلاً یک داشبورد Superset)، route ورودی با همان مجوز ظاهر می‌شود؛ (۵) `panels` = پنل‌هایی که ماژول دارند یا `can_view` روی `application:aurevia/<slug>`. هیچ Application grant اضافی لازم نیست: **یک Page مجاز، میکروی مالکش را نمایان می‌کند** و Pageهای هم‌سطح پنهان می‌مانند.

## ۸. Superset

**کجا:** مدیریت → Superset → Instanceها (PUBLIC/OPERATION) و دارایی‌ها. ثبت دارایی: `POST /internal/v1/registry/superset-assets` `{externalId, assetType: DASHBOARD|CHART, title, urlPath, published, instanceCode}` → منبع `external_resource:superset/<instance>/dashboard/<id>` ساخته می‌شود. اعطا: `POST /superset-assets/{assetId}/grants` `{subjectType, subjectId, level: VIEW|EDIT|MANAGE}`.

نتیجه: کاربر با یک داشبورد، Reports را می‌بیند (کشف از طریق `discovery_resource_key`) و **فقط همان داشبورد** را: فهرست `GET /api/v1/reports` سمت سرور فیلتر می‌شود؛ باز کردن هر داشبورد/چارت دوباره با `superset-access` چک می‌شود؛ پراکسی Superset فقط دارایی مجاز را عبور می‌دهد. هیچ `application:aurevia/reports` یا نقش گسترده لازم نیست.

**تست:** `twoUsersWithDifferentDashboardsNeverSeeEachOthersAssetsAndRevokeRemovesReports` (کاربر A/B، داشبورد A/B، چارت A، لغو).

## ۹. Service Target

**کجا:** مدیریت → پراکسی → Targetها. `code`, `name`, `gatewayBaseUrl` (origin تأییدشدهٔ BFF)، `upstreamBasePath`, `healthCheckPath`, timeoutها, `maxResponseSize`, `active`. «تست اتصال» health را می‌زند. **Target حالت احراز هویت ندارد** (از V73 اختیاری و بلااستفاده)؛ حالت روی Route است.

## ۱۰. Route (Forward)

**کجا:** مدیریت → پراکسی → Routeها. `panelId`, `serviceTargetId`, **`outboundAuthProfileId` (Forward یا Legacy)**, `serviceSlug`, `pathPrefix` (زیر `/api/...` یا `/<x>-micro/...`), `stripPrefix` (≤ segmentهای prefix), `rewritePattern/Replacement`, `priority`, `allowedMethods`, `retryEnabled` (فقط GET/HEAD/OPTIONS), `active`. «اعتبارسنجی/پیش‌نمایش/Resolve test» قبل از ذخیره.

Forward: BFF توکن Keycloak کاربر را از Token Vault می‌خواند (سمت سرور) و به Gateway می‌فرستد: `Authorization: Bearer <user token>`؛ 401 مقصد ⇒ یک تازه‌سازی.

## ۱۱. Route Operations

**کجا:** Route → Operations. `httpMethod`, `pathPattern` نسبی (`/employees`, `/employees/{id}`, `/files/**`, `/`), `resourceKey`+`actionKey` (باید متصل باشد), `authorizationRequired`, `maxBodyBytes`, `active`. قواعد پیکربندی: متد باید در `allowedMethods` Route باشد (`METHOD_NOT_ALLOWED_BY_ROUTE`)، الگوی تکراری (`DUPLICATE_OPERATION`)، هم‌پوشانی با specificity برابر (`AMBIGUOUS_OPERATION`)؛ کوچک کردن `allowedMethods` با Operation فعال ناسازگار رد می‌شود. همان `RoutePathPolicy`/`RouteResolutionService` در validate، match-test، resolve-test و runtime استفاده می‌شود.

## ۱۲. Legacy: Outbound Connection → Auth Profile → Route

1. **Outbound Connection** (`POST /outbound-connections`): `connectionRef` (مثل `connection://legacy/hr`), `baseUrl` سرویس توکن, `tlsRequired`.
2. **Secret**: مقدار از API عبور نمی‌کند. توسعه/تست: `FileSecretResolver` — فایل JSON `{ "username": "...", "password": "...", "version": "v1" }` (یا `client_id/client_secret`) زیر ریشهٔ پیکربندی‌شده (`.tmp/e2e-auth/secrets/e2e/legacy.json` ↔ `secret://e2e/legacy`). این سازوکار محلی برای سخت‌سازی بعدی جدا شده است.
3. **Auth Profile** (`POST /outbound-auth-profiles`): `authMode: LEGACY_SERVICE_TOKEN`, `tokenConnectionRef`, `tokenEndpointPath` (مثل `/auth/token`), `requestFormat` (`FORM_URLENCODED` برای username/password یا client credentials فرم؛ `JSON` برای بدنهٔ JSON), `credentialSecretRef`, `scope`, `audience`, pointerهای پاسخ (`/access_token`, `/data/accessToken`, `/result/token`, …), `expiresInResponsePointer`, `tokenTypeResponsePointer`, `authorizationScheme` (`Bearer`), `credentialTransport: INTERNAL_LEGACY_HEADER`, `expirySkewSeconds`, timeoutها.
4. **تست پروفایل:** `POST /api/v1/admin/outbound-auth-profiles/{id}/connection-test` (اتصال) و `.../token-test` (دریافت واقعی توکن بدون کش، بدون برگرداندن مقدار)؛ `.../invalidate-token` برای ابطال کش.
5. **Route** با `outboundAuthProfileId` این پروفایل.

جریان: Route → Operation → OpenFGA (**قبل از هر Secret/توکن**) → توکن Legacy از کش Redis یا دریافت (قفل همزمانی) → `Authorization: Bearer <user>` + `X-Internal-Legacy-Authorization: Bearer <legacy>` به Gateway → Gateway توکن Legacy را جایگزین `Authorization` می‌کند و هدر داخلی را حذف می‌کند. 401 مقصد ⇒ ابطال کش + یک دریافت مجدد. خطای endpoint توکن ⇒ `502 Legacy authentication unavailable` / کد `TOKEN_ACQUISITION_FAILED` در تست.

## ۱۳. چرا Route نسخهٔ Semantic ندارد

Route/Target/Profile پیکربندی عملیاتی هستند؛ فیلد `version` آن‌ها فقط **قفل خوش‌بینانه** است (در هر ویرایش +۱ می‌شود و باید در `?version=` فرستاده شود). تغییر Route/Operation/Profile بلافاصله در درخواست بعدی مؤثر است — بدون ری‌استارت، بدون rebuild و بدون تغییر نسخهٔ Artifact. نسخهٔ Semantic فقط برای Artifact میکروفرانت است.

## ۱۴. عیب‌یابی

| علامت | بررسی |
|---|---|
| 403 روی `/api/proxy/...` | `resolve-test` → `resourceObject`/`actionKey` عملیات → endpoint تشخیص مجوز برای همان subject/resource/action (`NO_CONTRIBUTING_GRANT`, `PROJECTION_*`, وضعیت نقش/گروه) |
| 404 `Proxy route resolution rejected` | Route/Operation/Panel/Target/Profile فعال نیست، متد در `allowedMethods` نیست، الگو تطبیق نمی‌کند، یا prefix زیر namespace Nginx نیست |
| 409 Ambiguous | دو Operation با specificity برابر (از V73 در زمان ذخیره رد می‌شود؛ رکوردهای قدیمی را اصلاح کنید) |
| Grant ثبت‌شده ولی مؤثر نیست | `projection_status` ≠ `APPLIED` → `projection_error`; متریک `aurevia_outbox_dead_letter`; endpoint تشخیص |
| توکن Legacy | `token-test`; `502 Legacy authentication unavailable`; لاگ BFF `LegacyTokenManager` (acquire failure), Secret موجود؟ Connection فعال؟ pointer پاسخ درست؟ |
| MFE هست ولی در Context نیست | Artifact فعال و `VALID`؟ Panel فعال؟ `classification=DEMO` با `DEMO_DATA_ENABLED=false`؟ کاربر مجوز حداقل یک route یا یک منبع زیر `discovery_resource_key` دارد؟ endpoint تشخیص → `panel.readiness` |
| ورود با 500 | لاگ BFF: `login-sync` 409 ⇒ تداخل گروه دایرکتوری؛ لاگ `auth-db` جزئیات constraint را نشان می‌دهد |
| Docker: تغییر کد اعمال نشده | overlay `compose.e2e-auth-core.yml` فایل `target/*.jar` محلی را mount می‌کند؛ قبل از `core:up` باید `mvn package` کنید |
