# راهنمای مدیریت میکروها، دسترسی‌ها و مسیرهای سرویس

تاریخ بررسی: ۲۰ سپتامبر ۲۰۲۶. این راهنما برای نسخه موجود در همین مخزن نوشته شده است. نتایج اجرای آزمون‌ها در [گزارش اجرای سراسری](project-e2e-results-2026-09-20-fa.md) جداگانه ثبت می‌شوند؛ وجود دستور یا سناریو در این راهنما به معنی اجرای موفق آن نیست.

## ۱. ترتیب راه‌اندازی و تعریف

ترتیب عملی کار چنین است: هویت کاربر ← ثبت میکرو و Artifact ← تعریف منابع و actionها ← ساخت نقش یا گروه دسترسی ← اعطای مجوز ← تعریف مقصد و پروفایل احراز هویت ← Route و Operation ← آزمون با کاربر مجاز و غیرمجاز.

| بخش | مسیر مدیریت | خروجی مورد انتظار |
|---|---|---|
| میکروفرانت | `/admin/panels` | میکروی فعال با Artifact و قرارداد معتبر |
| منابع و درخت دسترسی | `/admin/access-studio` | منبع، والد، action و grant |
| هویت و نقش | `/admin/identity` | نقش و انتساب به کاربر/گروه |
| واحدهای سازمانی | `/admin/ou-access/ous` | OUهای همگام‌شده از Directory |
| گروه دسترسی محاسباتی | `/admin/ou-access/groups` | قواعد عضویت بر اساس OU |
| تخصیص میکرو به گروه | `/admin/ou-access/applications` | دسترسی گروه به میکرو |
| توضیح دسترسی کاربر | `/admin/ou-access/explain` | مسیر مؤثر دسترسی سازمانی |
| مقصد سرویس | `/admin/proxy-routes/targets` | Target فعال و قابل دسترس از BFF |
| مسیر API | `/admin/proxy-routes/routes` | اتصال میکرو، Target و Auth Profile |
| عملیات API | `/admin/proxy-routes/operations` | method/path و مجوز همان endpoint |
| اتصال Legacy | `/admin/outbound-connections` | origin تأییدشدهٔ دریافت توکن |
| پروفایل احراز هویت | `/admin/outbound-auth` | Forward یا Legacy |
| آزمایش اتصال | `/admin/integration-test` | نتیجه واقعی فراخوانی مقصد |
| محیط گزارش | `/admin/superset-instances` | Public/Operation و mapping |
| مجوز گزارش و داشبورد | `/admin/superset` | grant در سطح دارایی |
| گزارش‌های کاربر | `/reports` | فقط دارایی‌های قابل مشاهده برای همان کاربر |
| رویدادها | `/admin/logs/api` و `/admin/logs/audit` | نتیجه، actor، مقصد و Correlation ID |

ورود به صفحه مدیریت به مجوز خود صفحه نیاز دارد. مخفی بودن منو جای کنترل سمت سرور را نمی‌گیرد. API مدیریت از مسیر عمومی `/api/v1/admin/...` و نشست BFF استفاده می‌کند؛ مسیرهای `/internal/v1/...` مستندات برای ارتباط داخلی سرویس‌ها هستند.

## ۲. تعریف و مدیریت میکروفرانت

۱. فایل‌های build شامل `remoteEntry.js`، chunkها و Manifestها را روی میزبان قابل دسترس قرار دهید. اتصال BFF به میزبان، MIME فایل JavaScript و تطابق SRI با محتوای فایل را بررسی کنید.

۲. در «میکرو جدید» کد پایدار، slug، نام فارسی/انگلیسی، Service Slug، Remote Name، آدرس مطلق Remote Entry، Exposed Module مانند `./plugin`، Route Prefix، Default Route ID، نسخه SemVer و Contract Version را ثبت کنید. Route Prefix مسیر نمایشی است؛ Service Slug فضای نام API است.

۳. حالت منابع را انتخاب کنید:

| حالت | کاربرد |
|---|---|
| `MANUAL` | منابع مجوز توسط راهبر تعریف می‌شوند |
| `MANIFEST` | منابع از Resource Manifest نسخه‌دار می‌آیند |
| `HYBRID` | هر دو روش با رعایت مالکیت مستقل منابع |

۴. Artifact را Validate و Publish کنید. `remoteName` باید با container واقعی build تطبیق داشته باشد. Snapshot مربوط به MF Manifest، routeهای محلی و navigation را تعریف می‌کند. نسخه منتشرشده immutable است؛ تغییر محتوا را با نسخه جدید منتشر کنید و سپس Activate بزنید.

۵. برای منابع Manifest، فایل Resource Manifest را Fetch یا Import کنید، Diff و تعارض‌ها را ببینید و Draft معتبر را Publish کنید. `moduleKey` این فایل باید با slug پنل تطبیق داشته باشد. MF Manifest و Resource Manifest دو قرارداد جدا هستند؛ انتشار یکی جای دیگری را نمی‌گیرد.

۶. برای اصلاح منو از Navigation Overlay استفاده کنید. PAGE باید به route معتبر ارجاع بدهد. مخفی کردن navigation یا visibility مجوز Backend را لغو نمی‌کند.

۷. بعد از grant، با نشست کاربر مقصد بارگذاری میکرو، route پیش‌فرض، refresh روی deep link و فراخوانی API را امتحان کنید. پاسخ 200 به HTML پوسته به‌تنهایی بارگذاری موفق میکرو را اثبات نمی‌کند.

بازگشت: Artifact معتبر قبلی را Activate کنید. برای توقف دسترسی، میکرو یا grant مربوط را غیرفعال/لغو کنید و نتیجه مؤثر را دوباره بسنجید. نمونه‌ها و جزئیات فیلدها: [چرخه عمر میکرو](microfrontend-registration-and-lifecycle-fa.md)، [راهنمای فرم‌ها](operator-admin-form-field-guide-fa.md).

## ۳. تعریف سطح دسترسی و درخت

در Access Studio ابتدا Resource را بسازید. یک ساختار نمونه:

```text
APPLICATION: application:payroll
└── MODULE: module:payroll.people
    └── PAGE: page:payroll.employees
        └── FIELD: field:payroll.employee.salary
```

نوع، کلید canonical، نام‌ها، والد و میکروی مالک را تعیین کنید. برای منبع خارجی، `externalSystem`، `externalType` و `externalId` نیز لازم‌اند. انواع دیگر شامل `UI_COMPONENT` و `BUSINESS_RESOURCE` هستند. والد در وراثت دسترسی مؤثر است؛ تغییر والد را با آزمون کاربر فاقد grant مستقیم کنترل کنید. چرخه در درخت مجاز نیست.

Actionهای مجاز را روی منبع فعال کنید؛ این کار فقط قرارداد عملیات را تعریف می‌کند و به کسی مجوز نمی‌دهد. سپس Subject و Action را در تخصیص دسترسی انتخاب کنید:

| Action رایج روی Resource | رابطه ذخیره‌شده |
|---|---|
| `view` | `viewer` |
| `create` | `creator` |
| `update` | `editor` |
| `delete` | `deleter` |
| `admin` | `manager` |

این جدول برای Resource عمومی است؛ روابط APPLICATION و EXTERNAL_RESOURCE را از قرارداد نوع همان منبع انتخاب کنید. جزئیات نگاشت و نمونه‌های عملی در [راهنمای تعریف مجوز](permission-definition-and-operation-fa.md) آمده است.

در صفحه هویت Role بسازید، grant منابع لازم را به Role بدهید و سپس Role را به USER، DIRECTORY_GROUP یا ACCESS_GROUP منتسب کنید. برای استثنای فردی می‌توان grant مستقیم داد. نام نمایشی کاربر کلید امنیتی نیست؛ هویت داخلی انتخاب‌شده از سرور مبنای انتساب است.

ثبت موفق در PostgreSQL پایان کار نیست: Outbox باید تغییر را به OpenFGA اعمال کند. پس از رسیدن به وضعیت مؤثر، هم مجوز API و هم کاتالوگ UI کاربر را بررسی کنید. در لغو، علاوه بر حذف grant مستقیم، مجوز باقی‌مانده از نقش، گروه یا والد را نیز بررسی کنید؛ ممکن است کاربر از مسیر دیگری هنوز مجاز باشد.

منابع با مالکیت `MANIFEST` را در محیط عادی با نسخه جدید Manifest تغییر دهید. در فرم راهبری، visibility قابل مدیریت است ولی ساختار و actionهای آن‌ها تابع قرارداد منتشرشده هستند.

## ۴. دسترسی سازمانی مبتنی بر OU

کاربر، OU و گروه Directory از سامانه هویت/Directory همگام می‌شوند. این صفحات جایگزین ساخت کاربر یا OU در سامانه مبدأ نیستند.

۱. OU فعال و مسیر آن را در فهرست کنترل کنید.
۲. Access Group بسازید و Ruleهای OU را به آن اضافه کنید.
۳. `EXACT` فقط همان OU و `SUBTREE` زیرشاخه‌ها را هم شامل می‌شود. `ANY_OF` اجتماع شرایط و `ALL_OF` الزام همزمان آن‌هاست.
۴. Preview اعضا را پیش از تخصیص ببینید؛ انتخاب یک OU بالادستی با SUBTREE می‌تواند دامنه وسیعی ایجاد کند.
۵. در «دسترسی Microfrontend» گروه را به میکرو Grant کنید، یا در هویت، Role مورد نظر را به Access Group بدهید.
۶. Explain را برای عضو داخل و خارج محدوده اجرا کنید و API و منوی هر دو را بسنجید.

بازگشت: Rule یا گروه را غیرفعال کنید یا grant/assignment را لغو کنید؛ پس از همگام‌سازی، عضویت و دسترسی مؤثر را دوباره بررسی کنید. [راهنمای OU](ou-based-microfrontend-authorization-fa.md).

## ۵. تعریف Route از نوع Forward

Forward یعنی ارسال توکن کاربر توسط BFF به مقصد. مرورگر فقط درخواست same-origin و نشست خود را به BFF می‌فرستد و محل نگهداری توکن سرویس نیست.

۱. در Auth Profile حالت `FORWARD_USER_TOKEN` و transport برابر `USER_AUTHORIZATION_HEADER` را انتخاب کنید.
۲. Target بسازید: Gateway origin مجاز، مسیر پایه upstream، مسیر health، timeoutها و سقف اندازه پاسخ.
۳. Route را به Panel، Target و Auth Profile وصل کنید. Prefix عمومی، methodهای مجاز، priority و روش تبدیل مسیر را تعیین کنید.
۴. Operationهای واقعی را زیر Route تعریف کنید: HTTP Method، الگوی مسیر، Resource Key، Action Key، سقف body و `authorizationRequired`.
۵. Validate، Resolve Test و Match Test را اجرا کنید، سپس درخواست واقعی با کاربر مجاز و فاقد مجوز بفرستید.

مثال آموزشی:

| تنظیم | مقدار |
|---|---|
| Prefix عمومی | `/api/proxy/payroll` |
| `stripPrefix` | `3` |
| Target base path | `/payroll-service` |
| Operation | `GET /employees/{id}` |
| مجوز | `page:payroll.employees` + `view` |
| درخواست | `/api/proxy/payroll/employees/42` |
| مسیر نهایی Gateway | `/payroll-service/employees/42` |

سرویس مقصد باید اعتبار توکن دریافتی را نیز کنترل کند. دسترسی به خود میکرو مجوز خودکار تمام Operationهای آن نیست.

## ۶. تعریف Route از نوع Legacy

۱. Outbound Connection با `connectionRef` پایدار، base URL سرویس توکن و سیاست TLS بسازید. این آدرس باید از شبکه BFF قابل دسترس و در policy مجاز باشد.
۲. ارجاع Secret را در secret resolver سمت BFF آماده کنید. مقدار رمز را در فرم، Manifest، مرورگر، سند یا لاگ ننویسید.
۳. Auth Profile با `LEGACY_SERVICE_TOKEN` بسازید و Connection، token endpoint، قالب درخواست، `credentialSecretRef`، pointerهای JSON پاسخ توکن/انقضا/نوع توکن، skew و timeoutها را وارد کنید. قالب درخواست باید با قرارداد واقعی token endpoint تطبیق داشته باشد.
۴. برای مسیر استاندارد Gateway، transport برابر `INTERNAL_LEGACY_HEADER` است. Connection Test و Token Test را اجرا کنید و Cache Status را بررسی کنید.
۵. Target، Route و Operation را مانند Forward بسازید ولی Auth Profile نوع Legacy را به Route متصل کنید.
۶. Gateway باید توکن داخلی Legacy را به Authorization مقصد تبدیل و هدر داخلی را حذف کند. نداشتن این تنظیم با ساختن Route در پایگاه داده جبران نمی‌شود.

قبل از دریافت credential Legacy، مجوز کاربر در BFF بررسی می‌شود. کش توکن به profile/version وابسته است. برای تغییر Secret یا عیب‌یابی از invalidate-token استفاده و بازیابی پس از ابطال را آزمایش کنید. در تست منفی، نبود Secret، پاسخ نامعتبر token endpoint، انقضا و خطای مقصد را بررسی کنید.

یک میکرو می‌تواند همزمان دو Route مستقل داشته باشد:

| Route | Prefix | Auth Profile |
|---|---|---|
| سرویس جدید | `/api/proxy/payroll/modern` | Forward |
| سامانه قدیمی | `/api/proxy/payroll/legacy` | Legacy |

برای هر کدام Target و Operation مناسب تعریف کنید. در این مثال با `stripPrefix=4` چهار segment حذف می‌شوند. Prefix بلندتر در انتخاب Route مقدم بر priority است. اگر rewrite فعال است، replacement مسیر کامل مقصد را می‌سازد و `upstreamBasePath` دوباره به آن اضافه نمی‌شود. نمونه‌های JSON، خطاها و تبدیل مسیر: [راهنمای کامل Legacy/Forward](proxy-routing-legacy-forward-fa.md).

## ۷. تعریف دسترسی گزارش و داشبورد

۱. در محیط‌های Superset، Instanceهای PUBLIC و OPERATION و mapping میان آن‌ها را ثبت و فعال کنید. اتصال، health و شناسه محیط مقصد را کنترل کنید.
۲. دارایی واقعی dashboard/chart باید در محیط Operation موجود و در کاتالوگ برنامه شناخته شده باشد. external ID باید متعلق به همان محیط باشد؛ عنوان گزارش جای شناسه پایدار آن را نمی‌گیرد.
۳. در `/admin/superset` دارایی را پیدا کنید، «سطوح دسترسی» را باز و Subject را از گزینه‌های سرور انتخاب کنید: کاربر، LDAP Group، Access Group یا Role.
۴. سطح مورد نیاز را انتخاب کنید:

| سطح | عملیات متناظر |
|---|---|
| `VIEW` | مشاهده (`view`) |
| `EDIT` | ویرایش (`update`) |
| `MANAGE` | مدیریت (`admin`) |

۵. با همان کاربر `/reports` را باز کنید. وجود لینک، باز شدن dashboard و موفقیت درخواست واقعی chart data را جداگانه بررسی کنید. برای داشبورد دارای چند chart، مجوز دارایی‌های مورد استفاده را هم کنترل کنید.
۶. با کاربر فاقد مجوز، لینک نباید قابل استفاده باشد و API مستقیم نیز باید رد شود. برای VIEW، درخواست نوشتن باید رد شود. سپس grant را لغو و نتیجه را پس از اعمال Outbox دوباره آزمایش کنید.

مجوز Aurevia جای مجوز datasource، نقش‌های داخلی Superset یا RLS را نمی‌گیرد. دسترسی دیدن صفحه گزارش با دسترسی خود دارایی متفاوت است. لغو grant دارایی، خود dashboard/chart را حذف نمی‌کند. [معماری گزارش و embedding](superset-routing-and-embedding-fa.md).

## ۸. کنترل‌های نهایی و عیب‌یابی

| نشانه | بررسی عملی |
|---|---|
| پوسته باز می‌شود ولی میکرو خطای integrity/network دارد | Remote Entry، chunkها، میزبان فایل، MIME، SRI و Artifact فعال |
| grant ثبت شده ولی اثری ندارد | وضعیت Subject، Role/Group، Outbox، OpenFGA و cache کاتالوگ |
| Route resolve می‌شود ولی مقصد 404 می‌دهد | strip/rewrite، مسیر نهایی و location واقعی Gateway |
| Legacy پاسخ 502 می‌دهد | policy میزبان/پورت، Connection، Secret resolver و token endpoint |
| کاربر بعد از Revoke هنوز مجاز است | grant از والد، گروه یا Role دیگر |
| dashboard باز می‌شود ولی داده ندارد | مجوز chart و datasource، mapping محیط و RLS |
| ویرایش همزمان رد می‌شود | رکورد را reload و با version جدید دوباره اقدام کنید |

برای هر تغییر، actor، target، نتیجه و Correlation ID را در Audit/API Logs دنبال کنید. برای آزمون از داده با شناسه یکتا استفاده کنید و پس از پایان grant و assignment را لغو و رکورد موقت را غیرفعال کنید. داده یا نقش واقعی دیگران را برای تست تغییر ندهید.

## ۹. اجرای آزمون‌ها

```powershell
npm test
npm run typecheck
npm run build
.\mvnw.cmd -Pe2e-sso-legacy test
npm run test:permission:verify
npm run infra:verify
npm run e2e:auth:verify
node tools/verify-all-pages.mjs
```

تست‌های permission به متغیرهای `AUREVIA_TEST_JDBC_URL`، مشخصات کاربر دیتابیس، `AUREVIA_TEST_OPENFGA_URL` و مشخصات Redis نیاز دارند؛ بدون آن‌ها ممکن است Maven موفق شود ولی تست‌های اصلی skip باشند. `test:permission:verify` برای تشخیص همین حالت است.

برای SSO/Legacy، علاوه بر سرویس‌های دمو، overlay `compose.e2e-auth-core.yml` نیز لازم است؛ برای چهار میکرو، میزبان فایل مستقل باید روشن باشد. runner صفحات برای سناریوهای native به `.tmp/superset-native/registration.json` نیاز دارد؛ نبود آن باید BLOCKED گزارش شود، نه PASS. تست‌های `tests/e2e` قرارداد کد را بررسی می‌کنند و معادل آزمون مرورگر نیستند.

جزئیات ۱۸ صفحه و فیلدهای فرم در [راهنمای فرم‌های راهبری](operator-admin-form-field-guide-fa.md) و سناریوهای منفی در [راهنمای آزمون راهبری](admin-governance-e2e-test-guide-fa.md) آمده است.
