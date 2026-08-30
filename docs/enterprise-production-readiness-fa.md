# راهنمای سخت‌سازی Enterprise و آمادگی Production

این سند وضعیت واقعی مخزن را توصیف می‌کند. `infra/docker-compose/compose.yml` یک محیط توسعه و دمو است و به‌تنهایی deployment پروداکشن محسوب نمی‌شود.

## مرزهای اعتماد و جریان امن توکن

جریان مرجع باید چنین باشد:

1. مرورگر فقط cookie نشست `HttpOnly`, `Secure` و `SameSite` متعلق به BFF را نگه می‌دارد؛ access token نباید در JavaScript، `localStorage`، URL یا log قرار گیرد.
2. BFF با Authorization Code + PKCE به Keycloak عمومی متصل می‌شود و token را server-side در Redis token vault نگه می‌دارد.
3. هر درخواست ابتدا در BFF با OpenFGA و شناسه canonical کاربر بررسی می‌شود. نبودن OpenFGA، Redis یا route معتبر باید fail-closed باشد.
4. ارتباط BFF با Authorization Service و API Gateway در پروداکشن با mTLS و trust store اختصاصی انجام می‌شود. credential کاربر جایگزین هویت workload نیست.
5. برای سرویس legacy، BFF ابتدا مجوز را بررسی و سپس توکن machine-to-machine را از token endpoint می‌گیرد. Gateway باید هم bearer عمومی و هم header داخلی legacy را اعتبارسنجی کند و header داخلی را از اینترنت نپذیرد.
6. OpenFGA فقط از شبکه خصوصی و با authentication/TLS قابل دسترسی است. browser و سرویس‌های business مستقیماً به آن دسترسی ندارند.

در این مخزن هیچ source یا module فعال Go (`go.mod`/`*.go`) وجود ندارد؛ بنابراین Go Proxy قابل ممیزی یا تضمین نیست. اگر نمونه‌ای بیرون از این مخزن deploy شده است، باید از مسیر token حذف شود یا فقط پشت mTLS قرار گیرد، headerهای `Authorization`, `Cookie` و legacy داخلی ورودی را حذف کند و token کاربر را به Java Proxy زنجیره نکند.

## کنترل دسترسی

Authorization Service منبع حقیقت resource metadata و relation tupleهاست و OpenFGA تصمیم نهایی را می‌دهد. شناسه‌ها باید canonical باشند؛ نمونه‌ها: `application:aurevia`, `resource:proxy.route`, `resource:integration.auth-profile`.

پنل ادمین اکنون عملیات را تفکیک می‌کند:

| عملیات HTTP | مجوز OpenFGA |
|---|---|
| GET/HEAD | `can_view` |
| POST ایجاد | `can_create` |
| PUT/PATCH | `can_edit` |
| DELETE | `can_delete` |
| تست token/connection، health، activate/deactivate و grant | `can_manage` |

مجوز UI فقط برای تجربه کاربری است؛ BFF و Authorization Service باید هر درخواست را مستقل بررسی کنند. tupleهای role/user/resource باید از API مدیریت شوند و mutation موفق در audit ثبت شود. تصمیم cache‌شده OpenFGA کوتاه‌عمر است و mutation باید epoch کش را invalidate کند.

## موارد سخت‌سازی‌شده در کد

- endpointهای token test، connection test، invalidate و health فقط `POST` هستند تا CSRF protection روی mutation اعمال شود.
- پروفایل `prod` برای BFF→Gateway، HTTPS، client key store و trust store را اجباری می‌کند.
- BFF→Gateway و BFF→Authorization Service دارای connect timeout، response timeout، connection pool محدود و redirect غیرفعال هستند.
- پاسخ خطای JSON پیش از ثبت log، redaction و محدودسازی اندازه می‌شود؛ API log و audit retention جدا دارند.
- CSP پوسته به originهای دقیق MFE محلی محدود شده و headerهای clickjacking، MIME sniffing، referrer و browser permissions تنظیم شده‌اند.
- secretهای legacy در پروفایل production فقط از token vault رمزگذاری‌شده پذیرفته می‌شوند و local secret adapter غیرفعال است.

## الزامات اجباری پیش از Production

موارد زیر به زیرساخت مقصد وابسته‌اند و با اجرای Compose حل نمی‌شوند:

- Keycloak با `start`، TLS، hostname ثابت، proxy headers معتبر، database HA، key rotation، MFA برای مدیران، brute-force protection و backup/restore آزموده‌شده؛ `start-dev` و realm محلی ممنوع است.
- PostgreSQLهای Keycloak، OpenFGA، Authorization و Superset با TLS verification، HA/PITR، encryption at rest، حساب‌های مجزا و migration/rollback آزموده‌شده.
- Redis cluster/Sentinel با TLS، ACL مجزا، persistence و eviction policy سازگار با session/token vault؛ Redis عمومی یا password مشترک ممنوع است.
- OpenFGA چند replica پشت load balancer، datastore HA، authentication/TLS شبکه‌ای، pinned image digest و تست سازگاری model migration.
- API Gateway واقعی باید dual-token validation، حذف headerهای داخلی ورودی، allowlist مقصد، محدودیت body، timeout، circuit breaker، rate limit و audit correlation را enforce کند. Gateway نمونه Nginx جایگزین این کنترل‌ها نیست.
- secretها از Vault/KMS/secret manager تزریق شوند؛ هیچ مقدار `change-me`، client secret، private key یا token در Git، image، environment dump یا log نباشد. rotation و revoke عملاً تمرین شود.
- TLS در ingress با HSTS، cipher policy سازمان، certificate automation و CSP دارای originهای واقعی محیط. CSP محلی این مخزن مستقیماً برای دامنه Production مناسب نیست.
- imageها با digest pin، SBOM، vulnerability scan، signature verification، non-root/read-only filesystem و resource request/limit اجرا شوند.
- audit به storage تغییرناپذیر/SIEM صادر شود؛ دسترسی مشاهده/خروجی log کنترل و alertهای authentication failure، denied spike، token failure و OpenFGA latency تعریف شود.

## کارایی، پایداری و مقیاس‌پذیری

- BFF و Authorization Service را stateless و چند replica اجرا کنید؛ affinity فقط اگر ضرورت اثبات‌شده دارد.
- برای OpenFGA، Redis، DB و upstreamها SLO و budget مستقل برای latency/error تعیین کنید. timeout کل زنجیره باید از بیرون به داخل کاهش یابد و retry فقط برای عملیات idempotent با jitter باشد.
- route resolution و authorization را با بار واقعی تست کنید. cache نباید هنگام قطعی، دسترسی stale را نامحدود ادامه دهد.
- probeهای startup/readiness/liveness را جدا کنید؛ readiness باید dependency حیاتی را منعکس کند و liveness نباید به‌علت اختلال dependency باعث restart storm شود.
- migrationها باید پیش از rollout و تنها توسط job واحد اجرا شوند. rollout به‌صورت canary/blue-green همراه با rollback نسخه و schema انجام شود.
- تست‌های load، soak، failover، chaos، restore و disaster recovery جزو شرط release باشند؛ صرف health سبز کافی نیست.

## دروازه انتشار

Release زمانی مجاز است که secret scan، SAST/SCA، unit/integration/contract/E2E، migration test و image scan موفق باشند؛ هیچ finding بحرانی/بالا بدون پذیرش ریسک تاریخ‌دار باقی نماند؛ restore پایگاه داده و rotation کلید آزمایش شده باشد؛ و تیم عملیات dashboard، alert، runbook و on-call مشخص داشته باشد.

نتیجه فعلی: معماری و کنترل‌های داخل مخزن یک پایه مناسب هستند، اما Compose محلی **Production-Ready نیست**. تا زمانی که الزامات زیرساختی بالا در محیط مقصد پیاده و با شواهد آزمون تأیید نشده‌اند، نباید برچسب Enterprise Production-Ready داده شود.
