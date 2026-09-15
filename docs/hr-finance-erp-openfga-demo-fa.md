# سناریوی HR و Finance ERP با OpenFGA

دو سرویس نمونه از مسیرهای same-origin زیر ارائه می‌شوند:

- HR: `/hr-micro/api/v1/employees`، `departments` و `positions`
- Finance: `/finance-micro/api/v1/invoices`، `budgets` و `payments`

هر درخواست عملیاتی ابتدا به `RouteOperation` نگاشت و با user/resource/action در OpenFGA بررسی می‌شود.

## درخت منابع

Migrationهای V25 و V26 برای هر ERP این سطوح را می‌سازند:

```text
APPLICATION
└── MODULE
    ├── PAGE
    │   └── UI_COMPONENT (button / grid / field)
    ├── EXTERNAL_RESOURCE (Superset dashboard)
    └── DATA_RESOURCE
        └── DATA_GOVERNANCE_RESOURCE
```

انواع قدیمی `API_RESOURCE` و `BUSINESS_RESOURCE` برای سازگاری routeهای REST حفظ شده‌اند.

در «استودیوی دسترسی OpenFGA» می‌توان resource را ساخت، ویرایش یا با تغییر parent جابه‌جا کرد. cycle و parent نامعتبر رد می‌شود. همان صفحه grant مستقیم USER/GROUP/ROLE به resource را مدیریت می‌کند. بخش «گروه‌ها و نقش‌ها» نیز USER→ROLE و GROUP→ROLE را مدیریت می‌کند. همه mutationها از outbox به OpenFGA می‌روند.

## دموی UI

در MFE منابع انسانی:

- `component:hr.employee.create-button:view`: دکمه ایجاد را enable/disable می‌کند.
- `component:hr.employee.grid:view`: گرید کارکنان را show/hide می‌کند.
- `component:hr.employee.salary-field:view`: فیلد حقوق را editable/read-only می‌کند.

این کنترل‌ها فقط UX هستند؛ API همچنان مستقل و اجباری توسط OpenFGA کنترل می‌شود. نقش `hr-viewer` این مجوزها را دارد و به `hr-user` متصل شده است. کاربر `viewer` روی فیلد حقوق `DENY` می‌گیرد.
