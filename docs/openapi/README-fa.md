# قراردادهای OpenAPI

قرارداد جاری به‌صورت خودکار از controller، DTO، validation، metadata فارسی و exampleهای داخل کد تولید می‌شود. snapshot دستی YAML در این پوشه نگهداری نمی‌شود، چون با اضافه‌شدن endpoint به‌سادگی قدیمی و متناقض می‌شود.

- پرتال یکپارچه: `http://localhost:8443/swagger-ui.html`
- JSON سرویس BFF: `/v3/api-docs`
- JSON عمومی پنل راهبری با pathهای واقعی BFF: `/api/v1/docs/admin/openapi`
- JSON authorization-service از مسیر امن BFF: `/api/v1/docs/authorization/openapi`
- راهنمای کامل: [swagger-openapi-fa.md](../swagger-openapi-fa.md)
- گزارش بازبینی و اصلاحات: [swagger-openapi-review-2026-09-12-fa.md](../swagger-openapi-review-2026-09-12-fa.md)
- آزمون مستقل: `npm run swagger:verify`؛ خروجی امن در `target/swagger/results.json`

سه قرارداد این پرتال برای BFF، API عمومی پنل راهبری و Authorization Service داخلی هستند.
قرارداد Admin در runtime و بدون کپی‌کردن schemaها از قرارداد Authorization Service ساخته
می‌شود: پیشوند `/internal/v1/registry` به `/api/v1/admin` تبدیل و هدرهای عامل که BFF از نشست
می‌سازد حذف می‌شوند. به این ترتیب path، DTO، validation و exampleهای پنل راهبری همواره از
همان کد backend می‌آیند.

سرویس‌های آزمایشی
`test-sso-service` و `test-legacy-service` Swagger عمومی جداگانه ندارند و endpointهای protected
آن‌ها تنها از proxy ثبت‌شدهٔ BFF فراخوانی می‌شوند. مشخصات این مسیرها در
[گزارش SSO/Legacy](../e2e-sso-legacy-proxy-test-fa.md) آمده است. افزودن Swagger به آن سرویس‌ها
نباید به بازکردن port یا دادن token پایین‌دست به مرورگر منجر شود.

برای ذخیره نسخه قابل ممیزی، پس از Login لینک JSON قرارداد انتخاب‌شده در Swagger را باز کنید، JSON را ذخیره و به artifact همان release در CI/CD پیوست کنید. منبع حقیقت همچنان کد همان commit است.

> **وضعیت فعلی (۲۰۲۶-۰۹-۲۲):** قرارداد خطا در هر دو سرویس به `{code, message, correlationId}` یکسان شد،
> کدهای وضعیت به ازای هر عملیات مستند می‌شوند، enumها از ثابت‌های کد گرفته می‌شوند و تست‌های
> همگام‌سازی دوطرفه (کنترلر ↔ سند زمان اجرا) اضافه شد. جزئیات در
> [swagger-openapi-fa.md](../swagger-openapi-fa.md) بخش «وضعیت فعلی همگام‌سازی قرارداد».
