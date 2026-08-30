# راهنمای جامع Aurevia Super App

[English](README-en.md) | **فارسی**

این پوشه مرجع فارسی طراحی، پیاده‌سازی، اجرا و امنیت پروژه است. مستندات بر اساس کد موجود در همین مخزن نوشته شده‌اند؛ هرجا طراحی هدف با پیاده‌سازی فعلی تفاوت دارد، با عنوان «شکاف فعلی» مشخص شده است.

## مسیر پیشنهادی مطالعه

1. [شروع، معماری و اجرای پروژه](guide-fa.md)
2. [راهنمای آموزشی صفر تا تسلط تیم فنی](technical-team-zero-to-production-fa.md)
3. [سطوح دسترسی و مدل مجوزدهی](access-control-fa.md)
4. [مرجع کد، فایل‌به‌فایل](code-reference-fa.md)
5. [راهنمای خواندن کد به ترتیب اجرا](code-walkthrough-fa.md)
6. [معماری Authorization Engine](authorization-engine-fa.md)
7. [مرجع جامع معماری و OpenFGA](architecture-openfga-complete-fa.md)
8. [عملیات، تست و عیب‌یابی](operations-fa.md)
9. [Superset عمومی، عملیاتی، Route و نمایش داخل MFE](superset-routing-and-embedding-fa.md)
10. [معماری Resource Catalog و دو Manifest](resource-catalog-manifest-architecture-fa.md)
11. [معماری فنی](architecture.md)
12. [حاکمیت Git و دسترسی](git-governance-fa.md)
13. [مدل تهدید](threat-model.md)
14. [نمودار پایگاه داده](er-diagram.md)
15. [تصمیم‌های معماری](adr/)
16. [قراردادهای OpenAPI](openapi/)

## نقشه مستندات

| پرسش | سند |
|---|---|
| پروژه از چه اجزایی ساخته شده است؟ | [guide-fa.md](guide-fa.md) |
| یک عضو جدید تیم چگونه پروژه را از صفر و آموزشی یاد بگیرد؟ | [technical-team-zero-to-production-fa.md](technical-team-zero-to-production-fa.md) |
| درخواست از مرورگر تا سرویس عملیاتی چگونه حرکت می‌کند؟ | [guide-fa.md](guide-fa.md#جریان-درخواست) |
| کاربر، گروه، نقش، منبع و action چه تفاوتی دارند؟ | [access-control-fa.md](access-control-fa.md) |
| موتور مجوزدهی دقیقاً چگونه تصمیم می‌گیرد؟ | [authorization-engine-fa.md](authorization-engine-fa.md) |
| معماری کامل، همه سطوح OpenFGA و محدودیت‌های فعلی چیست؟ | [architecture-openfga-complete-fa.md](architecture-openfga-complete-fa.md) |
| branch، review، CI و دسترسی Git چگونه مدیریت می‌شود؟ | [git-governance-fa.md](git-governance-fa.md) |
| دسترسی یک گزارش Superset چگونه داده می‌شود؟ | [access-control-fa.md](access-control-fa.md#انتصاب-گزارش-یا-داشبورد-به-کاربر) |
| دو Superset کجا تعریف می‌شوند و route یا iframe گزارش چگونه کار می‌کند؟ | [superset-routing-and-embedding-fa.md](superset-routing-and-embedding-fa.md) |
| ادمین چگونه بدون انتشار نسخه یک میکرو و backend Legacy تعریف می‌کند؟ | [legacy-service-authentication-fa.md](legacy-service-authentication-fa.md#تعریف-یک-micro-app-از-نوع-legacy-بدون-انتشار-نسخه) |
| هر فایل Java/React/Infra چه مسئولیتی دارد؟ | [code-reference-fa.md](code-reference-fa.md) |
| پروژه را چگونه بالا بیاوریم و تست کنیم؟ | [operations-fa.md](operations-fa.md) |
| خطاهای Login، CSRF، 403، 404 و Superset را چگونه بررسی کنیم؟ | [operations-fa.md](operations-fa.md#عیب‌یابی) |

> منظور از «خط‌به‌خط» در این مستند، توضیح مسئولیت هر فایل و بلوک منطقی کد است. توضیح تک‌تک importها یا فایل‌های تولیدشده مانند `*.d.ts` ارزش نگهداری ندارد و با هر build منقضی می‌شود.
