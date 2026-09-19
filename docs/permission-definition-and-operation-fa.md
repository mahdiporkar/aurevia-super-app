# راهنمای عملیاتی تعریف و بهره‌برداری از مجوزها (Permission)

این سند راهنمای گام‌به‌گام برای توسعه‌دهندگان و راهبران است: چگونه مجوز تعریف می‌شود، در سیستم چه اتفاقی می‌افتد، وضعیت‌های پروجکشن چه معنایی دارند و وقتی «مجوز هست ولی کاربر نمی‌بیند» دقیقاً چه چیزهایی باید بررسی شود.

همهٔ مثال‌ها از endpointهای واقعی سرویس Authorization (`/internal/v1/...`) استفاده می‌کنند. این endpointها از طریق BFF (`/api/admin/...`) به Admin UI می‌رسند و مستقیماً در معرض مرورگر نیستند. درخواست‌های داخلی به Basic Auth (`AUTH_INTERNAL_USER` / `AUTH_INTERNAL_PASSWORD`) و هدرهای `X-Actor`, `X-Actor-Issuer`, `X-Actor-Subject` نیاز دارند.

---

## A. مفاهیم

| مفهوم | تعریف | نمایش در OpenFGA |
|---|---|---|
| **User** (کاربر) | هویت canonical در جدول `app_user`. هر ورود OIDC (issuer + subject) به یک `external_identity` نگاشت می‌شود که به همین کاربر اشاره می‌کند. شناسهٔ پایدار: `canonical_user_id` (مثلاً `usr_8e4e7517…`). | `user:usr_8e4e7517…` |
| **Directory Group** (گروه دایرکتوری) | گروهی که از Keycloak/LDAP در زمان ورود همگام می‌شود. کلید یکتا: `(issuer, external_id)`. هرگز با نام نمایشی شناسایی نمی‌شود. | `group:directory/<uuid>` و عضویت `#member` |
| **Access Group** (گروه دسترسی / OU) | گروه محاسبه‌شده بر اساس قواعد OU. کد یکتا با الگوی `^[A-Z][A-Z0-9_]{2,159}$`. | `group:<code به حروف کوچک>` و عضویت `#member` |
| **Role** (نقش) | مجموعه‌ای از مجوزها با چرخهٔ حیات `ACTIVE`/`INACTIVE`. به کاربر، گروه دایرکتوری یا گروه دسترسی قابل انتساب است. | `role:<role_key>` و انتساب `#assignee` |
| **Resource** (منبع) | هر چیزی که مجوز روی آن تعریف می‌شود: Application، Module، Page، Component، منبع خارجی (Superset). کلید: `resource_key` مانند `page:hr.employees`. | `application:…`, `resource:…`, `external_resource:…` |
| **Action** (عمل) | عمل کسب‌وکاری مانند `view`, `update`, `delete`, `admin`. باید به منبع **متصل** (`resource_action`) شده باشد. | به یک relation نگاشت می‌شود (جدول زیر) |
| **Grant** (اعطا) | رکورد `authorization_grant`: (subject_type, subject_id, resource, action). وضعیت `ACTIVE`/`ARCHIVED`، اختیاری `expires_at`. | یک tuple: `<subject> <relation> <object>` |
| **OpenFGA relation** | relation ذخیره‌شده (`viewer`, `editor`, `manager`, …) و permission محاسبه‌شده (`can_view`, `can_edit`, …) که در زمان اجرا چک می‌شود. | — |
| **Effective permission** (مجوز مؤثر) | نتیجهٔ نهایی `check` در OpenFGA (با وراثت از والد، گروه، نقش) به‌علاوهٔ سیاست‌های زمان اجرا. **فقط این** معیار موفقیت است؛ وجود رکورد در PostgreSQL کافی نیست. | خروجی `can_*` |

نگاشت action → relation (منبع واحد: `AuthorizationSemanticsRegistry`):

| action | RESOURCE | APPLICATION | EXTERNAL_RESOURCE | permission چک‌شده |
|---|---|---|---|---|
| `view`, `access`, `list` | viewer | viewer | viewer | `can_view` |
| `create`, `import`, `upload` | creator | manager | manager | `can_create` |
| `update`, `approve`, `reject` | editor | manager | editor | `can_edit` |
| `delete` | deleter | manager | manager | `can_delete` |
| `share` | manager | manager | sharer | `can_share` |
| `export`, `download` | manager | manager | exporter | `can_export` |
| `admin`, `manage`, … | manager | manager | manager | `can_manage` |

---

## B. سلسله‌مراتب منابع

مدل OpenFGA وراثت را از **والد به فرزند** انجام می‌دهد: `can_view = viewer or … or can_view from parent`. یعنی اعطای `view` روی Module، همهٔ Pageهای زیر آن را قابل مشاهده می‌کند؛ ولی اعطای `view` روی یک Page، به Module یا Application دسترسی **نمی‌دهد**.

```
application:aurevia                         (ریشهٔ سامانه)
└─ application:aurevia/hr                   (APPLICATION – میکروفرانت HR)
   └─ module:hr.people                      (MODULE)
      ├─ page:hr.employees                  (PAGE)
      │  └─ component:hr.employees.salary   (UI_COMPONENT / SECTION)
      └─ page:hr.contracts                  (PAGE)
```

اشیاء OpenFGA متناظر:

| resource_key | type | OpenFGA object |
|---|---|---|
| `application:aurevia/hr` | APPLICATION | `application:aurevia/hr` |
| `module:hr.people` | MODULE | `resource:module/hr.people` |
| `page:hr.employees` | PAGE | `resource:page/hr.employees` |
| `external_resource:superset/operation-default/dashboard/7` | EXTERNAL_RESOURCE | `external_resource:superset/operation-default/dashboard/7` |

**منابع Superset**: هر داشبورد/چارت یک `EXTERNAL_RESOURCE` مستقل است که زیر ریشهٔ منطقی `external_resource:superset-public` ثبت می‌شود. میکروفرانت Reports با `panel.discovery_resource_key = external_resource:superset-public` اعلام می‌کند که «هر مجوزی روی زیرمجموعهٔ این منبع، مرا قابل کشف می‌کند» — بدون آن‌که مجوزی روی `application:aurevia/reports` ساخته شود (بخش H).

قاعدهٔ کشف میکروفرانت: یک Page مجاز کافی است تا میکروفرانت مالک آن در `uiCatalog.modules` ظاهر شود. **نیازی به اعطای جداگانهٔ Application نیست.** Pageهای هم‌سطحِ غیرمجاز ظاهر نمی‌شوند.

---

## C. اعطای مجوز

### C.1 با Admin UI (Access Studio)

1. «مدیریت → دسترسی‌ها (Access Studio)».
2. نوع سوژه را انتخاب کنید: **کاربر / گروه دایرکتوری / گروه دسترسی (OU) / نقش**.
3. سوژه را جست‌وجو و انتخاب کنید.
4. در درخت منابع، منبع و سپس Action را انتخاب کنید (فقط Actionهای متصل به آن منبع نمایش داده می‌شوند).
5. در صورت نیاز «تاریخ انقضا» بدهید.
6. «اعطا». ردیف جدید با ستون **وضعیت پروجکشن** ظاهر می‌شود. تا زمانی که `APPLIED` نشده، مجوز مؤثر نیست (بخش F).

### C.2 با API — همهٔ سوژه‌ها از یک endpoint

`POST /internal/v1/registry/grants` — بدنه:

```json
{
  "subjectType": "USER",
  "subjectId": "5c1c1c6e-0d2a-4d0e-9e8a-6d0a4c6c2b11",
  "resourceId": "0f4a4b7b-2d68-4d3e-a6f0-1a8b39d5a2e9",
  "actionId": "9d5a5a8f-4b1e-4c9b-9c1c-4e5f6a7b8c9d",
  "expiresAt": "2027-03-20T20:30:00Z"
}
```

`subjectType` یکی از `USER`, `GROUP`, `ACCESS_GROUP`, `ROLE`. `expiresAt` اختیاری است.

پاسخ:

```json
{ "id": "3b1d0c62-…", "version": 0, "existing": false }
```

`existing: true` یعنی همان اعطا قبلاً فعال بوده (idempotent).

شناسه‌ها را از این‌جا بگیرید:

- منابع و Actionهای متصل: `GET /internal/v1/registry/resources`
- کاربران: `GET /internal/v1/registry/users`
- گروه‌های دایرکتوری: `GET /internal/v1/registry/directory-groups`
- گروه‌های دسترسی: `GET /internal/v1/registry/ou-access/access-groups`
- نقش‌ها: `GET /internal/v1/registry/roles`

مثال هر سوژه (فقط `subjectType`/`subjectId` فرق می‌کند):

```json
{ "subjectType": "GROUP",        "subjectId": "<directory_group.id>", "resourceId": "…", "actionId": "…" }
{ "subjectType": "ACCESS_GROUP", "subjectId": "<access_group.id>",    "resourceId": "…", "actionId": "…" }
{ "subjectType": "ROLE",         "subjectId": "<application_role.id>","resourceId": "…", "actionId": "…" }
```

مشاهدهٔ اعطاهای یک سوژه (به‌همراه وضعیت پروجکشن):

```
GET /internal/v1/registry/subjects/{USER|GROUP|ACCESS_GROUP|ROLE}/{subjectId}/grants
```

```json
[{
  "id": "3b1d0c62-…",
  "resource_key": "page:hr.employees",
  "action_key": "view",
  "relation": "viewer",
  "expires_at": null,
  "status": "ACTIVE",
  "projection_status": "APPLIED",
  "projection_error": null
}]
```

لغو: `DELETE /internal/v1/registry/grants/{id}`

---

## D. انتساب نقش

ایجاد نقش:

```
POST /internal/v1/registry/roles
{ "roleKey": "hr-auditor", "nameFa": "حسابرس منابع انسانی", "nameEn": "HR Auditor" }
```

اعطای مجوز به نقش: مانند بخش C با `subjectType: "ROLE"`.

انتساب نقش — `POST /internal/v1/registry/role-assignments`:

```json
{ "subjectType": "USER",            "subjectId": "<app_user.id>",        "roleId": "<role.id>", "expiresAt": null }
{ "subjectType": "DIRECTORY_GROUP", "subjectId": "<directory_group.id>", "roleId": "<role.id>", "expiresAt": null }
{ "subjectType": "ACCESS_GROUP",    "subjectId": "<access_group.id>",    "roleId": "<role.id>", "expiresAt": "2026-12-31T20:30:00Z" }
```

> توجه: در انتساب نقش، گروه دایرکتوری با `DIRECTORY_GROUP` مشخص می‌شود؛ در اعطای مستقیم (بخش C) با `GROUP`.

لغو انتساب: `DELETE /internal/v1/registry/role-assignments/{subjectType}/{subjectId}/{roleId}`

غیرفعال/فعال کردن نقش:

```
PATCH /internal/v1/registry/roles/{id}/status?version=<version فعلی>
{ "active": false }
```

غیرفعال شدن نقش، **همهٔ** tupleهای انتساب و اعطای آن نقش را از OpenFGA حذف می‌کند (رکوردهای انتساب در PostgreSQL می‌مانند). فعال‌سازی مجدد، همان‌ها را — به جز موارد منقضی‌شده — دوباره پروجکت می‌کند.

---

## E. در داخل سیستم چه می‌گذرد

```
Admin UI / API
   │  تراکنش واحد PostgreSQL:
   ├─ INSERT authorization_grant (status=ACTIVE)
   └─ INSERT outbox_event (GRANT_WRITE, payload = {user, relation, object})
                      │
                      ▼  OutboxReconciler (هر OUTBOX_INTERVAL_MS، پیش‌فرض 5s)
                      │  claim با قفل + ترتیب sequence برای هر aggregate
                      ▼
                  OpenFGA.write(tuple)
                      │  موفق ➜ processed_at    ناموفق ➜ attempts+1, backoff (2^n تا 300s)
                      │                          پس از OUTBOX_MAX_ATTEMPTS ➜ dead_lettered_at
                      ▼
       AuthorizationDecisionService.check / manifest
                      │  OpenFGA check(can_*) + سیاست‌های زمان اجرا
                      ▼
   GET /internal/v1/subjects/{subject}/manifest  ──►  BFF  ──►  GET /api/me/context
                      │
                      ▼
   permissions / panels / uiCatalog.modules / uiCatalog.routes  ──►  Shell / MFE
                      │
                      ▼
   هر درخواست runtime (proxy, Superset) دوباره check می‌شود — منوی قابل مشاهده ≠ مجوز.
```

نکات مهم:

- **Grant و outbox در یک تراکنش** ثبت می‌شوند؛ هیچ اعطایی بدون رویداد پروجکشن وجود ندارد.
- **Revoke** همان مسیر را با `GRANT_DELETE` طی می‌کند؛ تغییر وضعیت در PostgreSQL به‌تنهایی کافی نیست.
- **انقضا** (`expires_at`) با `ExpirationSweeper` (هر `aurevia.expiration.sweep-interval-ms`، پیش‌فرض 30s) به همان رویدادهای حذف تبدیل می‌شود؛ نیاز به ری‌استارت نیست.
- **Reconciliation در استارت** (`OPENFGA_RECONCILE_ON_STARTUP=true`): ابتدا outbox را تخلیه می‌کند، سپس وضعیت مورد انتظار را از PostgreSQL می‌سازد، tupleهای غایب را می‌نویسد و tupleهای اضافی را حذف می‌کند و در پایان تأیید می‌کند که هیچ اختلافی نمانده. اجرای مکرر همیشه به یک وضعیت می‌رسد (idempotent).
- **Redis** فقط کش تصمیم‌ها است. قبل از هر نوشتن/حذف در OpenFGA «epoch» کش افزایش می‌یابد؛ اگر Redis در دسترس نباشد، نوشتن انجام نمی‌شود و رویداد در outbox می‌ماند (RETRYING) تا تصمیم کهنه در کش باقی نماند. خواندن بدون Redis مستقیماً به OpenFGA می‌رود.

متریک‌ها (`/actuator/prometheus`):

| متریک | معنا |
|---|---|
| `aurevia_outbox_pending` | رویدادهای پردازش‌نشده |
| `aurevia_outbox_retry` | رویدادهای در حال تلاش مجدد |
| `aurevia_outbox_dead_letter` | رویدادهای شکست‌خوردهٔ نهایی |
| `aurevia_outbox_oldest_pending_seconds` | سن قدیمی‌ترین رویداد معلق |
| `aurevia_openfga_projection_applied_total` | پروجکشن‌های موفق |
| `aurevia_openfga_projection_failed_total` | پروجکشن‌های ناموفق (قبل از dead-letter هم شمرده می‌شوند) |
| `aurevia_authorization_expired_grants_total` / `…_role_assignments_total` | رکوردهای منقضی‌شدهٔ پردازش‌شده |

---

## F. وضعیت‌های پروجکشن و اقدام راهبر

| وضعیت | معنا | مجوز مؤثر است؟ | اقدام |
|---|---|---|---|
| `PENDING` | در PostgreSQL ثبت شده، هنوز به OpenFGA نرفته | **خیر** | چند ثانیه صبر کنید. اگر بیش از یک دقیقه ماند: سرویس Authorization بالاست؟ `aurevia_outbox_oldest_pending_seconds` را ببینید. |
| `RETRYING` | نوشتن در OpenFGA شکست خورده و با backoff تکرار می‌شود | **خیر** | `projection_error` را بخوانید. معمولاً OpenFGA یا Redis در دسترس نیست. با بازگشت سرویس، خودکار `APPLIED` می‌شود. |
| `APPLIED` | tuple در OpenFGA نوشته شده | **بله** (اگر منبع فعال و نقش/گروه فعال باشد) | اگر کاربر همچنان نمی‌بیند ➜ بخش G. |
| `FAILED` | پس از `OUTBOX_MAX_ATTEMPTS` (پیش‌فرض 12) به dead-letter رفته | **خیر** | خطا ساختاری است (مثلاً tuple نامعتبر یا مدل OpenFGA ناسازگار). `projection_error` را بررسی کنید؛ پس از رفع علت، اعطا را لغو و دوباره ایجاد کنید یا سرویس را با `OPENFGA_RECONCILE_ON_STARTUP=true` ری‌استارت کنید (reconciliation وضعیت مورد انتظار را مستقیماً می‌نویسد). |
| `REVOKED` | آخرین رویداد حذف پردازش شده | خیر | طبیعی پس از لغو/انقضا. |
| `UNKNOWN` | رویداد outbox برای این اعطا پیدا نشد | نامشخص | فقط برای رکوردهای بسیار قدیمی. با reconciliation برطرف می‌شود. |

**قاعدهٔ طلایی:** تنها `APPLIED` را «اعمال‌شده» بدانید. Admin UI هرگز اعطای `PENDING/RETRYING/FAILED` را سبز نشان نمی‌دهد.

---

## G. عیب‌یابی: «Grant هست ولی کاربر نمی‌بیند»

به ترتیب بررسی کنید؛ در هر مرحله اگر مشکل پیدا شد، ادامه لازم نیست.

**۱. وضعیت Grant** — `GET /internal/v1/registry/subjects/{type}/{id}/grants`
`status` باید `ACTIVE` و `expires_at` یا خالی یا در آینده باشد.

**۲. وضعیت پروجکشن** — همان پاسخ: `projection_status` باید `APPLIED` باشد. در غیر این صورت بخش F.

**۳. هویت canonical** — کاربری که وارد می‌شود همان کاربری است که مجوز گرفته؟
```sql
select u.canonical_user_id, e.issuer, e.subject
from external_identity e join app_user u on u.id=e.user_id
where e.subject='<subject ورود>';
```
`subject` باید همان claim پیکربندی‌شده در Identity Provider باشد (`subject_claim`، پیش‌فرض `sub`؛ می‌تواند مثلاً `employee_id` باشد). login-sync و `/api/me/context` هر دو از همین claim استفاده می‌کنند.

**۴. عضویت گروه** — برای اعطای `GROUP`: کاربر واقعاً عضو است؟
```sql
select g.id, g.external_id, g.status from user_group_membership m
join directory_group g on g.id=m.group_id
join app_user u on u.id=m.user_id where u.canonical_user_id='usr_…';
```
عضویت گروه دایرکتوری در **هر ورود** از claim `groups` به‌روز می‌شود؛ کاربر باید پس از تغییر گروه در IdP دوباره وارد شود. برای گروه دسترسی، `effective_group_membership.active` را ببینید.

**۵. وضعیت نقش** — برای اعطای `ROLE`: `application_role.status='ACTIVE'` و انتساب منقضی نشده.

**۶. تصمیم OpenFGA** —
```
POST /internal/v1/authorize/check
{ "subjectId": "<subject>", "issuer": "<issuer>", "resource": "resource:page/hr.employees",
  "action": "view", "context": {}, "correlationId": "debug-1" }
```
`result: DENY` با `reasonCode`:
- `NO_RELATIONSHIP` ➜ OpenFGA tuple ندارد (مرحلهٔ ۲/۴/۵ را دوباره ببینید یا reconciliation).
- `RESOURCE_DISABLED` ➜ منبع `ACTIVE` نیست یا Action به آن متصل نیست.
- `POLICY_EVALUATION_ERROR` / سایر کدهای سیاست ➜ سیاست زمان اجرا؛ لاگ `RuntimePolicyService` را ببینید.

**۷. `/api/me/context.permissions`** — با نشست همان کاربر: باید `"page:hr.employees": ["view"]` وجود داشته باشد. اگر مرحلهٔ ۶ ALLOW است ولی این‌جا نیست: منبع `visibility_enabled=false` است، یا پنل مالک `classification=DEMO` است و `DEMO_DATA_ENABLED=false`.

**۸. endpoint تشخیص** — همهٔ موارد بالا را یک‌جا می‌دهد (نیازمند `can_manage` روی `application:aurevia`):
```
GET /internal/v1/registry/diagnostics/authorization
    ?issuer=<issuer>&subject=<subject>&resource=page:hr.employees&action=view
```
```json
{
  "canonical_subject": "user:usr_8e4e…",
  "open_fga_object": "resource:page/hr.employees",
  "relation": "viewer", "permission": "can_view",
  "allowed": false,
  "reason_code": "PROJECTION_FAILED",
  "resource": { "status": "ACTIVE", "action_attached": true, "visibility_enabled": true, "panel_id": "…" },
  "grants": [{ "subject_type": "GROUP", "subject_label": "Finance",
               "granted_resource_key": "module:hr.people", "projection_status": "FAILED",
               "projection_error": "OpenFGA tuple write failed: …" }],
  "memberships": [{ "kind": "DIRECTORY_GROUP", "open_fga_object": "group:directory/…", "active": true }],
  "roles": [{ "role_key": "hr-auditor", "status": "ACTIVE", "via": "USER" }],
  "panel": { "code": "HR", "readiness": "READY" }
}
```
کدهای `reason_code`: `ALLOWED_BY_GRANT`, `ALLOWED_WITHOUT_CONTROL_PLANE_GRANT` (اختلاف! tuple بدون رکورد), `RESOURCE_NOT_ACTIVE`, `ACTION_NOT_ATTACHED_TO_RESOURCE`, `NO_CONTRIBUTING_GRANT`, `PROJECTION_FAILED`, `PROJECTION_NOT_APPLIED`, `GRANT_APPLIED_BUT_OPENFGA_DENIES` (اختلاف! reconciliation لازم است), و کدهای آمادگی پنل `PANEL_DISABLED`, `NO_ACTIVE_ARTIFACT`, `ARTIFACT_NOT_VALID`, `MANIFEST_MISSING`.

`grants` اعطاهای روی خود منبع **و اجدادش** را برمی‌گرداند و `granted_resource_key` می‌گوید اعطا دقیقاً روی کدام سطح است.

---

## H. مجوزهای Superset — یک داشبورد بدون دسترسی عمومی Reports

هدف: کاربر فقط داشبورد ۴۲ را ببیند؛ نه کل Reports، نه داشبوردهای دیگر.

**۱. ثبت دارایی** (اگر قبلاً همگام نشده) — `POST /internal/v1/registry/superset-assets`:
```json
{ "externalId": "42", "assetType": "DASHBOARD", "title": "Sales Q3",
  "urlPath": "/superset/dashboard/42/", "published": true, "instanceCode": "operation-default" }
```
پاسخ: `{ "id": "<assetId>", "resourceId": "…", "resourceKey": "external_resource:superset/operation-default/dashboard/42", "existing": false }`

**۲. اعطا** — `POST /internal/v1/registry/superset-assets/{assetId}/grants`:
```json
{ "subjectType": "USER", "subjectId": "<app_user.id>", "level": "VIEW" }
```
`level`: `VIEW` (view/viewer) · `EDIT` (update/editor) · `MANAGE` (admin/manager). `subjectType` می‌تواند `GROUP`, `ACCESS_GROUP` یا `ROLE` هم باشد.

**۳. نتیجه پس از ورود کاربر:**
- `permissions` فقط شامل `external_resource:superset/operation-default/dashboard/42: ["view"]` است. **هیچ** `application:aurevia/reports` ساخته نمی‌شود.
- `panels` و `uiCatalog.modules` شامل `reports` است (کشف از طریق `discovery_resource_key`، بخش B). مسیر landing با همان منبع داشبورد guard می‌شود.
- `GET /api/v1/reports` فقط داشبورد ۴۲ را برمی‌گرداند.
- درخواست runtime به داشبورد ۴۲ ➜ `ALLOW`؛ به داشبورد دیگر یا چارت اعطانشده ➜ `403`.

بررسی runtime بدون مرورگر:
```
GET /internal/v1/registry/subjects/{subject}/superset-access
    ?issuer=<issuer>&instance=operation-default&path=/superset/dashboard/42/&assetType=DASHBOARD&assetId=42
→ { "result": "ALLOW", "reasonCode": "SUPERSET_ASSET_ALLOWED", "editAllowed": false }
```

**نکنید:** برای «دیده شدن Reports» به کاربر `application:aurevia/reports:view` یا نقش `superset-viewer` ندهید. آن‌ها دسترسی گسترده‌اند و برای مدیران/طراحان گزارش هستند.

---

## پیوست — چک‌لیست پس از هر تغییر مجوز

1. `projection_status = APPLIED` در Access Studio.
2. `POST /internal/v1/authorize/check` ➜ `ALLOW`.
3. کاربر یک‌بار خارج و وارد شود (برای تغییر گروه/هویت) و `/api/me/context.permissions` را ببیند.
4. برای لغو/انقضا: همان سه مورد باید به `REVOKED` / `DENY` / حذف از `permissions` برسد — حداکثر ظرف `OUTBOX_INTERVAL_MS` + `aurevia.expiration.sweep-interval-ms`.
5. در محیط تولید، `aurevia_outbox_dead_letter` باید صفر بماند؛ هر مقدار غیرصفر یک مجوز است که سبز نیست.
