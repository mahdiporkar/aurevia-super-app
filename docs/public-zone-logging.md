# لاگ متمرکز ناحیه عمومی (Public Zone)

این قابلیت عمداً فقط برای `superapp-bff`، `authorization-service` و درخواست‌های Public Zone طراحی شده است. معماری Operation Zone را تغییر نمی‌دهد و هیچ وابستگی به Kafka، OpenTelemetry یا زیرساخت سنگین دیگری ندارد. مقصد پایدار لاگ‌ها PostgreSQL سرویس Authorization است.

## اصول امنیتی غیرقابل‌تغییر

- بدنهٔ request در هیچ مرحله‌ای خوانده یا ذخیره نمی‌شود؛ فقط `Content-Length` در صورت وجود ثبت می‌شود.
- بدنهٔ response موفق (`status < 400`) هرگز capture یا persist نمی‌شود.
- برای خطا فقط JSON و media typeهای `+json` پذیرفته می‌شوند. دادهٔ binary، HTML و JSON نامعتبر ذخیره نمی‌شود.
- کلیدهای حساس در هر عمق JSON، از جمله `password`، `token`، `access_token`، `refresh_token`، `authorization`، `cookie`، `secret`، `client_secret` و گونه‌های مشابه با `[REDACTED]` جایگزین می‌شوند.
- خروجی پاک‌سازی‌شده با `LOG_ERROR_RESPONSE_MAX_BYTES` محدود می‌شود (پیش‌فرض 8192، بازهٔ مجاز 256 تا 65536 بایت).
- UI از React text rendering استفاده می‌کند و JSON را با `dangerouslySetInnerHTML` نمایش نمی‌دهد.
- APIهای لاگ فقط خواندنی هستند. حذف فقط توسط job زمان‌بندی‌شدهٔ retention انجام می‌شود.

## جریان داده و Correlation ID

در ورودی BFF، هدر `X-Correlation-ID` فقط وقتی پذیرفته می‌شود که حداکثر 128 کاراکتر و مطابق `[A-Za-z0-9._:-]+` باشد؛ در غیر این صورت UUID جدید ساخته می‌شود. همان مقدار در response، Reactor Context و تمام فراخوانی‌های WebClient به Authorization Service منتشر می‌شود. Authorization Service نیز همین قانون را اعمال می‌کند.

فیلتر BFF بدون لمس بدنهٔ request، metadata درخواست و پاسخ را جمع می‌کند و از endpoint داخلی `POST /internal/v1/logging/api` برای درج استفاده می‌کند. این endpoint با Basic Auth در local و workload mTLS در production محافظت می‌شود. شکست مسیر API logging پاسخ کسب‌وکار را خراب نمی‌کند؛ در مقابل audit یک mutation در همان تراکنش mutation نوشته می‌شود و شکست audit کل تغییر را rollback می‌کند.

## مدل داده

Migration `V20__public_zone_centralized_logging.sql` دو جدول append-only ایجاد می‌کند:

- `api_log`: زمان، کاربر، سرویس، method، route، status، duration، IP، user-agent، correlation، اندازه‌ها، نتیجهٔ authorization و زمان OpenFGA/DB/Redis/downstream، اطلاعات خطای امن.
- `audit_log`: actor، category/type، subject/target، action/result، snapshotهای امن before/after، metadata و correlation.

ایندکس‌ها برای زمان، کاربر، سرویس، status، route، target و correlation تعریف شده‌اند. مقدارهای JSON در audit باید snapshot حداقلی و allowlisted باشند؛ entity یا payload خام را در آن قرار ندهید.

## مجوز OpenFGA

منبع مستقل زیر ایجاد می‌شود و عمداً زیر application root قرار ندارد:

```text
business_resource:public-zone-logs
```

نگاشت مجوزها:

- `view_api` و `view_errors` → relation `viewer` → check `can_view`
- `view_audit` → relation `manager` → check `can_manage`
- `export` در catalog موجود است، ولی تا زمانی که endpoint export ساخته نشده کاربرد ندارد.

Interceptor عمومی admin همچنان اجرا می‌شود، اما هر endpoint لاگ یک check دوم و اختصاصی روی `resource:business_resource:public-zone-logs` انجام می‌دهد. بنابراین administrator بودن به‌تنهایی audit را قابل مشاهده نمی‌کند. grantهای bootstrap فقط برای هویت local با external id برابر `administrator` ساخته می‌شوند؛ در production باید grantها صریح و حداقلی باشند.

## APIهای مدیریتی

مسیر خارجی از BFF با پیشوند `/api/v1/admin` و مسیر داخلی Authorization Service با `/internal/v1/registry` است:

- `GET /logs/api?page=0&size=50`؛ فیلترها: `from`, `to`, `userId`, `serviceName`, `statusCode`, `route`, `correlationId`, `authorizationResult`
- `GET /logs/api/{id}`
- `GET /logs/api/summary?from=...&to=...`
- `GET /logs/audit?page=0&size=50`؛ فیلترها: `from`, `to`, `actorId`, `eventType`, `targetType`, `targetId`, `result`, `correlationId`
- `GET /logs/audit/{id}`
- `GET /logs/correlation/{correlationId}`؛ timeline ترکیبی API و audit، حداکثر 1000 رکورد

`page` از صفر شروع می‌شود و `size` بین 1 و 200 است. SQL فقط از fragmentهای ثابت و parameter binding استفاده می‌کند. detail ناشناخته `404`، شناسه/فیلتر نامعتبر `400` و فقدان مجوز `403` می‌دهد.

## Audit eventهای توسعه

برای هر mutation مهم متد `AuditTrail.success(...)` را داخل همان متد `@Transactional` و پس از تغییر business data فراخوانی کنید. نام event باید domain-oriented و نسخه‌پذیر باشد؛ نمونه‌ها: `resource.created`، `grant.created`، `role.assigned` و `superset.resource.register`. در `before_state` و `after_state` فقط فیلدهای لازم برای بررسی تغییر قرار دهید؛ credential، token، cookie و request payload ممنوع‌اند.

رخدادهای فعلی شامل تغییر منابع و actionها، ساخت/لغو grant، ثبت کاربر، ایجاد/تخصیص/لغو نقش و ثبت Superset asset است. توسعه‌دهندهٔ endpoint جدید مسئول افزودن audit در همان تراکنش و تست rollback است.

## UI مدیریت

در MFE Admin زبانهٔ «لاگ‌ها» شامل `API Logs` و `Audit Logs` است. جدول‌ها pagination سروری، فیلتر، summary API و drawer جزئیات دارند. خطای `403` به‌جای پنهان کردن مرز امنیتی نمایش داده می‌شود؛ امنیت واقعی همواره در backend enforce می‌شود.

## Retention و تنظیمات

```env
LOG_ERROR_RESPONSE_MAX_BYTES=8192
API_LOG_RETENTION_DAYS=30
AUDIT_LOG_RETENTION_DAYS=365
LOG_RETENTION_CRON=0 17 2 * * *
```

job روزانه در هر اجرا حداکثر 5000 ردیف قدیمی از هر جدول حذف می‌کند تا transaction بزرگ ایجاد نشود. برای backlog زیاد، job در اجراهای بعد ادامه می‌دهد. سیاست واقعی نگه‌داری باید توسط Security/Compliance تعیین و از environment تزریق شود.

## راه‌اندازی و بررسی پذیرش

1. migrationها را با اجرای Authorization Service اعمال کنید و وضعیت Flyway را بررسی کنید.
2. tupleهای outbox منبع لاگ را تا `PROCESSED` شدن کنترل کنید.
3. BFF و MFE Admin را build و deploy کنید.
4. درخواست موفق بفرستید و تأیید کنید `error_response_body is null` است.
5. خطای JSON شامل secret تو در تو بسازید و redaction/truncation را بررسی کنید.
6. با کاربر فاقد `can_view` و `can_manage` پاسخ `403` را بررسی کنید.
7. یک mutation انجام دهید و با correlation مشترک، ردیف API و audit را در timeline ببینید.
8. در محیط آزمایش retention کوتاه تنظیم کنید و حذف batch را بررسی کنید.

تست‌های واحد `SafeErrorBodySerializerTest` و `CorrelationIdsTest` قواعد عدم ذخیرهٔ success/binary، redaction عمیق، truncation و اعتبار correlation را پوشش می‌دهند. اجرای استاندارد backend: `mvn test` و frontend: `npm run typecheck --workspace=@aurevia/mfe-admin` سپس `npm run build --workspace=@aurevia/mfe-admin` است.
