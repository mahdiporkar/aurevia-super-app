# راهنمای جامع Aurevia Super App

[English](README-en.md) | **فارسی**

این پوشه مرجع فارسی طراحی، پیاده‌سازی، اجرا و امنیت پروژه است. مستندات بر اساس کد موجود در همین مخزن نوشته شده‌اند؛ هرجا طراحی هدف با پیاده‌سازی فعلی تفاوت دارد، با عنوان «شکاف فعلی» مشخص شده است.

## مسیر پیشنهادی مطالعه

1. [شروع، معماری و اجرای پروژه](guide-fa.md)
2. [سطوح دسترسی و مدل مجوزدهی](access-control-fa.md)
3. [مرجع کد، فایل‌به‌فایل](code-reference-fa.md)
4. [راهنمای خواندن کد به ترتیب اجرا](code-walkthrough-fa.md)
5. [معماری Authorization Engine](authorization-engine-fa.md)
5. [عملیات، تست و عیب‌یابی](operations-fa.md)
6. [معماری فنی](architecture.md)
7. [حاکمیت Git و دسترسی](git-governance-fa.md)
6. [مدل تهدید](threat-model.md)
7. [نمودار پایگاه داده](er-diagram.md)
8. [تصمیم‌های معماری](adr/)
9. [قراردادهای OpenAPI](openapi/)

## نقشه مستندات

| پرسش | سند |
|---|---|
| پروژه از چه اجزایی ساخته شده است؟ | [guide-fa.md](guide-fa.md) |
| درخواست از مرورگر تا سرویس عملیاتی چگونه حرکت می‌کند؟ | [guide-fa.md](guide-fa.md#جریان-درخواست) |
| کاربر، گروه، نقش، منبع و action چه تفاوتی دارند؟ | [access-control-fa.md](access-control-fa.md) |
| موتور مجوزدهی دقیقاً چگونه تصمیم می‌گیرد؟ | [authorization-engine-fa.md](authorization-engine-fa.md) |
| branch، review، CI و دسترسی Git چگونه مدیریت می‌شود؟ | [git-governance-fa.md](git-governance-fa.md) |
| دسترسی یک گزارش Superset چگونه داده می‌شود؟ | [access-control-fa.md](access-control-fa.md#انتصاب-گزارش-یا-داشبورد-به-کاربر) |
| هر فایل Java/React/Infra چه مسئولیتی دارد؟ | [code-reference-fa.md](code-reference-fa.md) |
| پروژه را چگونه بالا بیاوریم و تست کنیم؟ | [operations-fa.md](operations-fa.md) |
| خطاهای Login، CSRF، 403، 404 و Superset را چگونه بررسی کنیم؟ | [operations-fa.md](operations-fa.md#عیب‌یابی) |

> منظور از «خط‌به‌خط» در این مستند، توضیح مسئولیت هر فایل و بلوک منطقی کد است. توضیح تک‌تک importها یا فایل‌های تولیدشده مانند `*.d.ts` ارزش نگهداری ندارد و با هر build منقضی می‌شود.
