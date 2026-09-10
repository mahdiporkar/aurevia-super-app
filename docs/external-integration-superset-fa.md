# Superset به‌عنوان External Integration

## نتیجه معماری

Superset دیگر عضو lifecycle هسته Aurevia نیست. هسته فقط Shell/Nginx، BFF، Authorization
Service، OpenFGA، Keycloak، دیتابیس‌ها و زیرساخت عمومی را اجرا می‌کند. هر Superset با URL
خود در `superset_instance` ثبت می‌شود و خاموش بودن آن readiness هسته را تغییر نمی‌دهد.

پیش از این، `infra:up` پروفایل `superset` را فعال می‌کرد، Nginx به
`public-superset:8088` و Operation Gateway به `operation-superset:8088` وابسته بود. اکنون
هیچ نام کانتینر Superset در Core Compose، Nginx یا Gateway وجود ندارد و data plane چنین است:

```text
Browser -> Aurevia Shell (same origin)
        -> BFF /api/integrations/superset/{code}/...
        -> Authorization check (OpenFGA)
        -> SSRF/network policy + timeout + no redirect following
        -> base_url ثبت‌شده در Registry
```

## اجرای Core و Demo

فقط هسته:

```bash
npm run infra:up
```

Superset آزمایشی lifecycle مستقل دارد:

```bash
npm run superset:up
npm run superset:down
```

فایل آن `infra/docker-compose/compose.superset-demo.yml` است و پروژه Compose مستقل
`aurevia-superset-demo` می‌سازد. Operation روی `http://localhost:8088` و سرویس static
آزمایشی روی `http://localhost:8089` منتشر می‌شوند. این binding فقط loopback و برای توسعه
است. migration نسخه 58 فقط fixtureهای دقیق قدیمی را از نام شبکه Docker به این URLها تبدیل
می‌کند و رکوردهای سفارشی/Production را تغییر نمی‌دهد.

نام volume دیتابیس demo عمداً `aurevia_operation-superset-db` باقی مانده تا نصب‌های قبلی
dashboardهای خود را بدون کپی داده ببینند. `infra:up` کانتینرهای orphan پروفایل قدیمی را حذف
می‌کند، اما volume را پاک نمی‌کند؛ `superset:up` همان volume را در پروژه مستقل mount می‌کند.

تست جداسازی lifecycle:

```bash
npm run superset:config:test
```

## ثبت Superset خارجی بدون restart

از صفحه «محیط‌های Superset» در MFE Admin یا API زیر استفاده کنید:

```http
POST /api/v1/admin/superset-instances
Content-Type: application/json
X-CSRF-TOKEN: <session-csrf-token>

{
  "code": "superset-public",
  "name": "Public BI Dashboard",
  "type": "PUBLIC",
  "baseUrl": "https://bi.company.com/superset",
  "authMode": "OIDC",
  "tlsRequired": true,
  "proxyMode": true,
  "enabled": true,
  "metadata": {"owner": "BI", "environment": "production"},
  "version": 0
}
```

`zone` نام سازگار قبلی برای `type` و `active` نام سازگار قبلی برای `enabled` است.
`connectionRef` اختیاری است و در صورت حذف به‌صورت
`connection://superset/{code}` ساخته می‌شود. URL، auth mode، proxy mode و metadata در
دیتابیس ذخیره می‌شوند؛ افزودن یا ویرایش instance به تغییر environment، Compose، build یا
restart هسته نیاز ندارد.

در زمان ایجاد instance، منبع `application:{code}` و actionهای آن نیز ساخته و parent آن در
OpenFGA به `application:aurevia` همگام می‌شود. مجوز `view` روی این Application را از Access
Studio به user/group/role بدهید. مجوزهای دقیق Dashboard/Chart موجود همچنان لایه دوم کنترل
هستند و داشتن grant یک asset نیز دسترسی لازم به integration را نتیجه می‌دهد.

## Context و مسیر مصرف

`GET /api/me/context` آرایه `applications` را فقط از integrationهایی می‌سازد که کاربر حق
دیدن آن‌ها را دارد:

```json
{
  "applications": [
    {
      "key": "superset-public",
      "name": "Public BI Dashboard",
      "type": "PUBLIC",
      "url": "/api/integrations/superset/superset-public/",
      "auth_mode": "OIDC",
      "proxy_mode": true,
      "health_status": "ACTIVE"
    }
  ]
}
```

مخفی‌سازی UI کنترل امنیتی نیست. BFF در **هر درخواست** به مسیر integration، subject جاری را
به Authorization Service می‌فرستد؛ نداشتن permission با `403` متوقف می‌شود. مقصد ابتدا از
Registry resolve می‌شود و Browser هیچ‌گاه `base_url`، topology خصوصی یا credential را برای
ساخت درخواست دریافت نمی‌کند. endpointهای قدیمی `/api/v1/superset/**` و
`/api/v1/superset-instances/**` برای migration سازگار مانده‌اند، ولی توسعه جدید باید مسیر
`/api/integrations/superset/{code}/...` را مصرف کند.

## Login، Permission و SSO

Flow کامل:

1. Browser با Authorization Code Flow وارد Keycloak می‌شود؛ BFF token را در vault سمت سرور
   نگه می‌دارد و فقط cookie نشست HttpOnly را به Browser می‌دهد.
2. Shell از `/api/me/context` integrationهای مجاز را می‌گیرد.
3. Browser URL same-origin رجیستری را باز می‌کند.
4. BFF منبع `application:{code}` و در صورت مشخص بودن Dashboard/Chart، asset متناظر را با
   OpenFGA بررسی می‌کند.
5. BFF URL را دوباره در لحظه fetch با network policy کنترل و سپس درخواست را forward می‌کند.
6. در `REMOTE_USER`، BFF هدرهای `X-Aurevia-Subject` و `X-Aurevia-Issuer` را از Principal
   معتبر خودش می‌سازد؛ header ورودی Browser هیچ‌گاه forward نمی‌شود. Superset فعلی با
   `AUTH_REMOTE_USER` نشست داخلی خود را بدون فرم login دوم ایجاد می‌کند.
7. در `OIDC`، Superset خارجی باید client مستقل همان IdP را داشته باشد. redirectهای IdP دنبال
   نمی‌شوند و به Browser بازمی‌گردند؛ نشست موجود Keycloak معمولاً ورود دوم را بدون prompt
   تکمیل می‌کند. Aurevia token exchange یا guest token انجام نمی‌دهد.
8. cookieهای Superset با prefix مخصوص instance بازنویسی می‌شوند تا نشست چند instance با هم
   تداخل نکند.

برای Production در حالت `REMOTE_USER`، ingress جلوی Superset باید هدرهای هویتی اینترنتی را
حذف کند و فقط اتصال احرازشده BFF (ترجیحاً mTLS) را مجاز بداند. در غیر این صورت `OIDC` را
انتخاب کنید.

## Policy امنیت URL

ثبت و fetch از policy مشترک استفاده می‌کنند:

- فقط HTTP(S)، بدون user-info/credential، query یا fragment در `base_url`؛
- HTTPS اجباری در profile Production؛
- جلوگیری از loopback، any-local، link-local، multicast، cloud metadata و شبکه‌های رزروشده؛
- DNS resolution دوباره پیش از fetch برای کاهش SSRF و DNS rebinding؛
- `PRODUCTION_INTERNET` شبکه خصوصی را رد می‌کند؛
- `INTERNAL_ENTERPRISE` فقط CIDRهای `SUPERSET_ALLOWED_PRIVATE_CIDRS` را می‌پذیرد؛
- `DEVELOPMENT` می‌تواند `localhost` را با `SUPERSET_DEVELOPMENT_HOST` به bridge میزبان تبدیل کند؛
- connect timeout پیش‌فرض 3 ثانیه و response timeout پیش‌فرض 10 ثانیه است؛
- redirect خودکار غیرفعال است و Location هم-origin به tunnel Aurevia بازنویسی می‌شود.

Policy شبکه یک boundary سراسری است، نه allowlist یک instance؛ بنابراین افزودن host عمومی HTTPS
جدید environment تازه نمی‌خواهد. فعال‌کردن شبکه خصوصی تصمیم deployment است و فقط یک‌بار برای
CIDR سازمان تنظیم می‌شود.

## Health و جداسازی خطا

```http
GET /api/integrations/superset/{code}/health
```

نتیجه مستقل `ACTIVE` یا `UNREACHABLE` را برمی‌گرداند و در `superset_instance.health_status`
به‌همراه زمان بررسی ذخیره می‌کند. instance غیرفعال `DISABLED` است. این check عضو Actuator
health هسته، `depends_on` Compose یا startup هیچ سرویس Core نیست؛ بنابراین خرابی Superset
روی Authorization، سایر integrationها و readiness هسته اثر نمی‌گذارد.

## محدودیت‌های عملیاتی

- health check فعلی on-demand است؛ برای پایش دوره‌ای باید سامانه مانیتورینگ سازمان endpoint
  health را صدا بزند.
- اتصال `REMOTE_USER` در Production به trusted ingress/mTLS خارج از این مخزن نیاز دارد.
- `connection_ref` برای توسعه adapterهای client certificate یا secret اختصاصی حفظ شده، اما
  BFF فعلی credentialی از آن resolve یا به Superset ارسال نمی‌کند.
- Compose demo برای workstation است و HA، worker async، cache تولیدی و TLS termination
  Superset را فراهم نمی‌کند.
