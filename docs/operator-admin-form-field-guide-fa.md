# راهنمای جامع فرم‌ها و کنترل‌های میکرو راهبری Aurevia

نسخه سند: ۲.۰ — به‌روزرسانی: ۱۴۰۵/۰۶/۱۷ (۲۰۲۶-۰۹-۰۸) — مخاطب: راهبر سامانه، مدیر امنیت، مدیر گزارش و تیم استقرار

این سند مرجع field-by-field تمام فرم‌ها و کنترل‌های عملیاتی `mfe-admin` است. هر تغییر در
فرم، DTO یا validation سمت سرور باید در همان Pull Request در این سند نیز اعمال شود.

## ۰. شروع از نقطه صفر

### ۰.۱ این سند چه مسئله‌ای را حل می‌کند؟

«میکرو راهبری» پنل مدیریتی خود سوپر اپ است. راهبر در آن تعیین می‌کند چه Microfrontendهایی
قابل بارگذاری‌اند، هر درخواست API به کدام سرویس برسد، چه منبع و عملیاتی مجوز می‌خواهد، چه
کاربر/گروه/نقشی آن مجوز را دارد، و رخدادها چگونه قابل ردیابی‌اند. این سند هم آموزش انجام کار
است و هم مرجع تک‌تک فیلدها؛ بنابراین برای هر تغییر ابتدا سناریوی مربوط را بخوانید و هنگام پرکردن
فرم به جدول همان بخش رجوع کنید.

### ۰.۲ مدل ذهنی معماری

```text
کاربر → Keycloak (ورود و هویت) → Shell/BFF → کاتالوگ و تصمیم مجوز
                                      │              ├─ PostgreSQL: وضعیت مدیریتی و Audit
                                      │              ├─ Outbox → OpenFGA: رابطه‌های دسترسی
                                      │              └─ Effective Catalog: منو/route مجاز
                                      └─ /api/proxy/{serviceSlug}/... → Gateway → سرویس مقصد
```

- **Keycloak** هویت و گروه‌های سازمانی را می‌دهد؛ فرم‌های این پنل جای مدیریت رمز یا کاربر نیستند.
- **Resource** چیزی است که باید محافظت شود؛ **Action** کاری است که روی آن انجام می‌شود؛
  **Subject** دارنده دسترسی و **Grant** رابطه میان این سه است.
- **Panel/MFE** ثبت مدیریتی یک رابط مستقل است؛ **Artifact** نسخه اجرایی immutable آن؛
  **Resource Manifest** فقط قرارداد منابع مجوزدهی است و **MF Manifest** قرارداد runtime،
  routeهای محلی و navigation پیش‌فرض همان نسخه است.
- **Target → Route → Operation** زنجیره عبور API است: مقصد، نگاشت مسیر، سپس قرارداد مجوز هر عملیات.

### ۰.۳ پیش‌نیاز ورود و ترتیب یادگیری

1. محیط باید بالا باشد و `https://localhost:8443` به Keycloak هدایت کند. پس از ورود، دسترسی
   routeهای مدیریتی از Effective Context کاربر تعیین می‌شود؛ دیده‌نشدن منو لزوماً خطای UI نیست.
2. برای راهبری عمومی، کاربر باید `admin` روی `application:aurevia` داشته باشد. صفحات لاگ و
   آزمایش اتصال مجوزهای مستقل مندرج در بخش ۰.۴ دارند.
3. ابتدا بخش‌های ۱ تا ۴ را بیاموزید، سپس یک MFE را طبق سناریوی ۱۶.۱ ثبت کنید. Proxy و اتصال
   خروجی را فقط وقتی بسازید که قرارداد upstream و مالک امنیتی مشخص است.
4. قبل از هر mutation، ticket، مالک، محیط و برنامه rollback را ثبت کنید. بعد از آن هم وضعیت
   effective و Audit Log را بررسی کنید؛ پیام «ذخیره شد» به‌تنهایی اثبات اعمال مجوز نیست.

### ۰.۴ نقشه مسیرها و مجوز ورود

| مسیر زیر `/admin` | صفحه | Resource / Action لازم |
|---|---|---|
| `operator-guide` | راهنمای فرم‌ها | `application:aurevia / admin` |
| `ou-access/ous`, `groups`, `applications`, `explain` | دسترسی OU | `application:aurevia / admin` |
| `access-studio` | استودیوی دسترسی | `application:aurevia / admin` |
| `panels` | میکروفرانت‌ها | `application:aurevia / admin` |
| `proxy-routes/targets`, `routes`, `operations` | راهبری Proxy | به‌ترتیب `proxy.target`، `proxy.route`، `proxy.operation / admin` |
| `outbound-connections`, `outbound-auth` | اتصال و احراز هویت خروجی | `integration.auth-profile / admin` |
| `integration-test` | آزمایشگاه اتصال | `integration.auth-profile / test` |
| `superset-instances`, `identity` | Superset و هویت | `application:aurevia / admin` |
| `logs/api`, `logs/audit` | لاگ‌ها | `business_resource:public-zone-logs / view_api` یا `view_audit` |
| `superset` | دارایی‌های گزارش | `module:admin.superset-catalog / view` |

### ۰.۵ واژه‌نامه کوتاه

| اصطلاح | تعریف عملی |
|---|---|
| Canonical Key | کلید فنی پایدار؛ نام نمایشی نیست و تغییر آن معمولاً migration می‌خواهد |
| Effective | نتیجه نهایی پس از ترکیب Manifest، override، وضعیت فعال و مجوز کاربر |
| Immutable | پس از انتشار ویرایش نمی‌شود؛ اصلاح با نسخه جدید انجام می‌شود |
| Outbox | صف تراکنشی انتقال تغییر دسترسی از دیتابیس به OpenFGA |
| Allowlist | فهرست مقصدهای مجاز شبکه؛ دفاع اصلی در برابر SSRF |
| Optimistic Lock | جلوگیری از بازنویسی تغییر هم‌زمان با فیلد داخلی `version` |
| Soft delete / Deprecated | خروج از استفاده فعال با حفظ سابقه و قابلیت بررسی Audit |

## ۱. قراردادهای مشترک

- علامت «الزامی» یعنی UI و API هر دو مقدار را کنترل می‌کنند. «شرطی» یعنی فقط در یک حالت
  مشخص الزامی است.
- شناسه‌های پایدار (`code`، `slug`، `resourceKey` و referenceها) پس از مصرف در route،
  manifest یا OpenFGA نباید صرفاً برای زیبایی تغییر کنند.
- مقدار `version` داخلی و فقط برای optimistic locking است. راهبر آن را ویرایش نمی‌کند؛
  خطای `VERSION_CONFLICT` یعنی رکورد هم‌زمان تغییر کرده و باید صفحه بازخوانی شود.
- فعال‌سازی (`active`) با حذف فرق دارد. غیرفعال‌سازی برای rollback و حفظ audit ترجیح دارد.
- هیچ فرم مدیریتی اجازه دریافت یا نمایش مقدار password، client secret یا access token را
  ندارد. فقط referenceهایی مانند `secret://...` و `connection://...` ذخیره می‌شوند.
- تمام URLهای عملیاتی باید از allowlist استقرار نیز عبور کنند؛ ثبت موفق در دیتابیس به‌تنهایی
  اجازه egress شبکه ایجاد نمی‌کند.
- تغییرات دسترسی ابتدا در PostgreSQL و Outbox ثبت و سپس در OpenFGA اعمال می‌شوند. وضعیت
  `PENDING` کوتاه‌مدت طبیعی است؛ `FAILED` نیازمند بررسی لاگ و Reconciliation است.

## ۲. نقشه بخش‌های میکرو راهبری

| بخش | کاربرد | نقش معمول |
|---|---|---|
| دسترسی مبتنی بر OU | ساخت گروه محاسباتی از OUهای فقط‌خواندنی و اتصال آن به MFE | مدیر سامانه |
| استودیوی دسترسی | درخت resource/action و grant به کاربر، گروه یا نقش | مدیر امنیت |
| میکروفرانت‌ها | ثبت MFE و انتشار artifact/manifest immutable | راهبر پلتفرم |
| راهبری Proxy | تعریف Target، Route و Operation مجوزدار | راهبر یکپارچه‌سازی |
| اتصال‌های Legacy | allowlist مبدأ دریافت توکن Legacy | مدیر امنیت/شبکه |
| پروفایل احراز هویت | روش دریافت و حمل توکن بدون نگهداری secret | مدیر امنیت |
| آزمایشگاه اتصال | تست E2E مسیر Legacy و OAuth2 بدون نمایش token | راهبر توسعه |
| محیط‌های Superset | ثبت سرور عمومی/عملیاتی و mapping | راهبر گزارش |
| گزارش‌ها و داشبوردها | grant سطح دارایی Superset | طراح گزارش |
| گروه‌ها و نقش‌ها | مشاهده هویت‌های syncشده، ساخت role و assignment | مدیر سامانه |
| لاگ‌ها | جست‌وجوی API log و audit با Correlation ID | عملیات/SOC |

## ۳. دسترسی مبتنی بر OU

### ۳.۱ OUهای سازمانی

این قسمت فرم نوشتن ندارد. `Path`، `DN`، `External ID`، زمان Sync و تعداد کاربران از
LDAP/Keycloak وارد می‌شوند. راهبر نمی‌تواند OU بسازد، ویرایش یا حذف کند. برای اصلاح نام یا
ساختار باید مرجع Directory اصلاح و Sync دوباره اجرا شود.

### ۳.۲ فرم «Access Group محاسباتی»

| فیلد | الزام/قالب | معنا و نکته راهبری | مثال |
|---|---|---|---|
| کد پایدار | الزامی؛ `^[A-Z][A-Z0-9_]{2,159}$` | شناسه فنی گروه در tupleها؛ پس از استفاده تغییر نکند | `ACCOUNTING_USERS` |
| نام | الزامی | نام قابل‌فهم برای مدیر؛ مبنای مجوز نیست | `کاربران حسابداری` |
| توضیح | اختیاری | علت ایجاد، مالک کسب‌وکار و ticket تأیید را بنویسید | `مصوب درخواست SEC-104` |
| منطق ترکیب | الزامی؛ `ANY_OF` یا `ALL_OF` | `ANY_OF`: عضویت در یکی از ruleها کافی؛ `ALL_OF`: همه ruleها لازم‌اند | `ANY_OF` |

### ۳.۳ فرم Rule گروه OU

| فیلد/کنترل | الزام/قالب | معنا و نکته | مثال |
|---|---|---|---|
| OU | الزامی؛ انتخاب از catalog | فقط OU syncشده قابل انتخاب است؛ متن DN دستی پذیرفته نمی‌شود | `/Tehran/Finance` |
| Match Mode | الزامی؛ `EXACT` یا `SUBTREE` | `EXACT` فقط کاربران همان OU؛ `SUBTREE` شامل OUهای فرزند | `SUBTREE` |
| غیرفعال‌کردن Rule | تأیید لازم | حذف فیزیکی نیست؛ membership دوباره محاسبه و tupleهای زائد revoke می‌شوند | — |
| Preview | بدون mutation | پیش از اعمال، اعضای مؤثر ruleها را نشان می‌دهد | — |

نکته: انتخاب `SUBTREE` روی OU سطح بالا می‌تواند دامنه دسترسی بسیار بزرگی ایجاد کند. ابتدا
Preview و سپس تعداد عضو را با مالک داده تطبیق دهید.

### ۳.۴ فرم «دسترسی Microfrontend»

| فیلد | الزام | معنا |
|---|---|---|
| Microfrontend | الزامی؛ فقط MFE فعال | application مقصد که relation `viewer` دریافت می‌کند |
| Access Group | الزامی؛ فقط گروه فعال | گروه محاسباتی مبتنی بر OU که دسترسی به آن داده می‌شود |
| Grant VIEWER | mutation | رابطه در Outbox صف می‌شود؛ تا `APPLIED`شدن دسترسی نهایی فرض نشود |
| Revoke | تأیید لازم | دسترسی application را از گروه می‌گیرد، نه membership OU را |

### ۳.۵ «بررسی مسیر دسترسی User»

فیلد کاربر از جدول کاربران syncشده انتخاب می‌شود. خروجی باید مسیر
`User → OU → Rule → Access Group → Application` و `membership_version` را نشان دهد. این
نمایش ابزار explain است و خودش مجوز را تغییر نمی‌دهد.

## ۴. استودیوی دسترسی OpenFGA

### ۴.۱ کنترل‌های درخت

| کنترل | رفتار |
|---|---|
| جست‌وجوی نام یا کلید | روی نام فارسی، انگلیسی و canonical key فیلتر می‌کند و والدها را نگه می‌دارد |
| نوع منبع | نمایش را به APPLICATION/MODULE/PAGE/UI component/FIELD/Business/External محدود می‌کند |
| انتخاب گره | جزئیات، actionهای مجاز و grantهای همان resource را باز می‌کند |
| افزودن فرزند | فرم resource را با parent فعلی باز می‌کند |
| Switch هر Action | action را به قرارداد resource اضافه/حذف می‌کند؛ این کار به‌تنهایی grant نیست |

### ۴.۲ فرم Resource

| فیلد | الزام/قالب | معنا و کاربرد | مثال |
|---|---|---|---|
| نوع منبع | الزامی | `APPLICATION` ریشه UI، `MODULE` قابلیت، `PAGE` صفحه، `UI_COMPONENT` بخش حساس، `FIELD` فیلد entitlement مستقل، `BUSINESS_RESOURCE` موجودیت دامنه، `EXTERNAL_RESOURCE` دارایی بیرونی | `PAGE` |
| والد | اختیاری برای ریشه | inheritance مجوز را تعیین می‌کند؛ والد نادرست می‌تواند دسترسی ناخواسته ارثی بسازد | `module:hr.people` |
| کلید canonical | الزامی؛ حروف کوچک، عدد و `._:/-` | هویت پایدار resource و مبنای OpenFGA object؛ rename پرریسک است | `page:hr.employees` |
| نام فارسی | الزامی | عنوان راهبری | `فهرست کارکنان` |
| نام انگلیسی | الزامی | عنوان فنی/بین‌المللی | `Employee list` |
| دامنه مالک | اختیاری | تیم پاسخ‌گو و مرز bounded context | `hr` |
| طبقه‌بندی | اختیاری؛ `PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED` | برای review امنیت و data policy؛ به‌تنهایی مجوز ایجاد نمی‌کند | `CONFIDENTIAL` |
| Micro Frontend مالک | اختیاری؛ انتخاب از Panelها | مرز مالکیت resource را مشخص می‌کند. در mode=`MANIFEST` ساخت دستی در production مجاز نیست | `HR` |
| منبع مالکیت | فقط‌خواندنی؛ `ADMIN` یا `MANIFEST` | تعیین می‌کند چه کسی حق تغییر ساختار و metadata را دارد؛ UI هنگام ساخت دستی `ADMIN` می‌گذارد | `ADMIN` |
| نمایش در Effective UI Catalog | boolean | فقط visibility در کاتالوگ UI را کنترل می‌کند؛ grant یا کنترل Backend را حذف نمی‌کند | فعال |
| سامانه خارجی | شرطی برای External | provider دارایی خارجی | `superset` |
| نوع خارجی | شرطی برای External | نوع object در provider | `dashboard` |
| شناسه خارجی | شرطی برای External | شناسه immutable در provider | `42` |

برای دکمه‌های عادی resource جدا نسازید. فقط action حساس یا UI componentی که تصمیم مستقل
سرور دارد به catalog اضافه شود؛ مخفی‌کردن دکمه در Frontend جای کنترل Backend را نمی‌گیرد.

### ۴.۳ تخصیص دسترسی

| کنترل | معنا |
|---|---|
| نوع Subject | `USER` برای استثنای فردی، `GROUP` برای LDAP group، `ROLE` برای بسته قابلیت |
| Subject | هویت مقصد؛ نام نمایش مبنای tuple نیست و UUID داخلی استفاده می‌شود |
| Action | فقط actionهایی که قبلاً برای resource فعال شده‌اند |
| اعطا/لغو | relation استاندارد از action استخراج می‌شود: view→viewer، create→creator، update→editor، delete→deleter، admin→manager |

اصل راهبری: grant به Role یا Group بر grant فردی مقدم است. دسترسی فردی فقط برای استثنای
مستند و زمان‌دار استفاده شود.

منبع `MANIFEST` در حالت عادی از این فرم فقط اجازه تغییر visibility دارد. تغییر نوع، کلید، والد،
metadata یا action باید با نسخه جدید Manifest منتشر شود. گزینه توسعه‌ای، اگر در تنظیمات محیط
فعال شده باشد، صرفاً برای توسعه است و انتشار بعدی می‌تواند تغییر را بازنویسی کند.

## ۵. مدیریت Microfrontend

### ۵.۱ فرم تعریف/ویرایش MFE

| فیلد | الزام/قالب | معنا و نکته | مثال |
|---|---|---|---|
| کد | الزامی؛ حروف بزرگ، عدد، `_` یا `-`؛ ۲ تا ۱۰۰ نویسه | شناسه مدیریتی پایدار | `HR_PAYROLL` |
| Slug | الزامی؛ kebab-case، ۲ تا ۵۰ نویسه | شناسه module در manifest | `hr-payroll` |
| نام فارسی/انگلیسی | الزامی | عنوان کاربری و catalog | `حقوق و دستمزد` / `Payroll` |
| توضیحات | اختیاری | مالک، هدف و محدوده MFE | — |
| Service Slug | الزامی؛ lowercase kebab-case | namespace عمومی API؛ مستقل از route نمایشی | `payroll-api` |
| Remote Name | الزامی؛ حرف آغازین و سپس حرف/عدد/underscore | نام container در Module Federation و باید در هر artifact یکتا باشد | `payroll_ui_1_2_0` |
| آدرس کامل Remote Entry | الزامی؛ HTTP(S) absolute | URL دقیق artifact. در production باید policy شبکه و سیاست HTTPS/SRI رعایت شود؛ origin-list ثابت وجود ندارد | `https://cdn.example/mfe/payroll/remoteEntry.js` |
| Exposed Module | الزامی؛ با `./` | module exportشده توسط container | `./plugin` |
| Route Prefix | الزامی؛ `/` + kebab-case | مسیر UI؛ `login/admin/settings/api/assets/error` برای رکورد جدید رزروشده‌اند | `/payroll` |
| Default Route ID | الزامی | باید با یکی از `routes[].key` در MF Manifest یکسان باشد | `employee-list` |
| نسخه | الزامی؛ SemVer | نسخه deploy مانند `1.4.2` یا prerelease معتبر | `1.4.2` |
| نسخه قرارداد | الزامی | نسخه قرارداد Shell↔MFE؛ مستقل از نسخه محصول | `1.0` |
| ترتیب | اختیاری؛ عدد | ترتیب menu؛ عدد کوچک‌تر زودتر نمایش داده می‌شود | `30` |
| حالت تعریف Resource | الزامی؛ `MANIFEST`/`MANUAL`/`HYBRID` | فقط منبع تعریف Authorization Resource را تعیین می‌کند: فایل، ادمین، یا هر دو با مالکیت مستقل؛ روی MF Manifest/route/navigation اثری ندارد | `HYBRID` |
| Classification | الزامی؛ `REAL`/`DEMO` | MFE آزمایشی را صریح علامت می‌زند؛ در production و با `demo-data.enabled=false` از Catalog و تصمیم runtime حذف می‌شود | `REAL` |
| Resource Manifest URL | در `MANIFEST` الزامی؛ URL مطلق JSON | آدرس `resource-manifest.json`؛ بدون credential/query/fragment و سازگار با policy شبکه. Backend فقط JSON را می‌خواند و Webpack اجرا نمی‌کند | `https://cdn.example/mfe/payroll/resource-manifest.json` |
| MF Manifest URL | اختیاری تا زمان sync؛ URL مطلق JSON | آدرس مستقل `mf-manifest.json` برای runtime، route و navigation؛ تابع همان policy شبکه | `https://cdn.example/mfe/payroll/mf-manifest.json` |
| فعال | boolean | فقط MFE فعال وارد catalog/manifest runtime می‌شود | — |

### ۵.۲ فرم «انتشار Artifact immutable»

| فیلد | الزام/قالب | معنا و نکته | مثال |
|---|---|---|---|
| نسخه | الزامی؛ immutable در هر Panel | نسخه artifact؛ artifact منتشرشده ویرایش نمی‌شود، نسخه جدید بسازید | `1.4.2` |
| Remote Entry URL | الزامی؛ HTTP(S) | فایل deployشده همان نسخه | `https://cdn.../1.4.2/remoteEntry.js` |
| Remote Name | الزامی و global-unique | باید دقیقاً با container buildشده تطبیق داشته باشد | `payroll_ui_1_4_2` |
| Exposed Module | الزامی | export runtime که `mount` ارائه می‌کند | `./plugin` |
| Contract | الزامی | باید توسط Shell پشتیبانی شود | `1.0` |
| SRI | در local اختیاری؛ در production طبق policy | digest کامل مانند `sha384-...`؛ با کوچک‌ترین تغییر فایل باید عوض شود | `sha384-AbCd...` |
| MF Manifest Snapshot | الزامی؛ JSON معتبر | runtime، routeهای محلی، navigation پیش‌فرض و referenceهای resource؛ خود Resource را تعریف نمی‌کند و پس از انتشار immutable است | نمونه در بخش ۱۳ |
| Validate و Publish | mutation | ابتدا schema/URL/SRI را validate و سپس artifact را ثبت می‌کند |
| Activate / Rollback | تأیید نسخه | active artifact پنل را atomically تغییر می‌دهد؛ rollback یعنی فعال‌کردن artifact معتبر قبلی |

Artifact/MF Manifest قرارداد runtime، route و navigation پیش‌فرض است؛ Resource Manifest فقط
کاتالوگ منابع مجوزدهی نسخه‌دار است. انتشار یکی جای انتشار دیگری را نمی‌گیرد.

### ۵.۳ فرم «Resource Manifest و Draft»

| کنترل/فیلد | الزام/قالب | معنا و نکته |
|---|---|---|
| Fetch Manifest | URL از Panel | فایل را از URL ثبت‌شده با کنترل SSRF/network policy، timeout و سقف ۱ MiB دریافت و فقط Draft می‌سازد |
| Import JSON | JSON مطابق schema | برای محیطی که CDN در دسترس نیست؛ باز هم مستقیماً production را تغییر نمی‌دهد |
| Schema Version | الزامی | نسخه قرارداد JSON؛ اکنون `1.0` |
| Module Key | الزامی و دقیقاً مطابق Panel slug | از اتصال اتفاقی manifest یک MFE به Panel دیگر جلوگیری می‌کند |
| Module Version | SemVer و immutable در هر Panel | تکرار همان نسخه با checksum متفاوت تعارض است؛ محتوا را با نسخه جدید منتشر کنید |
| Diff | فقط خواندنی | `CREATE`، `UPDATE`، `UNCHANGED`، `DEPRECATE` یا `CONFLICT` |
| Publish | فقط Draft بدون conflict | تغییرات را تراکنشی منتشر می‌کند؛ Resource حذف‌شده از manifest را پاک نمی‌کند و `DEPRECATED` می‌سازد |

در mode برابر `MANUAL` دکمه‌های Fetch/Import غیرفعال‌اند. Resource با source=`MANIFEST`
از فرم دستی قابل تغییر نیست؛ Resource با source=`ADMIN` نیز توسط import تصاحب یا overwrite نمی‌شود.

### ۵.۴ فرم «Navigation Overlay»

| فیلد | الزام/قالب | معنا و نکته |
|---|---|---|
| Navigation Key | الزامی و پایدار | در `MANIFEST` باید key موجود فایل باشد؛ در `ADMIN` شناسه گره جدید است |
| Source | `MANIFEST` یا `ADMIN` | overlay روی گره فایل یا navigation مستقل ادمین؛ هیچ Resource امنیتی ساخته نمی‌شود |
| Node Type | `GROUP`/`PAGE`/`EXTERNAL_LINK` | Group مقصد ندارد؛ Page به Page Key؛ لینک خارجی فقط HTTPS |
| Parent Key | اختیاری | والد navigation در همان MFE؛ self-reference و cycle رد می‌شود |
| Page Key | برای `PAGE` الزامی | باید به route منتشرشده و موجود همان MFE اشاره کند |
| External URL | فقط برای `EXTERNAL_LINK` | URL مطلق HTTPS؛ برای Page/Group باید خالی باشد |
| عنوان/Icon/Order/Hidden | overlay | presentation را بدون تغییر Resource Key و Permission تغییر می‌دهد |

حذف overlay فقط override ادمین را حذف می‌کند؛ گره اصلی manifest در انتشار بعدی دوباره با مقدار
اصلی دیده می‌شود. Effective Navigation برابر Manifest Navigation به‌علاوه Overlay فعال است.

## ۶. راهبری Proxy

مدل سه‌لایه است: `Service Target` مقصد منطقی و auth profile، `Proxy Route` namespace و
rewrite، و `Route Operation` قرارداد HTTP و resource/action.

### ۶.۱ فرم Service Target

| فیلد | الزام/محدوده | معنا و نکته | مثال |
|---|---|---|---|
| کد | الزامی؛ شناسه فنی | پایدار و یکتا | `legacy-payroll` |
| نام | الزامی | عنوان راهبری | `سامانه حقوق قدیمی` |
| محیط | الزامی | محیط مقصد؛ اکنون `OPERATION` یا `STAGING` | `OPERATION` |
| Outbound Auth Profile | الزامی | `FORWARD_USER_TOKEN` برای OAuth2 جدید؛ `LEGACY_SERVICE_TOKEN` برای token سرویس Legacy | `legacy-payroll-password` |
| آدرس کامل Gateway | الزامی؛ HTTP(S)، host allowlisted | باید Gateway کنترل‌شده باشد، نه URL مستقیم هر سرویس؛ SSRF در سرور کنترل می‌شود | `http://operation-gateway:80` |
| Upstream Base Path | الزامی؛ path امن | namespace سرویس پشت Gateway؛ query/`..`/encoded path مجاز نیست | `/legacy-payroll` |
| Health Path | الزامی؛ path امن | endpoint سلامت Gateway/target؛ نباید عملیات business انجام دهد | `/health` |
| TLS Profile Ref | اختیاری | مرجع mTLS؛ فقط `tls://...`. در production معمولاً برای Gateway الزامی است | `tls://operation-gateway-client` |
| Secret Ref | اختیاری | metadata مرجع عمومی target؛ credential Legacy در Auth Profile تعریف می‌شود | `secret://gateway/client` |
| Connect Timeout | الزامی عددی؛ ۱۰۰..۳۰۰۰۰ ms | سقف برقراری اتصال | `3000` |
| Response Timeout | الزامی عددی؛ ۱۰۰..۱۲۰۰۰۰ ms | سقف کل پاسخ upstream | `10000` |
| Max Response Bytes | الزامی؛ ۱۰۲۴..۱۰۴۸۵۷۶۰۰ | دفاع در برابر پاسخ بزرگ | `1048576` |
| فعال | boolean | target غیرفعال resolve نمی‌شود | — |
| توضیح | اختیاری | مالک، قرارداد و change ticket | — |

`TLS Profile Ref` و `Secret Ref` اختیاری‌اند و فرم نباید برای routeهای بدون mTLS آن‌ها را
اجباری کند. در عوض policy استقرار production می‌تواند mTLS Gateway را اجباری کند.

### ۶.۲ فرم Proxy Route

| فیلد | الزام/محدوده | معنا و نکته | مثال |
|---|---|---|---|
| کد | الزامی و یکتا | شناسه route در audit | `legacy-payroll-api` |
| Panel | الزامی | مالک route و دامنه UI | `ADMIN` |
| Service Target | الزامی | مقصد و auth profile | `legacy-payroll` |
| Service Slug | الزامی؛ lowercase kebab-case | namespace پایدار زیر `/api/proxy/` | `legacy-payroll` |
| Path Prefix | الزامی؛ path canonical | ورودی مرورگر؛ برای مسیر جدید الگوی توصیه‌شده `/api/proxy/{serviceSlug}` است | `/api/proxy/legacy-payroll` |
| Strip Segments | ۰..۲۰ | تعداد segmentهای ورودی که پیش از rewrite حذف می‌شوند؛ Preview را حتماً اجرا کنید | `0` |
| Priority | ‎-۱۰۰۰..۱۰۰۰ | فقط برای prefixهای هم‌پوشان؛ مقدار بالاتر مقدم است | `100` |
| Allowed Methods | حداقل یک مورد | allowlist متد؛ فقط نیاز واقعی را فعال کنید | `GET,POST` |
| Rewrite Prefix | دو فیلد شرطی | اگر یکی مقدار دارد دیگری نیز الزامی؛ فقط literal prefix با `^/` و بدون regex آزاد | `^/api/proxy/legacy-payroll` |
| Rewrite Replacement | دو فیلد شرطی | path مقصد Gateway و نه URL کامل | `/legacy-payroll` |
| Retry | فقط روش‌های safe | فقط برای `GET/HEAD/OPTIONS`؛ برای POST/PUT/PATCH/DELETE ممنوع تا عملیات تکراری نشود | خاموش |
| Max Retries | ۰..۳ | در عمل یک retry کنترل‌شده برای refresh/reacquire کافی است | `1` |
| Preserve Host | پیش‌فرض خاموش | فقط اگر upstream صریحاً Host اصلی را می‌خواهد؛ معمولاً خاموش | — |
| فعال | boolean | route غیرفعال قابل resolve نیست | — |

### ۶.۳ فرم Route Operation

| فیلد | الزام/محدوده | معنا و نکته | مثال |
|---|---|---|---|
| HTTP Method | الزامی | method دقیق عملیات | `GET` |
| Path Pattern | الزامی؛ زبان محدود segment | نسبت به route prefix؛ literal، `*`، `{id}` و `**` انتهایی؛ regex آزاد پذیرفته نمی‌شود | `/employees/{id}` |
| Resource | الزامی و فعال | resource سمت سرور که تصمیم مجوز روی آن گرفته می‌شود | `api:hr.employee` |
| Action | الزامی و متعلق به Resource | action business، نه permission محاسباتی مثل `can_view` | `view` |
| Data Policy | اختیاری | کلید policy برای scope/masking؛ اگر policy ندارید خالی بماند | `hr.branch-scope` |
| Max Body Bytes | ۰..۱۰۴۸۵۷۶۰۰ | سقف request body؛ برای GET می‌تواند ۰/کم باشد | `1048576` |
| نیازمند OpenFGA | در business API باید روشن باشد | خاموش فقط برای endpoint عمومیِ صریح و reviewشده | روشن |
| فعال | boolean | operation غیرفعال match نمی‌شود | — |

کنترل‌های `Preview`، `Match Test` و `Resolution Test` هیچ mutation ندارند و باید پیش از
فعال‌سازی route استفاده شوند.

## ۷. اتصال‌های خروجی Legacy

### ۷.۱ فرم «اتصال خروجی تأییدشده»

| فیلد | الزام/قالب | معنا و نکته | مثال |
|---|---|---|---|
| نام | الزامی | عنوان endpoint دریافت token | `Payroll token endpoint` |
| Reference پایدار | الزامی؛ `connection://...`؛ پس از ایجاد قفل | کلید اتصال که Auth Profile به آن اشاره می‌کند | `connection://legacy/payroll` |
| Origin سرویس توکن | الزامی؛ فقط scheme/host/port | path، query، fragment و user-info ممنوع؛ path در Profile جداست | `https://identity.legacy.example:443` |
| TLS اجباری | boolean | اگر روشن باشد Origin باید HTTPS باشد؛ در production روشن نگه دارید | روشن |
| فعال | boolean | اتصال غیرفعال در runtime resolve نمی‌شود | — |
| version | مخفی | optimistic locking؛ قابل ویرایش نیست | — |

این جدول username/password ندارد. مقدار credential در Secret Store و فقط reference آن در
Auth Profile قرار می‌گیرد.

### ۷.۲ فرم Outbound Auth Profile

| فیلد | الزام/محدوده | معنا و نکته | مثال |
|---|---|---|---|
| کد | الزامی؛ حرف آغازین، سپس حرف/عدد/`._-` | شناسه پایدار profile | `legacy-payroll-password` |
| نام | الزامی | عنوان قابل‌فهم | `Payroll Legacy token` |
| Auth Mode | الزامی | `FORWARD_USER_TOKEN`: توکن Keycloak کاربر؛ `LEGACY_SERVICE_TOKEN`: توکن server-side | `LEGACY_SERVICE_TOKEN` |
| Token Connection | شرطی؛ برای Legacy الزامی | اتصال allowlisted دریافت token | `connection://legacy/payroll` |
| Token Endpoint Path | شرطی؛ برای Legacy الزامی؛ با `/` | path نسبی بدون URL/query/`..`/encoding | `/oauth/token` |
| Request Adapter | الزامی | `FORM_URLENCODED`، `JSON`، `HTTP_BASIC` یا `OAUTH_CLIENT_CREDENTIALS` | `FORM_URLENCODED` |
| Credential Secret Ref | شرطی؛ برای Legacy الزامی | reference واحد شامل username/password یا clientId/clientSecret؛ مقدار secret هرگز اینجا نیست | `secret://legacy/payroll` |
| Scope | اختیاری | در صورت نیاز token endpoint | `payroll.read` |
| Audience | اختیاری | در صورت نیاز token endpoint | `payroll-api` |
| Token JSON Pointer | الزامی | محل access token در JSON پاسخ | `/access_token` |
| Expires JSON Pointer | الزامی | محل عمر برحسب ثانیه | `/expires_in` |
| Token Type Pointer | الزامی | محل نوع token | `/token_type` |
| Scheme | الزامی | scheme هدر upstream | `Bearer` |
| Credential Transport | الزامی | Public IAM فقط `USER_AUTHORIZATION_HEADER`؛ Legacy فقط `INTERNAL_LEGACY_HEADER` | `INTERNAL_LEGACY_HEADER` |
| Expiry Skew | ۵..۶۰۰ ثانیه | چند ثانیه پیش از expiry، token نامعتبر فرض شود | `30` |
| Connect Timeout | ۱۰۰..۳۰۰۰۰ ms | timeout endpoint token | `3000` |
| Response Timeout | ۱۰۰..۱۲۰۰۰۰ ms | timeout پاسخ token | `10000` |
| Max Response | ۱۰۲۴..۵۲۴۲۸۸۰ bytes | سقف پاسخ token برای دفاع حافظه | `1048576` |
| فعال | boolean | profile غیرفعال target را غیرقابل resolve می‌کند | — |
| توضیح | اختیاری | مالک secret، روش rotation و ticket | — |

کنترل‌ها: «اعتبارسنجی اتصال» secret را نمی‌خواند؛ «تست توکن» یک token واقعی می‌گیرد اما
آن را برنمی‌گرداند و cache را پر نمی‌کند؛ «Cache» فقط وجود token معتبر را می‌گوید؛ «ابطال
توکن» cache رمز‌شده Redis را حذف می‌کند.

## ۸. آزمایشگاه اتصال Legacy و OAuth2

| کنترل/ستون | معنا |
|---|---|
| رجیستری فعال | Target، Route، Auth Profile و وضعیت ready را از control plane می‌خواند |
| اجرای Legacy | Session مرورگر → BFF → دریافت/cache token Legacy → Gateway → mock Legacy |
| اجرای OAuth2 / Keycloak | Session → token vault → BFF → Gateway → Keycloak userinfo validation → mock OAuth |
| Legacy ×2 | اجرای پشت‌سرهم برای مشاهده `cache=miss` و سپس `cache=hit` در dev log |
| Correlation ID | کلید تطبیق پاسخ UI با لاگ BFF؛ token نیست و اشتراک آن مجاز است |
| پاسخ امن | فقط نوع credential و موفقیت اعتبارسنجی؛ هیچ token/secret نمایش داده نمی‌شود |

این fixture فقط در Docker Compose توسعه ثبت می‌شود. در production، property لاگ اثباتی
حتی در صورت تنظیم اشتباه متغیر محیطی به‌علت profile `prod` خاموش می‌ماند.

## ۹. محیط‌های Superset

### ۹.۱ فرم Instance

| فیلد | الزام/قالب | معنا و نکته | مثال |
|---|---|---|---|
| کد پایدار | الزامی؛ lowercase kebab-case، ۳..۸۰ نویسه؛ در ویرایش قفل | شناسه mapping/runtime | `operation-tehran` |
| نام | الزامی | عنوان محیط | `Superset عملیات تهران` |
| محیط | الزامی | `PUBLIC` برای ورودی/نمای عمومی، `OPERATION` برای سرور حفاظت‌شده | `OPERATION` |
| Origin شامل آدرس و پورت | الزامی؛ HTTP(S) origin بدون path/query/credential | مقصد دقیق؛ باید allowlisted باشد | `https://superset-op.example:443` |
| Connection reference | الزامی؛ `connection://...` | مرجع اتصال امن استقرار | `connection://superset/operation-tehran` |
| روش احراز هویت | الزامی | `REMOTE_USER`، `OIDC` یا `GUEST_TOKEN` مطابق پیکربندی همان سرور | `REMOTE_USER` |
| TLS اجباری | boolean | در production روشن؛ اگر روشن URL باید HTTPS باشد | روشن |
| فعال | boolean | فقط instance فعال در mapping/runtime استفاده می‌شود | — |
| version | مخفی | optimistic locking | — |

### ۹.۲ فرم نگاشت Public → Operation

| فیلد | الزام | معنا |
|---|---|---|
| محیط عمومی | الزامی؛ instance فعال `PUBLIC` | ورودی منطقی کاربر |
| محیط عملیاتی | الزامی؛ instance فعال `OPERATION` | مقصد private که BFF/Gateway به آن متصل می‌شود |
| مسیر عمومی | الزامی؛ path بدون `..` | mount point مانند `/reports-runtime` |
| پیش‌فرض | boolean | فقط یک mapping پیش‌فرض می‌ماند؛ انتخاب جدید قبلی را atomically غیرفعالِ پیش‌فرض می‌کند |
| فعال | boolean | mapping غیرفعال resolve نمی‌شود |

## ۱۰. گزارش‌ها و داشبوردهای Superset

| فیلد | الزام | معنا و نکته |
|---|---|---|
| نوع Subject | الزامی | کاربر، LDAP Group، OU Access Group یا Role |
| دارنده دسترسی | الزامی | هویت مقصد از لیست server-side؛ شناسه دستی پذیرفته نمی‌شود |
| سطح دسترسی | الزامی | `VIEW`→view، `EDIT`→update، `MANAGE`→admin |
| لغو دسترسی | تأیید لازم | grant همان asset را revoke می‌کند، نه خود dashboard/chart را |

Designer می‌تواند assetهای ثبت‌شده را به گروه یا فرد بدهد. Viewer صرفاً assetهای مجاز و
published را می‌بیند. دسترسی iframe/UI بدون بررسی proxy backend کافی نیست؛ BFF هر درخواست
Superset را نیز با asset catalog کنترل می‌کند.

## ۱۱. هویت، گروه و نقش

### ۱۱.۱ جدول کاربران و گروه‌ها

کاربران و LDAP groupها فقط از login/sync می‌آیند. فیلدهای issuer، external subject، username،
OU و status قابل مشاهده‌اند اما ایجاد دستی کاربر/OU/LDAP group در این UI مجاز نیست.

### ۱۱.۲ فرم Role

| فیلد | الزام/قالب | معنا | مثال |
|---|---|---|---|
| کلید پایدار نقش | الزامی؛ کد فنی پایدار | OpenFGA role object؛ پس از assignment تغییر نکند | `hr-supervisor` |
| نام فارسی | الزامی | عنوان راهبری | `سرپرست منابع انسانی` |
| نام انگلیسی | الزامی | عنوان فنی | `HR Supervisor` |

### ۱۱.۳ فرم Role Assignment

| فیلد | الزام | معنا |
|---|---|---|
| نوع Subject | الزامی | `USER`، `DIRECTORY_GROUP` یا `ACCESS_GROUP` |
| کاربر/گروه | الزامی | مقصد assignment؛ OU خام subject نیست، Access Group حاصل rule است |
| نقش | الزامی؛ role فعال | بسته capability که به subject متصل می‌شود |
| انقضا | در API اختیاری | در صورت استفاده باید زمان UTC معتبر و آینده باشد؛ UI فعلی assignment بدون انقضا می‌سازد |
| لغو | تأیید لازم | tuple assignee را از Outbox حذف می‌کند |

## ۱۲. لاگ‌ها

### ۱۲.۱ فیلتر API Log

| فیلد | اختیاری | معنا |
|---|---|---|
| سرویس | بله | نام سرویس تولیدکننده رویداد |
| مسیر | بله | route template، نه URL حاوی داده حساس |
| کاربر | بله | شناسه امن ثبت‌شده در log |
| Status | بله؛ ۱۰۰..۵۹۹ | HTTP status code |
| Correlation ID | بله | دقیق‌ترین راه ردیابی یک درخواست بین سرویس‌ها |

### ۱۲.۲ فیلتر Audit Log

| فیلد | اختیاری | معنا |
|---|---|---|
| عامل | بله | actor تغییر مدیریتی |
| نوع رویداد | بله | مانند `proxy.route.updated` |
| نوع هدف | بله | نوع aggregate مانند `PROXY_ROUTE` |
| شناسه هدف | بله | UUID/key هدف |
| نتیجه | بله | `SUCCESS`، `DENY` یا `ERROR` |
| Correlation ID | بله | اتصال audit به درخواست اصلی |

جزئیات log باید safe metadata باشد. Authorization header، cookie، token، password و payload
حساس نباید در فیلتر یا safe details ظاهر شوند.

## ۱۳. نمونه قراردادهای جداشدهٔ MF و Resource Manifest

MF Manifest:

```json
{
  "schemaVersion": "1.0",
  "microfrontend": {
    "key": "hr-payroll",
    "name": "Payroll",
    "version": "1.4.2"
  },
  "runtime": {
    "remoteEntry": "https://cdn.example/mfe/payroll/remoteEntry.js",
    "remoteName": "payroll_ui",
    "exposedModule": "./plugin",
    "contractVersion": "1.0",
    "apiBasePath": "/api/proxy/payroll-api"
  },
  "defaultRouteKey": "employee-list",
  "routes": [
    {
      "key": "employee-list",
      "path": "employees",
      "title": "کارکنان",
      "requiredResource": "page:hr.employees",
      "requiredAction": "view"
    }
  ],
  "navigation": [
    {
      "key": "employees-menu",
      "type": "PAGE",
      "routeKey": "employee-list",
      "title": "کارکنان",
      "icon": "team",
      "order": 10
    }
  ]
}
```

Resource Manifest:

```json
{
  "schemaVersion": "1.0",
  "module": {
    "key": "hr-payroll",
    "name": "Payroll",
    "nameFa": "حقوق و دستمزد",
    "version": "1.4.2"
  },
  "resources": [
    {
      "key": "page:hr.employees",
      "type": "PAGE",
      "nameFa": "کارکنان",
      "nameEn": "Employees",
      "actions": ["view"]
    }
  ]
}
```

قواعد مهم:

- backend یک Application root با کلید `application:aurevia/{panel-slug}` می‌سازد؛ root والد ندارد.
- هر Resource غیرریشه والد معتبر در همان Panel دارد و ساخت cycle/self-parent مجاز نیست.
- هر PAGE navigation با `routeKey` به `routes[].key` موجود اشاره می‌کند و مجوز را از route به ارث می‌برد.
- `requiredResource` فقط reference است؛ باید همراه `requiredAction` در Resource Registry موجود باشد.
- MFE فقط عناصر غیرمجاز را پنهان می‌کند؛ تصمیم امنیتی نهایی همیشه در BFF/Authorization Service است.

قرارداد کامل، APIهای مستقل sync و قواعد مالکیت در
[جداسازی MF و Resource Manifest](mf-and-resource-manifest-separation-fa.md) آمده است.

## ۱۴. دو نمونه کامل Proxy

### OAuth2 جدید

1. Auth Profile = `public-iam-forward` / `FORWARD_USER_TOKEN`.
2. Target = Gateway allowlisted و upstream base path سرویس.
3. Route = `/api/proxy/payroll` با GETهای لازم.
4. Operation = resource/action معتبر و `authorizationRequired=true`.
5. BFF token Keycloak را از vault Redis می‌خواند؛ browser token را نمی‌بیند.

### Legacy

1. Secret Store: credential با نام `secret://legacy/payroll` ایجاد و rotate شود.
2. Outbound Connection: فقط origin token endpoint ثبت شود.
3. Auth Profile: connection، endpoint path، adapter و Secret Ref ثبت شوند.
4. Target: همان Gateway با profile Legacy انتخاب شود.
5. Route/Operation: مانند سرویس جدید، resource/action و مجوز مستقل داشته باشد.
6. BFF token Legacy را می‌گیرد، رمز‌شده و TTLدار در Redis نگه می‌دارد، و Gateway هدر خصوصی را
   به Authorization upstream تبدیل و سپس حذف می‌کند.

## ۱۵. چک‌لیست انتشار تغییر راهبری

1. نام مالک و change ticket در توضیحات ثبت شده است.
2. شناسه‌های canonical و slugها با naming convention تطبیق دارند.
3. URLها Gateway/allowlist را دور نمی‌زنند و در production TLS روشن است.
4. Secret value در هیچ فرم، SQL، log یا screenshot وجود ندارد.
5. Preview/Match/Resolution و health check موفق‌اند.
6. resource/action پیش از route operation وجود دارد.
7. grant با کمترین سطح و ترجیحاً به Role/Group داده شده است.
8. Outbox به `APPLIED` رسیده و explain مسیر دسترسی را تأیید می‌کند.
9. rollback برای artifact، route، profile و grant مشخص است.
10. Correlation ID تست در ticket است، اما token fingerprint فقط در log محلی نگه داشته می‌شود.

## ۱۶. آموزش گام‌به‌گام سناریوهای اصلی

### ۱۶.۱ از صفر تا نمایش یک Microfrontend

**قبل از شروع:** تیم MFE باید `remoteEntry.js`، نام container، exposed module، نسخه قرارداد
و manifest معتبر را تحویل داده باشد. URL را در مرورگر باز کنید؛ پاسخ باید JavaScript با status
200 باشد، نه JSON خطا یا صفحه HTML.

1. در «میکروفرانت‌ها» رکورد جدید بسازید. `slug`، `service_slug` و `route_base_path` به‌ترتیب
   هویت ماژول، namespace API و مسیر صفحه‌اند و نباید با هم اشتباه شوند.
2. برای پروژه دارای قرارداد منابع `HYBRID` یا `MANIFEST` را انتخاب کنید؛ برای مهاجرت تدریجی
   بدون manifest از `MANUAL` استفاده کنید.
3. رکورد را ابتدا غیرفعال ذخیره و URLها را از شبکه خود سرویس Registry/BFF آزمایش کنید.
4. «Sync Frontend Manifest» را اجرا کنید؛ برای مهاجرت یا rollback می‌توان Artifact snapshot را
   دستی منتشر و Activate کرد.
5. مستقل از آن، Fetch/Import، Preview Diff و Publishِ Resource Manifest را انجام دهید.
   `CONFLICT` را دور نزنید؛ مالکیت یا نسخه را اصلاح کنید.
6. navigation را بررسی کنید؛ overlay برای نمایش است و جای Resource/Permission را نمی‌گیرد.
7. در Access Studio، resource/actionها را بررسی و grant لازم را به Role یا Group بدهید.
8. Panel را فعال و با یک کاربر مجاز و یک کاربر غیرمجاز تست کنید.

**معیار قبولی:** catalog کاربر مجاز Remote Entry کامل، route پیش‌فرض معتبر و navigation مجاز را
می‌دهد؛ کاربر غیرمجاز نه منو را می‌بیند و نه API محافظت‌شده را اجرا می‌کند.

### ۱۶.۲ ساخت دسترسی سازمانی بر پایه OU

1. از تازه‌بودن Sync و درست‌بودن path در «OUهای سازمانی» مطمئن شوید.
2. Access Group را با کد پایدار و `ANY_OF` یا `ALL_OF` بسازید.
3. Ruleها را با `EXACT` یا `SUBTREE` اضافه و برای OU سطح بالا Preview بگیرید.
4. `wouldAdd` و `wouldRemove` و نمونه اعضای مؤثر را با مالک سازمانی تأیید کنید.
5. در «دسترسی Microfrontend» برنامه و گروه را انتخاب و VIEWER را Grant کنید.
6. پس از همگام‌سازی، در «بررسی دسترسی User» مسیر کامل تصمیم را کنترل کنید.

Rollback با revoke کردن grant برنامه یا غیرفعال‌کردن Rule مسئله‌دار انجام می‌شود. چون Rule ممکن
است روی چند برنامه اثر بگذارد، پیش از آن دامنه اثر را Preview کنید.

### ۱۶.۳ تعریف مجوز دقیق Resource/Action

1. کوچک‌ترین resourceای را انتخاب کنید که Backend واقعاً می‌تواند از آن دفاع کند.
2. والد را بر اساس دامنه و inheritance تعیین و canonical key را با تیم Backend قطعی کنید.
3. actionهای لازم را روشن کنید؛ روشن‌کردن action به کسی grant نمی‌دهد.
4. Subject را ترجیحاً Role یا Group انتخاب و grant کنید. endpoint باید دقیقاً همان
   `resourceKey/actionKey` را کنترل کند.
5. تست مثبت و منفی انجام دهید؛ مخفی‌شدن دکمه در UI کنترل امنیت Backend نیست.

### ۱۶.۴ از قرارداد upstream تا Proxy عملیاتی

ترتیب ساخت: **Outbound Connection → Auth Profile → Service Target → Proxy Route → Operation**.

1. origin توکن Legacy را در اتصال خروجی ثبت کنید؛ credential در فرم ننویسید.
2. برای OAuth2 کاربرمحور `FORWARD_USER_TOKEN` و برای Legacy، حالت
   `LEGACY_SERVICE_TOKEN` را بسازید و فیلدهای شرطی بخش ۷.۲ را کامل کنید.
3. Target را با Gateway کنترل‌شده، base path، timeout و سقف پاسخ بسازید و Health را بزنید.
4. Route را به Panel و Target وصل کنید. `serviceSlug` باید با درخواست MFE زیر
   `/api/proxy/{serviceSlug}` هماهنگ باشد. Validate و Resolve Test را اجرا کنید.
5. برای هر method/path یک Operation با resource/action واقعی، سقف body و نیاز مجوز بسازید.
6. Match Test بگیرید، سپس route و target را فعال و آزمایشگاه اتصال را اجرا کنید.

برای عملیات غیر idempotent retry را فعال نکنید مگر upstream کلید idempotency داشته باشد.
`Data Policy` فقط ارجاع به policy ازپیش‌تعریف‌شده است؛ این صفحه فرم ساخت policy ندارد.

### ۱۶.۵ راه‌اندازی دسترسی گزارش Superset

1. instanceهای `PUBLIC` و `OPERATION` را با origin، connection reference و auth mode بسازید.
2. mapping را با public path یکتا ایجاد و در صورت نیاز فقط یکی را پیش‌فرض کنید.
3. external ID دارایی‌ها را با Superset مقصد تطبیق دهید.
4. Subject و کمترین سطح دسترسی لازم را در «گزارش‌ها و داشبوردها» انتخاب کنید.
5. با کاربر آزمایشی، هم catalog و هم دریافت embed/داده را تست کنید.

## ۱۷. راهنمای تصمیم‌گیری

| وضعیت | انتخاب | دلیل |
|---|---|---|
| منابع همراه کد MFE نسخه‌بندی می‌شوند | `MANIFEST` | منبع حقیقت واحد و انتشار تکرارپذیر |
| مهاجرت تدریجی یا منابع تکمیلی دارید | `HYBRID` | مالکیت ADMIN و MANIFEST مستقل می‌ماند |
| MFE قدیمی و بدون قرارداد است | `MANUAL` | راهبری دستی صریح تا زمان مهاجرت |
| چند OU مستقل گروه را می‌سازند | `ANY_OF` | عضویت در هر شاخه کافی است |
| همه معیارهای OU باید برقرار باشند | `ALL_OF` | تقاطع صریح Ruleها |
| API با توکن همان کاربر کار می‌کند | `FORWARD_USER_TOKEN` | حفظ هویت و scope کاربر |
| Legacy فقط credential سرویس می‌پذیرد | `LEGACY_SERVICE_TOKEN` | تبادل server-side با secret reference |
| استثنای کوتاه برای یک نفر | `USER` grant مستند | دامنه محدود ولی نیازمند بازبینی |
| دسترسی شغلی تکرارشونده | `ROLE` یا Group | حسابرسی و نگهداری ساده‌تر |

## ۱۸. چرخه عمر و وضعیت‌ها

| وضعیت/عمل | برداشت صحیح |
|---|---|
| `ACTIVE` | قابل استفاده، مشروط به فعال‌بودن وابستگی‌ها و مجوزها |
| `DEPRECATED` | خارج از درخت فعال، با سابقه قابل Audit |
| `PENDING` | Outbox هنوز تغییر را اعمال نکرده؛ نتیجه نهایی فرض نشود |
| `APPLIED` | OpenFGA همگام شده؛ تست نهایی کاربر همچنان لازم است |
| `FAILED` | لاگ، Correlation ID و reconciliation باید بررسی شود |
| Publish | نسخه immutable ثبت می‌شود، الزاماً فعال نمی‌شود |
| Activate | نسخه منتشرشده مؤثر می‌شود |
| Revoke | رابطه دسترسی حذف می‌شود، نه هویت یا resource |

## ۱۹. عیب‌یابی بر اساس نشانه

| نشانه | علت محتمل | اقدام |
|---|---|---|
| منوی ادمین دیده نمی‌شود | grant route نیست یا context قدیمی است | مجوز بخش ۰.۴، ورود مجدد و پاسخ context را بررسی کنید |
| `Remote Entry must be a complete http(s) URL` | URL artifact خالی/نسبی یا catalog قدیمی است | URL و نسخه فعال را کنترل و catalog را تازه کنید |
| MIME برابر JSON و status 500 برای Remote Entry | proxy پاسخ خطا داده، نه JavaScript | `/api/mfe/{slug}/remoteEntry.js` و لاگ BFF/شبکه MFE را بررسی کنید |
| Manifest Fetch رد می‌شود | network policy، DNS، size/schema یا moduleKey ناسازگار است | مقصد، content، slug و نسخه را تطبیق دهید |
| `VERSION_CONFLICT` | تغییر هم‌زمان رخ داده | reload، مقایسه و اعمال دوباره؛ overwrite کور نکنید |
| target اشتباه resolve می‌شود | prefix/priority یا rewrite هم‌پوشان است | Validate، Resolve Test و Match Test را با path واقعی اجرا کنید |
| upstream پاسخ 401 می‌دهد | profile یا token transport غلط است | mode، connection/secret ref و expiry را بررسی؛ token را لاگ نکنید |
| با وجود منو پاسخ 403 است | operation/grant ناقص یا Outbox معطل است | کلیدهای Backend، sync و explain کاربر را بررسی کنید |
| عضویت OU ناخواسته است | `SUBTREE` گسترده یا combiner غلط است | Preview و source عضویت را بررسی و Rule را غیرفعال کنید |
| Superset باز نمی‌شود | mapping، external ID یا grant ناسازگار است | instance، mapping و grant را با Correlation ID کنترل کنید |

در ticket زمان، محیط، کاربر، URL بدون secret، نسخه و Correlation ID را ثبت کنید. access token،
client secret، password و header حساس نباید در تصویر، ticket یا فرم قرار گیرند.

## ۲۰. Runbook کنترل تغییر

**پیش از تغییر:** هدف، مالک، ticket، محیط، کاربران متاثر، مقدار فعلی، نسخه فعال، تست منفی و
راه rollback را ثبت کنید. **هنگام تغییر:** هر بار یک لایه را عوض کنید، رکورد را در صورت امکان
غیرفعال بسازید و preview/probe را اجرا کنید. **پس از تغییر:** نتیجه effective را با هویت کم‌اختیار،
Audit/API Log و وضعیت sync بررسی و نسخه قبلی را تا پایان پایش نگه دارید.

## ۲۱. منابع فنی و مرز اعتبار سند

این راهنما از فرم‌ها و قراردادهای همین مخزن استخراج شده و قابلیت اختراع‌شده ندارد:

- [کاتالوگ routeها و مجوز صفحات](../apps/mfe-admin/src/admin-route-catalog.ts)
- [استودیوی دسترسی](../apps/mfe-admin/src/AccessStudio.tsx) و [دسترسی OU](../apps/mfe-admin/src/OuAccessManagement.tsx)
- [مدیریت Microfrontend، Artifact و Manifest](../apps/mfe-admin/src/Panels.tsx)
- [Target، Route و Operation](../apps/mfe-admin/src/ProxyRoutes.tsx)
- [اتصال‌های خروجی](../apps/mfe-admin/src/OutboundConnections.tsx) و [پروفایل‌های احراز هویت](../apps/mfe-admin/src/OutboundAuthProfiles.tsx)
- [آزمایشگاه اتصال](../apps/mfe-admin/src/IntegrationTestLab.tsx)
- [محیط‌های Superset](../apps/mfe-admin/src/SupersetInstances.tsx) و [دارایی‌ها](../apps/mfe-admin/src/SupersetAssets.tsx)
- [هویت و نقش](../apps/mfe-admin/src/IdentityAndRoles.tsx) و [لاگ‌ها](../apps/mfe-admin/src/Logs.tsx)
- [معماری Resource Catalog و Manifest](resource-catalog-manifest-architecture-fa.md)

مرجع نهایی allowlist، TLS، SRI، secret provider و timeout تنظیمات همان محیط است؛ موفقیت فرم
تضمین نمی‌کند زیرساخت production نیز مقصد را مجاز بداند.

## ۲۲. نمایه نام فنی همه فیلدهای فرم

این نمایه برای تطبیق UI با payload و عیب‌یابی DevTools است. شرح، الزام و مثال هر مورد در جدول
بخش مربوط آمده است. `version` فیلد مخفی قفل خوش‌بینانه است و نباید دستی تغییر کند.

| فرم | نام فنی فیلدها |
|---|---|
| Access Group و Rule | `code`, `name`, `description`, `ruleCombiner`, `ouId`, `matchMode` |
| دسترسی MFE | `applicationId`, `accessGroupId` |
| Resource | `type`, `parentId`, `resourceKey`, `nameFa`, `nameEn`, `ownerDomain`, `classification`, `panelId`, `source`, `visibilityEnabled`, `externalSystem`, `externalType`, `externalId` |
| Grant | `subjectType`, `subjectId`, `actionId`, `relation`, `expiresAt` |
| Panel | `code`, `slug`, `name_fa`, `name_en`, `description`, `service_slug`, `remote_name`, `remote_entry_path`, `exposed_module`, `route_base_path`, `default_route_id`, `semantic_version`, `contract_version`, `resource_definition_mode`, `classification`, `mf_manifest_url`, `resource_manifest_url`, `sort_order`, `active` |
| Artifact | `artifactVersion`, `remoteEntryUrl`, `remoteName`, `exposedModule`, `contractVersion`, `integrity`, `manifest` |
| Navigation | `key`, `source`, `nodeType`, `title`, `parentKey`, `pageKey`, `externalUrl`, `order`, `hidden` |
| Service Target | `code`, `name`, `environment`, `outboundAuthProfileId`, `gatewayBaseUrl`, `upstreamBasePath`, `healthCheckPath`, `tlsProfileRef`, `secretRef`, `connectTimeoutMs`, `responseTimeoutMs`, `maxResponseSize`, `active`, `description` |
| Proxy Route | `code`, `panelId`, `serviceTargetId`, `serviceSlug`, `pathPrefix`, `stripPrefix`, `priority`, `allowedMethods`, `rewritePattern`, `rewriteReplacement`, `retryEnabled`, `maxRetries`, `preserveHost`, `active` |
| Route Operation | `httpMethod`, `pathPattern`, `resourceKey`, `actionKey`, `dataPolicyKey`, `maxBodyBytes`, `authorizationRequired`, `active` |
| Resolve/Match probe | `path`, `method` |
| Outbound Connection | `name`, `connectionRef`, `baseUrl`, `tlsRequired`, `active`, `version` |
| Outbound Auth Profile | `code`, `name`, `authMode`, `tokenConnectionRef`, `tokenEndpointPath`, `requestFormat`, `credentialTransport`, `credentialSecretRef`, `authorizationScheme`, `tokenResponsePointer`, `tokenTypeResponsePointer`, `expiresInResponsePointer`, `expirySkewSeconds`, `connectTimeoutMs`, `responseTimeoutMs`, `maxTokenResponseSize`, `active`, `description`, `version` |
| Superset Instance | `code`, `name`, `zone`, `baseUrl`, `connectionRef`, `authMode`, `tlsRequired`, `active`, `version` |
| Superset Mapping | `publicInstanceId`, `operationInstanceId`, `publicPath`, `isDefault`, `active` |
| Superset Grant | `subjectType`, `subjectId`, `level` |
| Role | `roleKey`, `nameFa`, `nameEn` |
| Role Assignment | `subjectType`, `subjectId`, `roleId` |
| API Log filters | `serviceName`, `route`, `userId`, `statusCode`, `correlationId` |
| Audit Log filters | `actorId`, `eventType`, `targetType`, `targetId`, `result`, `correlationId` |
