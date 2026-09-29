# بررسی چند Resource MODULE در یک Micro Frontend

تاریخ: 2026-09-29. مبنا: کد همین checkout، شامل migration تا V81؛ مستندات قبلی مبنای نتیجه‌گیری نبوده‌اند.

## ۱. نتیجه نهایی

**PARTIALLY SUPPORTED**

دیتابیس، اعتبارسنجی درخت، import/publish، Admin و Shell برای چند MODULE هم‌سطح در یک Panel ساختار لازم را دارند.
اما درخواست را نباید به «سه کلید مستقل ماژول بدون هیچ ماژول اضافی، مجوز view ماژول با دسترسی خودکار به صفحات،
و انتشار مستقل از Artifact» تعبیر کرد: هر سه با رفتار فعلی اختلاف دارند.
ساخت نمونهٔ دقیقاً سه‌ماژولی با قرارداد موجود ممکن است، مشروط به استفاده از MODULE پیش‌فرض به عنوان Employee.
نتیجهٔ انتهابه‌انتها در مرورگر و PostgreSQL/OpenFGA در این محیط تأیید اجرایی نشده است.

| مسیر | نتیجهٔ بررسی |
|---|---|
| Manifest → Validation | MODULEهای اضافی مجاز؛ یک MODULE پیش‌فرض همیشه ساخته می‌شود |
| Import → Database | Draft بدون تغییر resource؛ Publish همهٔ منابع را با panel_id مشترک materialize می‌کند |
| Publish → Artifact | یک active pair؛ اتصال artifact به resource revision تغییرناپذیر |
| Authorization → Context | منابع مستقل؛ view محلی، admin ارثی؛ اجداد درخت مجوز جدید نمی‌گیرند |
| Admin | درخت و انتخاب بر اساس UUID؛ چند sibling مجاز؛ مالکیت MANIFEST محدودیت ویرایش دارد |
| Shell | یک UI runtime برای Panel، چند route مجاز و یک remote؛ MODULE منبع ورودی مستقل uiCatalog نیست |

## ۲. مدل واقعی داده

```text
panel (یک ثبت Micro Frontend)
├── ui_module_artifact[]                  UNIQUE(panel_id, artifact_version)
│   └── resource_manifest_id ────────────┐
├── resource_manifest_import[]           │ نسخه‌های immutable
├── active_artifact_id                   │ یک اشاره‌گر فعال، نه چند remote فعال
├── active_resource_manifest_id ◄────────┘ باید با Artifact فعال سازگار باشد
├── ui_menu_override[]                   نمایش منو، مستقل از grant
└── resource[] (panel_id)
    └── APPLICATION application:aurevia/<slug>
        ├── MODULE module:<slug>         ساخته‌شده توسط normalize
        │   └── PAGE → UI_COMPONENT → FIELD
        ├── MODULE module:<slug>.payroll
        │   └── PAGE ...
        └── MODULE module:<slug>.attendance
            └── PAGE ...

resource_action(resource_id, action_id) ← action
authorization_grant(subject, resource_id, action_id, relation)
resource_external_binding(resource_id, provider, external_type, external_id)
outbox_event → OpenFGA parent/grant tuples
```

APPLICATION پنل در manifest نرمال‌شده parent=null دارد، اما `upsert` اگر root سراسری موجود باشد
آن را زیر `application:aurevia` ذخیره می‌کند یا parent موجود را حفظ می‌کند. بنابراین یک APPLICATION متعلق به Panel
به معنی تنها APPLICATION کل database نیست. `SECTION` و `COMPONENT` نام نوع معتبر نیستند؛ نوع موجود `UI_COMPONENT` است.

| فیلد / قید | رفتار واقعی | شاهد |
|---|---|---|
| panel.code / slug | هرکدام globally unique؛ code uppercase و slug kebab-case | V1، `PanelAdministrationService.validate` |
| panel.name_fa / name_en / route_base_path | در V1 جداگانه unique | V1 |
| resource.resource_key | globally unique، نه فقط بین خواهرها | V1؛ upsert با `on conflict(resource_key)` |
| resource.parent_id | FK به resource با ON DELETE RESTRICT؛ unique نیست | V1 |
| resource.panel_id | FK به panel؛ ایندکس معمولی روی `(panel_id,status,resource_key)` | V51 |
| resource.type | enum؛ قید یکتایی با panel_id ندارد | V1، V11، V28، V51 و baseline B79 |
| resource.code / slug | چنین ستون هویتی‌ای برای Resource وجود ندارد؛ از resource_key استفاده می‌شود | تعریف جدول و DTO |
| external identity | ترکیب provider/type/id در binding unique؛ همچنین resource/provider | V28 |
| manifest version | unique بر `(panel_id,manifest_version)` برای panel غیر null | V52 |
| runtime remote_name | قید UNIQUE قدیمی V33 در V48 حذف شده؛ service مالکیت بین پنل‌ها را کنترل می‌کند | V48؛ `UiPluginRegistryService.validate` |

هیچ `UNIQUE(panel_id,type)` یا محدودیت عددی MODULE در schema/validator بررسی‌شده وجود ندارد.
این فقط شاهد DB است؛ شواهد لایه‌های دیگر در ادامه آمده است.

## ۳. قواعد درخت

`ResourceManifestService.PARENTS / validate / assertAcyclic` و
`AccessAdministrationService.ALLOWED_PARENT_TYPES / validateResource` قواعد زیر را اعمال می‌کنند:

| سؤال | پاسخ |
|---|---|
| چند MODULE زیر APPLICATION؟ | بله؛ تعداد محدود نشده است |
| MODULE مستقیماً فرزند APPLICATION؟ | بله |
| PAGE فرزند MODULE؟ | بله |
| MODULE زیر MODULE؟ | رد می‌شود |
| sibling MODULE؟ | مجاز؛ کلیدهای global باید متفاوت باشند |
| نام تکراری sibling؟ | قید یکتایی روی نام Resource در این مسیر وجود ندارد |
| parent خارج از manifest؟ | در validation manifest رد می‌شود؛ تمام والدها باید در مجموعهٔ نرمال‌شده باشند |
| ساخت دستی با والد پنل دیگر؟ | اگر هر دو panel_id غیر null و متفاوت باشند رد می‌شود |

UI_COMPONENT زیر PAGE یا UI_COMPONENT، FIELD زیر UI_COMPONENT مجاز است.
EXTERNAL_RESOURCE می‌تواند زیر APPLICATION/MODULE/PAGE/BUSINESS_RESOURCE باشد و provider/externalType/externalId لازم دارد.
قواعد این موارد در enum دیتابیس به تنهایی enforce نمی‌شوند؛ سرویس‌ها مسئول اعتبارسنجی سلسله‌مراتب‌اند.

## ۴. معنای دقیق دو manifest و دو module

`ResourceManifestDtos.ResourceManifest` دقیقاً شامل `schemaVersion`, `module`, `resources` است.
`ModuleMetadata` شامل `key,name,nameFa,nameEn,version` است؛ سه مورد key/name/version لازم‌اند.
`ManifestResource` شامل `key,type,parentKey,parent,name,nameFa,nameEn,ownerDomain,classification,actions,metadata,provider,externalType,externalId` است.
`parentKey` بر alias قدیمی `parent` اولویت دارد. actions و metadata پیش‌فرض خالی دارند.

`module.key` باید با slug پنل برابر باشد. این object توصیف نسخه و مالک manifest است، **ولی صرفاً runtime metadata نیست**:
`normalize` از آن حتماً `module:<key>` می‌سازد؛ در نتیجه به MODULE منبع نیز متصل است.
runtime در `mf-manifest.json` و Artifact است: `runtime.remoteEntry`, `remoteName`, `exposedModule`, `contractVersion`.
فیلدهای route/menu/remote در Resource Manifest و metadata منابع صراحتاً رد می‌شوند.

مثال معتبر از نمونه:

```json
{
  "schemaVersion": "1.0",
  "module": {"key": "test-multi-module", "name": "TEST_MULTI_MODULE", "version": "1.0.0"},
  "resources": [
    {"key": "module:test-multi-module", "type": "MODULE", "name": "Employee", "actions": ["view", "admin"]},
    {"key": "module:test-multi-module.payroll", "type": "MODULE", "name": "Payroll", "actions": ["view", "admin"]},
    {"key": "page:test-multi-module.salary", "type": "PAGE", "parentKey": "module:test-multi-module.payroll", "name": "Salary", "actions": ["view"]}
  ]
}
```

MODULE بدون parent به APPLICATION پنل متصل می‌شود؛ PAGE بدون parent به MODULE پیش‌فرض، نه آخرین MODULE داخل آرایه.
به همین دلیل همهٔ PAGEهای نمونه parentKey صریح دارند.
`syntheticEquivalent` اجازه می‌دهد کلید/type/parent یکسانِ منبع synthetic با عنوان/actions صریح جایگزین شود.
نمونه از همین رفتار عمومی استفاده می‌کند؛ کلید Employee مستقل از slug نشده است.

## ۵. جریان Hybrid Import و هویت منابع

1. Admin `Panels.importResourceManifest` یا `fetchResourceManifest`، endpoint متناظر BFF را فراخوانی می‌کند.
2. `ResourceManifestController.importDraft/fetch` به `ResourceManifestService.stageJson/fetch` می‌رسد.
3. `read` ممنوعیت frontend fields، `normalize` ساخت syntheticها و parent پیش‌فرض، `validate` هویت و درخت را بررسی می‌کنند.
4. `stage` با checksum و نسخه، Draft/Diff را در `resource_manifest_import` ذخیره می‌کند. resource هنوز تغییر نمی‌کند.
5. `publish → applyRevision` قفل پنل، انتخاب Artifact سازگار و validation routes را انجام می‌دهد.
6. `materialize` ترتیب وابستگی والد را رعایت کرده و `upsert` تمام منابع را با همان `panel.id` می‌نویسد؛ actions جایگزین می‌شوند.
7. `deprecateMissing` فقط منابع `MANIFEST` متعلق به همان Panel را deprecate می‌کند؛ ADMIN حذف نمی‌شود.
8. `JdbcManifestCatalogProjection.snapshot/enqueueChanges` تغییرات parent/grant را به outbox می‌دهد؛ V81 تا پایان projection پنل را fail-closed می‌کند.

| تغییر | نتیجه |
|---|---|
| import دوباره همان نسخه/محتوا | همان revision؛ Draft تکراری ساخته نمی‌شود |
| همان نسخه با محتوای متفاوت | خطای immutable |
| نسخهٔ جدید، همان resource_key | update همان UUID؛ type و ownership قابل تصاحب نیست |
| حذف MODULE و PAGEهایش | deprecated؛ تاریخچه/grants در DB باقی می‌ماند، actions و binding مؤثر حذف/غیرفعال می‌شوند |
| حذف MODULE ولی حفظ PAGE با parent همان MODULE | missing parent در validation |
| تغییر name | همان هویت، update metadata |
| تغییر key | منبع جدید + deprecate قدیمی؛ grant خودکار به کلید جدید منتقل نمی‌شود |
| تغییر panel slug | metadata ساده نیست: ریشه/synthetic key عوض می‌شوند و module.key باید match شود؛ این بررسی مهاجرت slug را اجرایی تأیید نکرده است |
| rollback | payload immutable نسخهٔ قبلی دوباره materialize، موارد جدید deprecated، actions/parent/binding قبلی بازگردانده می‌شوند |

`definitionTree` منابع deprecated را هم برمی‌گرداند؛ برای شمارش «درخت فعال» نباید خروجی خام آن را بدون بررسی status شمرد.
`effective_resource_catalog` و فیلترهای Context معیار دسترسی جاری هستند.

## ۶. Publish و مدل runtime

```text
ONE PANEL
├── ONE ACTIVE ARTIFACT → remoteEntry.js → ./plugin
└── ONE ACTIVE RESOURCE REVISION
    └── APPLICATION
        ├── MODULE Employee
        ├── MODULE Payroll
        └── MODULE Attendance
```

وجود Artifact فعال برای همهٔ پنل‌های DB تضمین نشده؛ ستون nullable است. نمودار وضعیت یک پنل منتشرشدهٔ قابل بارگذاری را نشان می‌دهد.
`JdbcAuthorizationQueryRepository.panels` پنل را به Artifact فعال معتبر join می‌کند و تطابق revision را می‌سنجد.
`ResourceManifestService.applyRevision` اگر Artifact فعلی وجود داشته باشد ولی Artifact سازگار با revision جدید پیدا نشود، publish را رد می‌کند.
V81 و `JdbcManifestReleaseRepository.bind` اجازه نمی‌دهند Artifact متصل به V1 به V2 منتقل شود.

بنابراین Resource Manifest و Artifact دو موجودیت‌اند، ولی lifecycle جاری مستقل نیست.
برای افزودن MODULE در پنلی با release فعال معمولاً رکورد Artifact جدید مرتبط با revision جدید لازم است؛
ساخت فایل JavaScript جدید الزام کد نیست. اگر route/runtime تغییری ندارد، همان remote URL/bytes می‌تواند استفاده شود.
بدون Artifact فعال و بدون candidate، publish کاتالوگ می‌تواند بدون runtime انجام شود.

## ۷. مجوز و Context دقیق

`AccessAdministrationService.grant` هدف را با `(resourceId,actionId)` می‌سنجد؛ MODULEها grant مستقل دارند.
`AuthorizationSemanticsRegistry` action را به relation/permission و `ResourceObjectKey` منبع را به object canonical تبدیل می‌کنند.
مثلاً `module:test-multi-module.payroll` به `resource:module/test-multi-module.payroll` می‌رود.
در `infra/openfga/model.fga`، `viewer` ارث نمی‌رسد؛ فقط `manager` دارای `manager from parent` است.
`can_view = viewer or editor or manager`؛ بنابراین admin ماژول می‌تواند به descendants برسد، نه siblings و نه به والد.

| grant کاربر A | درخت/permissions | Panel و uiCatalog نمونه |
|---|---|---|
| فقط Employee/view | Employee مجاز؛ APPLICATION و root برای navigation با actions خالی؛ PAGEها و siblings غیرمجاز | چون routeها PAGE-guarded هستند و discovery fallback تنظیم نشده، MFE وارد uiCatalog نمی‌شود؛ Panel هم بدون مجوز application نمایش داده نمی‌شود |
| Employee/view + دو PAGE/view | سه permission؛ ancestorها بدون permission | یک runtime HR/Test، فقط دو route و menu Employee؛ گروه‌های خالی Payroll/Attendance حذف می‌شوند |
| Employee/admin | مجوز مدیریتی descendants مطابق actions تعریف‌شده | فقط routeهای مجاز همان subtree؛ siblingها مجاز نمی‌شوند |

`uiCatalog.modules` لیست Resource MODULE نیست؛ لیست runtimeهای Panel است. پرسش «فقط Employee داخل modules باشد»
باید به «همان HR runtime با routes/navigation فیلترشده» اصلاح شود.

`AuthorizationDecisionService.manifest` ابتدا `checkBatch` می‌کند و permissions را می‌سازد؛ سپس ancestorها را به
`resourceTree` اضافه می‌کند و `permissions.getOrDefault(key,List.of())` را برای actions هر node می‌گذارد.
`uiModule` routeها را با resource/action فیلتر، PAGE menuهای نامجاز و GROUPهای خالی را حذف و defaultRoute را از مجازها انتخاب می‌کند.
استثنای declarative آن `discovery_resource_key` + route خالی است؛ هیچ sibling permission ساخته نمی‌شود.
External-linkهای navigation PAGE نیستند و همان guard صفحه را ندارند؛ نمونه چنین لینک‌هایی ندارد.

`MeController.context` در BFF، manifest مؤثر را به `allowedMicros`, `dynamicRoutes`, `navigation`, `resources`, `actions`, `uiCatalog`
نگاشت می‌کند و remoteها را proxy می‌کند. خودش از هر Resource MODULE یک remote جدید نمی‌سازد.
Context نمایش capabilities است؛ مسیر `AuthorizationDecisionService.check` برای runtime علاوه بر relationship، policy را هم بررسی می‌کند.

## ۸. Admin و Shell

`AccessStudio` منابع را با `id` در Map قرار می‌دهد و با `parent_id` به children اضافه می‌کند؛ هیچ single-MODULE lookup ندارد.
فرم نوع MODULE، parent و panel را می‌پذیرد؛ `openEditor` برای فرزند به‌طور پیش‌فرض PAGE انتخاب می‌کند،
پس برای افزودن MODULE زیر APPLICATION باید نوع را خودتان MODULE انتخاب کنید.
`save`, `toggleAction` و grant از شناسهٔ resource انتخاب‌شده استفاده می‌کنند. شواهد static نشانه‌ای از یکی‌گرفتن siblingها نشان نمی‌دهد؛
تضمین رفتار کلیک/race در مرورگر از این خواندن به دست نمی‌آید.

HYBRID ساخت منابع ADMIN را کنار MANIFEST می‌پذیرد؛ MANIFEST mode ساخت دستی را در حالت عادی رد می‌کند.
منابع MANIFEST در حالت عادی فقط از طریق publish ساختار/metadata/actions جدید می‌گیرند؛ UI برای آن‌ها محدودیت را نشان می‌دهد.
حذف واقعی نیست: ADMIN با فرزند فعال قابل deprecate نیست؛ MANIFEST باید از نسخهٔ بعد حذف شود.
`Panels` تمام changeها را با resourceKey نمایش می‌دهد و چند MODULE را به یکی تقلیل نمی‌دهد.

Shell در `index.tsx` برای هر `UiModuleDefinition.registrationId` یک Host می‌سازد؛
`authorizedCatalogRoute` قبل از mount مسیر مؤثر را بررسی می‌کند؛ `catalogMenuItems/catalogMenuTree` navigation را گروه‌بندی می‌کنند.
`remote-loader.loadRemote` بر `(scope,url,exposedModule,integrity)` cache دارد، نه Resource MODULE.
پس شش route یک MFE سه remote بارگذاری نمی‌کنند. [نمونه](../apps/mf-test-multi-module/README.md) همین قرارداد را اجرا می‌کند.

## ۹. فهرست شواهد کد

مسیر Java تولید زیر `services/authorization-service/src/main/java/com/aurevia/authz/` است مگر خلافش ذکر شود.

| فایل / کلاس | تابع یا پیاده‌سازی مرتبط | چیزی که ثابت می‌کند |
|---|---|---|
| `resources/db/migration/V1__control_plane.sql` | CREATE panel/resource/grant/action | قیود پایه، رابطه parent و identity |
| `V28__canonical_resource_catalog.sql` | binding/import tables | مدل external و manifest |
| `V33__dynamic_ui_plugin_registry.sql`, `V48__effective_admin_ui_catalog.sql` | Artifact و حذف UNIQUE remote_name | چند نسخهٔ runtime در یک panel |
| `V51__microfrontend_governance_catalog.sql`, `V52__immutable_resource_manifest_versions.sql` | panel_id/index و unique version | چند منبع با یک مالک، نسخهٔ immutable |
| `V81__coordinated_manifest_activation.sql` | FKها، triggerها، effective_resource_catalog/catalog_grant_tuple | active pair، rollback و fail-closed |
| `registry/ResourceManifestService.java` | read, normalize, syntheticEquivalent, validateContract, validate | قرارداد واقعی، synthetic MODULE و قواعد درخت |
| همان | stage, diff, materialize, upsert, applyRevision, activateForArtifact | import/update/deprecate، اتصال revision/runtime |
| `registry/JdbcResourceManifestRepository.java` | upsertResource, deprecateMissing, definitionTree | panel مشترک، stable UUID و soft deletion |
| `registry/JdbcManifestReleaseRepository.java` | lock, bind, activateArtifact | serialization و یک active artifact |
| `registry/JdbcManifestCatalogProjection.java` | snapshot, enqueueChanges | delta parent/grant بعد از تغییر درخت |
| `api/ResourceManifestController.java` | importDraft, fetch, publish, activate | مسیر HTTP workflow |
| `api/dto/ResourceManifestDtos.java` | ResourceManifest, ModuleMetadata, ManifestResource | schema و defaultهای DTO |
| `registry/PanelModels.java`, `PanelAdministrationService.java`, `JdbcPanelRepository.java` | PanelCommand, validate, create/update | Panel همان ثبت MFE است؛ code/slug مستقل از resource key |
| `access/AccessAdministrationService.java` | createResource, updateResource, deprecateResource, validateResource, grant | مدیریت دستی، parent و grant مستقل |
| `ui/UiPluginRegistryService.java` | publish, syncFrontendManifest, activate, validate | runtime manifest، artifact association و staged sync |
| `authorization/AuthorizationDecisionService.java` | manifest, uiModule, discoveryGuard, check | permission و navigation ancestry دو مرحلهٔ جدا |
| `authorization/JdbcAuthorizationQueryRepository.java` | panels, permissionCandidates, activeResources | فقط active pair و effective resources |
| `infra/openfga/model.fga` | resource.manager/can_view | ordinary permission غیرارثی و manager ارثی |
| `services/superapp-bff/.../api/MeController.java` | context | نگاشت `/api/me/context` |
| `apps/mfe-admin/src/AccessStudio.tsx` | tree useMemo, openEditor, save, toggleAction | مدیریت چند sibling با UUID |
| `apps/mfe-admin/src/Panels.tsx` | importResourceManifest, publishResourceManifest, activateResourceManifest | نمایش Diff و release workflow |
| `apps/shell/src/manifest-routing.ts`, `index.tsx`, `remote-loader.ts` | catalogMenuTree, authorizedCatalogRoute, Host, loadRemote | یک runtime، چند مسیر مجاز |

مدل persistence این مسیر JDBC با record/DTO است؛ لازم نیست Entity/JPA یک‌به‌یک برای این جدول‌ها وجود داشته باشد.

## ۱۰. تست‌های مرتبط و قدرت اثبات آن‌ها

مسیر تست‌های Java زیر `services/authorization-service/src/test/java/com/aurevia/authz/` است.

| فایل / متد | fixture و assertion | اثبات / محدودیت |
|---|---|---|
| `registry/ResourceManifestServiceTest.acceptsCanonicalResourceTree` | application/module/page/component/field؛ validate بدون خطا | یک MODULE؛ شمارش چند MODULE را اثبات نمی‌کند |
| `registry/ResourceManifestWorkflowTest.hybridImportCreatesDraftAndPublishAppliesOnlyAfterApproval` | mocked repo، ۳ منبع و عدم upsert پیش از publish | workflow؛ نه DB واقعی و نه چند MODULE |
| همان: `aPublishedSemanticVersionCannotBeReusedForDifferentContent`, `moduleKeyMustMatchTheRegisteredPanel` | رد checksum جدید/کلید panel متفاوت | immutable و coupling کلید |
| همان: تست‌های malformed/frontend fields/manual mode | رد ورودی ناسازگار | مرز schema/import |
| `registry/ManifestReleaseIntegrationTest.publishRollbackAndRollForwardCoordinateCatalogActionsParentsBindingsAndAuthorization` | `module:{slug}.other` کنار synthetic؛ جابه‌جایی PAGE، rollback، parent tuple و route assertion | چند MODULE واقعاً مصرف شده‌اند؛ ولی ۳ MODULE/۶ PAGE/سه principal مستقل را نمی‌سنجد؛ کاربر root-admin است |
| همان: `failedMaterializationRollsBackCatalogPointersBindingsAndOutbox` | constraint خطای عمدی و ثابت ماندن pointers/outbox | atomic rollback |
| همان: `mismatchedArtifactAndUnpublishedActivationAreRejectedWithoutChangingActiveRelease` | رد pair نامعتبر و draft activation | coordinated release |
| `authorization/AuthorizationDecisionServiceManifestTest.leafGrantIncludesOnlyNavigationAncestorsWithoutAuthorizingThemOrOtherResources` | leaf مجاز، ancestors با actions خالی | navigation ≠ grant، با mock relationship |
| همان: `manifestPageGrantExposesItsMicrofrontendWithoutApplicationGrant` | PAGE-only و ظهور runtime | discovery مستقیم PAGE |
| همان: `authorizedModuleContainsOnlyAuthorizedRelativePagesAndRegisteredMetadata` و `panelWithNoAuthorizedPageIsNotAnEffectiveModule` | route subset و نبود runtime بدون PAGE | semantics فیلتر runtime |
| `sync/PermissionLifecycleIntegrationTest.moduleViewDoesNotAuthorizeEitherChildPage` | MODULE/view و DENY دو PAGE، containsOnlyKeys(module) | شواهد صریح ordinary permission غیرارثی؛ در این نوبت اجرا نشده |
| همان: `leafViewDoesNotAuthorizeItsComponent` | PAGE/view و DENY component/sibling | مرز ارث‌بری |
| همان: `manuallyCreatedMicroFrontendAndResourceTreeBehaveLikeAnImportedOne` | مسیر دستی و context | پشتیبانی manual؛ نه سناریوی کامل سه‌ماژولی |
| `sync/NormalPermissionMigrationIntegrationTest.remapsLegacyOrdinaryGrantsAndPendingEventsButPreservesExplicitAdminAndHistory` | مهاجرت tuple/grant | تبدیل semantics قدیمی؛ fixture چند MODULE به‌تنهایی شاهد کامل نیست |
| `apps/shell/src/manifest-routing.test.ts` | ۶ تست مسیر محلی/menu/guard | منطق Shell؛ نه browser remote fetch واقعی |
| `apps/mfe-admin/src/manifest-activation.test.ts` | ۳ تست activation | تعامل انتخاب revision/artifact؛ نه کلیک واقعی Resource Tree |
| `tests/e2e/admin-governance.contract.test.mjs` | تست contract/source | نام پوشه e2e اثبات browser E2E نیست |
| `tools/e2e-manifest-scenario/02-hybrid-import.mjs`, `10-publish-v2.mjs`, `14-rollback.mjs`, `33-failures.mjs` | سناریوی API و چاپ evidence | وجود script معادل اجرای موفق یا assertion سه‌ماژولی نیست |

تست‌های جدید:

- `MultiModuleManifestTest.shippedManifestNormalizesToOneApplicationThreeModulesAndSixPages`: parser/normalize واقعی روی فایل نمونه، ۱+۳+۶ و عدم materialization هنگام draft.
- `threeIndependentModuleKeysAlsoProduceTheSyntheticFourthModule`: محدودیت ماژول synthetic را با assertion عدد ۴ تثبیت می‌کند.
- `moduleCannotNestInsideSiblingAndPageCannotSkipModule`: رد MODULE زیر MODULE و PAGE زیر APPLICATION.
- `ManifestReleaseIntegrationTest.testMultiModuleThreePrincipalsRemainIsolatedAcrossPublishAndRollback`: DB واقعی؛ شمارش ۱ Panel، ۱ Application، ۳ Module، ۶ Page؛ panel و parent همه؛ import idempotent؛ سه کاربر بدون grant مدیریتی؛ مرحلهٔ MODULE/view-only، سپس PAGE grants؛ permissions/routes/menus/ancestor actions؛ نسخهٔ دوم چهار MODULE؛ rollback به سه؛ Benefits خارج از permissions؛ UUIDها ثابت.

Integration جدید در سطح سرویس‌های واقعی Spring/DB/OpenFGA است؛ HTTP BFF و browser E2E را جایگزین نمی‌کند.

## ۱۱. محدودیت‌ها، اصلاح پیشنهادی و ریسک

| شدت / لایه | رفتار فعلی و علت | انتظار / حداقل اصلاح | اصلاح معماری و ریسک |
|---|---|---|---|
| متوسط؛ manifest | `normalize` همواره `module:<slug>` می‌سازد | اگر سه کلید مستقل لازم باشد، قرارداد صریح برای حذف/انتخاب default MODULE نیاز است؛ نمونه فعلی default را Employee نام‌گذاری می‌کند | schema version جدید با resource roots صریح؛ خطر تغییر parentهای implicit و grantهای قدیمی |
| بالا در صورت برداشت اشتباه؛ authorization | MODULE/view به PAGE ارث نمی‌رسد (`model.fga`, V80) | برای read-only، مجوز PAGEها را صریح بدهید؛ این semantics فعلی است نه باگ | اگر محصول ارث‌بری view بخواهد، طراحی و migration مجوز مستقل لازم است؛ خطر گسترش ناخواستهٔ دسترسی |
| متوسط؛ publish | Artifact به یک resource revision قفل می‌شود (`applyRevision`, V81) | ساخت Artifact record جدید با remote موجود | جداکردن release binding از immutable runtime artifact؛ نیازمند migration و تست atomicity |
| پایین؛ Admin UX | فرزند جدید همیشه PAGE پیش‌فرض دارد (`AccessStudio.openEditor`) | انتخاب دستی MODULE ممکن است؛ پیشنهاد default بر اساس نوع parent | نیازی به بازطراحی ندارد؛ بهبود UX و تست مربوط کافی است |
| محدودیت ارزیابی | Docker engine و test-stack در این محیط در دسترس نبود | اجرای integration موجود روی PostgreSQL/Redis/OpenFGA و تست browser با سه login | بدون این اجرا ادعای E2E کامل معتبر نیست |

هیچ‌یک از این موارد با تغییر گستردهٔ backend «اصلاح» نشده‌اند. نمونه از semantics فعلی استفاده می‌کند و محدودیت‌ها آشکارند.

## ۱۲. نتیجه اجرای این نوبت و تحویل

- TypeScript نمونه: موفق.
- webpack production نمونه: موفق؛ یک `remoteEntry.js` تولید شد.
- Java: ۲۷ تست موفق؛ ۴ integration test skipped، صفر failure/error در اجرای نهایی. خطای اولیهٔ مسیر fixture در تست جدید اصلاح شد.
- Shell routing: ۶ تست موفق. Admin activation: ۳ تست موفق. Vitest ابتدا با EPERM محیط محدود متوقف شد؛ اجرای مجاز بیرون sandbox موفق شد.
- PostgreSQL/OpenFGA/browser E2E اجرا نشده؛ Docker پاسخ داد pipe مربوط به engine موجود نیست و test-stack تنظیم نشده بود.
- ثبت Panel یا grant در سرویس زنده انجام نشده؛ فایل registration، manifestهای V1/V2 و راهنمای ثبت آماده‌اند.

نمونه و دستورها: [apps/mf-test-multi-module/README.md](../apps/mf-test-multi-module/README.md).
کد backend تولید تغییر نکرد؛ تغییرات شامل MFE آزمایشی، تست‌ها، lockfile و این گزارش است.
