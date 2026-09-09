# راهنمای جامع تغییر نام پروژه Aurevia

این سند runbook تغییر نام همین repository است؛ یعنی توضیح می‌دهد برای تبدیل کامل `Aurevia` به نامی مانند `SPR` چه قراردادها، فایل‌ها، داده‌ها و تنظیمات بیرون از Git باید تغییر کنند. این راهنما از وضعیت واقعی مخزن استخراج شده است، نه یک فهرست عمومی Java/React.

> **نکتهٔ املایی:** نام موجود در کد `Aurevia` / `aurevia` است. عبارت `auervia` در فایل‌های tracked وجود ندارد. جست‌وجوی نهایی بهتر است هر دو املا را پوشش دهد.

## خلاصهٔ اجرایی

نام پروژه در این سامانه یک رشتهٔ واحد نیست. حداقل یازده قرارداد مستقل با چرخهٔ عمر و ریسک متفاوت وجود دارد:

| قرارداد | فعلی | نمونهٔ مقصد SPR | ریسک |
|---|---|---|---|
| نام نمایشی | `Aurevia`، `AUREVIA`، `آرویا` | `SPR` | کم |
| repository | `aurevia-super-app` | `spr-super-app` | متوسط |
| npm scope | `@aurevia/*` | `@spr/*` | متوسط |
| Java namespace | `com.aurevia` | `com.spr` | متوسط |
| Module Federation | `aurevia_admin` و مشابه | `spr_admin` | زیاد |
| ریشهٔ منابع مجوز | `application:aurevia` | `application:spr` | بسیار زیاد |
| Keycloak realm/client | `aurevia` / `aurevia-bff` | `spr` / `spr-bff` | بسیار زیاد |
| session/cookie/cache | `AUREVIA_SESSION`، `aurevia:*` | `SPR_SESSION`، `spr:*` | زیاد |
| PostgreSQL | DB/user با نام Aurevia | `spr_auth` / `spr` | زیاد |
| LDAP/AD | `AUREVIA.TEST`، `DC=aurevia` | `SPR.TEST`، `DC=spr` | بسیار زیاد |
| Compose/image/service | `aurevia`, `aurevia/...`, `aurevia-bff` | معادل‌های SPR | زیاد |

در اسکن baseline این سند، بدون احتساب خود سند، ۱۱۴۶ تطابق در ۳۸۴ فایل tracked و ۲۳۸ مسیر دارای نام Aurevia وجود داشت. بیشتر مسیرها مربوط به package جاوا هستند؛ بنابراین rename کامل یک refactor چندلایه و migration عملیاتی است، نه یک replace متنی.

اگر هدف فقط عوض‌کردن لوگو و نامی است که کاربر می‌بیند، بخش «تغییر فقط برند» کافی است. اگر قرار است هیچ اثر فنی از نام قبلی باقی نماند، تمام مراحل سند لازم‌اند.

## ۱. پیش از تغییر: جدول نگاشت را قطعی کنید

پیش از اولین commit این جدول را برای نام واقعی مقصد تکمیل و در PR ثبت کنید. برای مثال `SPR`:

```text
DISPLAY_NAME         Aurevia                 -> SPR
DISPLAY_NAME_UPPER   AUREVIA                 -> SPR
DISPLAY_NAME_FA      آرویا                   -> اس‌پی‌آر (یا نام مصوب)
REPO_NAME            aurevia-super-app       -> spr-super-app
NPM_SCOPE            @aurevia                -> @spr
JAVA_GROUP           com.aurevia             -> com.spr
MF_PREFIX            aurevia_                -> spr_
RESOURCE_ROOT        application:aurevia     -> application:spr
KEYCLOAK_REALM       aurevia                 -> spr
OIDC_CLIENT          aurevia-bff             -> spr-bff
COOKIE               AUREVIA_SESSION         -> SPR_SESSION
REDIS_PREFIX         aurevia:                -> spr:
COMPOSE_PROJECT      aurevia                 -> spr
IMAGE_NAMESPACE      aurevia/                -> spr/
DATABASE             aurevia_auth            -> spr_auth
DATABASE_USER        aurevia                 -> spr
AD_REALM             AUREVIA.TEST            -> SPR.TEST
AD_BASE_DN           DC=aurevia,DC=test      -> DC=spr,DC=test
PUBLIC_DOMAIN        workspace.aurevia.local -> workspace.spr.local
ENV_PREFIX           AUREVIA_                -> SPR_
```

این مقادیر الزاماً نباید یکسان باشند. برای نمونه، شرکت ممکن است نام نمایشی `SPR` را انتخاب کند ولی namespace پایدار `com.company.platform` و root پایدار `application:platform` را نگه دارد. این تصمیم هزینهٔ renameهای بعدی را کم می‌کند.

## ۲. انتخاب دامنهٔ rename

### تغییر فقط برند

این گزینه داده، login، packageها و integrationها را دست نمی‌زند. موارد زیر را تغییر دهید:

- عنوان و header در `apps/shell/public/index.html` و `apps/shell/src/index.tsx`؛
- ترجمهٔ `appName` در `packages/i18n/src/index.ts` و declaration متناظر در `index.d.ts`؛
- titleهای `apps/*/webpack.config.cjs`؛
- صفحهٔ `showcase/index.html` شامل meta description، title، brand، URL نمایشی و footer؛
- عنوان‌ها و معرفی‌ها در `README.md`، `docs/**/*.md`، `CHANGELOG.md`، `SECURITY.md` و `CONTRIBUTING.md`؛
- دادهٔ نمایشی mock مانند supplier در `infra/mock-operation/mappings/*.json`؛
- لوگو، favicon، تصاویر و theme صفحهٔ login، اگر بعداً اضافه شده باشند.

در این سناریو عمداً `@aurevia`، `com.aurevia`، `application:aurevia`، realm، cookie و database را نگه دارید. باقی‌ماندن آن‌ها باگ نیست؛ شناسهٔ فنی پایدار از برند مستقل است.

### تغییر کامل فنی

تمام بخش‌های بعدی را اجرا کنید. پیشنهاد می‌شود کار در سه release انجام شود: سازگاری، cutover، پاک‌سازی. تغییر هم‌زمان همهٔ شناسه‌ها در یک deploy فقط برای محیط تازه و بدون دادهٔ مهم قابل قبول است.

## ۳. فهرست دقیق فایل‌های repository

### ۳.۱ نام repository، لینک‌ها و مالکیت

| فایل/محدوده | چه چیزی تغییر کند |
|---|---|
| `package.json` | فیلد root `name` و workspaceهای `@aurevia/*` در scripts |
| `package-lock.json` | root package name، workspace package names و dependency links؛ با npm بازتولید شود |
| `pom.xml` | root `groupId` و `artifactId` |
| `services/*/pom.xml` | parent `groupId` و `artifactId` |
| `README.md` | clone URL، Pages URL، عنوان‌ها، badgeها و مثال‌ها |
| `apps/mfe-admin/src/OperatorGuide.tsx` | لینک hard-coded به GitHub و repository |
| `tools/release-runtime-verify.mjs` | URL پیش‌فرض GitHub Pages و نام envهای `AUREVIA_*` |
| `showcase/index.html` | لینک repository و domain نمایشی |
| `CODEOWNERS` | handle سازمان/تیم مانند `@aurevia/...`، در صورت تغییر سازمان |
| `.github/workflows/release.yml` | نام artifact انتشار `aurevia-super-app-*` |
| همهٔ `docs/**/*.md` | مسیر clone، نام directory، URL GitHub/Pages و commandهای نمونه |

پس از rename در GitHub/GitLab، موارد خارج از repository را نیز بررسی کنید: remote محلی، branch protection، environments، secrets، deploy keys، webhooks، GitHub Pages custom domain، package registry، runners و integrationهای issue tracker.

```bash
git remote set-url origin https://github.com/<owner>/spr-super-app.git
git remote -v
```

### ۳.۲ npm workspaces و importها

فایل‌های package زیر دارای `@aurevia` هستند و باید به `@spr` تغییر کنند:

```text
package.json
apps/mfe-admin/package.json
apps/mfe-finance/package.json
apps/mfe-hr/package.json
apps/mfe-reports/package.json
apps/shell/package.json
packages/authorization-sdk/package.json
packages/contracts/package.json
packages/http-client/package.json
packages/i18n/package.json
packages/sh-core-ui/package.json
tests/e2e/package.json
```

همهٔ importهای `@aurevia/*` در `apps/**/src` و `packages/**/src`، فایل‌های test و declaration نیز باید تغییر کنند. `package-lock.json` را دستی ویرایش نکنید؛ پس از ویرایش manifestها آن را بازتولید کنید:

```bash
npm install --package-lock-only
npm ci
npm run typecheck
npm test
npm run build
```

### ۳.۳ Module Federation

نام‌های Module Federation قرارداد runtime هستند. هم producer، هم مقدار `remote_name` ثبت‌شده در پایگاه داده/panel registry و هم assertionهای تست باید هماهنگ شوند.

| فایل | فعلی | مقصد نمونه |
|---|---|---|
| `apps/shell/webpack.config.cjs` | `aurevia_superapp_shell` | `spr_superapp_shell` |
| `apps/mfe-admin/webpack.config.cjs` | `aurevia_admin` | `spr_admin` |
| `apps/mfe-hr/webpack.config.cjs` | `aurevia_hr_ui` و `aurevia_hr_ui_0_1_0` | `spr_hr_ui` و `spr_hr_ui_0_1_0` |
| `apps/mfe-finance/webpack.config.cjs` | `aurevia_finance` | `spr_finance` |
| `apps/mfe-reports/webpack.config.cjs` | `aurevia_reports` | `spr_reports` |
| `apps/mfe-admin/src/Panels.tsx` | default `aurevia_${slug}` | `spr_${slug}` |
| `apps/mfe-admin/src/admin-route-catalog.ts` | نمونه‌های remote name | معادل `spr_*` |
| `services/**/db/migration/*.sql` | seed/registrationهای تاریخی | ویرایش نشوند؛ migration جدید |
| `apps/shell/src/remote-loader.test.ts` و سایر testها | expectationهای remote/container | نام جدید |
| `tools/verify-token-proxy.mjs` | assertion مربوط به `aurevia_admin` | `spr_admin` |

اگر فقط نام container در webpack عوض شود ولی registry هنوز `aurevia_admin` بدهد، `remoteEntry.js` دانلود می‌شود اما global container پیدا نمی‌شود و MFE load نخواهد شد.

### ۳.۴ Java/Maven namespace

برای `com.aurevia -> com.spr` این چهار دسته با هم تغییر می‌کنند:

1. `groupId` در `pom.xml` و parent هر دو service؛
2. declarationهای `package` و `import` در Java؛
3. مسیر فیزیکی main/test؛
4. stringهای namespace در تست‌های معماری یا configuration.

دایرکتوری‌های اصلی که باید با refactor IDE یا move قابل بازبینی جابه‌جا شوند:

```text
services/authorization-service/src/main/java/com/aurevia
services/authorization-service/src/test/java/com/aurevia
services/superapp-bff/src/main/java/com/aurevia
services/superapp-bff/src/test/java/com/aurevia
```

مقصد نمونه همین چهار مسیر با `com/spr` است. در این مخزن صدها فایل زیر این rootها هستند؛ فهرست‌کردن تک‌تک فایل‌ها ارزش نگهداری ندارد و همین چهار root دامنهٔ کامل است. پس از move:

```powershell
.\mvnw.cmd clean verify
```

نام user غیر-root در دو Dockerfile (`services/authorization-service/Dockerfile` و `services/superapp-bff/Dockerfile`) نیز `aurevia` است. rename آن اختیاری است، اما اگر عوض شد، `--chown` و تعریف/مصرف user باید هماهنگ باشند.

### ۳.۵ نام نمایشی frontend و showcase

| فایل | نقاط تغییر |
|---|---|
| `apps/shell/public/index.html` | `<title>Aurevia</title>` |
| `apps/shell/src/index.tsx` | brand mark `A` و eyebrow `AUREVIA / ...` |
| `packages/i18n/src/index.ts` و `index.d.ts` | `Aurevia Super App` / `سوپر اپ آرویا` |
| `apps/mfe-{admin,hr,finance,reports}/webpack.config.cjs` | `HtmlWebpackPlugin` title و HTML title |
| `showcase/index.html` | meta/title/aria/brand/domain/footer/repository link |
| `infra/mock-operation/mappings/*.json` | نام نمایشی شرکت و headerهای نمایشی |

### ۳.۶ resource keyها، manifestها و Authorization

این قسمت migration داده است. ریشهٔ canonical فعلی `application:aurevia` است و در grantها، hierarchy، cache، audit، OpenFGA tuples و registry استفاده می‌شود.

فایل‌های source manifest که مستقیماً آن را دارند:

- `apps/mfe-admin/mf-manifest.json`: routeهای operator guide، access studio، panels و identity؛
- `apps/mfe-reports/mf-manifest.json`: مسیر reports؛
- `apps/mfe-*/resource-manifest.json`: فقط resource keyها و actionهای مجوزدهی؛
- `apps/mfe-admin/src/admin-route-catalog.ts`: مجوزهای admin و fallbackهای legacy؛
- `apps/mfe-admin/src/Panels.tsx`: نمونهٔ manifest برای panel جدید؛
- `apps/shell/src/*.test.ts` و MFE testها: fixtureها و expectationها.

`apps/mfe-hr/resource-manifest.json` در وضعیت فعلی نام Aurevia ندارد و resourceهای `page:hr.*` را نگه می‌دارد؛ صرف rename پروژه نیاز به تغییر این فایل ندارد. `apps/mfe-finance/resource-manifest.json` نیز مستقل است. فایل‌های `apps/*/dist/resource-manifest.json` خروجی build هستند و نباید source تغییر محسوب شوند؛ بعد از build بازتولید می‌شوند.

در backend و seedها، جست‌وجوی `application:aurevia` را روی این محدوده‌ها اعمال کنید:

```text
services/authorization-service/src/main/java/**
services/authorization-service/src/test/java/**
services/authorization-service/src/main/resources/db/migration/**
services/superapp-bff/src/main/java/**
services/superapp-bff/src/test/java/**
infra/demo/integration-catalog.sql
infra/openfga/model-tests.yaml
tools/fresh-install-verify.mjs
tools/verify-token-proxy.mjs
tests/e2e/**
```

**هیچ Flyway migration اجراشده‌ای را ویرایش نکنید.** نام قدیمی در `V2__...sql` تا `V51__...sql` تاریخچهٔ schema/data است. یک migration جدید، مثلاً `V52__rename_aurevia_resource_root_to_spr.sql`، بسازید که به ترتیب:

1. وجود مقصد و نبود collision را بررسی کند؛
2. root و تمام child key/referenceها را در PostgreSQL منتقل کند؛
3. parent relationship، grants، route operations، panel manifests و payloadهای pending outbox را منتقل کند؛
4. tupleهای مقصد را در OpenFGA write کند؛
5. با reconciliation، برابری مجوزها را اثبات کند؛
6. بعد از دورهٔ سازگاری tupleهای قدیمی را delete کند؛
7. cacheهای decision را invalidate کند و audit migration بسازد.

نام دقیق جدول‌ها و ترتیب constraintها باید از schema همان release استخراج شود. migration را با حدس یا replace روی dump تولید نکنید.

### ۳.۷ Keycloak، OIDC و هویت canonical

| فایل | نقاط تغییر |
|---|---|
| `infra/keycloak/realm-aurevia.json` | نام فایل، `realm`، `clientId`، redirect URIها، first name و emailهای demo |
| `infra/keycloak/configure-samba-ldap.sh` | تمام `-r aurevia`، endpointها و lookup مربوط به client |
| `infra/docker-compose/compose.yml` | mount فایل realm و تمام URIهای OIDC/issuer/client |
| `services/superapp-bff/src/main/resources/application.yml` | client id و authorization/token/JWK/user-info URIها |
| `services/authorization-service/src/main/resources/application.yml` | `DIRECTORY_ISSUER` پیش‌فرض |
| `infra/demo/integration-catalog.sql` | issuer کاربران demo |
| `tools/fresh-install-verify.mjs` و `tools/verify-token-proxy.mjs` | issuer، realm file و login verification |
| `infra/mock-operation/gateway.conf` | user-info endpoint |

issuer بخشی از هویت canonical کاربر است. تغییر `/realms/aurevia` به `/realms/spr` باعث می‌شود همان `sub` از دید سامانه subject دیگری باشد. برای محیط دارای داده، migration نگاشت `(old_issuer, sub) -> (new_issuer, sub)` و تأثیر آن بر user/group/role/grant/audit را طراحی کنید. import فایل realm جدید به تنهایی کاربران موجود Keycloak را migrate نمی‌کند.

همهٔ redirect URIها، post-logout URIها، web originها، client secretهای محیط و تنظیمات reverse proxy را کنترل کنید. با تغییر realm/client، sessionهای قبلی را عمداً باطل و این downtime را اعلام کنید.

### ۳.۸ LDAP / Samba AD

موارد وابسته به نام domain:

- `infra/samba-ad/entrypoint.sh`: `--realm=AUREVIA.TEST` و `--domain=AUREVIA`؛
- `infra/docker-compose/compose.yml`: hostname، `DIRECTORY_BASE_DN` و `DIRECTORY_BIND_DN`؛
- `infra/keycloak/configure-samba-ldap.sh`: `usersDn` و `bindDn`؛
- اسناد و تست‌های directory sync که DN نمونه دارند.

تغییر AD realm/base DN معمولاً rename ساده نیست. برای production، domain جدید، trust یا export/import هویت‌ها و برنامهٔ cutover لازم است. `external_id`، group DNها، OU ruleها و grantهای وابسته باید قبل و بعد مقایسه شوند.

### ۳.۹ session، cookie، Redis و headerها

| قرارداد | فایل‌های مهم | اثر تغییر |
|---|---|---|
| `AUREVIA_SESSION` | BFF `application.yml`، OpenAPI configها، verifier، README | logout اجباری کاربران |
| `AUREVIA_OPERATION_SUPERSET` | `infra/superset-operation/superset_config.py`، `VaultLogoutHandler.java` | session جدید Superset |
| `AUREVIA_SS_*` | `OperationSupersetProxyController.java` | prefix cookieهای instance |
| `aurevia:session:v2` و fallback قدیمی | BFF config و `verify-token-proxy.mjs` | sessionهای Redis قبلی دیگر خوانده نمی‌شوند |
| `aurevia:token-vault` | BFF config | token cache قبلی از دسترس خارج می‌شود |
| `aurevia:openfga:check` | Authorization config و adapter | decision cache cold start |
| `X-Aurevia-Subject/Issuer` | Nginx/mock gateway، Superset middleware و Java | قرارداد integration شکسته می‌شود |

برای headerها یک دورهٔ سازگاری پیشنهاد می‌شود: producer هر دو `X-Aurevia-*` و `X-Spr-*` را بفرستد، consumer ابتدا جدید و سپس قدیمی را بخواند، و پس از telemetry نام قدیمی حذف شود. برای cookie/Redis معمولاً cutover و logout کنترل‌شده ساده‌تر و امن‌تر از dual-read است.

### ۳.۱۰ Spring property prefix و env vars

دو service از prefix سفارشی `aurevia:` در `application.yml` و `application-prod.yml` استفاده می‌کنند. تغییر آن به `spr:` باید هم‌زمان با همهٔ `@ConfigurationProperties`، `@Value("${aurevia...}")` و test propertyها انجام شود.

متغیرهای ابزارها که prefix برند دارند:

```text
AUREVIA_ORIGIN
AUREVIA_SHOWCASE_URL
AUREVIA_BASE_URL
AUREVIA_DEMO_USERNAME
AUREVIA_DEMO_PASSWORD
AUREVIA_SKIP_DEMO_CATALOG_REFRESH
```

برای migration بدون شکست CI، یک release مقدار جدید `SPR_*` را بخواند و در نبود آن به `AUREVIA_*` fallback کند؛ release بعدی warning بدهد و release سوم fallback را حذف کند. secrets واقعی در `.env`، CI/CD و secret manager خارج از Git نیز باید منتقل شوند. `.env` را commit نکنید؛ فقط `.env.example` را به‌روز کنید.

### ۳.۱۱ Compose، imageها، شبکه، database و proxy

`infra/docker-compose/compose.yml` بیشترین تراکم شناسهٔ زیرساختی را دارد. این موارد را مستقل بررسی کنید:

- top-level `name: aurevia`؛
- service `aurevia-bff` و تمام `depends_on`/hostnameهای مصرف‌کننده؛
- imageهای `aurevia/authorization-service`, `aurevia/superapp-bff`, `aurevia/superset*`؛
- PostgreSQL DB/user: `aurevia_auth` / `aurevia` و healthcheck/connection string؛
- Keycloak realm/client/URI و mount فایل realm؛
- LDAP realm/domain/DN/hostname؛
- email و first/last name حساب admin Superset.

فایل‌های همراه:

- `infra/nginx/nginx.conf`: upstream service `aurevia-bff` و log format `aurevia_safe`؛
- `infra/mock-operation/gateway.conf`: Keycloak URI و headerها؛
- `infra/superset-operation/superset_config.py`: class، header و cookie؛
- `infra/openfga/model-tests.yaml`: model test name؛
- `infra/samba-ad/Dockerfile`: نام entrypoint؛
- `services/*/Dockerfile`: image build user؛
- `.env.example`: defaults و secret names مرتبط؛
- ابزارهای `tools/*.mjs`: service name، DB/user، realm، URL و assertionها.

Compose project name در نام network/volume/containerها اثر دارد. عوض‌کردن آن stack جدیدی می‌سازد و volumeهای قبلی را خودکار به stack جدید وصل نمی‌کند. volume قبلی را حذف نکنید. ابتدا نام واقعی منابع را با `docker compose config` و `docker volume ls` ثبت، backup/restore را تمرین و سپس مقصد را صریح map کنید.

تغییر `aurevia_auth -> spr_auth` و user به معنی rename داده نیست. برای production یا نام فنی قدیمی را نگه دارید، یا database/user مقصد را بسازید، restore/ownership/grantها را verify و بعد connection string را عوض کنید.

### ۳.۱۲ migrationها، fixtureها، tests و مستندات تاریخی

همهٔ matchها نباید حذف شوند:

- migrationهای Flyway اجراشده باید immutable بمانند؛
- سندهای audit/validation تاریخ‌دار می‌توانند نام تاریخی را برای قابلیت ردیابی حفظ کنند؛
- changelog باید واقعیت releaseهای گذشته را عوض نکند؛
- fixture/testهایی که compatibility نام قدیمی را اثبات می‌کنند باید عمداً باقی بمانند و comment داشته باشند؛
- فایل‌های build در `dist/` و `target/` باید پاک‌سازی و بازتولید شوند، نه دستی patch.

هدف جست‌وجوی نهایی «صفر match» نیست؛ هدف این است که هر match باقی‌مانده در allowlist مستند قرار گیرد.

## ۴. ترتیب اجرای پیشنهادی

### Release 1: سازگاری

1. جدول نگاشت و مالک هر قرارداد را تصویب کنید.
2. backup قابل restore از PostgreSQL، OpenFGA، Keycloak، Redis و volumeهای Superset/AD بگیرید.
3. نام نمایشی SPR را منتشر کنید.
4. env/headerهای جدید را با fallback قدیمی اضافه کنید.
5. resource root/remote name جدید را به‌صورت alias یا dual registration ایجاد کنید.
6. telemetry برای استفاده از شناسه‌های قدیمی اضافه کنید.

### Release 2: cutover

1. npm/Java namespace و build artifactها را تغییر دهید.
2. imageها و deployment config جدید را منتشر کنید.
3. migration دادهٔ PostgreSQL و OpenFGA را اجرا و reconcile کنید.
4. Keycloak/LDAP را طبق طرح مصوب منتقل کنید.
5. registry MFE و `remote_name`ها را عوض کنید.
6. cookie/Redis namespace را تغییر دهید و logout سراسری انجام دهید.
7. smoke/E2E کاربران مجاز و غیرمجاز را اجرا کنید.

### Release 3: پاک‌سازی

1. پس از پایان window بازگشت، aliasها و dual-write را حذف کنید.
2. secrets/env/headerهای قدیمی را revoke یا حذف کنید.
3. tupleها، cache namespaceها و registrationهای قدیمی را پاک کنید.
4. redirect repository/URL قدیمی و telemetry را برای یک دوره نگه دارید.
5. matchهای باقی‌مانده را با allowlist تاریخی نهایی کنید.

## ۵. دستورهای جست‌وجو و کنترل

برای جست‌وجوی فقط فایل‌های version-controlled از `git grep` استفاده کنید تا `node_modules`، `dist` محلی و secretهای `.env` وارد گزارش نشوند:

```bash
git grep -I -n -i -E 'aurevia|auervia'
git grep -I -n -E 'Aurevia|AUREVIA|آرویا'
git grep -I -n -E 'com\.aurevia|@aurevia'
git grep -I -n -E 'application:aurevia|aurevia_(admin|hr|finance|reports|superapp)'
git grep -I -n -E 'realms/aurevia|aurevia-bff|realm-aurevia'
git grep -I -n -E 'AUREVIA_SESSION|AUREVIA_OPERATION_SUPERSET|aurevia:(session|token-vault|openfga)'
git ls-files | rg -i 'aurevia|auervia'
```

برای فایل‌های local و تنظیمات خارج از Git، جداگانه و بدون چاپ secret value بررسی کنید:

```bash
rg -l -i 'aurevia|auervia' .env* infra tools .github
```

قبل از اجرای stack، render نهایی Compose را کنترل کنید:

```bash
docker compose --env-file .env -f infra/docker-compose/compose.yml config
```

## ۶. ماتریس راستی‌آزمایی

| حوزه | آزمون پذیرش |
|---|---|
| Git/build | clone با URL جدید، `npm ci` و Maven build از workspace تمیز |
| frontend | title/brand جدید، Shell و هر چهار MFE بدون خطای container |
| registry | remote entry، remote name، exposed module و manifest version هماهنگ |
| authentication | login، callback، refresh، logout و post-logout با realm/client جدید |
| session | cookie جدید HttpOnly/Secure/SameSite صحیح؛ session قدیمی طبق تصمیم رد یا migrate شود |
| authorization | direct/group/role/inheritance و deny برای کاربران غیرمجاز |
| OpenFGA | reconciliation بدون drift؛ tuple قدیمی فقط طبق برنامه باقی بماند |
| database | count/checksum رکوردهای حساس قبل و بعد؛ Flyway history سالم |
| LDAP | full sync، OU mapping، گروه‌ها و canonical subjectها |
| proxy | route resolution، legacy token forwarding، Superset public/operation proxy |
| observability | log/metric/dashboard/alert با labelهای جدید و correlation ID سالم |
| release | image pull، healthcheck، rollback تمرین‌شده و backup قابل restore |

فرمان‌های اصلی repository:

```powershell
npm ci
npm run typecheck
npm test
npm run build
.\mvnw.cmd clean verify
npm run infra:preflight
npm run infra:verify
npm run infra:verify:token-proxy
npm run release:verify:runtime
```

سه دستور آخر به stack در حال اجرا و credential/config مناسب نیاز دارند. secret را در command history یا Git قرار ندهید.

## ۷. برنامهٔ rollback

پیش از cutover پاسخ این پرسش‌ها باید مستند باشد:

- آیا deployment قبلی هنوز image و config قابل اجرا دارد؟
- آیا DB migration برگشت‌پذیر است یا rollback به restore نیاز دارد؟
- آیا tupleهای قدیمی OpenFGA تا پایان window حفظ شده‌اند؟
- آیا realm/client قدیمی می‌تواند موقتاً دوباره فعال شود؟
- آیا registry می‌تواند remote name/entry قبلی را برگرداند؟
- آیا DNS/repository redirect و certificate قبلی معتبرند؟
- آیا logout سراسری و از دست رفتن session پذیرفته شده است؟

برای migrationهای داده، rollback را به down migration مخرب وابسته نکنید. backup restore آزمایش‌شده، config قبلی، image قبلی و script بازگردانی tuple/registry را قبل از deploy آماده کنید.

## ۸. چک‌لیست PR نهایی برای `SPR`

- [ ] جدول نگاشت نام‌ها تصویب و collisionهای npm/Maven/DNS/registry بررسی شده است.
- [ ] نام نمایشی، ترجمه، title، logo و showcase به SPR تغییر کرده است.
- [ ] repository، لینک‌ها، badgeها، Pages و remote Git به مقصد جدید اشاره می‌کنند.
- [ ] تمام package manifestها/importها از `@aurevia` به `@spr` منتقل و lockfile بازتولید شده است.
- [ ] `com.aurevia` در POM، source، test و مسیر فایل‌ها به `com.spr` refactor شده است.
- [ ] Module Federation producer، DB registry، defaults و tests همگی `spr_*` را استفاده می‌کنند.
- [ ] برای `application:aurevia -> application:spr` migration جدید و reconciliation نوشته شده است؛ migration تاریخی ویرایش نشده است.
- [ ] realm/client/issuer جدید و migration هویت canonical آزمایش شده است.
- [ ] LDAP/AD rename یا تصمیم رسمی برای ثابت‌ماندن آن ثبت شده است.
- [ ] cookie، Redis، OpenFGA cache، Superset cookie و headerها migration/cutover مشخص دارند.
- [ ] Compose service/project/image/database و Nginx upstreamها هماهنگ‌اند.
- [ ] `.env.example`، CI/CD، secret manager، webhook و runnerها به‌روز شده‌اند؛ `.env` commit نشده است.
- [ ] npm، Maven، Compose preflight، E2E و security smoke test موفق‌اند.
- [ ] backup restore و rollback در staging تمرین شده است.
- [ ] تمام matchهای قدیمی باقی‌مانده در allowlist تاریخی توجیه شده‌اند.

## منابع این سند

این راهنما از قراردادها و فایل‌های tracked همین repository استخراج شده است. مرجع نهایی هر ادعا source code، manifest، configuration و migrationهای همان commit است؛ به همین دلیل هنگام اجرای rename باید جست‌وجوهای بخش ۵ دوباره اجرا شوند تا فایل‌های اضافه‌شده پس از نگارش سند نیز پوشش داده شوند.
