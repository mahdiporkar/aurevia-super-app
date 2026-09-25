# پلتفرم Dynamic Microfrontend

Shell تنها مالک `BrowserRouter` است و در startup فقط `GET /api/v1/me/manifest` را می‌خواند. پاسخ قدیمی manifest حفظ شده و فیلد نسخه‌دار `uiCatalog` به آن افزوده شده است. منو و root routeها از snapshot معتبر ساخته می‌شوند؛ `remoteEntry.js` تنها پس از navigation دانلود می‌شود.

هویت پایدار میکرو همان `panel` موجود است. جدول `ui_module_artifact` نسخه‌های immutable، Remote و Manifest معتبر را نگه می‌دارد؛ `active_artifact_id` pointer قابل rollback است و `ui_menu_override` تغییرات مدیر را بر اساس `menuId` معتبر ذخیره می‌کند. Authorization Service catalog را با مجوزهای کاربر فیلتر می‌کند و BFF همان نتیجه را به مرورگر می‌دهد. مقصد داخلی Proxy هرگز در Catalog نیست؛ Runtime فقط `/api/proxy/{serviceSlug}` را دریافت می‌کند.

## قرارداد و سازگاری

Contract جدید `1.0` یک React component به نام `App` و `HostRuntime` تایپ‌شده ارائه می‌کند. loader قرارداد قدیمی `1` را موقتاً برای میکروهای مهاجرت‌نکرده پشتیبانی می‌کند. React، React DOM، React Router و Ant Design singleton و نسخه‌شان از `package.json` گرفته می‌شود. ارتقای breaking قرارداد نیازمند Shell سازگار است.

## HR standalone

```powershell
$env:HR_BACKEND_URL='http://localhost:<hr-backend-port>'
npm run dev:mfe:hr
```

آدرس‌های `/`، `/personal/123`، `/departments` و `/positions` به کمک `historyApiFallback` مستقیم refresh می‌شوند. Standalone از session توسعه‌ای محلی و dev proxy استفاده می‌کند؛ هیچ bypass تولیدی اضافه نشده است.

## Registration نمونه HR

`moduleKey=hr` (نام موجود مخزن)، `routePrefix=/hr2`، `serviceSlug=hr`، `remoteName=aurevia_hr_ui_0_1_0`، `exposedModule=./plugin`. تغییر prefix فقط registration را تغییر می‌دهد؛ routeهای داخل HR relative هستند. انتشار نسخه جدید از بخش «نسخه‌ها» در پنل موجود انجام می‌شود: Manifest validate و snapshot می‌شود، سپس Activate pointer را جابه‌جا می‌کند. Rollback همان Activate کردن artifact معتبر قبلی است و Shell rebuild نمی‌شود.

## امنیت و عملیات

Remote URL فقط از Catalog مجاز همان کاربر load می‌شود، HTTPS روی صفحه HTTPS اجباری است، SRI در صورت تنظیم اعمال می‌شود، scope collision رد می‌شود و loader timeout، deduplication، cache، retry و Error Boundary دارد. Typed Runtime sandbox امنیتی نیست؛ Remoteها باید داخلی و مورد اعتماد باشند.

## Release هماهنگ Artifact و Resource Manifest

نسخهٔ UI و نسخهٔ authorization یک Release منطقی واحد هستند. هر Panel دو pointer دارد: `active_artifact_id` برای MF و `active_resource_manifest_id` برای revision فعال کاتالوگ. هر `ui_module_artifact` از طریق `resource_manifest_id` به revision خود bind می‌شود و این انتساب immutable است؛ بنابراین حالت نامعتبر «MF=v1 و Resource Manifest=v2» در سطح schema ناممکن است.

revisionهای `resource_manifest_import` پس از PUBLISHED شدن immutable هستند و trigger پایگاه‌داده هر تغییر payload/checksum را رد می‌کند. Publish یک DRAFT را materialize و PUBLISHED می‌کند؛ Activate/Rollback همان revision منتشرشدهٔ قبلی را دوباره materialize می‌کند بدون آنکه تاریخچه ویرایش یا clone شود. هر دو مسیر از یک عملیات مشترک استفاده می‌کنند، پس رفتارشان یکسان است.

Materialization کاتالوگ را دقیقاً با revision هدف منطبق می‌کند: resourceهای غایب DEPRECATED می‌شوند، `resource_action` جایگزین می‌شود، `parent_id` و binding خارجی به مقدار همان revision بازمی‌گردند. تعریف‌های دستی (`source='ADMIN'`) دست‌نخورده می‌مانند. کل عملیات در یک transaction با قفل روی سطر panel انجام می‌شود؛ شکست materialization هیچ pointer یا رویدادی باقی نمی‌گذارد.

همگام‌سازی OpenFGA با diff گرفتن از snapshot tupleهای کاتالوگ قبل و بعد از materialization و صف‌کردن رویدادهای Outbox در همان transaction انجام می‌شود، پس حذف parent tuple برای resourceهای حذف‌شده تضمین شده است. هنگام پروجکت‌شدن، هر رویداد دوباره با snapshot فعلی کاتالوگ زیر همان قفل panel سنجیده می‌شود؛ بنابراین یک رویداد قدیمی نمی‌تواند tuple حذف‌شده را احیا کند. تا وقتی رویدادی برای یک panel pending است، `effective_resource_catalog` برای آن panel fail-closed است.

### مهاجرت و استقرار

مهاجرت `V81` ستون‌ها و قیدها را اضافه می‌کند ولی هیچ pointerی را حدس نمی‌زند: تاریخچهٔ قبلی نسخهٔ UI و نسخهٔ resource را مستقل نگه می‌داشت و نه `max(version)` و نه آخرین publish اثبات نمی‌کند کدام کاتالوگ به کدام artifact تعلق دارد. بنابراین Panelهایی که تاریخچهٔ PUBLISHED دارند تا فعال‌سازی صریح یک جفت معتبر، fail-closed می‌مانند.

ترتیب استقرار:

1. اجرای مهاجرت `V81`.
2. استقرار سرویس.
3. برای هر Panel دارای تاریخچهٔ manifest، یک بار Activate صریح جفت artifact/revision درست از پنل مدیریت (یا `POST /panels/{panelId}/artifacts/{artifactId}/activate?version=...&resourceManifestId=...`). تا انجام این کار، کاتالوگ آن Panel غیرفعال است.
4. اجرای یک بار reconciliation کامل OpenFGA برای هم‌ترازی وضعیت گراف با کاتالوگ materialize‌شده.

Panelهای بدون تاریخچهٔ PUBLISHED نیازی به اقدام ندارند.
