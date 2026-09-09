# راهنمای اتصال یک فرانت‌اند مستقل به Backend آرویا

## هدف و تصمیم معماری

این سند برای زمانی است که یک SPA، وب‌اپ یا Micro Frontend جدید می‌خواهد از Backend موجود استفاده کند. مسیر پیشنهادی و پشتیبانی‌شده چنین است:

```text
Browser -> same-origin reverse proxy -> Superapp BFF -> Authorization Service / Operation Gateway
```

مرورگر نباید مستقیماً به Authorization Service، OpenFGA، سرویس‌های عملیاتی یا Redis وصل شود و نباید access token یا refresh token را نگه دارد. BFF ورود OIDC، session، CSRF، نگهداری رمز‌شده token و کنترل مجوز backend را انجام می‌دهد.

دو روش اتصال وجود دارد:

1. **فرانت‌اند مستقل روی همان origin**: مناسب برنامه‌ای با deployment و repository جدا. reverse proxy مسیرهای UI و BFF را زیر یک scheme/host/port منتشر می‌کند.
2. **Micro Frontend داخل Shell**: مناسب قابلیتی که باید در navigation و runtime فعلی بارگذاری شود. علاوه بر مراحل API، registration پنل، MF Manifest و در صورت نیاز Resource Manifest جداگانه همگام می‌شوند.

اتصال cross-origin مستقیم توصیه نمی‌شود. cookie نشست `HttpOnly`، `Secure` و `SameSite=Lax` است و BFF عمداً قرارداد عمومی CORS برای SPAهای پراکنده ندارد. اگر دامنه جدا الزامی است، همان دامنه باید یک reverse proxy/BFF هم‌مبدأ داشته باشد؛ فعال‌کردن عمومی CORS یا قراردادن bearer token در browser راه‌حل قابل قبول نیست.

## پیش‌نیازها

- آدرس عمومی BFF، مثلاً `https://portal.example.com`؛
- client و redirect URI معتبر در Keycloak/Public IAM؛
- resource/actionهای canonical برای صفحه و عملیات کسب‌وکار؛
- route عملیاتی ثبت‌شده در رجیستری BFF؛
- grant آزمایشی برای حداقل دو کاربر مجاز و غیرمجاز؛
- HTTPS در محیط اشتراکی و Production.

Swagger تجمیعی BFF در محیطی که مستندات فعال است از `/swagger-ui.html` در دسترس است. قراردادهای frontend باید فقط از API عمومی BFF استفاده کنند؛ مسیرهای `/internal/v1/**` داخلی‌اند.

## روش اول: فرانت‌اند مستقل روی همان origin

### ۱. مسیریابی reverse proxy

خروجی build فرانت‌اند را زیر مسیری مانند `/customer-app/` ارائه کنید و مسیرهای زیر را بدون تغییر به BFF بفرستید:

```text
/oauth2/**
/login/**
/auth/**
/api/**
/<panel-slug>/**       # پراکسی عملیات ثبت‌شده، در صورت نیاز
```

نمونه Nginx مفهومی:

```nginx
location /customer-app/ {
  try_files $uri /customer-app/index.html;
}

location ~ ^/(api|oauth2|login|auth)/ {
  proxy_pass http://superapp-bff:8081;
  proxy_set_header Host $host;
  proxy_set_header X-Forwarded-Proto $scheme;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

در Production فهرست proxy headerهای مورد اعتماد را محدود کنید و TLS را در ingress یا BFF مطابق runbook استقرار پایان دهید.

### ۲. ورود و تشخیص session

در شروع برنامه `GET /api/v1/me` یا `GET /api/v1/me/manifest` را با `credentials: 'same-origin'` بزنید. در پاسخ `401` یا redirect، مرورگر را به مسیر زیر هدایت کنید:

```ts
window.location.assign('/oauth2/authorization/public-iam');
```

پس از callback، BFF یک cookie نشست opaque ایجاد می‌کند. فرانت‌اند نباید cookie را بخواند و نباید token را در `localStorage`، `sessionStorage`، IndexedDB یا state قابل مشاهده نگه دارد.

### ۳. استفاده از HTTP client مشترک

اگر فرانت داخل همین monorepo است، از `@aurevia/http-client` استفاده کنید:

```ts
import { createJsonHttpClient, ApiError } from '@aurevia/http-client';

export const api = createJsonHttpClient({ basePath: '' });

export async function currentManifest() {
  try {
    return await api.get('/api/v1/me/manifest', {
      headers: { 'Cache-Control': 'no-cache' },
    });
  } catch (error) {
    if (error instanceof ApiError && [0, 302, 401].includes(error.status)) {
      window.location.assign('/oauth2/authorization/public-iam');
    }
    throw error;
  }
}
```

این client برای درخواست‌های `POST`، `PUT`، `PATCH` و `DELETE` ابتدا `/api/v1/csrf` را می‌خواند، نام header را از پاسخ می‌گیرد و `X-Correlation-ID` می‌فرستد. در repository جدا می‌توانید همین رفتار را پیاده کنید؛ نام header را hard-code نکنید:

```ts
const csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(r => r.json());
await fetch('/api/v1/admin/example', {
  method: 'POST',
  credentials: 'same-origin',
  headers: {
    'Content-Type': 'application/json',
    'X-Correlation-ID': crypto.randomUUID(),
    [csrf.headerName]: csrf.token,
  },
  body: JSON.stringify({ example: true }),
});
```

### ۴. دریافت و مصرف Manifest دسترسی

`GET /api/v1/me/manifest` شامل permissionها و resource tree مؤثر کاربر است. نمایش route، دکمه یا فیلد را با کلید canonical و action کنترل کنید:

```ts
const canUpdate = manifest.permissions['business:hr.employee']?.includes('update') ?? false;
```

Manifest فقط برای UX است و credential مجوزدهی نیست. مخفی‌بودن دکمه جای کنترل backend را نمی‌گیرد؛ BFF و Authorization Service باید هر عملیات محافظت‌شده را دوباره بررسی کنند. Manifest را در `expiresAt`، پس از login و پس از تغییر grant بازخوانی کنید و در حالت منقضی یا ناموفق fail closed باشید.

### ۵. فراخوانی API کسب‌وکار

Frontend باید URL عمومی ثبت‌شده در BFF را با مسیر نسبی صدا بزند. کنترلر `OperationalProxyController` درخواست `/<panelSlug>/**` را با route registry تطبیق می‌دهد، مجوز resource/action را بررسی می‌کند و credential لازم را server-side به مقصد می‌فرستد.

برای endpoint جدید:

1. Service Target و Proxy Route را در پنل راهبری ثبت کنید.
2. operation شامل HTTP method، الگوی مسیر، resource و action را تعریف کنید.
3. resource/action را در OpenFGA به subject آزمایشی grant کنید.
4. از frontend فقط مسیر نسبی ثبت‌شده را فراخوانی کنید.
5. پاسخ‌های `401`، `403`، `404`، `502` و `504` را صریح مدیریت کنید.

فرانت‌اند نباید URL داخلی service، header داخلی `X-Actor` یا bearer عملیاتی بسازد.

## روش دوم: ثبت به‌عنوان Micro Frontend

برای ادغام داخل Shell علاوه بر مراحل بالا:

1. قراردادهای `@aurevia/contracts` و runtime موجود را مصرف کنید.
2. entry دارای `contractVersion: '1.0'` و export قابل بارگذاری بسازید.
3. دو فایل مستقل منتشر کنید: `resource-manifest.json` فقط برای Resourceها و
   `mf-manifest.json` برای runtime، routeهای محلی، navigation پیش‌فرض و reference مجوز.
4. Remote Entry را روی HTTPS و origin مجاز منتشر کنید.
5. در بخش Micro Frontend پنل، `remoteEntryUrl`، `remoteName`، `exposedModule`، `routePrefix`، نسخه قرارداد و integrity را ثبت کنید.
6. MF Manifest را sync کنید و Resource Manifest را جداگانه stage، preview و publish کنید.
7. به user/group/role مجوز application/page/business resource بدهید.

کلیدهای resource باید پایدار و canonical باشند؛ تغییر label ترجمه‌شده نباید باعث تغییر کلید مجوز شود.

## قرارداد خطا و observability

- برای هر درخواست `X-Correlation-ID` یکتا بفرستید و آن را کنار خطای UI ثبت کنید.
- body خطا را به‌عنوان HTML در UI render نکنید.
- `401`: session نامعتبر؛ شروع login.
- `403`: CSRF یا مجوز ناکافی؛ یک بار CSRF را refresh کنید، سپس پیام دسترسی نشان دهید.
- `409`: تعارض نسخه optimistic locking؛ داده را دوباره بخوانید.
- `502/504`: اختلال dependency؛ retry محدود و قابل لغو.
- هیچ token، cookie، secret یا پاسخ حساس را در console و telemetry ثبت نکنید.

## چک‌لیست آزمون پذیرش

- ورود، callback، refresh صفحه و logout بدون قرارگرفتن token در browser storage؛
- `GET /api/v1/me` و Manifest برای کاربر معتبر؛
- mutation با CSRF معتبر موفق و بدون CSRF با `403` ناموفق؛
- کاربر مجاز موفق و کاربر غیرمجاز روی همان API با `403`؛
- bypass کردن guard فرانت‌اند همچنان در backend رد شود؛
- grant مستقیم، گروهی، نقشی، inherited، revoked و expired؛
- Manifest منقضی fail closed شود؛
- route ناشناخته و method ثبت‌نشده fail closed شوند؛
- correlation ID در BFF، Authorization Service و مقصد قابل ردیابی باشد؛
- build production با base path نهایی و refresh روی deep link درست کار کند.

## منابع مرتبط

- [راهنمای Shell و بارگذاری Micro Frontend](shell-runtime-and-mfe-loading-fa.md)
- [معماری Resource Catalog و Manifest](resource-catalog-manifest-architecture-fa.md)
- [راهنمای Dynamic Proxy Routing](dynamic-proxy-routing-fa.md)
- [مدل دسترسی](access-control-fa.md)
- [آمادگی Production](enterprise-production-readiness-fa.md)
