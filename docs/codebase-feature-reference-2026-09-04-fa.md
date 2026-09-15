# مرجع فیچرها، جریان اجرا و مالکیت کد Aurevia

این سند نقشه‌ی نگهداری کد در baseline `1000dc8` است: هر فیچر از کجا وارد سیستم می‌شود، چه componentهایی آن را اجرا می‌کنند، داده در کجا نگهداری می‌شود و نقطه‌ی اعمال امنیت کجاست. فایل‌های generated مانند `*.d.ts`، خروجی `dist/target`، binaryهای ابزار و mappingهای تکراری mock به‌صورت تک‌به‌تک توضیح داده نشده‌اند؛ منبع TypeScript/Java/SQL و الگوی آن‌ها مرجع است.

برای حکم کیفیت و برنامه‌ی اصلاح، [ممیزی معماری، Clean Code و SOLID](codebase-architecture-clean-code-solid-audit-2026-09-04-fa.md) را بخوانید.

## ۱. نقشه مخزن

```text
apps/
  shell/                 میزبان مرورگر، login redirect، manifest و remote loader
  mfe-admin/             راهبری access، OU، panel، route، legacy profile، Superset و log
  mfe-hr/                نمونه‌ی ERP منابع انسانی با page/action guard
  mfe-finance/           نمونه‌ی ERP مالی با page/action guard
  mfe-reports/           فهرست و embed گزارش‌های مجاز Superset
packages/
  contracts/             قرارداد manifest، module و HostRuntime
  sh-core-ui/            provider و guardهای authorization در UI
  i18n/                  ترجمه و جهت نمایش
services/
  superapp-bff/          session/OIDC/token vault/proxy و API هم‌مبدأ Browser
  authorization-service/ control plane، policy، OpenFGA، OU و registryها
infra/
  docker-compose/        topology محلی و network isolation
  keycloak/              realm و mapperهای LDAP
  samba-ad/              directory نمونه
  openfga/               مدل و model tests
  nginx/                 public ingress
  mock-operation/        operation gateway و backendهای mock
  superset*/             Public و Operation Superset
tests/e2e/               contract testهای cross-package
docs/                    معماری، امنیت، عملیات، ADR و قرارداد OpenAPI
tools/                   bootstrap/preflight/verification
```

## ۲. مرزها و مالک داده

| داده/تصمیم | مالک | مصرف‌کنندگان | قاعده |
|---|---|---|---|
| OIDC identity و session | BFF/Keycloak | Browser، Authz sync | Browser فقط cookie opaque می‌گیرد |
| access/refresh token کاربر | BFF Token Vault در Redis | proxy و refresh service | token نباید وارد Browser یا log شود |
| کاربر، OU و entitlement metadata | Authorization PostgreSQL | Admin، manifest builder، projector | Authz writer اصلی است |
| رابطه‌ی authorization | OpenFGA با projection از Authz | checkهای Authz | فقط Authorization Service باید write کند |
| route و operation policy | Authorization PostgreSQL | BFF resolver/proxy، Admin | route تعریف‌نشده deny می‌شود |
| secret سرویس Legacy | secret manager خارجی | BFF SecretResolver | DB فقط secret reference نگه می‌دارد |
| token سرویس Legacy | Redis encrypted cache | BFF Legacy provider | Browser و DB آن را نمی‌بینند |
| Superset asset/grant | Authorization PostgreSQL/OpenFGA | Reports و Superset tunnel | asset باید instance-scoped شود؛ فعلاً نیست |
| UI artifact/catalog | Authorization PostgreSQL | Shell remote loader | remote کد trusted محسوب می‌شود |
| API/audit log | Authorization PostgreSQL | Admin Logs | payload حساس باید redact شود |

## ۳. جریان ورود کاربر و sync هویت

```mermaid
sequenceDiagram
    participant U as Browser
    participant B as BFF
    participant K as Keycloak
    participant A as Authorization Service
    participant D as PostgreSQL
    participant R as Redis

    U->>B: GET protected path + session cookie/none
    B-->>U: redirect to authorization endpoint
    U->>K: Authorization Code Flow
    K-->>B: callback + code
    B->>K: code exchange
    K-->>B: validated OIDC principal + tokens
    B->>A: syncLogin(issuer, sub, username, DN, OU, attrs)
    A->>D: upsert app_user / directory_ou / assignment
    A->>D: recalculate membership + enqueue outbox
    B->>R: encrypt/store tokens; receive random handle
    B->>R: Redis WebSession contains handle
    B-->>U: rotated opaque session cookie
```

### فایل‌های مسئول

- `services/superapp-bff/src/main/resources/application.yml`: registration/provider، Authorization Code Flow، cookie/session و username attribute.
- `services/superapp-bff/.../security/SecurityConfig.java`: security chain، CSRF، login success و logout.
- `OidcLoginSuccessHandler.java`: استخراج claimهای validateشده، sync داخلی، vault کردن token و rotation session ID.
- `TokenVaultService.java`, `TokenVaultCrypto.java`: envelope رمز‌شده و TTL Redis.
- `TokenRefreshService.java`, `RefreshCoordinator.java`: refresh پیشگیرانه و single-flight.
- `VaultLogoutHandler.java`: حذف vault، invalidate session و expire کردن Superset cookie.
- `services/authorization-service/.../api/IdentitySyncController.java`: endpoint داخلی login/directory identity sync.
- `directory/OuAccessService.java`: upsert identity/OU، محاسبه membership و تولید projection event.
- `directory/DirectoryDnParser.java`: parse استاندارد DN و canonical OU path.
- `infra/keycloak/configure-samba-ldap.sh`: federation و protocol mapperهای LDAP.

### داده‌های نوشته‌شده

- `app_user`: `issuer`, `external_id=sub`, username/display/email، external directory ID و attributes.
- `directory_ou`: object GUID/external ID، DN/path، hierarchy و sync status.
- `user_ou_assignment`: OU فعال هر کاربر و evidence login/directory.
- `effective_group_membership`: نتیجه‌ی محاسبه rule.
- `outbox_event`: relationship write/delete برای OpenFGA.
- Redis session: شناسه session و handle vault.
- Redis vault: access/refresh token رمز‌شده.

### invariant مورد انتظار

`issuer + sub` باید کلید هویت canonical باشد. `preferred_username` فقط attribute نمایشی/جست‌وجو است. baseline فعلی این invariant را در همه‌ی checkها رعایت نمی‌کند؛ جزئیات در `ID-001` سند ممیزی آمده است.

## ۴. sync دوره‌ای Active Directory

جریان:

1. `ActiveDirectorySyncJob` با flag زمان‌بندی فعال می‌شود.
2. با credential سرویس به LDAP متصل می‌شود.
3. OUها و hierarchy آن‌ها را می‌خواند و با external ID upsert می‌کند.
4. کاربرانی را که directory ID آن‌ها قبلاً شناخته شده است به OU مشاهده‌شده پیوند می‌دهد.
5. OUهای مشاهده‌نشده را inactive و نتیجه‌ی run را در `directory_sync_run` ثبت می‌کند.
6. membershipهای متاثر باید recalculated و relationshipها eventually projected شوند.

تنظیمات مهم در Authorization Service:

- enable/cron/base DN/filter/attribute names
- LDAP URL، bind DN و bind password
- timeout و TLS/truststore در محیط Production

نقطه‌ی عملیات: run باید metric آخرین موفقیت، تعداد OU/user، duration و failure reason امن داشته باشد. empty-result protection و stale-data policy باید قبل از Production اضافه شود.

## ۵. ساخت گروه از OU و دادن دسترسی MFE

### مسیر ادمین

1. Admin در `OuAccessManagement.tsx` فهرست OUها را می‌بیند.
2. `POST /api/v1/admin/ou-access/groups` از BFF عبور می‌کند.
3. `AdminProxyController` آن را به `/internal/v1/registry/ou-access/...` می‌فرستد و `X-Actor` را از principal می‌سازد.
4. `AdminAuthorizationInterceptor` بر اساس method مجوز راهبری را بررسی می‌کند.
5. `OuAccessAdminController` گروه و ruleهای `EXACT/SUBTREE` را می‌سازد.
6. preview نتیجه‌ی rule را بدون تغییر membership نشان می‌دهد.
7. پس از اعمال، `OuAccessService` membership را محاسبه و outbox می‌سازد.
8. application grant، گروه را با relation `viewer` به `application:{code}` وصل می‌کند.
9. `OutboxReconciler` tuple را در OpenFGA اعمال می‌کند.

### endpointهای داخلی اصلی

| method/path | کاربرد |
|---|---|
| `GET /internal/v1/registry/ou-access/ous` | فهرست OUهای syncشده |
| `GET/POST /internal/v1/registry/ou-access/groups` | فهرست/ساخت access group |
| `PUT /.../groups/{id}` | تغییر نام، combiner و وضعیت گروه |
| `GET/POST /.../groups/{id}/rules` | مشاهده/افزودن OU rule |
| `DELETE /.../groups/{groupId}/rules/{ruleId}` | حذف rule |
| `POST /.../groups/{id}/preview` | preview اعضا و delta |
| `GET /.../groups/{id}/members` | membership مؤثر |
| `GET/POST/DELETE /.../application-grants` | اتصال/قطع گروه به application |
| `GET /.../users/{id}/explain` | مسیر OU→group→application |

### مدل OpenFGA

مفهوم مورد انتظار:

```text
user:<canonical-subject> member group:<access-group-code>
group:<access-group-code>#member viewer application:<panel-code>
```

Shell نباید فقط وجود panel را بسنجد؛ route/menu و API همان entitlement را باید مصرف کنند.

## ۶. Authorization check و Effective Manifest

### check

`POST /internal/v1/authorize/check` این مراحل را طی می‌کند:

1. validate subject/resource/action/context.
2. تبدیل action به relation canonical با semantics registry.
3. OpenFGA relationship check.
4. ارزیابی policy/condition/operational rule مرتبط.
5. ثبت decision audit با reason code.
6. return کردن allow/deny؛ failure باید deny شود.

`check-batch` برای چند درخواست وجود دارد، ولی manifest فعلی برای panelها هنوز check تکی انجام می‌دهد.

### manifest

مسیر Browser:

```text
GET /api/v1/me/manifest
  -> MeController
  -> AuthorizationServiceClient
  -> GET /internal/v1/subjects/{id}/manifest
  -> AuthorizationController.manifest
```

manifest شامل expiry/version، panelها، permissions، resource tree و UI catalog/module metadata است. Shell آن را credential امنیتی تلقی نمی‌کند؛ فقط navigation و presentation guard می‌سازد. هر عملیات backend دوباره authorize می‌شود.

فایل‌های مرتبط:

- `AuthorizationController.java`: check، batch و manifest builder فعلی.
- `AuthorizationSemanticsRegistry.java`: نگاشت resource/action به relation.
- `RuntimePolicyService.java`, `StructuredPolicyEvaluator.java`, `OperationalRules.java`: policyهای تکمیلی.
- `OpenFgaRelationshipAdapter.java`: adapter HTTP و cache.
- `packages/contracts/src/index.ts`: قرارداد TypeScript manifest/module/runtime.
- `packages/sh-core-ui/src/index.tsx`: provider، `SHCan`, `SHAction`, `SHRouteGuard`.

## ۷. Resource Catalog، Action و Grant

### مدل داده

- `resource`: درخت منابع و metadata/owner/classification/source.
- `action`: واژگان action canonical.
- `resource_action`: actionهای معتبر هر resource.
- `authorization_grant`: grant به USER/GROUP/ROLE با relation/expiry/status/version.
- `condition_definition`, `data_policy`: شرط و obligation.
- `resource_api_binding`: اتصال resource به method/path API.
- `resource_external_binding`: اتصال به سامانه/شناسه بیرونی.
- `resource_manifest_import`: سابقه‌ی import catalog.

### کد

- `AccessAdminController.java`: CRUD resource/action/grant و projection parent/grant.
- `ResourceManifestController.java`: validate/import manifest resource.
- `AccessStudio.tsx`: رابط مدیریت identity/resource/grant.
- migrations V1، V6، V11 تا V19 و V25 تا V31: تکامل enum، relation، catalog و demo tree.

### قواعد نگهداری

- `resource_key` پس از ایجاد immutable است.
- تغییر parent و grant باید همراه outbox همان transaction باشد.
- enum و prefix باید از یک schema/codegen مشترک تولید شوند؛ امروز چند منبع حقیقت وجود دارد.
- grant منقضی یا archived نباید manifest/backend check را مجاز کند.

## ۸. Outbox و OpenFGA reconciliation

فایل‌ها:

- `sync/OutboxReconciler.java`: polling، retry و اعمال tuple.
- `sync/OpenFgaReconciliationService.java`: مقایسه/reprojection.
- `sync/OutboxMetrics.java`: metricهای outbox.
- `OperationsController.java`: trigger مدیریتی reconcile.
- `openfga/RelationshipAuthorizationPort.java`: port domain-facing.
- `openfga/OpenFgaRelationshipAdapter.java`: implementation.
- `infra/openfga/model.fga`, `model-tests.yaml`: مدل و تست policy.

چرخه event:

```text
command DB transaction
  -> state row update
  -> outbox insert with idempotency key
commit
  -> reconciler claims pending event
  -> OpenFGA write/delete
  -> mark processed or retry/dead-letter
```

برای استحکام Production، claim کردن row و network call نباید یک transaction طولانی بسازد؛ ordering/version هر aggregate باید در payload و شرط apply باشد.

## ۹. Panel و UI Plugin Registry

### panel قدیمی و artifact جدید

- `panel`: هویت business، code/slug/route/service slug، وضعیت و active artifact.
- `ui_module_artifact`: نسخه‌ی artifact، remote URL/name/exposed module، contract/schema، SRI، manifest snapshot و validation result.
- `ui_menu_override`: override title/icon/order/hidden بر اساس menu ID پایدار.
- `RegistryController.java`: CRUD panel و route-prefix collision validation.
- `UiPluginRegistryController.java`: publish/list/activate artifact و menu override.
- `Panels.tsx`: رابط راهبری panel/artifact/rollback.
- `V33__dynamic_ui_plugin_registry.sql`: schema و migration catalog موجود.

### lifecycle پیشنهادی

```text
DRAFT metadata
  -> publish immutable artifact
  -> schema + compatibility + security validation
  -> approval
  -> activate with expected panel version
  -> health/canary observation
  -> rollback to prior artifact if needed
```

در baseline approval/canary و optimistic activation کامل نیست و validation امنیتی URL/SRI/schema باید توسعه یابد.

## ۱۰. Shell و Remote Loader

`apps/shell/src/index.tsx` این مسئولیت‌ها را دارد:

- دریافت `/api/v1/me/manifest` و redirect login روی 401/302.
- نگهداری locale و Ant Design theme.
- ساخت menu/routes از `uiCatalog`.
- ساخت `HostRuntime` شامل HTTP، navigation، event bus، shared state و session facade.
- mount کردن plugin و نمایش fallback/loading/error.

`remote-loader.ts`:

1. remote URL را parse و protocol را کنترل می‌کند.
2. در صفحه HTTPS، remote غیرHTTPS را رد می‌کند.
3. syntax integrity را validate و `script.integrity/crossOrigin` را تنظیم می‌کند.
4. بارگذاری را deduplicate و timeout می‌کند.
5. container Module Federation را initialize و exposed factory را می‌گیرد.
6. contract version را با module catalog تطبیق می‌دهد.

`webpack.config.cjs`های Shell/MFE shared singletonهای React/ReactDOM/Router/Ant Design را تنظیم می‌کنند. mismatch نسخه باید در publish validation یا runtime با خطای قابل فهم fail شود.

نکته امنیتی: remote code داخل origin برنامه اجرا می‌شود و به DOM/session-backed API دسترسی دارد؛ SRI فقط تغییر artifact را تشخیص می‌دهد و sandbox ایجاد نمی‌کند.

## ۱۱. MFEهای نمونه

### Admin

| فایل منبع | مسئولیت |
|---|---|
| `bootstrap.tsx` | route/layout Admin و اتصال panelها |
| `AccessStudio.tsx` | resource، action، subject و grant |
| `OuAccessManagement.tsx` | OU، access group، preview، application grant و explain |
| `Panels.tsx` | panel و UI artifact registry |
| `ProxyRoutes.tsx` | service target، proxy route و operation |
| `OutboundAuthProfiles.tsx` | metadata و secret reference پروفایل Legacy |
| `SupersetAssets.tsx` | sync/publish/grant asset گزارش |
| `Logs.tsx` | جست‌وجوی API/audit/correlation log |

تمام mutationهای Admin باید CSRF token، correlation ID و مجوز backend داشته باشند. تکرار fetch boilerplate در این componentها باید به client typed مشترک منتقل شود.

### HR و Finance

- `bootstrap.tsx`: صفحه‌ها، table/form/demo API و `SHRouteGuard/SHAction`.
- هر route به resource/action catalog متصل است.
- mutationها با UI guard پوشانده شده‌اند، ولی امنیت واقعی در proxy check است.
- mock mappingهای `infra/mock-operation/mappings` پاسخ‌های demo را فراهم می‌کنند.

### Reports

- `ReportsController` در BFF assetهای مجاز را از Authz می‌گیرد.
- `report-security.ts` URL embed را به tunnel هم‌مبدأ محدود می‌کند.
- `bootstrap.tsx` card/list/iframe را بر اساس asset مجاز نمایش می‌دهد.
- `report-security.test.ts` قواعد client-side URL را تست می‌کند.

## ۱۲. Dynamic Proxy Routing

### control plane

- `service_target`: target metadata، auth profile reference، connection ref، timeout و response limit.
- `proxy_route`: panel/service slug، prefix، rewrite/strip، retry metadata و target.
- `route_operation`: method + normalized pattern + resource/action + body/response policy.
- `ProxyRouteAdminController.java`: CRUD و collision/format validation.
- `RouteResolutionController.java`: read-only resolution برای runtime BFF.
- `RoutePathPolicy.java`: normalization و pattern policy.
- `ProxyRoutes.tsx`: UI راهبری.

### data plane

مسیر نمونه:

```text
Browser POST /hr/api/employees
  -> BFF OperationalProxyController
  -> Authz resolve(panel=hr, path=/api/employees, method=POST)
  -> Authz check(subject, resource, action)
  -> BFF load token(s) from vault/cache
  -> Operation Gateway over internal/mTLS connection
  -> target service
  -> bounded response back to Browser
```

Browser نباید `Authorization` بسازد. BFF headerهای کنترل‌شده را تعیین می‌کند و Gateway credential نهایی target را اعمال می‌کند.

## ۱۳. Modern و Legacy outbound authentication

`OutboundAuthMode` حالت‌های اصلی را تفکیک می‌کند. providerها:

- `UserBearerTokenProvider`: access token کاربر را از Token Vault برای سرویس modern می‌دهد.
- `LegacyServiceTokenProvider`: token سرویس را مستقل از token کاربر می‌گیرد.
- `OutboundTokenProvider`: interface مشترک انتخاب credential.

### پروفایل Legacy

`outbound_auth_profile` metadata زیر را نگه می‌دارد:

- mode/request format، scope/audience/scheme
- token/expiry/refresh JSON pointers
- secret referenceها
- timeout، response max، expiry skew، status/version

اجزا:

- `OutboundAuthProfileController.java`: CRUD/version/status/usage؛ secret value را برنمی‌گرداند.
- `OutboundAuthProfile.java`: تبدیل response metadata به مدل runtime.
- `SecretResolver.java`: port حل secret reference.
- `ConfiguredSecretResolver.java`: فقط local و opt-in.
- `UnavailableSecretResolver.java`: fail-closed پیش‌فرض.
- `LegacyTokenClient.java`: call کنترل‌شده token endpoint.
- `LegacyTokenResponseParser.java`: parse محدود و امن.
- `LegacyTokenCache.java`: cache رمز‌شده Redis.
- `LegacyTokenRefreshCoordinator.java`: lock/single-flight بین instanceها.
- `LegacyTokenManager.java`: cache lookup/acquire/invalidate/test orchestration.
- `LegacyTokenAuditPublisher.java`: audit موفق/ناموفق بدون token.

### محل صحیح اطلاعات حساس

| اطلاعات | محل صحیح |
|---|---|
| username/password/client secret | secret manager خارجی |
| آدرس token endpoint و TLS policy | approved connection registry |
| secret URI/version metadata | DB profile |
| access/refresh token حاصل | Redis encrypted cache با TTL |
| route-to-profile link | `service_target.outbound_auth_profile_id` |

در baseline connection registry چندگانه و SecretResolver Production هنوز وجود ندارند.

## ۱۴. Superset

### Public zone

- `public-superset` در نمونه یک سرویس public/static جداست.
- نباید credential یا dataset عملیاتی دریافت کند.
- route عمومی از Nginx public قابل دسترس است.

### Operation zone

- `operation-superset` روی network داخلی است و port مستقیم Browser ندارد.
- `OperationSupersetProxyController` درخواست هم‌مبدأ `/api/v1/superset/**` را می‌گیرد.
- `SupersetAssetController` path/query/subject را به access asset ارزیابی می‌کند.
- BFF token کاربر را از vault می‌گیرد، سپس Gateway headerهای Remote User را برای Superset اعمال می‌کند.
- session cookie Superset توسط BFF tunnel forward و در login/logout identity جدید expire می‌شود.

### asset model

- `superset_asset`: resource، external ID/type/title/path، publish و sync version.
- `superset_subject_mapping`: نگاشت subject داخلی به subject Superset.
- `superset_access_sync`: وضعیت همگام‌سازی grant.

برای پشتیبانی نیاز چند سرور باید موجودیت زیر افزوده شود:

```text
superset_instance(
  id, code, name, zone[PUBLIC|OPERATION],
  approved_connection_ref, auth_profile_id,
  base_path, tls_policy, active, version
)
```

و unique asset از `external_id` به `(instance_id, asset_type, external_id)` تغییر کند. host/port خام نباید بدون connection policy از DB proxy شود.

## ۱۵. Admin security

### Browser تا Authz

- Browser فقط endpointهای `/api/v1/admin/**` BFF را صدا می‌زند.
- `AdminProxyController` actor را از principal می‌سازد؛ header Browser منبع اعتماد نیست.
- BFF با credential داخلی یا mTLS به Authorization Service متصل می‌شود.
- `AdminAuthorizationInterceptor` method/path را به permission راهبری نگاشت می‌کند.
- mutationها تحت CSRF Spring هستند؛ Superset tunnel به دلیل CSRF داخلی Superset استثنا شده است.

### نکات توسعه

- endpoint جدید زیر registry بدون تست interceptor پذیرفته نشود.
- عملیات‌هایی مانند connection test، token test، health probe و reconcile باید action اختصاصی داشته باشند، نه صرفاً `can_view`.
- mapping permission بهتر است declarative/typed باشد تا substring method در interceptor.
- `X-Actor` تنها در شبکه‌ی trusted داخلی معتبر است.

## ۱۶. Logging و Audit

| جزء | مسئولیت |
|---|---|
| `PublicApiLoggingWebFilter` | ثبت metadata درخواست‌های BFF و ارسال امن به Authz |
| `AuthorizationApiLoggingFilter` | log API داخلی Authorization Service |
| `SensitiveDataRedactor` | حذف token/secret/cookie/password و fieldهای حساس |
| `SafeErrorBodySerializer` | محدودسازی body خطا |
| `CorrelationIds` | validate/generate/propagate شناسه correlation |
| `AuditTrail` | رویدادهای control-plane |
| `AuthorizationDecisionAuditor` | allow/deny و reason/model |
| `LogIngestionController` | ingest داخلی API/audit log |
| `LogQueryController` | query/summary/correlation برای Admin |
| `LogRetentionJob` | retention و پاکسازی دوره‌ای |
| `Logs.tsx` | UI جست‌وجو و drill-down |

داده‌های ممنوع در log: bearer token، refresh token، cookie، authorization code، LDAP bind password، Legacy credential، secret value و body حساس. secret reference و hash context در صورت نیاز مجازند.

## ۱۷. Database migrations

Flyway تنها مسیر تغییر schema است. گروه‌بندی فعلی:

| migration | موضوع |
|---|---|
| V1–V3 | control plane پایه، catalog و user/action demo |
| V4–V6 | Superset asset/access و uniqueness grant |
| V7–V9 | group/role/admin، route و panel authorization |
| V10–V15 | URL MFE، API resource، resource tree و canonical external tuple |
| V16–V21 | decision detail، dead letter، relation/action canonical، logging و tuple ID |
| V22–V24 | dynamic proxy و outbound auth profile |
| V25–V31 | ERP/data resource catalog، صفحات demo و reprojection |
| V32 | OU-based application access |
| V33 | dynamic UI plugin registry |

قواعد:

- migration اعمال‌شده ویرایش نشود؛ forward migration جدید ساخته شود.
- enum change با همه‌ی Java/TypeScript/OpenAPI contractها هم‌زمان شود.
- backfill قبل از `NOT NULL/UNIQUE` با داده‌ی واقعی و collision report تست شود.
- migrationی که tuple semantics را عوض می‌کند باید reprojection و verification OpenFGA داشته باشد.
- schema جدید باید owner، retention، index و delete behavior روشن داشته باشد.

## ۱۸. Configuration و deployment topology

سرویس‌های Compose:

- data/control: `auth-db`, `openfga-db`, `redis`, `openfga-migrate`, `openfga`, `authorization-service`
- identity/public: `keycloak-db`, `keycloak`, `aurevia-bff`, `nginx`
- optional directory: `samba-ad`, `keycloak-directory-config`
- operation demo: `mock-hr`, `mock-finance`, `operation-gateway`
- Superset: `public-superset`, `operation-superset-db`, `operation-superset-init`, `operation-superset`
- UI: `mfe-admin`, `mfe-hr`, `mfe-finance`, `mfe-reports`

networkها:

- `public-services`: ingress/BFF/UI/identity-facing.
- `public-data`: دیتابیس‌ها و control-plane داخلی.
- `bff-egress`: BFF تا gateway/internal API.
- `operation-services`: targetهای عملیاتی و Operation Superset.
- `superset-bootstrap-egress`: خروجی محدود bootstrap Superset.

Production باید DNS/cert/secret manager/backup/monitoring جدا داشته باشد. `.env` و Compose topology محلی، secret delivery مناسب Production محسوب نمی‌شوند.

## ۱۹. تست و CI

### تست‌های Java

- BFF: CSRF، Superset proxy، Legacy response/refresh، retry/route normalization و Token Vault crypto.
- Authorization: controller manifest/resource، Admin interceptor، DN/OU rule، observability، OpenFGA adapter/guard، operational/structured/runtime policy، route path و semantics.

### تست‌های TypeScript/contract

- remote loader: URL/integrity/loading behavior.
- SH core UI: policy state و guard.
- Reports: embed URL safety.
- e2e contract: guard شدن routeها و mutationهای HR/Finance/Admin.

### CI فعلی

```text
frontend: npm ci -> typecheck -> test -> build -> npm audit
backend:  Java 21 -> ./mvnw -B clean verify
config:   copy env example -> compose config -> git diff --check
```

### شکاف‌های مهم تست

- OIDC واقعی + Redis session scan برای اثبات نبود token.
- Testcontainers برای PostgreSQL/Redis و migrationها.
- OpenFGA واقعی برای OU/reprojection/order/idempotency.
- LDAP/Keycloak integration با move/rename/empty sync.
- Gateway mTLS و Legacy secret/token end-to-end.
- چند Superset instance و isolation asset/session.
- concurrent artifact activation و Shell refresh race.
- load/performance test manifest N+1 و proxy buffering.

## ۲۰. فرمان‌های توسعه و راستی‌آزمایی

روی Windows:

```powershell
npm ci
npm run typecheck
npm test -- --runInBand
npm run build
.\mvnw.cmd -B verify
npm run openfga:bootstrap
npm run infra:up
npm run infra:verify
```

اعتبارسنجی فقط config:

```powershell
docker compose --env-file .env -f infra/docker-compose/compose.yml config --quiet
```

در نصب تازه، `.env.example` کپی و تمام placeholderها عوض می‌شوند. credential واقعی نباید در Git، command line history، log یا screenshot قرار گیرد.

## ۲۱. راهنمای افزودن فیچر جدید

ترتیب پیشنهادی:

1. owner داده، SubjectKey و resource/action را مشخص کنید.
2. threat scenario و deny behavior را بنویسید.
3. migration forward-only و constraint/index لازم را اضافه کنید.
4. DTO typed و use case مستقل از HTTP بسازید.
5. port خارجی و adapter آن را از domain جدا کنید.
6. outbox/idempotency/version را برای side effect تعریف کنید.
7. backend authorization و audit را قبل از UI کامل کنید.
8. OpenAPI و TypeScript contract را هم‌زمان تغییر دهید.
9. unit + integration + negative/concurrency test بنویسید.
10. runbook، metric/SLO و rollback/forward-fix را ثبت کنید.

## ۲۲. مسیر مطالعه برای عضو جدید تیم

1. `README.md` و `docs/guide-fa.md`
2. `infra/docker-compose/compose.yml` برای topology
3. `services/superapp-bff/.../SecurityConfig.java` و `OidcLoginSuccessHandler.java`
4. `MeController.java` و `OperationalProxyController.java`
5. `AuthorizationController.java` و `AuthorizationSemanticsRegistry.java`
6. `infra/openfga/model.fga`
7. V1، V22، V24، V32 و V33 migrationها
8. `OuAccessService.java` و `OutboxReconciler.java`
9. `apps/shell/src/index.tsx` و `remote-loader.ts`
10. `packages/contracts` و `packages/sh-core-ui`
11. Admin panels و سپس HR/Finance/Reports
12. سند ممیزی و roadmap اصلاح

این ترتیب ابتدا مرزهای اعتماد و جریان runtime را روشن می‌کند و سپس جزئیات CRUD/UI را نشان می‌دهد.
