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
