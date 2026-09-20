# قابلیت‌های فعلی (آنچه امروز واقعاً کار می‌کند)

فقط قابلیت‌هایی فهرست شده‌اند که با آزمون اجراشده تأیید شده‌اند. ستون «تست» نام کلاس/اسکریپت و آخرین نتیجهٔ اجرا (2026-09-20) است.

| قابلیت | چه می‌کند | کجا پیکربندی می‌شود | سرویس مالک | تست (نتیجه) |
|---|---|---|---|---|
| ثبت میکروفرانت با آدرس شبکه‌ای دلخواه | Panel با remoteEntry/manifest روی هر host (loopback/LAN/public/IPv6/DNS) طبق حالت شبکه | Admin → میکروفرانت‌ها؛ `POST /internal/v1/registry/panels` | Authorization Service | `UiArtifactNetworkMatrixTest` 54، `tools/mfe-external-e2e.mjs` 11/11 |
| توافق سیاست شبکه ثبت/اجرا | همان `UiArtifactUriPolicy` در ثبت و در BFF؛ `UI_ARTIFACT_*` از `.env` به هر دو سرویس | `.env`, `compose.yml` | ui-artifact-security | ماتریس «fetch agrees with registration» |
| همگام‌سازی MF Manifest و فعال‌سازی Artifact | fetch، اعتبارسنجی (ارجاع منابع، نسخهٔ تغییرناپذیر)، ساخت و فعال‌سازی | `POST /panels/{id}/frontend-manifests/sync`, `/artifacts`, `/activate` | Authorization Service | `UiPluginRegistryServiceTest` 8، `mfe-external-e2e` SYNC/ARTIFACT-ACTIVE |
| انتشار دستی Artifact (بدون fetch) | JSON مانیفست دستی → Artifact معتبر | `POST /panels/{id}/artifacts` | Authorization Service | `manuallyCreatedMicroFrontendAndResourceTreeBehaveLikeAnImportedOne` |
| ورود Resource Manifest (fetch یا JSON) → Draft → Diff → Publish | ساخت/به‌روزرسانی/Deprecate منابع | `/panels/{id}/resource-manifests/*` | Authorization Service | `ResourceManifestWorkflowTest` 7، `enterprise-hr-e2e` IMPORT |
| ساخت دستی درخت منابع + اتصال Action | APPLICATION/MODULE/PAGE/… با اعتبارسنجی والد | `POST /resources`, `PUT /resources/{id}/actions/{actionId}` | Authorization Service | `AccessAdministrationServiceTest` 11، `enterprise-hr-e2e` MANUAL-RESOURCE |
| اعطای مجوز به USER/GROUP/ACCESS_GROUP/ROLE با اعتبارسنجی سوژه | grant قابل‌پروجکشن، سوژهٔ ناموجود رد می‌شود | Access Studio؛ `POST /grants` | Authorization Service | `AccessAdministrationServiceTest`، `PermissionLifecycleIntegrationTest` |
| پروجکشن Outbox → OpenFGA با وضعیت‌های PENDING/RETRYING/APPLIED/FAILED | قابل مشاهده در Access Studio + متریک‌ها | خودکار | Authorization Service | Lifecycle: `openFgaOutage…`, `aPermanentlyInvalidProjection…` |
| Reconciliation در استارت | حذف tuple کهنه، حفظ tupleهای معتبر، idempotent | `OPENFGA_RECONCILE_ON_STARTUP` | Authorization Service | `OpenFgaReconciliationRepositoryIntegrationTest` 11 |
| انقضای Grant/انتساب بدون ری‌استارت | `ExpirationSweeper` | `aurevia.expiration.sweep-interval-ms` | Authorization Service | Lifecycle: `expiredGrants…` |
| نقش‌ها: انتساب به کاربر/گروه/گروه دسترسی، غیرفعال/فعال | | `POST /roles`, `/role-assignments`, `PATCH /roles/{id}/status` | Authorization Service | Lifecycle: `roleGrantReachesEveryAssignmentPath`, `disablingARole…` |
| `/api/me/context` مؤثر | permissions/resources/panels/uiCatalog از OpenFGA؛ Page مجاز میکرو را نمایان می‌کند | — | Authorization Service + BFF | `AuthorizationDecisionServiceManifestTest` 12، `enterprise-hr-e2e` CONTEXT-* |
| Superset: دارایی‌ها به‌عنوان `external_resource`، اعطای تک‌داشبورد، فهرست/دسترسی فیلترشده | کاربر A فقط داشبورد A؛ Reports از داشبورد کشف می‌شود | Admin → Superset؛ `/superset-assets` | Authorization Service (+ BFF proxy) | Lifecycle: `twoUsersWithDifferentDashboards…`, `aSingleDashboardGrant…` |
| endpoint تشخیص مجوز | علت ALLOW/DENY با grant‌ها، عضویت‌ها، نقش‌ها، پروجکشن، وضعیت Panel | `GET /internal/v1/registry/diagnostics/authorization` | Authorization Service | `AuthorizationDiagnosticsServiceTest` 9، `…RepositoryIntegrationTest` 11 |
| Service Target (مقصد شبکه، بدون حالت احراز هویت) | | Admin → پراکسی → Targetها | Authorization Service | `ProxyRouteAdministrationServiceTest` |
| Route با حالت Forward یا Legacy، بدون نسخهٔ Semantic | ویرایش زنده، قفل خوش‌بینانه | Admin → پراکسی → Routeها | Authorization Service | `enterprise-hr-e2e` ROUTE-EDIT-NO-RELEASE-VERSION, ROUTE-CHANGE-LIVE |
| Route Operations با اعتبارسنجی پیکربندی | متد مجاز، تکراری، ابهام در زمان ذخیره | Route → Operations | Authorization Service | `ProxyRouteAdministrationServiceTest` (operations…) |
| Resolve قطعی (prefix > priority > specificity، ابهام=409) | همان الگوریتم در validate/match/resolve/runtime | — | Authorization Service | `RouteResolutionServiceTest` 12، `UpstreamPathPolicyTest` 14 |
| FORWARD_USER_TOKEN | توکن Keycloak کاربر به مقصد؛ 401→یک تازه‌سازی؛ 403 بدون تازه‌سازی | Route + Profile Forward | BFF | `OperationalProxyForwardingTest` 10، `e2e-auth` 41/41، `enterprise-hr-e2e` FORWARD-* |
| LEGACY_SERVICE_TOKEN | Connection + Profile (فرم/JSON، pointer پاسخ) + کش + تازه‌سازی + تست توکن | Admin → Outbound Connections / Auth Profiles | BFF (+ Authorization Service رجیستری) | `e2e-auth` LEGACY-*, `enterprise-hr-e2e` LEGACY-* |
| میکروی ترکیبی (Forward + Legacy در یک میکرو) | | دو Route با Profile متفاوت | BFF | `enterprise-hr-e2e` MIXED-MICRO، `e2e-auth` AUTH-DUAL-* |
| لغو مجوز → 403 و حذف از Context | | `DELETE /grants/{id}` | Authorization Service + BFF | `enterprise-hr-e2e` REVOKE-*, Lifecycle: `revokingAGrant…` |
| رندر میکروی خارج از Docker در Shell | artifactها فقط از `/api/mfe/...` | — | BFF + Shell | `mfe-external-e2e` SHELL-RENDER-EXTERNAL-MFE |

## محدودیت‌های شناخته‌شده

- Secret Legacy فقط با `FileSecretResolver`/پیکربندی محلی؛ Secret Manager خارجی اجباری نیست (فاز بعد).
- UI تشخیص مجوز وجود ندارد؛ endpoint هست.
- MFEهای دمو (HR/Finance/Reports/Admin) با `classification=DEMO` فقط با `DEMO_DATA_ENABLED=true` دیده می‌شوند.
- برای مقصد جدید پشت Operation Gateway باید یک `location` در Gateway (زیرساخت استقرار) وجود داشته باشد؛ Nginx عمومی نیازی به تغییر ندارد.
- سرویس آزمایشی SSO فقط توکن‌های دارای `aud` مورد انتظار را می‌پذیرد؛ کاربران دموی realm بدون نقش پیش‌فرض `account` توسط آن fixture رد می‌شوند (رفتار fixture، نه پلتفرم).
