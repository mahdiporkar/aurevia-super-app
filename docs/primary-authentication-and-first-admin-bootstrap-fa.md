# احراز هویت اصلی، راه‌اندازی اولین مدیر و مدیریت کاربران

نسخهٔ کامل انگلیسی: [primary-authentication-and-first-admin-bootstrap.md](primary-authentication-and-first-admin-bootstrap.md)

## مرز مسئولیت‌ها

```text
Keycloak                                   Authorization Service + OpenFGA
========                                   ================================
احراز هویت (ورود، رمز عبور)                نقش‌ها، مجوزها، برنامه‌ها، ماژول‌ها، صفحه‌ها،
هویت کاربر (شناسهٔ پایدار sub)             کامپوننت‌ها، گزارش‌ها، داشبوردها، عملیات، سیاست‌ها،
ایجاد پایهٔ کاربر                          Grant منابع / کاربران / گروه‌ها
```

Aurevia هیچ رمز عبوری ذخیره نمی‌کند، مجوزها را به نقش‌های Keycloak منتقل نمی‌کند و OpenFGA جایگزین نمی‌شود.

## پیکربندی احراز هویت اصلی (فقط از محیط اجرا)

| متغیر | الزامی | توضیح |
|---|---|---|
| `OIDC_ISSUER_URI` | بله | issuer realm در Keycloak؛ endpointها از `/.well-known/openid-configuration` کشف می‌شوند (Spring Security). |
| `OIDC_CLIENT_ID` | بله | کلاینت محرمانهٔ ورود، مثلاً `aurevia-bff` |
| `OIDC_CLIENT_SECRET` | بله | secret کلاینت ورود؛ از Secret Kubernetes/Docker/Vault تزریق شود |
| `OIDC_SCOPES` | خیر | پیش‌فرض `openid,profile,email` |
| `OIDC_ENDPOINT_OVERRIDES_ENABLED` | خیر (پیش‌فرض `false`) | فقط Docker محلی که مرورگر و کانتینرها آدرس متفاوتی برای Keycloak دارند؛ در این حالت باید `OIDC_AUTHORIZATION_URI`، `OIDC_TOKEN_URI`، `OIDC_JWK_SET_URI` و `OIDC_USER_INFO_URI` کامل داده شوند. |

BFF در صورت نبودن هر یک از سه متغیر الزامی با پیام
`OIDC configuration is incomplete. Missing required configuration: …` بالا نمی‌آید. هیچ ردیف
`identity_provider`، هیچ SQL و هیچ اقدامی در پنل مدیریت پیش از اولین ورود لازم نیست. secret اصلی
هرگز در پایگاه‌دادهٔ برنامه ذخیره نمی‌شود.

## کلاینت‌های Keycloak

1. **کلاینت ورود** (`aurevia-bff`): OpenID Connect، محرمانه، فقط Standard Flow، Redirect URI برابر
   `https://<host>/login/oauth2/code/public-iam`.
2. **Service Account مدیریتی** (`aurevia-identity-admin`): محرمانه، Standard Flow و Direct Access
   خاموش، Service Accounts روشن، و فقط نقش `realm-management/manage-users`. مقادیر آن با
   `KEYCLOAK_ADMIN_CLIENT_ID` و `KEYCLOAK_ADMIN_CLIENT_SECRET` (و در صورت نیاز
   `KEYCLOAK_ADMIN_BASE_URL`) به BFF داده می‌شود و هرگز به مرورگر نمی‌رسد.

## راه‌اندازی اولین مدیر (First Administrator Bootstrap)

1. در Keycloak کاربر انسانی مدیر را بسازید و رمز آن را همان‌جا تعیین کنید؛ Aurevia این رمز را نمی‌داند.
2. **ID** کاربر را از کنسول Keycloak بردارید (مثلاً `8c604f37-33d2-42e4-a982-35bd5613e974`).
3. `AUREVIA_BOOTSTRAP_ADMIN_SUB=<ID>` را در محیط Authorization Service و BFF قرار دهید.
4. Aurevia را اجرا کنید. Authorization Service در اولین اجرا و **فقط یک بار**، در یک تراکنش:
   subject canonical را می‌سازد/پیدا می‌کند، مجوز موجود `admin` روی `application:aurevia` را از
   مسیر عادی Grant/Outbox/OpenFGA اعطا می‌کند و نشانگر `schema_version(component='first-administrator')`
   را می‌نویسد.
5. BFF (اگر Service Account تنظیم باشد) همین ID را از Keycloak می‌خواند و در لاگ تأیید یا خطای واضح می‌دهد.
6. مدیر با Keycloak وارد می‌شود؛ `/api/me/context` مجوز `admin` روی `application:aurevia` و پنل مدیریت
   (شامل صفحهٔ «مدیریت کاربران») را نشان می‌دهد.

تضمین‌ها: راه‌اندازی مجدد، ارتقا یا Redeploy فقط پیام `already completed` می‌دهد؛ اگر مدیر بعداً این
Grant را از استودیوی مجوزها حذف کند، حتی با وجود متغیر محیطی، دوباره اعطا نمی‌شود. اجرای ناقص
هیچ چیزی commit نمی‌کند و تکرار آن به همان یک subject/identity/grant/marker همگرا می‌شود.
مقدار نادرست (فاصله، کاراکتر کنترلی، کاربر غیرفعال) با پیام واضح مانع بالا آمدن سرویس می‌شود.

## مدیریت کاربران در پنل مدیریت

صفحهٔ «مدیریت کاربران» فرم ایجاد کاربر (نام کاربری، نام، نام خانوادگی، ایمیل، فعال، رمز اولیه) را
به `POST /api/v1/admin/keycloak-users` می‌فرستد. BFF ابتدا مجوز `admin` روی `application:aurevia`
را از OpenFGA می‌گیرد، سپس با توکن Service Account کاربر را در Keycloak Admin REST API می‌سازد و
فقط `id` پایدار و فیلدهای امن را برمی‌گرداند. رمز اولیه ذخیره، لاگ یا بازگردانده نمی‌شود. خطاها:
`409` تکراری، `400` نامعتبر/سیاست رمز، `403` بدون مجوز، `502/503/504` خطاهای Keycloak.
پس از اولین ورود کاربر، مجوزها مثل هر کاربر دیگری از استودیوی مجوزها/نقش‌ها/گروه‌ها اعطا می‌شود.

خارج از دامنهٔ این فاز: گروه‌ها و نقش‌های Keycloak، بازنشانی رمز، MFA، LDAP، federation، مدیریت realm/client، چند IdP.

## ارائه‌دهندگان هویت اضافی

رجیستری `identity_provider` و کارت «ارائه‌دهندگان هویت اضافی» فقط برای IdPهای ثانویه باقی مانده‌اند و
نمی‌توانند ورود اصلی (`public-iam` / issuer اصلی) را تعریف یا تغییر دهند (پاسخ `409`).

## دمو محلی در برابر Production

- دمو محلی: کاربران fixture با رمز `local-change-me`، شناسهٔ ثابت `administrator`، secretهای fixture، و
  `OIDC_ENDPOINT_OVERRIDES_ENABLED=true`؛ `demo-fixture-init` هیچ IdP و هیچ Grant مدیر را seed نمی‌کند.
- Production: کاربر مدیر و رمز فقط در Keycloak، secretها تزریق‌شده، issuer واحد HTTPS، override خاموش.
- Migration `V78` امتیازهای ضمنی هویت دموی `administrator` را بازنشسته می‌کند؛ `V79` مانیفست 0.6.0 پنل مدیریت را با صفحهٔ کاربران فعال می‌کند.

## اعتبارسنجی

`node tools/primary-auth-bootstrap-e2e.mjs` (نصب تمیز → Bootstrap → ورود → ایجاد کاربر → Grant →
راه‌اندازی مجدد → ابطال → راه‌اندازی مجدد) و `npm run infra:verify`.
