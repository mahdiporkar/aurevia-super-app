# راهنمای جامع فرم‌ها و کنترل‌های میکرو راهبری Aurevia

نسخه سند: ۱.۰ — مخاطب: راهبر سامانه، مدیر امنیت، مدیر گزارش و تیم استقرار

این سند مرجع field-by-field تمام فرم‌ها و کنترل‌های عملیاتی `mfe-admin` است. هر تغییر در
فرم، DTO یا validation سمت سرور باید در همان Pull Request در این سند نیز اعمال شود.

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
| آدرس کامل Remote Entry | الزامی؛ HTTP(S) absolute | URL دقیق artifact. در production باید origin allowlisted و سیاست HTTPS/SRI رعایت شود | `https://cdn.example/mfe/payroll/remoteEntry.js` |
| Exposed Module | الزامی؛ با `./` | module exportشده توسط container | `./plugin` |
| Route Prefix | الزامی؛ `/` + kebab-case | مسیر UI؛ `login/admin/settings/api/assets/error` برای رکورد جدید رزروشده‌اند | `/payroll` |
| Default Route ID | الزامی | باید با یکی از `routes[].id` در manifest artifact یکسان باشد | `employee-list` |
| نسخه | الزامی؛ SemVer | نسخه deploy مانند `1.4.2` یا prerelease معتبر | `1.4.2` |
| نسخه قرارداد | الزامی | نسخه قرارداد Shell↔MFE؛ مستقل از نسخه محصول | `1.0` |
| ترتیب | اختیاری؛ عدد | ترتیب menu؛ عدد کوچک‌تر زودتر نمایش داده می‌شود | `30` |
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
| Manifest Snapshot | الزامی؛ JSON معتبر | routes، menus، resource و action همان artifact؛ پس از انتشار immutable است | نمونه در بخش ۱۳ |
| Validate و Publish | mutation | ابتدا schema/URL/SRI را validate و سپس artifact را ثبت می‌کند |
| Activate / Rollback | تأیید نسخه | active artifact پنل را atomically تغییر می‌دهد؛ rollback یعنی فعال‌کردن artifact معتبر قبلی |

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

## ۱۳. نمونه Manifest استاندارد

```json
{
  "schemaVersion": "1.0",
  "moduleKey": "hr-payroll",
  "defaultRouteId": "employee-list",
  "routes": [
    {
      "id": "employee-list",
      "path": "employees",
      "title": "کارکنان",
      "resource": "page:hr.employees",
      "action": "view"
    }
  ],
  "menus": [
    {
      "id": "employees-menu",
      "routeId": "employee-list",
      "title": "کارکنان",
      "icon": "team",
      "order": 10
    }
  ]
}
```

قواعد مهم:

- `defaultRouteId` باید در `routes[].id` وجود داشته باشد.
- هر menu باید به route موجود اشاره کند.
- `resource` باید canonical و در catalog فعال باشد و `action` باید برای همان resource تعریف شده باشد.
- MFE فقط عناصر غیرمجاز را پنهان می‌کند؛ تصمیم امنیتی نهایی همیشه در BFF/Authorization Service است.

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

