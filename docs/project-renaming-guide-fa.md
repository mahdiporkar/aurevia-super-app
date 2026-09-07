# راهنمای تغییر نام پروژه آرویا

## دامنه تغییر نام

«نام پروژه» در این repository یک مقدار واحد نیست. پیش از تغییر مشخص کنید کدام لایه‌ها باید عوض شوند:

| لایه | نمونه فعلی | اثر |
|---|---|---|
| نام نمایشی | `Aurevia` / `آرویا` | متن UI و مستندات |
| نام repository | `aurevia-super-app` | URL Git، badgeها و CI/Pages |
| namespace جاوا | `com.aurevia` | packageها، importها و مسیر فایل‌ها |
| scope پکیج npm | `@aurevia/*` | dependencyها و workspace scripts |
| شناسه‌های runtime | `application:aurevia` | دیتابیس، grantها و tupleهای OpenFGA |
| IAM | realm `aurevia` و client `aurevia-bff` | issuer، login و sessionهای کاربران |
| زیرساخت | image/network/database/cache names | deployment و داده‌های پایدار |

برای تغییر صرفاً برند، فقط لایه نام نمایشی را عوض کنید. تغییر namespace یا شناسه‌های runtime یک migration فنی است و نباید با search/replace کور انجام شود.

## ترتیب پیشنهادی

1. نام‌های جدید را در یک جدول نگاشت تصویب کنید.
2. backup دیتابیس PostgreSQL، OpenFGA، Keycloak و Redis تهیه کنید.
3. ابتدا identifierهای کد و build را تغییر دهید.
4. migrationهای forward-only برای داده‌های runtime بسازید.
5. IAM و deployment را با دوره سازگاری یا cutover هماهنگ کنید.
6. تست کامل و سپس تغییر URL repository/Pages را انجام دهید.

نمونه جدول تصمیم:

```text
old display: Aurevia             -> new display: Example
old repo:    aurevia-super-app   -> new repo:    example-super-app
old Java:    com.aurevia         -> new Java:    com.example
old npm:     @aurevia            -> new npm:     @example
old root:    application:aurevia -> new root:    application:example
old realm:   aurevia             -> new realm:   example
old client:  aurevia-bff         -> new client:  example-bff
```

## ۱. نام نمایشی و لینک‌ها

موارد زیر را بررسی کنید:

- `README.md`، فایل‌های `docs/`، `showcase/` و titleهای HTML؛
- ترجمه‌ها در `packages/i18n` و header پوسته در `apps/shell`؛
- لوگو، favicon، متن ایمیل و صفحه login Keycloak؛
- badgeها و لینک‌های GitHub Pages در README؛
- لینک ثابت راهنما در `apps/mfe-admin/src/OperatorGuide.tsx`؛
- `CODEOWNERS`، `SECURITY.md` و اطلاعات تماس سازمانی.

اگر فقط rebranding انجام می‌شود، کلیدهایی مثل `application:aurevia` را تغییر ندهید؛ کلید فنی می‌تواند مستقل از label باقی بماند و ریسک migration را کم کند.

## ۲. repository، GitHub Actions و GitHub Pages

- repository را در GitHub rename کنید؛ GitHub معمولاً redirect می‌دهد اما به آن به‌عنوان قرارداد دائمی تکیه نکنید.
- remote محلی را به‌روزرسانی کنید:

```bash
git remote set-url origin https://github.com/<owner>/<new-repository>.git
git remote -v
```

- `.github/workflows/ci.yml` و `.github/workflows/pages.yml`، badgeها، Pages base path و لینک‌های absolute را بررسی کنید.
- branch protection، environments، secrets، deploy keys، webhookها، package permissions و runnerها را دوباره کنترل کنید.
- URLهای مصرف‌کننده Remote Entry و CSP allowlist را به‌روزرسانی کنید.

## ۳. پکیج‌های Node و workspaceها

فایل‌های زیر محل اصلی تغییرند:

- `package.json` ریشه: `name`، workspace commandها و نام packageهای هدف؛
- `apps/*/package.json` و `packages/*/package.json`؛
- تمام importهای `@aurevia/*`؛
- تنظیمات Module Federation شامل `name` و `remoteName`؛
- `package-lock.json` که باید با npm بازتولید شود، نه دستی.

پس از تغییر `package.json`ها:

```bash
npm install --package-lock-only
npm ci
npm run typecheck
npm test
npm run build
```

نام Module Federation یک قرارداد runtime است. producer و registration ذخیره‌شده در panel باید هم‌زمان تغییر کنند؛ در غیر این صورت Shell فایل را می‌گیرد ولی container را پیدا نمی‌کند.

## ۴. Maven و namespace جاوا

برای تغییر namespace سازمانی:

- `groupId` و `artifactId` در `pom.xml` ریشه و `services/*/pom.xml`؛
- declarationهای `package com.aurevia...` و importها؛
- مسیر فیزیکی `src/main/java/com/aurevia` و `src/test/java/com/aurevia`؛
- نام main class، test selectorها، ArchUnit ruleها و logging categoryها؛
- image labelها و artifact pathهای CI.

جابجایی packageها را با refactor IDE یا ابزار قابل بازبینی انجام دهید. سپس:

```bash
./mvnw clean verify
```

در Windows از `mvnw.cmd clean verify` استفاده کنید.

## ۵. شناسه‌های Authorization و OpenFGA

این بخش حساس‌ترین قسمت است. نمونه‌های مهم:

- `application:aurevia` و فرزندان آن؛
- resource keyهای داخل `resource-manifest.json`؛
- grantهای PostgreSQL و tupleهای OpenFGA؛
- parent relationshipها و cache keyهای تصمیم؛
- policyها، route operationها و تست‌هایی که کلید canonical دارند.

فایل migration اجراشده را ویرایش نکنید. یک Flyway migration جدید بسازید که با ترتیب درست موارد زیر را migrate کند:

1. resource keyهای PostgreSQL؛
2. referenceها و payloadهای Outbox؛
3. Manifest/registrationهای جدید؛
4. tupleهای OpenFGA با write کلید جدید و delete کلید قدیمی؛
5. invalidation cache؛
6. audit قابل ردیابی و امکان reconciliation.

بهتر است یک دوره dual-read/dual-write یا maintenance window تعریف شود. تا زمانی که همه grantها و parent edgeها منتقل نشده‌اند، کلید قدیمی را حذف نکنید. پس از cutover، reconciliation OpenFGA و تست کاربران مجاز/غیرمجاز الزامی است.

## ۶. Keycloak، issuer و session

تغییر نام realm از تغییر label جداست. issuer بخشی از هویت canonical کاربر است؛ تغییر `/realms/aurevia` به realm جدید باعث می‌شود همان `sub` به‌عنوان subject دیگری دیده شود.

موارد مرتبط:

- `OIDC_CLIENT_ID` و `OIDC_CLIENT_SECRET`؛
- `OIDC_AUTHORIZATION_URI`، `OIDC_TOKEN_URI`، `OIDC_JWK_SET_URI` و `OIDC_USER_INFO_URI`؛
- redirect URI و post-logout URI؛
- `DIRECTORY_ISSUER` و issuer ذخیره‌شده کاربران؛
- realm import در `infra/keycloak`؛
- نقش‌ها، mapperها، groupها و accountهای demo.

اگر realm عوض می‌شود، migration نگاشت `(old issuer, sub)` به `(new issuer, sub)` را طراحی کنید و همه sessionهای قدیمی را باطل کنید. تغییر صرفاً display name realm این اثر را ندارد.

نام cookie `AUREVIA_SESSION` و namespaceهای Redis مثل `aurevia:session:*` و `aurevia:token-vault` نیز باید بررسی شوند. تغییر namespace عملاً sessionهای قبلی را نامعتبر می‌کند؛ این رفتار باید بخشی از برنامه cutover باشد.

## ۷. PostgreSQL، Redis و داده‌های پایدار

نام‌های زیر ممکن است برند را در خود داشته باشند:

- database/user/schema و secretهای `POSTGRES_*`؛
- Redis namespaceها؛
- Flyway history و seed/demo emailها؛
- backup bucket، volume و retention jobها.

تغییر نام database یا volume با تغییر داده داخل آن یکی نیست. ابتدا مقصد جدید را بسازید، backup/restore را آزمایش کنید و فقط پس از verification مصرف‌کننده‌ها را منتقل کنید. volume را برای rename حذف نکنید.

## ۸. Compose، container، شبکه و تنظیمات محیطی

جست‌وجو کنید:

- `infra/docker-compose/compose.yml`؛
- Dockerfileها و imageهایی مانند `aurevia/...`؛
- hostnameهای داخلی serviceها؛
- network، volume، certificate subject/SAN و truststoreها؛
- `.env.example`، secret manager و متغیرهای CI/CD؛
- Nginx/ingress، CSP، CORS allowlist و Remote Entry URLها؛
- metric labelها، dashboardها، alertها و log indexها.

نام service داخلی را فقط وقتی عوض کنید که تمام dependency URLها و health checkها هم‌زمان تغییر کنند. تغییر display brand نیازی به rename شبکه و database ندارد.

## ۹. جست‌وجوی کنترل‌شده

قبل و بعد از تغییر از جست‌وجوهای تفکیک‌شده استفاده کنید:

```bash
rg -n "Aurevia|آرویا" . -g '!node_modules' -g '!.git'
rg -n "aurevia-super-app|mahdiporkar/aurevia" . -g '!node_modules' -g '!.git'
rg -n "com\.aurevia|@aurevia" . -g '!node_modules' -g '!.git'
rg -n "application:aurevia|AUREVIA_SESSION|aurevia:" . -g '!node_modules' -g '!.git'
rg -n "realms/aurevia|aurevia-bff" . -g '!node_modules' -g '!.git'
```

نتایج را بر اساس دسته بررسی کنید؛ migrationهای تاریخی می‌توانند برای audit نام قدیمی را نگه دارند. تغییر متن داخل migration اجراشده ممنوع است.

## ۱۰. برنامه انتشار و rollback

پیشنهاد می‌شود rename در سه release انجام شود:

1. **سازگاری**: aliasها، نام نمایشی جدید و پشتیبانی هم‌زمان identifierهای قدیم/جدید؛
2. **Cutover**: تغییر config، IAM، registry و OpenFGA و invalidation session/cache؛
3. **پاک‌سازی**: حذف alias قدیمی پس از پایان telemetry و دوره rollback.

Rollback باید شامل config قبلی، بازگرداندن routeها و tupleها، restore آزموده‌شده دیتابیس و امکان فعال‌کردن client/realm قبلی باشد. rollback را قبل از Production در staging تمرین کنید.

## چک‌لیست نهایی

- `npm ci && npm run typecheck && npm test && npm run build` موفق؛
- `./mvnw clean verify` موفق؛
- Compose preflight و fresh-install verification موفق؛
- login، logout، CSRF و session renewal موفق؛
- Manifest، navigation و Remote Entryها سالم؛
- grant مستقیم/گروه/نقش و inheritance در OpenFGA سالم؛
- proxy route، Legacy auth و Superset تست شده؛
- هیچ secret یا token با نام‌گذاری جدید وارد Git نشده؛
- badge، Pages، remote، webhook و documentation linkها صحیح؛
- backup و rollback ثبت و آزمایش شده‌اند.

