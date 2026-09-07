# راهنمای جامع آزمون End-to-End راهبری سوپر اپ Aurevia

نسخه ۱.۰ — ۱۴۰۵/۰۶/۱۷ — دامنه: ۱۸ صفحه مدیریتی و ۱۱۸ فیلد فرم

## ۱. هدف و تعریف Done

این سند هم دستور اجرای تست و هم گزارش اجرای baseline است. آزمون کامل راهبری فقط «بازشدن صفحه»
نیست؛ باید مسیر ورود، مجوز route، validation فرم، mutation، وضعیت async، نتیجه مؤثر، تست منفی،
Audit و Rollback را پوشش دهد.

یک سناریو Done است وقتی:

1. کاربر مجاز صفحه و داده را می‌بیند و کاربر غیرمجاز 401/403 یا catalog محدود می‌گیرد؛
2. مقدار معتبر ذخیره و مقدار نامعتبر بدون mutation رد می‌شود؛
3. اثر در PostgreSQL و در صورت دسترسی در Outbox/OpenFGA تأیید می‌شود؛
4. secret/token در UI، response، log و screenshot ظاهر نمی‌شود؛
5. Audit دارای actor، target، result و Correlation ID است؛
6. Rollback آزمایش شده و اثر نهایی دوباره کنترل می‌شود.

## ۲. محدوده و لایه‌های تست

| لایه | چه چیزی را اثبات می‌کند | فرمان/روش |
|---|---|---|
| UI contract | ۱۸ route، مجوز هر route، ۱۱۸ فیلد و حالت‌های شرطی حذف نشده‌اند | `npm test` |
| Frontend unit | loader، routing، guard، SDK و redaction | `npm test` |
| Backend unit/integration | validation، proxy، manifest، policy، OpenFGA و security | `.\mvnw.cmd test` |
| Runtime infrastructure | migration، artifact فعال، Outbox، drift و دسترسی administrator | `npm run infra:verify` |
| Edge smoke | تمام URLهای مدیریتی و مرز authentication فایل‌های MFE | دستور بخش ۵ |
| مرورگر تعاملی | layout، focus، dropdown، toast، modal و workflow واقعی | چک‌لیست بخش‌های ۷ تا ۱۰ |

محدودیت baseline این اجرا: اتصال automation مرورگر داخلی به علت نبود metadata مربوط به sandbox
در runner برقرار نشد. بنابراین ادعای «بازبینی بصری خودکار» نمی‌شود؛ پوشش اجراشده شامل contract،
unit/integration، runtime و Edge است. چک‌لیست مرورگر زیر برای اجرای دستی یا runner دارای Browser
ارائه شده و نباید با تست‌های اجراشده اشتباه گرفته شود.

## ۳. داده و نقش‌های آزمایش

| Persona | انتظار |
|---|---|
| `administrator` | دسترسی کامل راهبری و تست مثبت mutation |
| کاربر فقط‌خواندنی | مشاهده صفحات مجاز بدون امکان mutation |
| کاربر بدون مجوز | نبود منو و پاسخ 403 برای API محافظت‌شده |
| کاربر عضو OU هدف | مسیر explain و دسترسی MFE مثبت |
| کاربر خارج OU | explain منفی و عدم دسترسی |

credentialهای local فقط از fixture محیط دریافت شوند. password، token و cookie در سند، خروجی CI
یا Git قرار نگیرند. داده تست mutation باید prefix یکتای `E2E_YYYYMMDD_` داشته باشد و در پایان
غیرفعال یا پاک‌سازی منطقی شود.

## ۴. آماده‌سازی

```powershell
docker compose --env-file .env --profile superset `
  -f infra/docker-compose/compose.yml ps
npm run infra:verify
```

همه سرویس‌های دارای healthcheck باید `healthy` باشند. قبل از mutation از نسخه فعال Panelها،
تعداد Outbox pending/dead-letter و شناسه مدل OpenFGA یادداشت بگیرید.

## ۵. اجرای خودکار

```powershell
npm test
.\mvnw.cmd test
npm run infra:verify
```

Smoke تمام صفحات:

```powershell
$paths = @(
  '/admin/operator-guide','/admin/ou-access/ous','/admin/ou-access/groups',
  '/admin/ou-access/applications','/admin/ou-access/explain','/admin/access-studio',
  '/admin/panels','/admin/proxy-routes/targets','/admin/proxy-routes/routes',
  '/admin/proxy-routes/operations','/admin/outbound-connections','/admin/outbound-auth',
  '/admin/integration-test','/admin/superset-instances','/admin/identity',
  '/admin/logs/api','/admin/logs/audit','/admin/superset'
)
foreach ($path in $paths) {
  curl.exe -s -o NUL -w "$path %{http_code} %{content_type}`n" "http://localhost:8443$path"
}
```

صفحه‌های SPA در Edge باید `200 text/html` بدهند. این تست به‌تنهایی render شدن MFE را اثبات نمی‌کند.
فراخوانی `/api/mfe/{slug}/remoteEntry.js` بدون نشست باید 401 باشد؛ بعد از ورود باید 200، MIME
قابل اجرای JavaScript و body غیرخالی باشد.

## ۶. ماتریس ۱۸ صفحه

| صفحه | سناریوی اصلی | تست منفی/حالت مرزی | اثر و Rollback |
|---|---|---|---|
| راهنمای فرم‌ها | نمایش متن و navigation | کاربر بدون admin منو را نبیند | بدون mutation |
| OUهای سازمانی | لیست، path و زمان sync | نبود داده/خطای sync | فقط‌خواندنی؛ اصلاح در Directory |
| Access Groupها | ساخت گروه و Rule، Preview | کد غلط، OU غیرفعال، `SUBTREE` گسترده | disable Rule/group و بررسی اعضا |
| دسترسی MFE | Grant گروه به Panel | Panel/group غیرفعال و grant تکراری | Revoke و انتظار برای Outbox |
| Explain User | مسیر مثبت و منفی کاربر | user ناشناخته | بدون mutation |
| Access Studio | Resource، action و grant | key تکراری، cycle، ویرایش Manifest | Revoke/Deprecated و reconcile |
| Microfrontendها | Panel، artifact، manifest و overlay | URL/MIME/SRI/schema/version غلط | Activate نسخه قبلی یا disable |
| Service Targets | ساخت و Health | host خارج allowlist، timeout غلط | disable Target |
| Proxy Routes | Validate و Resolve | prefix هم‌پوشان، rewrite ناامن | disable Route/بازگردانی version |
| Route Operations | Match و مجوز | method/path نامعتبر یا ambiguity | disable Operation |
| اتصال‌های Legacy | origin و reference | URL/query یا TLS policy نامعتبر | disable Connection |
| Auth Profile | Forward/Legacy | secret value، pointer و transport غلط | disable Profile/بازگشت version |
| آزمایشگاه اتصال | تست OAuth2 و Legacy | upstream 401/timeout/oversize | بدون mutation؛ ثبت Correlation |
| محیط‌های Superset | Instance و mapping | zone/mapping تکراری | disable mapping/instance |
| هویت و نقش | Role و assignment | role key تکراری/subject غیرفعال | Revoke assignment |
| API Logs | فیلتر و جزئیات | status خارج 100..599/بدون مجوز | بدون mutation |
| Audit Logs | actor/target/result/correlation | result نامعتبر/بدون مجوز | immutable |
| گزارش‌ها و داشبوردها | grant سطح دارایی | subject/asset غیرفعال | Revoke grant |

## ۷. الگوی تست هر فرم و تمام حالت‌ها

برای هر فیلدِ نمایه‌شده در بخش ۲۲ راهنمای فرم‌ها، این شش حالت اجرا شود:

1. **خالی:** فیلد الزامی باید پیام واضح بدهد؛ اختیاری باید payload درست بسازد.
2. **معتبر حداقل/حداکثر:** مرز طول و عدد پذیرفته شود.
3. **نامعتبر:** regex، enum، URL، path، SemVer یا JSON بدون درخواست mutation رد شود.
4. **وابستگی:** تغییر mode/type باید فیلد شرطی را نمایش/غیرفعال و validation را هماهنگ کند.
5. **ویرایش هم‌زمان:** با `version` قدیمی، `VERSION_CONFLICT` و بدون overwrite دریافت شود.
6. **امنیت:** HTML/script، traversal، URL خارج allowlist و secret خام رد یا بی‌اثر شود.

### حالت‌های شرطی ضروری

- Resource نوع `EXTERNAL_RESOURCE`: هر سه `externalSystem/externalType/externalId`.
- Resource با source=`MANIFEST`: در production فقط visibility قابل ویرایش باشد.
- Panel modeهای `MANUAL/MANIFEST/HYBRID` و classificationهای `REAL/DEMO`.
- Navigation نوع `GROUP/PAGE/EXTERNAL_LINK` و sourceهای `ADMIN/MANIFEST`.
- OU combinerهای `ANY_OF/ALL_OF` و matchهای `EXACT/SUBTREE`.
- Auth modeهای `FORWARD_USER_TOKEN/LEGACY_SERVICE_TOKEN` و transportهای پشتیبانی‌شده.
- عملیات Proxy با مجوز روشن/خاموش، body صفر/مرزی و path parameter.
- Subjectهای `USER/GROUP/ACCESS_GROUP/ROLE` در نقاطی که UI پشتیبانی می‌کند.
- وضعیت‌های فعال/غیرفعال، empty/loading/error، `PENDING/APPLIED/FAILED` و `ACTIVE/DEPRECATED`.

## ۸. سناریوی E2E طلایی: MFE تا API مجاز

1. با administrator وارد شوید و baseline Audit/Outbox را ثبت کنید.
2. Panel غیرفعال با mode=`HYBRID` بسازید و validation تمام فیلدها را منفی/مثبت تست کنید.
3. Artifact معتبر را Publish و Activate کنید؛ نسخه تکراری با محتوای متفاوت باید رد شود.
4. Manifest را Import، Diff را بررسی و Publish کنید؛ conflict و moduleKey غلط را نیز تست کنید.
5. Resource/action را ببینید، Role بسازید و grant بدهید؛ تا `APPLIED` منتظر بمانید.
6. Connection، Auth Profile، Target، Route و Operation را به همین ترتیب ایجاد کنید.
7. Health، Validate، Resolve و Match Test را اجرا کنید.
8. با کاربر مجاز، نمایش منو، بارگذاری Remote Entry و پاسخ API را تست مثبت کنید.
9. با کاربر غیرمجاز، نبود منو و پاسخ 403 را تست منفی کنید.
10. Correlation ID را در API Log و Audit Log دنبال و نبود secret/token را تأیید کنید.
11. grant را Revoke، Operation/Route/Target را disable و artifact قبلی را Activate کنید.
12. دوباره تست منفی/مثبت بگیرید تا Rollback اثبات شود.

## ۹. سناریوهای شکست اجباری

| ورودی/رخداد | نتیجه مورد انتظار |
|---|---|
| Remote Entry نسبی، خالی یا با MIME JSON | عدم اجرا و خطای قابل‌فهم، بدون crash کل Shell |
| manifest بزرگ/خراب یا moduleKey متفاوت | Draft/Publish رد و نسخه فعال بدون تغییر |
| SRI ناسازگار | artifact بارگذاری نشود |
| canonical key یا code تکراری | 409/validation و بدون رکورد نیمه‌کاره |
| `SUBTREE` روی OU گسترده | Preview دامنه اثر را پیش از grant نشان دهد |
| Outbox موقتاً fail شود | وضعیت `FAILED/PENDING` قابل مشاهده و reconcile امن باشد |
| دو Route با match برابر | validation/match ambiguity را آشکار کند |
| upstream timeout/5xx | پاسخ safe، Correlation ID و retry فقط طبق policy |
| token response فاقد pointer | تست profile شکست امن و بدون body حساس |
| کاربر منقضی/غیرفعال | grant مؤثر نشود |
| Audit/API log بدون permission | 403، بدون نشت metadata |

## ۱۰. شواهد و قالب گزارش خطا

برای هر کیس ثبت کنید: شناسه تست، commit SHA، زمان/محیط، persona، پیش‌شرط، ورودی redacted، نتیجه
مورد انتظار/واقعی، status، Correlation ID، screenshot بدون داده حساس و روش Rollback.

```text
ID: GOV-PROXY-NEG-004
Commit/Environment:
Persona:
Precondition:
Steps:
Expected:
Actual:
HTTP status / Correlation ID:
Evidence:
Rollback result:
```

## ۱۱. نتیجه اجرای baseline این نسخه

| اجرا | نتیجه |
|---|---|
| `npm test` پیش از افزودن regression جدید | ۳۱ تست موفق، صفر شکست |
| `.\mvnw.cmd test` | ۱۳۵ تست موفق (۳۰ BFF + ۱۰۵ Authorization)، صفر شکست |
| `npm run infra:verify` | موفق؛ V53+، Outbox بدون pending/dead-letter، drift صفر، ADMIN artifact `0.3.0` و دسترسی مؤثر administrator |
| Edge smoke برای ۱۸ صفحه | همه `200 text/html` |
| Remote Entry بدون نشست برای admin/hr/finance/reports | همه 401؛ مرز authentication برقرار |
| Browser interactive automation | اجرا نشد؛ محدودیت runner، نه نتیجه Pass |
| regression جدید راهبری | ۴ تست جدید موفق؛ مجموعه `@aurevia/e2e-contracts` اکنون ۸/۸ موفق |

پس از افزودن `admin-governance.contract.test.mjs` چهار تست regression جدید اجرا و سبز شدند.
هر تغییر route یا فرم که تعداد ۱۸/۱۱۸ را تغییر دهد باید هم تست و
هم این سند و [راهنمای فیلدها](operator-admin-form-field-guide-fa.md) را در همان commit به‌روز کند.
