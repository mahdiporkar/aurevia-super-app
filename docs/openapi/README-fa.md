# قراردادهای OpenAPI

قرارداد جاری به‌صورت خودکار از controller، DTO، validation، metadata فارسی و exampleهای داخل کد تولید می‌شود. snapshot دستی YAML در این پوشه نگهداری نمی‌شود، چون با اضافه‌شدن endpoint به‌سادگی قدیمی و متناقض می‌شود.

- پرتال یکپارچه: `http://localhost:8443/swagger-ui.html`
- JSON سرویس BFF: `/v3/api-docs`
- JSON authorization-service از مسیر امن BFF: `/api/v1/docs/authorization/openapi`
- راهنمای کامل: [swagger-openapi-fa.md](../swagger-openapi-fa.md)
- گزارش بازبینی و اصلاحات: [swagger-openapi-review-2026-09-12-fa.md](../swagger-openapi-review-2026-09-12-fa.md)
- آزمون مستقل: `npm run swagger:verify`؛ خروجی امن در `target/swagger/results.json`

دو قرارداد این پرتال برای BFF و Authorization Service هستند. سرویس‌های آزمایشی
`test-sso-service` و `test-legacy-service` Swagger عمومی جداگانه ندارند و endpointهای protected
آن‌ها تنها از proxy ثبت‌شدهٔ BFF فراخوانی می‌شوند. مشخصات این مسیرها در
[گزارش SSO/Legacy](../e2e-sso-legacy-proxy-test-fa.md) آمده است. افزودن Swagger به آن سرویس‌ها
نباید به بازکردن port یا دادن token پایین‌دست به مرورگر منجر شود.

برای ذخیره نسخه قابل ممیزی، پس از Login لینک JSON قرارداد انتخاب‌شده در Swagger را باز کنید، JSON را ذخیره و به artifact همان release در CI/CD پیوست کنید. منبع حقیقت همچنان کد همان commit است.
