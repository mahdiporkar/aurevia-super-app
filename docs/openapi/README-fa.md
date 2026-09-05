# قراردادهای OpenAPI

قرارداد جاری به‌صورت خودکار از controller، DTO، validation، metadata فارسی و exampleهای داخل کد تولید می‌شود. snapshot دستی YAML در این پوشه نگهداری نمی‌شود، چون با اضافه‌شدن endpoint به‌سادگی قدیمی و متناقض می‌شود.

- پرتال یکپارچه: `http://localhost:8443/swagger-ui.html`
- JSON سرویس BFF: `/v3/api-docs`
- JSON authorization-service از مسیر امن BFF: `/api/v1/docs/authorization/openapi`
- راهنمای کامل: [swagger-openapi-fa.md](../swagger-openapi-fa.md)

برای ذخیره نسخه قابل ممیزی، پس از Login از گزینه `Download definition` همان Swagger UI استفاده و فایل خروجی را به artifact همان release در CI/CD پیوست کنید. منبع حقیقت همچنان کد همان commit است.
