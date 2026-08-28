# شروع، طراحی و معماری

## هدف پروژه

Aurevia یک Super App سازمانی فارسی‌محور است که چند Micro Frontend را در یک Shell نمایش می‌دهد. مرورگر فقط با origin عمومی `localhost:8443` صحبت می‌کند. Access Token هرگز به JavaScript داده نمی‌شود و در BFF/Redis نگهداری می‌شود.

## فناوری‌ها

| لایه | فناوری |
|---|---|
| Shell و MFE | React، TypeScript، Webpack Module Federation |
| BFF | Java 21، Spring Boot WebFlux، Spring Security OAuth2 |
| مجوزدهی | Authorization Service، PostgreSQL، OpenFGA |
| هویت | Keycloak با Authorization Code، PKCE، state و nonce |
| نشست | Spring Session و Redis؛ token vault رمز‌شده |
| گزارش | Superset عمومی برای asset و Superset عملیاتی برای اجرا |
| ورودی عمومی | Nginx روی پورت 8443 میزبان |

## ساختار مخزن

```text
apps/
  shell/          پوسته، منو و بارگذاری remoteها
  mfe-admin/      مدیریت پنل، منبع، action، کاربر، grant و Superset
  mfe-hr/         نمونه دامنه منابع انسانی
  mfe-finance/    نمونه دامنه مالی
  mfe-reports/    فهرست گزارش‌های مجاز
packages/
  contracts/      قرارداد TypeScript مشترک
  i18n/           ترجمه‌ها
  sh-core-ui/     کنترل‌های UI مبتنی بر permission
services/
  superapp-bff/   OAuth، session، CSRF و proxyهای امن
  authorization-service/ کنترل‌پلین مجوز و registry
infra/
  docker-compose/ محیط محلی
  nginx/          ورودی عمومی
  keycloak/       realm توسعه
  openfga/        مدل رابطه‌ای و تست‌ها
  superset-public/ asset host عمومی
  superset-operation/ موتور گزارش عملیاتی
docs/             معماری، امنیت، API و راهنماها
```

## نواحی اعتماد

```mermaid
flowchart LR
  U[مرورگر] --> N[Public Nginx]
  N --> S[Shell و MFEها]
  N --> B[Java BFF]
  B --> K[Keycloak]
  B --> R[(Redis Session/Vault)]
  B --> A[Authorization Service]
  A --> P[(PostgreSQL)]
  A --> F[OpenFGA]
  N --> PS[Public Superset Assets]
  B --> G[Operation Gateway :80]
  G --> OS[Operation Superset]
  OS --> D[(DWH)]
```

مرورگر هیچ route مستقیمی به Operation Superset یا DWH ندارد. Superset عمومی دیتابیس تحلیلی و dashboard runtime ندارد و فقط فایل‌های static را سرو می‌کند.

## جریان درخواست

### ورود

1. کاربر `/auth/login` را باز می‌کند.
2. BFF کاربر را با Authorization Code Flow به Keycloak هدایت می‌کند.
3. Keycloak پس از ورود، code را به BFF می‌دهد.
4. BFF در سمت سرور code را به token تبدیل می‌کند.
5. token رمز‌شده در Redis ذخیره می‌شود.
6. مرورگر فقط کوکی opaque با نام `AUREVIA_SESSION` می‌گیرد.
7. `GET /api/v1/me/manifest` پنل‌ها و permissionهای کاربر را دریافت می‌کند.

### APIهای معمولی

```text
Browser -> Nginx /api/* -> BFF -> Authorization check -> Gateway -> Service
```

BFF باید مسیر را از registry معتبر انتخاب، path را normalize و headerها را allowlist کند. نبود route، action، session یا تصمیم معتبر باید به DENY منجر شود.

### گزارش و Superset

```text
GET /static/*
Browser -> Public Nginx -> Public Superset asset image

/reports-runtime/* و endpointهای runtime
Browser -> Public Nginx -> OperationSupersetProxyController
        -> Operation Gateway:80 -> Operation Superset -> DWH
```

Superset 5 بعضی endpointها را root-relative تولید می‌کند. Nginx درخواست‌های `/superset/*` و درخواست‌های `/api/v1/*` متعلق به سند `/reports-runtime/*` را به tunnel جاوا هدایت می‌کند. کوکی مستقل `AUREVIA_OPERATION_SUPERSET` نشست داخلی Superset را نگه می‌دارد.

## راه‌اندازی از صفر

### پیش‌نیازها

- Docker Desktop و Docker Compose
- Node.js مطابق `.nvmrc`
- Java 21
- Maven Wrapper موجود در مخزن

### تنظیم محیط

```powershell
Copy-Item .env.example .env
npm ci
```

تمام مقدارهای `change-me` در `.env` باید برای محیط واقعی تغییر کنند. secret را commit نکنید.

### build و اجرا

```powershell
npm run build
docker compose -f infra/docker-compose/compose.yml up -d --build
```

برای تست Java در ویندوز:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd verify
```

### آدرس‌ها

```text
Super App: http://localhost:8443/
Keycloak:  http://localhost:8180/
Superset runtime: http://localhost:8443/reports-runtime/superset/welcome/
```

## داده و مالکیت

- PostgreSQL منبع حقیقت metadata، کاربر، گروه، نقش، grant، audit و outbox است.
- OpenFGA projection سریع روابط برای runtime است.
- Redis فقط داده transient نشست و token vault را نگه می‌دارد.
- جدول `panel` metadata استقرار MFE است و به‌تنهایی مجوز امنیتی محسوب نمی‌شود.
- `superset_asset` گزارش/داشبورد خارجی را به یک `resource` داخلی متصل می‌کند.

## اصول امنیتی

- token در localStorage، sessionStorage یا JavaScript قرار نمی‌گیرد.
- همه mutationهای BFF به‌جز tunnel Superset تحت CSRF خود Spring هستند.
- خود Superset CSRF درخواست‌های tunnel را کنترل می‌کند.
- Authorization Service فقط از طریق Basic Auth داخلی BFF قابل دسترسی است.
- حذف منطقی و version برای جلوگیری از overwrite هم‌زمان استفاده می‌شود.
- logها نباید token، cookie، password یا connection string داشته باشند.
