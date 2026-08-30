# Production hardening report

این گزارش زنده است و هم‌زمان با بسته‌شدن موارد به‌روزرسانی می‌شود. وضعیت فعلی هنوز
`production-ready` نیست و تا بسته‌شدن همه P0ها چنین ادعایی نباید در release metadata مطرح شود.

| Finding | Severity | Impacted files | Fix implemented | Test coverage | Remaining operational dependency |
|---|---|---|---|---|---|
| نگاشت action/relation/permission پراکنده بود و relation دلخواه UI وارد tuple می‌شد | P0 | `AuthorizationController`, `AccessAdminController` | `AuthorizationSemanticsRegistry` مرجع یکتا شد؛ action ناشناخته، `can_*` عمومی و ترکیب نامعتبر پیش از DB/outbox رد می‌شوند؛ relation ورودی دیگر استفاده نمی‌شود | تست exhaustive نگاشت‌های معتبر و نامعتبر | ندارد |
| Structured policy در runtime check اجرا نمی‌شد | P0 | `AuthorizationController`, `RuntimePolicyService`, `StructuredPolicyEvaluator` | پس از OpenFGA ALLOW تمام policyهای فعال همان resource/action با context صرفاً server-derived اجرا و obligations تجمیع می‌شوند؛ parse/context/schema failure برابر DENY است | تست allow+obligation، policy deny و OpenFGA deny/skip | نگاشت group claims سازمانی باید توسط operator تأیید شود |
| تصمیم‌های runtime در decision log ثبت نمی‌شدند | P0 | `AuthorizationDecisionAuditor`, V16 | subject/resource/action/permission، تصمیم‌های لایه‌ها، علت، latency، correlation و policy version ثبت می‌شود؛ audit failure اجازه ALLOW نمی‌دهد | تست audit invocation و fail-closed | retention period باید توسط عملیات تعیین شود |
| outbox فاقد dead-letter و reconciliation کامل بود؛ بعضی مسیرهای projection ناقص بودند | P0 | `OutboxReconciler`, identity/Superset controllers, V17، reconciliation service | retry نمایی bounded، dead-letter، metrics، group membership events، Superset parent event و dry-run/repair اضافه شد | unit build سبز؛ integration idempotency هنوز لازم است | alerting و approval فرایند repair |
| invalidation فقط tuple دقیق را حذف می‌کرد | P0 | `OpenFgaRelationshipAdapter` | graph epoch مشترک Redis قبل از هر graph mutation افزایش می‌یابد؛ شکست Redis mutation را متوقف می‌کند و check هنگام cache outage مستقیم OpenFGA را می‌خواند | cache-hit تست شده؛ سناریوهای integration revoke هنوز لازم است | Redis production HA |
| workloadها از Basic Auth استفاده می‌کردند | P0 | Security config، BFF authorization client، production YAML | local mode صریح Basic باقی ماند؛ production فقط mTLS با client identity و trust validation است و config ناقص startup را fail می‌کند | build/test context local سبز؛ certificate integration هنوز لازم است | صدور certificate و secret mount توسط platform |
| production profile fail-fast و ingress headers کامل نیست | P1 | Spring config، Nginx، Compose | باز است | لازم است | دامنه، TLS و trusted proxy production |
| Superset runtime از development server و rate-limit حافظه‌ای استفاده می‌کند | P1 | Compose، `superset_config.py` | باز است | smoke/config test لازم است | secret store و deployment sizing |
| CI فاقد secret/SAST/container/OpenFGA/Flyway gates کامل است | P1 | `.gitlab-ci.yml` | باز است | pipeline validation | GitLab runners و registry |
| remoteEntry در local با URL کامل ثبت می‌شود ولی production origin policy مستقل لازم دارد | P2 | Registry، Shell remote loader | باز است | contract/security tests | allowlist دامنه‌های production |

## Audit scope

در ممیزی اولیه README، SECURITY، راهنمای عملیات، مرجع OpenFGA، کد کامل Authorization
Service، security/proxy/token-vault در BFF، تمام migrationهای Flyway، مدل و تست OpenFGA،
Docker Compose، Nginx، Superset، Dockerfileها و GitLab CI بررسی شدند. تغییرات بعدی باید
این جدول را با وضعیت تست واقعی و وابستگی‌های باقی‌مانده به‌روزرسانی کنند.
