# ممیزی جامع کیفیت، امنیت و تجربه کاربری — ۲۰۲۶-۰۸-۳۱

## دامنه و نتیجه

این ممیزی Shell، چهار Micro Frontend، BFF، Authorization Service، مدل و migrationهای
OpenFGA، Compose و مسیر انتشار GitHub Pages را پوشش می‌دهد. نتیجه قابل تکرار است:
`npm test` اکنون قرارداد صفحه/دکمه/Resource را نیز اجرا می‌کند و CI علاوه بر آن
typecheck، build تولید، Maven verify، npm audit و Compose validation را کنترل می‌کند.

## ماتریس صفحه و Resource

| دامنه | صفحه | Resource | کنترل UI | کنترل نهایی |
|---|---|---|---|---|
| HR | کارکنان | `page:hr.employee.list` | `SHRouteGuard:view` | Route Operation + OpenFGA |
| HR | واحدها | `page:hr.departments` | `SHRouteGuard:view` | Route Operation + OpenFGA |
| HR | سمت‌ها | `page:hr.positions` | `SHRouteGuard:view` | Route Operation + OpenFGA |
| Finance | پرداخت‌ها | `page:finance.payments` | `SHRouteGuard:view` | Route Operation + OpenFGA |
| Finance | صورتحساب‌ها | `page:finance.invoices` | `SHRouteGuard:view` | Route Operation + OpenFGA |
| Finance | بودجه‌ها | `page:finance.budgets` | `SHRouteGuard:view` | Route Operation + OpenFGA |
| Reports | کاتالوگ گزارش | External Resource هر asset | فهرست server-filtered | BFF + OpenFGA asset check |
| Admin | همه tabهای مدیریتی | `application:aurevia` یا Resource تخصصی | پنل فقط در Manifest مجاز | `AdminAuthorizationInterceptor` |

## ماتریس عملیات حساس UI

| دکمه | Resource | Action | رفتار بدون مجوز |
|---|---|---|---|
| ایجاد کارمند | `business:hr.employee` | `create` | مخفی |
| ویرایش کارمند | `business:hr.employee` | `update` | مخفی |
| ایجاد پرداخت | `finance.payment` | `create` | غیرفعال |
| تأیید پرداخت | `finance.payment` | `approve` | مخفی |
| رد پرداخت | `finance.payment` | `reject` | مخفی |

کنترل UI صرفاً برای UX است. تمام mutationهای عملیاتی در BFF دوباره به Route Operation
و Authorization Service نگاشت می‌شوند. عملیات Admin نیز بر اساس method به
`can_create`، `can_edit`، `can_delete` یا `can_manage` نگاشت و سمت سرور enforce می‌شود.

## بهبودهای اعمال‌شده

- تست contract جدید، وجود guard هر شش صفحه، policy هر پنج mutation و حضور Resource/Action
  در migrationها را کنترل می‌کند.
- لینک گزارش فقط مسیر relative و same-origin می‌پذیرد؛ scheme خارجی، protocol-relative و
  ورودی نامعتبر غیرفعال می‌شود و tab جدید `noopener noreferrer` دارد.
- JSON خراب یا خارج از قرارداد در tags دیگر Micro Frontend گزارش را crash نمی‌کند.
- fetchهای Shell و Reports هنگام unmount لغو می‌شوند و Promiseهای Remote پس از teardown
  state را تغییر نمی‌دهند.
- Ant Design از share scope هر Remote خارج شد؛ در نمونه Reports، بزرگ‌ترین payload تولید
  از ۱٫۴۴ MiB به ۵۹۸ KiB کاهش یافت و Remote فقط componentهای مصرف‌شده را bundle می‌کند.
- Showcase مستقل، responsive و دارای CSP سخت‌گیرانه است و هیچ token، API یا داده واقعی
  را در GitHub Pages استفاده نمی‌کند.

## فرمان‌های پذیرش

```bash
npm ci
npm run typecheck
npm test
npm run build
npm audit
./mvnw -B clean verify
docker compose --env-file .env -f infra/docker-compose/compose.yml config --quiet
```

GitHub Pages از پوشه `showcase/` و workflow اختصاصی `.github/workflows/pages.yml`
منتشر می‌شود. برای فعال‌شدن URL عمومی، Source مخزن در Settings → Pages باید روی
GitHub Actions باشد.
