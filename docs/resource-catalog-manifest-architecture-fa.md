# معماری Resource Catalog و Manifest

## نتیجه بازبینی و شکاف‌های اصلاح‌شده

مدل قبلی Resource، Action، Grant، Data Policy و OpenFGA را جدا نگه می‌داشت و route operation را به Resource+Action متصل می‌کرد؛ اما `FIELD` نداشت، ۹ نوع را به UI عرضه می‌کرد، دکمه و گرید عادی را Resource کرده بود، `resource_key` قابل ویرایش بود و Definition Manifest/sync مستقل وجود نداشت. از V27 مدل منطقی فقط هفت نوع دارد، کلید immutable و canonical است، binding خارجی/API مستقل است، sync idempotent اضافه شده و button-resourceهای دموی HR Deprecated شده‌اند.

## مدل نهایی

```text
Subject + Resource + Action + Policy/Conditions = Decision
```

- Resource Catalog منبع metadata است: نام، نوع، والد، مالک، classification، lifecycle، source و binding.
- Action عملیات مستقل است و با `resource_action` به Resource متصل می‌شود.
- Permission برابر Resource+Action است؛ Action فرزند Resource در persistence نیست.
- Grant انتساب Permission به USER/GROUP/ROLE است و با Outbox در OpenFGA project می‌شود.
- Data Policy شرط و obligation مستقل از هویت Resource است.
- OpenFGA رابطه و grant را ارزیابی می‌کند و جای Catalog را نمی‌گیرد.

هفت نوع قابل ثبت از API عبارت‌اند از `APPLICATION`، `MODULE`، `PAGE`، `UI_COMPONENT`، `FIELD`، `BUSINESS_RESOURCE` و `EXTERNAL_RESOURCE`. انواع قدیمی دیتابیسی فقط برای مهاجرت داده‌های نصب‌های موجود باقی مانده‌اند و API ثبت جدید آن‌ها را رد می‌کند.

## الگوریتم تشخیص Resource

1. اگر تصمیم authorization مستقل لازم نیست، Resource نسازید.
2. اگر نیاز با Action روی Resource موجود بیان می‌شود، Action اضافه کنید.
3. اگر هویت منطقی پایدار ندارد، Resource نسازید.
4. فقط سپس نوع معنایی هفت‌گانه را انتخاب کنید.

بنابراین Create/Delete/Edit/Export button، table، pagination، modal و URL API Resource نیستند. مثال صحیح:

```text
business:hr.employee + create  → نمایش دکمه Create و مجوز POST
business:hr.employee + delete  → نمایش دکمه Delete و مجوز DELETE
```

یک UI section فقط اگر boundary مستقل داشته باشد Resource است؛ مانند `component:hr.employee.salary-information`. یک field نیز فقط با entitlement مستقل مانند `field:hr.employee.salary-amount` ثبت می‌شود. masking عمومی باید Data Policy باشد.

## هویت، hierarchy و lifecycle

کلیدها lowercase و پایدارند: `application:hr`، `module:hr.employee-management`، `page:hr.employee.list`، `component:hr.employee.salary-information`، `field:hr.employee.salary-amount`، `business:hr.employee` و `external:hr.workforce-dashboard`.

منابع flat و با `parent_id` ذخیره می‌شوند؛ API درخت را می‌سازد. والد صرفاً سازمان‌دهی/navigation است و دسترسی فرزند را ضمنی نمی‌کند. inheritance تنها در صورت rule صریح OpenFGA معتبر است. lifecycle هدف `DRAFT/ACTIVE/DEPRECATED/DISABLED` است؛ منبع دارای سابقه grant/audit hard-delete نمی‌شود. source یکی از `APPLICATION_MANIFEST/ADMIN/EXTERNAL_SYNC/SYSTEM` است.

## Bindingها

`resource_api_binding` متد و path فنی را به Resource+Action وصل می‌کند. `route_operation` نیز همین قرارداد را در مسیر Proxy اجرا می‌کند؛ routing پاسخ «کجا؟» و authorization پاسخ «آیا مجاز است؟» است. URL هرگز resource key نیست.

`resource_external_binding` provider/type/id فنی را از هویت منطقی جدا می‌کند. برای Superset:

```text
external:hr.workforce-dashboard
provider=SUPERSET, external_type=DASHBOARD, external_id=127
```

## دو Manifest مستقل

### Resource Definition Manifest

قابلیت‌های یک application را تعریف می‌کند و grant کاربر ندارد:

```http
GET /api/v1/admin/resource-definition-manifests/hr
PUT /api/v1/admin/resource-definition-manifests/hr
```

نمونه:

```json
{
  "application":"hr",
  "manifestVersion":"1.0.0",
  "resources":[
    {"key":"application:hr","type":"APPLICATION","nameFa":"منابع انسانی","nameEn":"HR","actions":["access"]},
    {"key":"business:hr.employee","type":"BUSINESS_RESOURCE","parent":"application:hr","nameFa":"کارمند","nameEn":"Employee","actions":["view","create","update","delete","export"]}
  ]
}
```

sync با application/version/checksum idempotent است. مورد جدید ایجاد، metadata مورد موجود update و مورد حذف‌شده از manifest به `DEPRECATED` تبدیل می‌شود؛ grantها silently حذف نمی‌شوند. manifest فرانت برای backendهای امنیتی blindly trusted نیست و validation سرور الزامی است.

### Effective User Manifest

```http
GET /api/v1/me/manifest
```

BFF subject را از session می‌گیرد. خروجی `manifestType=EFFECTIVE_USER_MANIFEST`، subject، panelهای مجاز، permissions و resourceTree مؤثر را دارد. Shell منو/remote را از panels می‌سازد و Manifest را به MFE می‌دهد. MFE از `SHRouteGuard`، `SHAction` یا `useSHPolicy(resource,action)` استفاده می‌کند.

```tsx
<SHAction resource="business:hr.employee" action="create">
  <Button>ایجاد کارمند</Button>
</SHAction>
```

Manifest فقط UX است. هر API باید در BFF/Gateway با Resource+Action check شود و فراخوانی دستی بدون مجوز `403` بگیرد.

## RBAC، ABAC و OpenFGA

Role یک درخت serialized نگه نمی‌دارد؛ grantهای مستقل دارد. Direct user/group grant نیز پشتیبانی می‌شود. Data Policy با `resource_id/action_id`، condition و obligations مانند organization/branch/ownership، field deny/masking، max rows و time window مستقل است. تصمیم نهایی فقط وقتی ALLOW است که رابطه OpenFGA و policy هر دو اجازه دهند.

## Validation

- کلید globally unique، normalized، دارای prefix نوع و پس از ایجاد immutable است.
- فقط هفت نوع پذیرفته می‌شود؛ self-parent و cycle رد می‌شوند.
- buttonهای عملیاتی و URL/method API به‌عنوان Resource رد می‌شوند.
- EXTERNAL_RESOURCE بدون provider/type/id رد می‌شود.
- Action به شکل رکورد مستقل و unique متصل می‌شود.
- sync idempotent و حذف آن deprecation است.
- FIELD/UI_COMPONENT باید boundary مستقل داشته باشند؛ این قاعده در UI توضیح داده و در review مالک دامنه تأیید می‌شود.

## نمونه HR

V27 هر هفت نوع را seed می‌کند. `business:hr.employee` دارای `view/create/update/delete/export` است و Superset با `external:hr.workforce-dashboard` binding دارد. منابع غلط `component:hr.employee.create-button` و grid عادی Deprecated و grant آن‌ها غیرفعال می‌شوند؛ intent دسترسی نمونه به Action صحیح منتقل می‌شود.

## چک نهایی معماری

- Create/Delete Employee button Resource نیست؛ create/delete Action روی `business:hr.employee` است.
- React component و FIELD فقط برای boundary مستقل Resource می‌شوند.
- API URL binding است، نه identity.
- Superset از EXTERNAL_RESOURCE پشتیبانی می‌شود.
- Data Policy از Resource جداست.
- Definition Manifest و Effective User Manifest API و DTO مستقل دارند.
- مخفی‌سازی frontend امنیت محسوب نمی‌شود؛ backend Resource+Action را enforce می‌کند.
- OpenFGA grantها را نگه می‌دارد و Catalog metadata را نگه می‌دارد.
