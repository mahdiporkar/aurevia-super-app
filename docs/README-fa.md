# راهنمای جامع Aurevia Super App

[English](README-en.md) | **فارسی**

این پوشه مرجع فارسی طراحی، پیاده‌سازی، اجرا و امنیت پروژه است. مستندات بر اساس کد موجود در همین مخزن نوشته شده‌اند؛ هرجا طراحی هدف با پیاده‌سازی فعلی تفاوت دارد، با عنوان «شکاف فعلی» مشخص شده است.

## مسیر پیشنهادی مطالعه

1. [شروع، معماری و اجرای پروژه](guide-fa.md)
2. [راهنمای آموزشی صفر تا تسلط تیم فنی](technical-team-zero-to-production-fa.md)
3. [سطوح دسترسی و مدل مجوزدهی](access-control-fa.md)
4. [راهنمای کامل فیلدها و فرم‌های میکرو راهبری](operator-admin-form-field-guide-fa.md)
5. [مرجع کد، فایل‌به‌فایل](code-reference-fa.md)
6. [مرجع فیچرها، جریان اجرا و مالکیت کد در ۲۰۲۶-۰۹-۰۴](codebase-feature-reference-2026-09-04-fa.md)
7. [ممیزی معماری، Clean Code و SOLID در ۲۰۲۶-۰۹-۰۴](codebase-architecture-clean-code-solid-audit-2026-09-04-fa.md)
8. [راهنمای خواندن کد به ترتیب اجرا](code-walkthrough-fa.md)
9. [معماری Authorization Engine](authorization-engine-fa.md)
10. [مرجع جامع معماری و OpenFGA](architecture-openfga-complete-fa.md)
11. [پیکربندی و انتشار OpenFGA](openfga-deployment-configuration-fa.md)
12. [عملیات، تست و عیب‌یابی](operations-fa.md)
13. [انتشار Demo روی سرور Linux](deployment-demo-linux-fa.md)
14. [انتشار Production روی Linux](deployment-production-linux-fa.md)
15. [Superset عمومی، عملیاتی، Route و نمایش داخل MFE](superset-routing-and-embedding-fa.md)
16. [ارسال امن توکن به Gateway و سرویس‌های عملیاتی](operational-token-forwarding-fa.md)
17. [تست سرتاسری Legacy و OAuth2](legacy-oauth2-end-to-end-demo-fa.md)
18. [معماری Resource Catalog و دو Manifest](resource-catalog-manifest-architecture-fa.md)
19. [Shell و بارگذاری Micro Frontendها](shell-runtime-and-mfe-loading-fa.md)
20. [دموی دسترسی چندصفحه‌ای HR و Finance](two-page-openfga-demo-fa.md)
21. [معماری فنی](architecture.md)
22. [حاکمیت Git و دسترسی](git-governance-fa.md)
23. [مدل تهدید](threat-model.md)
24. [نمودار پایگاه داده](er-diagram.md)
25. [تصمیم‌های معماری](adr/)
26. [قراردادهای OpenAPI](openapi/)

## نقشه مستندات

| پرسش | سند |
|---|---|
| پروژه از چه اجزایی ساخته شده است؟ | [guide-fa.md](guide-fa.md) |
| یک عضو جدید تیم چگونه پروژه را از صفر و آموزشی یاد بگیرد؟ | [technical-team-zero-to-production-fa.md](technical-team-zero-to-production-fa.md) |
| درخواست از مرورگر تا سرویس عملیاتی چگونه حرکت می‌کند؟ | [guide-fa.md](guide-fa.md#جریان-درخواست) |
| آیا توکن Keycloak به Gateway، سرویس Modern، Legacy یا Superset ارسال می‌شود؟ | [operational-token-forwarding-fa.md](operational-token-forwarding-fa.md) |
| آیا Session مرورگر همان توکن Keycloak است و کجا نگهداری می‌شود؟ | [operational-token-forwarding-fa.md#تفاوت-session-مرورگر-با-توکن-keycloak](operational-token-forwarding-fa.md#تفاوت-session-مرورگر-با-توکن-keycloak) |
| کاربر، گروه، نقش، منبع و action چه تفاوتی دارند؟ | [access-control-fa.md](access-control-fa.md) |
| هر فیلد و کنترل در فرم‌های میکرو راهبری چه معنا، محدودیت و اثر امنیتی دارد؟ | [operator-admin-form-field-guide-fa.md](operator-admin-form-field-guide-fa.md) |
| دسترسی یک Micro Frontend چگونه مستقیم یا از طریق نقش/گروه داده می‌شود؟ | [access-control-fa.md#دسترسی-micro-frontend-به-کاربر-گروه-و-نقش](access-control-fa.md#دسترسی-micro-frontend-به-کاربر-گروه-و-نقش) |
| دموی دو کاربر با دسترسی متفاوت به صفحات HR و Finance چگونه اجرا می‌شود؟ | [two-page-openfga-demo-fa.md](two-page-openfga-demo-fa.md) |
| Shell چه کاری انجام می‌دهد و MFEها را چگونه بارگذاری می‌کند؟ | [shell-runtime-and-mfe-loading-fa.md](shell-runtime-and-mfe-loading-fa.md) |
| موتور مجوزدهی دقیقاً چگونه تصمیم می‌گیرد؟ | [authorization-engine-fa.md](authorization-engine-fa.md) |
| معماری کامل، همه سطوح OpenFGA و محدودیت‌های فعلی چیست؟ | [architecture-openfga-complete-fa.md](architecture-openfga-complete-fa.md) |
| هنگام انتشار، آدرس/پورت OpenFGA، Store ID و Model ID را کجا تعریف کنیم؟ | [openfga-deployment-configuration-fa.md](openfga-deployment-configuration-fa.md) |
| branch، review، CI و دسترسی Git چگونه مدیریت می‌شود؟ | [git-governance-fa.md](git-governance-fa.md) |
| دسترسی یک گزارش Superset چگونه داده می‌شود؟ | [access-control-fa.md](access-control-fa.md#انتصاب-گزارش-یا-داشبورد-به-کاربر) |
| دو Superset کجا تعریف می‌شوند و route یا iframe گزارش چگونه کار می‌کند؟ | [superset-routing-and-embedding-fa.md](superset-routing-and-embedding-fa.md) |
| چرا Superset بدون فرم Login دوم باز می‌شود و آیا از SSO یا password استفاده می‌کند؟ | [superset-routing-and-embedding-fa.md#احراز-هویت-و-sso-بین-super-app-و-superset](superset-routing-and-embedding-fa.md#احراز-هویت-و-sso-بین-super-app-و-superset) |
| ادمین چگونه بدون انتشار نسخه یک میکرو و backend Legacy تعریف می‌کند؟ | [legacy-service-authentication-fa.md](legacy-service-authentication-fa.md#تعریف-یک-micro-app-از-نوع-legacy-بدون-انتشار-نسخه) |
| سناریوی واقعی Legacy و OAuth2 را چگونه با اثبات نبود توکن در Session اجرا کنیم؟ | [legacy-oauth2-end-to-end-demo-fa.md](legacy-oauth2-end-to-end-demo-fa.md) |
| هر فایل Java/React/Infra چه مسئولیتی دارد؟ | [code-reference-fa.md](code-reference-fa.md) |
| جریان هر فیچر، مالک داده و فایل‌های اجرایی فعلی چیست؟ | [codebase-feature-reference-2026-09-04-fa.md](codebase-feature-reference-2026-09-04-fa.md) |
| مهم‌ترین ایرادهای معماری، امنیتی، Clean Code و SOLID چیست و به چه ترتیب اصلاح شوند؟ | [codebase-architecture-clean-code-solid-audit-2026-09-04-fa.md](codebase-architecture-clean-code-solid-audit-2026-09-04-fa.md) |
| پروژه را چگونه بالا بیاوریم و تست کنیم؟ | [operations-fa.md](operations-fa.md) |
| Demo کامل را چگونه روی یک سرور Linux منتشر کنیم؟ | [deployment-demo-linux-fa.md](deployment-demo-linux-fa.md) |
| انتشار واقعی Production از صفر چه الزاماتی دارد؟ | [deployment-production-linux-fa.md](deployment-production-linux-fa.md) |
| خطاهای Login، CSRF، 403، 404 و Superset را چگونه بررسی کنیم؟ | [operations-fa.md](operations-fa.md#عیب‌یابی) |

> منظور از «خط‌به‌خط» در این مستند، توضیح مسئولیت هر فایل و بلوک منطقی کد است. توضیح تک‌تک importها یا فایل‌های تولیدشده مانند `*.d.ts` ارزش نگهداری ندارد و با هر build منقضی می‌شود.
