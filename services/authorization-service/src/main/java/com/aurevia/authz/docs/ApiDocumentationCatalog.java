package com.aurevia.authz.docs;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single, reviewable catalogue for Persian operation semantics. */
final class ApiDocumentationCatalog {
  private ApiDocumentationCatalog() {}

  private static final Map<String, String> TAGS = Map.ofEntries(
      Map.entry("AuthorizationController", "01 - تصمیم‌گیری مجوز و Manifest"),
      Map.entry("IdentitySyncController", "02 - همگام‌سازی هویت ورود"),
      Map.entry("AccessAdminController", "03 - منابع، کاربران و Grantها"),
      Map.entry("IdentityAdminController", "04 - نقش‌ها و انتساب نقش"),
      Map.entry("OuAccessAdminController", "05 - گروه دسترسی و OU سازمانی"),
      Map.entry("RegistryController", "06 - میکروفرانت‌ها"),
      Map.entry("UiPluginRegistryController", "07 - Artifact و منوی میکروفرانت"),
      Map.entry("ResourceManifestController", "08 - ثبت درخت منابع"),
      Map.entry("ProxyRouteAdminController", "09 - Route و مقصد سرویس"),
      Map.entry("RouteResolutionController", "10 - Resolve زمان اجرا"),
      Map.entry("OutboundAuthProfileController", "11 - احراز هویت سرویس Legacy"),
      Map.entry("OutboundConnectionController", "12 - Connectionهای خروجی"),
      Map.entry("SupersetInstanceController", "13 - محیط‌ها و نگاشت Superset"),
      Map.entry("SupersetProxyResolutionController", "14 - Resolve پراکسی Superset"),
      Map.entry("SupersetAssetController", "15 - گزارش، داشبورد و سطح دسترسی"),
      Map.entry("LogIngestionController", "16 - دریافت لاگ فنی"),
      Map.entry("LogQueryController", "17 - جست‌وجوی لاگ و Audit"),
      Map.entry("OperationsController", "18 - عملیات نگهداشت"));

  private static final Map<String, String> SUMMARIES = summaries();

  static Map<String, String> tags() { return TAGS; }

  static String summary(String key) {
    String value = SUMMARIES.get(key);
    if (value == null) {
      throw new IllegalStateException("Missing Persian OpenAPI documentation for " + key);
    }
    return value;
  }

  static String description(String key, String summary) {
    if (key.startsWith("AuthorizationController#")) {
      return summary + ". نتیجه بر پایه issuer + subject، کلید منبع و action محاسبه می‌شود و correlationId برای رهگیری الزامی است.";
    }
    if (key.startsWith("ProxyRouteAdminController#")) {
      return summary + ". این API فقط metadata مسیر را مدیریت می‌کند؛ credential و token در payload یا پاسخ پذیرفته نمی‌شوند. در عملیات تغییر، X-Actor و در به‌روزرسانی version الزامی است.";
    }
    if (key.startsWith("Outbound")) {
      return summary + ". فقط reference راز ثبت می‌شود و مقدار username/password یا client secret هرگز در دیتابیس رجیستری یا پاسخ API قرار نمی‌گیرد.";
    }
    if (key.startsWith("OuAccessAdminController#")) {
      return summary + ". OU فقط از فرایند login-sync/LDAP ایجاد یا به‌روزرسانی می‌شود و از پنل راهبری CRUD مستقیم ندارد.";
    }
    if (key.startsWith("Superset")) {
      return summary + ". محیط عمومی به نمونه عملیاتی نگاشت می‌شود و دسترسی asset برای subject و instance به‌صورت مستقل کنترل می‌گردد.";
    }
    return summary + ". این endpoint داخلی است؛ در پرتال توسعه با نشست BFF و در ارتباط سرویس‌به‌سرویس با Basic محلی یا mTLS تولید احراز هویت می‌شود.";
  }

  static String tagDescription(String controller) {
    return switch (controller) {
      case "AuthorizationController" -> "تصمیم واحد/دسته‌ای و Manifest نهایی مصرف‌شونده توسط Shell و میکروفرانت.";
      case "IdentitySyncController" -> "ثبت خودکار کاربر، گروه‌های Directory و OU معتبر در پایان Authorization Code Flow.";
      case "OuAccessAdminController" -> "ساخت گروه دسترسی، اتصال OU خواندنی و اعطای میکرو به گروه.";
      case "ProxyRouteAdminController" -> "تعریف مقصد، Route و قرارداد مجوز هر operation برای سرویس جدید یا Legacy.";
      case "OutboundAuthProfileController", "OutboundConnectionController" -> "metadata اتصال و reference راز برای دریافت و cache امن token سرویس Legacy.";
      case "SupersetInstanceController", "SupersetProxyResolutionController", "SupersetAssetController" -> "تعریف نمونه عمومی/عملیاتی Superset و کنترل طراحی یا مشاهده asset.";
      default -> "API داخلی راهبری و زمان اجرای موتور مجوزدهی Aurevia.";
    };
  }

  private static Map<String, String> summaries() {
    Map<String, String> m = new LinkedHashMap<>();
    put(m, "AuthorizationController", "check", "بررسی یک مجوز", "checkBatch", "بررسی دسته‌ای مجوزها", "manifest", "دریافت Manifest کامل دسترسی کاربر");
    put(m, "IdentitySyncController", "loginSync", "همگام‌سازی هویت، گروه Directory و OU پس از ورود");
    put(m, "AccessAdminController", "resourceTypes", "فهرست انواع مجاز منبع", "resources", "فهرست درختی منابع", "createResource", "ایجاد منبع", "updateResource", "ویرایش منبع با کنترل نسخه", "actions", "فهرست Actionها", "createAction", "ایجاد Action", "attachAction", "اتصال Action به منبع", "detachAction", "حذف اتصال Action از منبع", "users", "فهرست کاربران همگام‌شده", "createUser", "ایجاد کاربر راهبری", "userGrants", "فهرست مجوزهای مستقیم کاربر", "subjectGrants", "فهرست مجوزهای یک subject", "grant", "اعطای مجوز منبع", "revoke", "لغو Grant");
    put(m, "IdentityAdminController", "directoryGroups", "فهرست گروه‌های همگام‌شده Directory", "roles", "فهرست نقش‌ها", "roleAssignments", "فهرست انتساب نقش‌ها", "createRole", "تعریف نقش", "updateRole", "ویرایش نقش با کنترل نسخه", "updateRoleStatus", "فعال یا غیرفعال‌کردن نقش", "assignRole", "انتساب نقش به فرد یا گروه", "revokeRole", "لغو انتساب نقش");
    put(m, "OuAccessAdminController", "ous", "فهرست OUهای کشف‌شده از LDAP", "groups", "فهرست گروه‌های دسترسی", "rules", "قواعد OU یک گروه دسترسی", "members", "اعضای مؤثر گروه دسترسی", "jobs", "سوابق محاسبه عضویت", "createGroup", "ایجاد گروه دسترسی", "updateGroup", "ویرایش گروه دسترسی", "addRule", "افزودن شرط OU به گروه", "removeRule", "حذف شرط OU از گروه", "preview", "پیش‌نمایش اعضای حاصل از قواعد OU", "grants", "فهرست اتصال میکروها به گروه‌ها", "grant", "اعطای میکرو به گروه دسترسی", "revoke", "لغو دسترسی میکرو از گروه", "explain", "توضیح علت دسترسی‌های یک کاربر");
    put(m, "RegistryController", "panels", "فهرست میکروفرانت‌ها", "create", "تعریف میکروفرانت", "update", "ویرایش میکروفرانت", "archive", "آرشیو میکروفرانت", "audit", "مشاهده Audit رجیستری میکروفرانت");
    put(m, "UiPluginRegistryController", "artifacts", "فهرست نسخه‌های Artifact میکروفرانت", "publish", "انتشار و اعتبارسنجی Artifact", "activate", "فعال‌سازی نسخه Artifact", "menu", "ثبت override منوی میکروفرانت");
    put(m, "ResourceManifestController", "definition", "دریافت Manifest تعریف منابع یک برنامه", "sync", "همگام‌سازی idempotent درخت منابع برنامه");
    put(m, "ProxyRouteAdminController", "targets", "جست‌وجوی مقصدهای سرویس", "target", "دریافت جزئیات مقصد سرویس", "createTarget", "تعریف مقصد سرویس", "updateTarget", "ویرایش مقصد سرویس", "targetStatus", "تغییر وضعیت مقصد سرویس", "routes", "جست‌وجو و فیلتر Routeها", "route", "دریافت جزئیات Route", "validate", "اعتبارسنجی Route پیش از ذخیره", "preview", "پیش‌نمایش بازنویسی مسیر", "resolveTest", "آزمون انتخاب Route", "createRoute", "تعریف Route", "updateRoute", "ویرایش Route", "routeStatus", "تغییر وضعیت Route", "operations", "فهرست قراردادهای operation یک Route", "createOperation", "تعریف operation و مجوز لازم", "updateOperation", "ویرایش operation", "deleteOperation", "حذف operation", "match", "آزمون تطبیق operation");
    put(m, "RouteResolutionController", "resolve", "Resolve مسیر و روش HTTP در زمان اجرا");
    put(m, "OutboundAuthProfileController", "list", "فهرست پروفایل‌های احراز هویت خروجی", "one", "جزئیات پروفایل خروجی بدون راز", "runtime", "دریافت تنظیمات runtime پروفایل برای BFF", "create", "تعریف پروفایل احراز هویت Legacy", "update", "ویرایش پروفایل احراز هویت Legacy", "status", "تغییر وضعیت پروفایل خروجی", "usage", "نمایش Routeهای مصرف‌کننده پروفایل");
    put(m, "OutboundConnectionController", "list", "فهرست Connectionهای خروجی", "resolve", "Resolve امن Connection برای BFF", "create", "تعریف Connection خروجی", "update", "ویرایش Connection خروجی");
    put(m, "SupersetInstanceController", "instances", "فهرست نمونه‌های Superset", "create", "تعریف نمونه عمومی یا عملیاتی Superset", "update", "ویرایش نمونه Superset", "mappings", "فهرست نگاشت محیط عمومی به عملیاتی", "map", "ایجاد نگاشت Superset عمومی به عملیاتی");
    put(m, "SupersetProxyResolutionController", "resolve", "Resolve نمونه عملیاتی Superset از نام عمومی");
    put(m, "SupersetAssetController", "assets", "فهرست گزارش‌ها و داشبوردها", "accessOptions", "گزینه‌های معتبر سطح دسترسی Superset", "assetsForSubject", "فهرست assetهای قابل مشاهده برای subject", "accessForSubject", "بررسی runtime دسترسی به asset", "grants", "فهرست Grantهای یک asset", "create", "ثبت گزارش یا داشبورد", "grant", "اعطای سطح دسترسی asset به فرد یا گروه", "revoke", "لغو Grant یک asset");
    put(m, "LogIngestionController", "api", "ثبت لاگ فنی پالایش‌شده BFF");
    put(m, "LogQueryController", "api", "جست‌وجوی لاگ API", "apiDetail", "جزئیات یک لاگ API", "audit", "جست‌وجوی Audit Log", "auditDetail", "جزئیات یک Audit", "correlation", "رهگیری کامل با Correlation ID", "summary", "خلاصه آماری لاگ‌ها");
    put(m, "OperationsController", "reconcile", "بررسی یا ترمیم سازگاری OpenFGA");
    return Map.copyOf(m);
  }

  private static void put(Map<String, String> target, String controller, String... entries) {
    for (int index = 0; index < entries.length; index += 2) {
      target.put(controller + "#" + entries[index], entries[index + 1]);
    }
  }
}
