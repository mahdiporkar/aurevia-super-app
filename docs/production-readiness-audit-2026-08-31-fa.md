# گزارش ممیزی Production — ۲۰۲۶-۰۸-۳۱

## نتیجهٔ اجرایی

کد مخزن از نظر build، type safety، تست‌های خودکار موجود، آسیب‌پذیری dependencyهای npm و سناریوی runtime دسترسی HR/Finance بررسی و سخت‌سازی شد. با این حال عبارت «بدون باگ» یا «Production-Ready قطعی» فقط با مشاهدهٔ محیط مقصد، TLS و secret manager واقعی، تست بار، آزمون بازیابی و تأیید عملیاتی ممکن است. Compose این مخزن همچنان محیط demo است و سند انتشار Production مرجع الزامات بیرونی است.

## اصلاحات این ممیزی

- منوی HR و Finance اکنون policy امضاشده/زمان‌دار manifest را با `evaluateSHPolicy` ارزیابی می‌کند و manifest منقضی یا نامعتبر fail-closed است.
- Shell فقط URLهای HTTP(S) موجود در allowlist دقیق را می‌پذیرد، scope را اعتبارسنجی می‌کند، SRI را اعمال می‌کند و rebinding یک scope به remote دیگر را رد می‌کند.
- OpenFGA Check با `HIGHER_CONSISTENCY` اجرا می‌شود تا بلافاصله پس از grant/revoke تصمیم کهنه بازگردانده نشود؛ Redis فقط شتاب‌دهنده است و graph epoch mutationها را invalidate می‌کند.
- migration نسخهٔ ۳۱ tupleهای چهار صفحهٔ demo را idempotent دوباره project می‌کند تا نصب‌های قبلی نیز اصلاح شوند.
- profile تولید Authorization Service با Store/Model placeholder یا URL غیر HTTPS شروع نمی‌شود.
- خطای projection در outbox علت sanitizeشدهٔ OpenFGA را نگه می‌دارد و policy failure لاگ قابل پیگیری دارد.
- imageهای Java با کاربر غیر root اجرا می‌شوند.
- CI روی Node 22.15 اجرا و audit همه dependencyها را enforce می‌کند.

## شواهد تست

| کنترل | نتیجه |
|---|---|
| Maven verify کل reactor | موفق؛ تست‌های BFF و Authorization Service |
| تست Authorization Service پس از تغییر consistency | موفق؛ ۴۷ تست |
| TypeScript typecheck تمام workspaceها | موفق |
| تست frontend | موفق؛ Shell و `sh-core-ui` |
| production webpack build تمام MFEها و Shell | موفق؛ هشدار اندازه bundle ثبت شد |
| `npm audit` و `npm audit --omit=dev` | صفر vulnerability گزارش‌شده |
| Flyway V31 | روی PostgreSQL محلی موفق و outboxها processed |
| runtime OpenFGA | APIهای اصلی allow/deny پایدار بودند؛ در volume قدیمی Compose برای دو page تازه، ناسازگاری بین Write/Read/Check مشاهده شد و release blocker ثبت شد |
| اجرای container Java با non-root | Authorization Service و BFF باید در smoke نهایی با `Config.User=aurevia` تأیید شوند |

## ریسک‌ها و شروطی که خارج از کد مخزن‌اند

موارد زیر release blocker محیط Production هستند و نباید با موفقیت Compose محلی اشتباه گرفته شوند:

1. DNS، گواهی TLS، trusted proxy و NetworkPolicy واقعی.
2. secret manager، rotation و صدور certificateهای mTLS.
3. PostgreSQL و Redis با HA، backup رمزنگاری‌شده و restore drill دارای RTO/RPO تأییدشده.
4. deployment چند replica، autoscaling، PodDisruptionBudget و rollout/rollback آزمایش‌شده.
5. Superset با WSGI production، metadata DB و rate-limit store عملیاتی.
6. تست بار و soak با SLO مصوب، تست نفوذ و SAST/container scan سازمانی.
7. alert، dashboard، retention، SIEM و on-call عملیاتی.
8. آزمون مرورگری واقعی روی مرورگرهای هدف؛ در این ممیزی ابزار browser automation محیط Codex به‌علت خطای metadata sandbox قابل اجرا نبود و با تست service/runtime جایگزین شد.

## Release blocker مشاهده‌شده در محیط محلی موجود

در volume قدیمی OpenFGA محیط demo، برای بعضی tupleهای صفحهٔ تازه، Write گاهی موفق یا duplicate گزارش شد اما Read/Check بلافاصله همان tuple را مشاهده نکرد. کد با consistency بالاتر، graph epoch، replay نسخهٔ ۳۱ و بازنویسی duplicate نامرئی سخت‌سازی شد، ولی تکرار ناپایدار در همین دیتای محلی مشاهده شد. تا اجرای integration test روی Store پاک و اختصاصی و اثبات پایداری grant/revoke/check، این مورد **P0 و مانع برچسب Production-Ready** است؛ پاک‌کردن volume موجود به‌صورت خودکار انجام نشد چون عملیاتی مخرب و خارج از مجوز ضمنی ممیزی است.

## بدهی فنی غیرمسدودکنندهٔ demo

bundleهای Ant Design در build از حد پیشنهادی webpack بزرگ‌ترند. برای ظرفیت Production باید importهای granular، code splitting و performance budget در CI افزوده شود. این هشدار correctness را نمی‌شکند، ولی روی زمان بارگذاری اولیه اثر دارد.

## معیار تصمیم انتشار

تنها پس از تکمیل تمام checklist سند [انتشار Production](deployment-production-linux-fa.md)، ثبت evidence در change ticket و تأیید مشترک Platform، Security، DBA و مالک محصول می‌توان نسخه را Production-Ready نامید.
