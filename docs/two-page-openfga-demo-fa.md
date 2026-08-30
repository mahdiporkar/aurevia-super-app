# دموی دسترسی دو صفحه HR و Finance با OpenFGA

این سناریو نشان می‌دهد که دسترسی Micro Frontend، صفحه و API سه کنترل مستقل هستند و مدیر می‌تواند از پنل Admin دسترسی یک شخص را اعطا یا لغو کند.

## صفحات Demo

| صفحه | Micro Frontend | Page Resource | API Resource اصلی |
|---|---|---|---|
| لیست کارکنان | HR | `page:hr.employee.list` | `business:hr.employee` |
| مدیریت پرداخت‌ها | Finance | `page:finance.payments` | `finance.payment` |

صفحه کارکنان برای داده‌های مرجع فرم از `hr.department` و `hr.position` نیز استفاده می‌کند. صفحه پرداخت فقط endpointهای `/payments` را فراخوانی می‌کند و دیگر به Invoice یا Budget وابسته نیست.

## کاربران نمونه

این کاربران فقط برای Local/Demo هستند و password مشترک آن‌ها `local-change-me` است:

| کاربر | پنل HR | صفحه کارکنان | پنل Finance | صفحه پرداخت‌ها |
|---|---:|---:|---:|---:|
| `demo-full-access` | مجاز | مجاز | مجاز | مجاز |
| `demo-hr-only` | مجاز | مجاز | غیرمجاز | غیرمجاز |

کاربر دوم هیچ grant مالی ندارد. بنابراین Finance در `manifest.panels` او قرار نمی‌گیرد، Remote مالی load نمی‌شود و درخواست دستی به API مالی نیز در OpenFGA برابر DENY خواهد بود.

تعریف هویت‌های Login در `infra/keycloak/realm-aurevia.json` و تعریف Control Plane/Outbox در migration زیر است:

```text
services/authorization-service/src/main/resources/db/migration/V29__two_page_access_demo.sql
```

## زنجیره کنترل دسترسی

```text
Login در Keycloak
  -> AUREVIA_SESSION در BFF
  -> GET /api/v1/me/manifest
  -> OpenFGA can_view برای application panel
  -> SHRouteGuard برای page resource
  -> درخواست API به BFF
  -> route resolution
  -> OpenFGA can_<action> برای business resource
  -> Operation Gateway و سرویس Demo
```

`SHRouteGuard` فقط کنترل UX است. حتی اگر کاربر JavaScript را تغییر دهد یا URL API را دستی بزند، BFF مجوز resource/action همان operation را دوباره از Authorization Service/OpenFGA می‌گیرد.

## Resourceها و Actionها

### HR

```text
application:aurevia/hr             view       نمایش پنل در Shell
page:hr.employee.list              view       ورود به صفحه کارکنان
business:hr.employee               list       GET /employees
business:hr.employee               view       GET /employees/{id}
business:hr.employee               create     POST /employees
business:hr.employee               update     PUT /employees/{id}
hr.department                      list/view  داده مرجع واحدها
hr.position                        list/view  داده مرجع سمت‌ها
field:hr.employee.salary-amount    view       نمایش فیلد حساس حقوق، در صورت اعطا
```

### Finance

```text
application:aurevia/finance        view        نمایش پنل در Shell
page:finance.payments              view        ورود به صفحه پرداخت‌ها
finance.payment                    list/view   مشاهده صف و جزئیات
finance.payment                    create      ساخت پرداخت
finance.payment                    approve     تأیید پرداخت
finance.payment                    reject      رد پرداخت
```

Migration، grantها را در PostgreSQL ثبت و `GRANT_WRITE` را در Transactional Outbox قرار می‌دهد. `OutboxReconciler` tuple متناظر را در OpenFGA می‌نویسد. کنترل مستقیم دیتابیس یا نوشتن دستی tuple برای عملیات روزمره مجاز نیست.

## اجرای سناریو از صفر

برای محیط تازه:

```bash
npm ci
npm run build
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml up -d --build
```

Flyway، migration نسخه ۲۹ را اجرا می‌کند و realm تازه Keycloak نیز دو کاربر را import می‌کند.

اگر volume قدیمی Keycloak وجود دارد، `--import-realm` یک Realm موجود را بازنویسی نمی‌کند. در این حالت دو کاربر را با همان username از Keycloak Admin Console بسازید، یا فقط در محیط Demo قابل حذف، Keycloak DB را طبق Runbook پاک و Realm را دوباره import کنید. حذف volume داده عملیاتی یا Production ممنوع است.

وضعیت migration:

```bash
docker compose --env-file .env -f infra/docker-compose/compose.yml \
  logs authorization-service | grep -E "Migrating schema|Successfully applied"
```

وضعیت Outbox باید پس از چند ثانیه پردازش شده باشد. از صفحه Logs/Admin برای backlog و dead-letter استفاده کنید.

## آزمایش کاربر اول: دسترسی به هر دو صفحه

1. از Super App خارج شوید.
2. با `demo-full-access / local-change-me` وارد شوید.
3. Manifest را در Network بررسی کنید:

```http
GET /api/v1/me/manifest
```

انتظار:

```text
panels شامل mfe-hr و mfe-finance
permissions[page:hr.employee.list] شامل view
permissions[page:finance.payments] شامل view
permissions[business:hr.employee] شامل list/view/create/update
permissions[finance.payment] شامل list/view/create/approve/reject
```

4. پنل HR را باز کنید؛ صفحه کارکنان و عملیات مجاز دیده می‌شود.
5. پنل Finance را باز کنید؛ صف پرداخت و دکمه‌های مجاز دیده می‌شود.
6. در Network کنترل کنید که APIها از `/hr-micro/api/v1` و `/finance-micro/api/v1` عبور می‌کنند.

## آزمایش کاربر دوم: فقط صفحه HR

1. Logout کنید تا session و Token Vault قبلی حذف شوند.
2. با `demo-hr-only / local-change-me` وارد شوید.
3. Manifest باید HR را داشته باشد و Finance را نداشته باشد.
4. صفحه کارکنان باید نمایش داده شود.
5. Remote مالی نباید load شود.
6. درخواست دستی زیر، حتی اگر توسط ابزار توسعه ساخته شود، باید `403` بگیرد:

```http
GET /finance-micro/api/v1/payments
```

این deny سمت API اثبات می‌کند که نبودن منوی Finance تنها کنترل امنیتی نیست.

## اعطا از پنل Admin

با `administrator` وارد شوید:

1. پنل «مدیریت» را باز کنید.
2. وارد «استودیوی دسترسی OpenFGA» شوید.
3. در درخت، `page:finance.payments` را جست‌وجو و انتخاب کنید.
4. در بخش «تخصیص دسترسی»، نوع هویت را روی «کاربر» قرار دهید.
5. کاربر «کاربر دموی فقط منابع انسانی» (`demo-hr-only`) را انتخاب کنید.
6. برای action `view` دکمه «اعطا» را بزنید.
7. سپس منبع `application:aurevia/finance` را انتخاب و `view` را نیز اعطا کنید.
8. برای اجرای واقعی API، روی `finance.payment` نیز actionهای موردنیاز مانند `list` و `view` را اعطا کنید.

بعد از پردازش Outbox و انقضای cache کوتاه OpenFGA، کاربر باید logout/login یا Manifest را refresh کند. اکنون پنل و صفحه Finance قابل مشاهده است.

نکته مهم: اعطای Page به‌تنهایی پنل را وارد Shell نمی‌کند؛ اعطای Panel به‌تنهایی نیز API را مجاز نمی‌کند. برای یک قابلیت عملیاتی کامل هر سه لایه لازم‌اند.

## ندادن یا لغو دسترسی

برای این‌که `demo-hr-only` دوباره فقط HR را ببیند:

1. در Admin، کاربر را انتخاب کنید.
2. روی `finance.payment`، grantهای مالی را با «لغو» حذف کنید.
3. روی `page:finance.payments`، action `view` را لغو کنید.
4. روی `application:aurevia/finance`، action `view` را لغو کنید.
5. وضعیت Outbox را بررسی کنید تا `GRANT_DELETE` اعمال شود.
6. کاربر logout/login کند.

در این سامانه «عدم دسترسی» معمولاً با نبودن tuple تعریف می‌شود، نه با tuple صریح deny. اگر دسترسی از ROLE یا GROUP آمده باشد، لغو grant مستقیم USER کافی نیست؛ باید منبع مؤثر role/group نیز در فهرست grantها بررسی شود.

## نتیجه مورد انتظار OpenFGA

```text
check(user:demo-full-access, can_view, resource:page/hr.employee.list)       = true
check(user:demo-full-access, can_view, resource:page/finance.payments)      = true
check(user:demo-hr-only, can_view, resource:page/hr.employee.list)          = true
check(user:demo-hr-only, can_view, resource:page/finance.payments)          = false
check(user:demo-hr-only, can_view, application:aurevia/finance)             = false
check(user:demo-hr-only, can_view, resource:finance.payment)                = false
```

## عیب‌یابی

| نشانه | بررسی |
|---|---|
| کاربر در Admin هست ولی Login نمی‌کند | کاربر PostgreSQL با کاربر Keycloak متفاوت است؛ هویت را در Keycloak هم بسازید |
| صفحه در Manifest هست ولی پنل نیست | grant `view` روی `application:aurevia/<panel>` وجود ندارد |
| پنل هست ولی Access denied دیده می‌شود | grant `view` روی Page وجود ندارد |
| صفحه باز می‌شود ولی API برابر 403 است | grant resource/action عملیاتی وجود ندارد |
| بعد از اعطا تغییری نیست | Outbox، OpenFGA، Redis graph epoch و refresh Manifest را بررسی کنید |
| پس از لغو هنوز دسترسی هست | grant مؤثر ROLE/GROUP یا session/Manifest قدیمی را بررسی کنید |

## فایل‌های پیاده‌سازی

| بخش | فایل |
|---|---|
| صفحه کارکنان و Route Guard | `apps/mfe-hr/src/bootstrap.tsx` |
| صفحه پرداخت‌ها و Route Guard | `apps/mfe-finance/src/bootstrap.tsx` |
| کاربران Login Demo | `infra/keycloak/realm-aurevia.json` |
| کاربران، grantها و Outbox | `V29__two_page_access_demo.sql` |
| مدیریت Grant/Revoke | `apps/mfe-admin/src/AccessStudio.tsx` |
| enforcement API | `OperationalProxyController.java` |
