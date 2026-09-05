package com.aurevia.authz.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Adds Persian field/parameter semantics without coupling transport DTOs to Swagger annotations. */
final class ApiSchemaDocumentation {
  private ApiSchemaDocumentation() {}

  private static final Map<String, String> FIELDS = Map.ofEntries(
      Map.entry("id", "شناسه یکتای رکورد از نوع UUID."),
      Map.entry("code", "کد پایدار، یکتا و مناسب استفاده سیستمی؛ پس از استفاده در integration تغییر نکند."),
      Map.entry("name", "نام قابل نمایش برای راهبر."),
      Map.entry("namefa", "عنوان فارسی قابل نمایش در پنل و Manifest."),
      Map.entry("nameen", "عنوان انگلیسی قابل نمایش و fallback رابط کاربری."),
      Map.entry("description", "توضیح عملیاتی برای راهبر؛ credential یا داده حساس در آن وارد نشود."),
      Map.entry("version", "نسخه optimistic locking؛ مقدار آخرین GET را بدون تغییر ارسال کنید."),
      Map.entry("active", "فعال‌بودن رکورد در resolve و runtime؛ false رکورد را بدون حذف تاریخچه غیرفعال می‌کند."),
      Map.entry("resourcekey", "کلید canonical و پایدار منبع؛ باید با Resource Manifest و check مجوز یکسان باشد."),
      Map.entry("type", "نوع منبع در درخت مجوزدهی؛ مانند APPLICATION، PAGE، UI_COMPONENT یا API_RESOURCE."),
      Map.entry("parentid", "شناسه والد برای تشکیل درخت منابع؛ برای ریشه می‌تواند خالی باشد."),
      Map.entry("ownerdomain", "دامنه کسب‌وکاری مالک منبع، مانند finance یا hr."),
      Map.entry("classification", "رده‌بندی حساسیت داده یا منبع، مانند INTERNAL یا CONFIDENTIAL."),
      Map.entry("externalsystem", "نام سامانه خارجی مالک شناسه منبع."),
      Map.entry("externaltype", "نوع منبع در سامانه خارجی."),
      Map.entry("externalid", "شناسه پایدار صادرشده توسط Directory یا سامانه خارجی؛ عنوان نمایشی نیست."),
      Map.entry("source", "منشأ ثبت داده؛ برای CRUD راهبری یکی از APPLICATION_MANIFEST، ADMIN، EXTERNAL_SYNC یا SYSTEM."),
      Map.entry("metadata", "metadata توسعه‌پذیر و غیرحساس؛ برای منطق امنیتی اصلی به آن اتکا نشود."),
      Map.entry("actionkey", "عمل کسب‌وکاری ثبت‌شده برای منبع، مانند view، create، approve یا manage."),
      Map.entry("userid", "شناسه داخلی کاربر در دیتابیس authorization-service."),
      Map.entry("subjecttype", "نوع دریافت‌کننده مجوز: USER، GROUP، ACCESS_GROUP یا ROLE."),
      Map.entry("subjectid", "شناسه subject در نوع انتخاب‌شده؛ برای هویت runtime همراه issuer تفسیر می‌شود."),
      Map.entry("subject", "شناسه تغییرناپذیر `sub` صادرشده توسط Identity Provider."),
      Map.entry("issuer", "issuer دقیق Identity Provider؛ بخشی از کلید یکتای هویت و حساس به تفاوت رشته است."),
      Map.entry("username", "نام کاربری نمایشی/جست‌وجویی؛ مبنای یکتایی و تصمیم دسترسی نیست."),
      Map.entry("displayname", "نام کامل قابل نمایش کاربر یا گروه."),
      Map.entry("email", "ایمیل همگام‌شده؛ می‌تواند خالی باشد و کلید هویت محسوب نمی‌شود."),
      Map.entry("relation", "relation مشتق‌شده از semantics منبع/action؛ client نباید آن را جعل کند."),
      Map.entry("expiresat", "زمان اختیاری انقضای Grant یا Role Assignment با قالب ISO-8601 UTC."),
      Map.entry("resource", "شیء canonical OpenFGA مورد ارزیابی، مانند `resource:page/finance.payments`."),
      Map.entry("action", "عمل کسب‌وکاری مورد ارزیابی؛ استفاده از permission محاسباتی `can_*` ممنوع است."),
      Map.entry("context", "زمینه غیرحساس تصمیم مانند branch یا IP؛ policy صریح باید مصرف آن را تعریف کند."),
      Map.entry("correlationid", "شناسه یکتای رهگیری درخواست بین BFF، Authorization، Redis/OpenFGA و مقصد."),
      Map.entry("groups", "گروه‌های معتبر استخراج‌شده از claim/Directory در لحظه ورود."),
      Map.entry("distinguishedname", "DN کامل کاربر از LDAP؛ برای parse و Audit همگام‌سازی OU."),
      Map.entry("ouexternalid", "شناسه پایدار OU سازمانی استخراج‌شده از LDAP؛ از فرم ادمین ساخته نمی‌شود."),
      Map.entry("directoryexternalid", "شناسه immutable شیء Directory مانند objectGUID."),
      Map.entry("attributes", "ویژگی‌های allowlist‌شده Directory؛ password یا token نباید ارسال شود."),
      Map.entry("rolekey", "کلید یکتای نقش مدیریتی/کسب‌وکاری."),
      Map.entry("rulecombiner", "روش ترکیب قواعد OU: ANY_OF برای اجتماع یا ALL_OF برای اشتراک."),
      Map.entry("ouid", "شناسه OU کشف‌شده و فقط‌خواندنی در رجیستری Directory."),
      Map.entry("matchmode", "EXACT فقط همان OU و SUBTREE همان OU به‌همراه زیرشاخه‌ها را تطبیق می‌دهد."),
      Map.entry("applicationid", "شناسه منبع APPLICATION یا Panel مقصد Grant."),
      Map.entry("accessgroupid", "شناسه گروه دسترسی مبتنی بر قواعد OU."),
      Map.entry("connectionref", "reference پایدار با پیشوند `connection://`؛ URL یا credential خام نیست."),
      Map.entry("baseurl", "origin کامل مقصد شامل scheme، host و port؛ user-info، query و fragment مجاز نیست."),
      Map.entry("tlsrequired", "اگر true باشد baseUrl باید HTTPS و trust policy معتبر داشته باشد."),
      Map.entry("authmode", "روش احراز هویت خروجی/‌Superset؛ مقادیر معتبر به نوع schema وابسته‌اند."),
      Map.entry("tokenconnectionref", "reference اتصال endpoint دریافت token Legacy."),
      Map.entry("tokenendpointpath", "مسیر نسبی endpoint token؛ URL کامل، query، fragment و `..` مجاز نیست."),
      Map.entry("requestformat", "adapter ساخت درخواست token: FORM_URLENCODED، JSON، HTTP_BASIC یا OAUTH_CLIENT_CREDENTIALS."),
      Map.entry("credentialsecretref", "reference با پیشوند `secret://`؛ مقدار واقعی راز خارج از رجیستری نگهداری می‌شود."),
      Map.entry("scope", "scopeهای درخواستی سرویس مقصد؛ فقط حداقل دسترسی لازم."),
      Map.entry("audience", "audience مورد انتظار token در صورت پشتیبانی endpoint."),
      Map.entry("tokenresponsepointer", "JSON Pointer محل access token در پاسخ، مانند `/access_token`."),
      Map.entry("expiresinresponsepointer", "JSON Pointer مدت اعتبار token، مانند `/expires_in`."),
      Map.entry("tokentyperesponsepointer", "JSON Pointer نوع token، مانند `/token_type`."),
      Map.entry("authorizationscheme", "scheme header Authorization مقصد، معمولاً Bearer."),
      Map.entry("credentialtransport", "FORWARD_USER_TOKEN از USER_AUTHORIZATION_HEADER و Legacy از INTERNAL_LEGACY_HEADER استفاده می‌کند."),
      Map.entry("expiryskewseconds", "حاشیه انقضا برای refresh پیش از پایان اعتبار token؛ بین ۵ تا ۶۰۰ ثانیه."),
      Map.entry("connecttimeoutms", "حداکثر زمان برقراری اتصال TCP/TLS بر حسب میلی‌ثانیه."),
      Map.entry("responsetimeoutms", "حداکثر زمان انتظار پاسخ کامل مقصد بر حسب میلی‌ثانیه."),
      Map.entry("maxtokenresponsesize", "حداکثر اندازه مجاز پاسخ endpoint token برای جلوگیری از مصرف حافظه."),
      Map.entry("slug", "نام URL-safe میکروفرانت در Shell."),
      Map.entry("serviceslug", "بخش اول مسیر runtime برای resolve Route پویا."),
      Map.entry("remotename", "نام container در Module Federation؛ باید identifier معتبر JavaScript باشد."),
      Map.entry("defaultrouteid", "شناسه route پیش‌فرض تعریف‌شده در manifest میکروفرانت."),
      Map.entry("remoteentry", "URL فایل remoteEntry.js از origin موجود در allowlist."),
      Map.entry("remoteentryurl", "URL فایل remoteEntry.js از origin موجود در allowlist."),
      Map.entry("exposedmodule", "ماژول expose‌شده Module Federation، مانند `./App`."),
      Map.entry("routebasepath", "مسیر پایه ناوبری میکروفرانت در Shell."),
      Map.entry("semanticversion", "نسخه SemVer میکروفرانت، مانند 1.4.0."),
      Map.entry("artifactversion", "نسخه immutable Artifact با قالب SemVer."),
      Map.entry("contractversion", "نسخه قرارداد Shell/MFE؛ در وضعیت فعلی 1.0."),
      Map.entry("integrity", "SRI با sha256/sha384/sha512 برای اعتبارسنجی بایت‌های remoteEntry."),
      Map.entry("sortorder", "اولویت نمایش صعودی در منوی Shell."),
      Map.entry("gatewaybaseurl", "origin ثابت Operation Gateway؛ مقصد دلخواه client نیست."),
      Map.entry("upstreambasepath", "مسیر پایه سرویس پشت Gateway."),
      Map.entry("environment", "نام محیط مقصد برای راهبری و Audit، مانند OPERATION."),
      Map.entry("tlsprofileref", "reference اختیاری policy TLS/mTLS؛ مقدار کلید یا certificate نیست."),
      Map.entry("secretref", "reference اختیاری secret مقصد؛ مقدار secret در API پذیرفته نمی‌شود."),
      Map.entry("healthcheckpath", "مسیر نسبی و کم‌هزینه health check مقصد."),
      Map.entry("maxresponsesize", "سقف بایت پاسخ runtime برای جلوگیری از مصرف کنترل‌نشده حافظه."),
      Map.entry("outboundauthprofileid", "پروفایل احراز هویت خروجی؛ null یعنی بدون پروفایل اختصاصی."),
      Map.entry("panelid", "شناسه میکروفرانت مالک Route یا Artifact."),
      Map.entry("servicetargetid", "شناسه مقصد ثابت و تأییدشده Route."),
      Map.entry("pathprefix", "prefix عمومی و بدون ابهام که BFF برای انتخاب Route تطبیق می‌دهد."),
      Map.entry("stripprefix", "تعداد segmentهای حذف‌شونده پیش از ساخت مسیر upstream."),
      Map.entry("rewritepattern", "regex اختیاری و کنترل‌شده برای بازنویسی مسیر پس از strip."),
      Map.entry("rewritereplacement", "replacement متناظر rewritePattern."),
      Map.entry("priority", "اولویت انتخاب Route؛ عدد بزرگ‌تر زودتر بررسی می‌شود."),
      Map.entry("allowedmethods", "روش‌های HTTP مجاز Route؛ روش‌های دیگر پیش از forward رد می‌شوند."),
      Map.entry("preservehost", "در صورت false، Host از مقصد امن WebClient گرفته می‌شود."),
      Map.entry("retryenabled", "retry فقط برای روش‌های idempotent مانند GET/HEAD/OPTIONS مجاز است."),
      Map.entry("maxretries", "تعداد retry محدود؛ حداکثر ۳ و برای mutation باید صفر باشد."),
      Map.entry("httpmethod", "روش HTTP قرارداد operation به حروف بزرگ."),
      Map.entry("pathpattern", "الگوی نسبی operation؛ segment پارامتری مانند `{id}` مجاز است."),
      Map.entry("authorizationrequired", "اگر true باشد check مجوز پیش از هر درخواست الزامی است."),
      Map.entry("datapolicykey", "کلید اختیاری policy داده برای obligationهای سطح رکورد."),
      Map.entry("maxbodybytes", "حداکثر اندازه body ورودی operation؛ صفر یعنی body پذیرفته نمی‌شود."),
      Map.entry("routeid", "شناسه Route مورد آزمون یا مدیریت."),
      Map.entry("path", "مسیر URL برای resolve، preview یا check runtime."),
      Map.entry("method", "روش HTTP درخواست runtime، مانند GET یا POST."),
      Map.entry("application", "کلید برنامه مالک Resource Manifest."),
      Map.entry("manifestversion", "نسخه ناشر manifest برای Audit و idempotency."),
      Map.entry("resources", "لیست کامل منابع اعلام‌شده توسط همان نسخه برنامه."),
      Map.entry("status", "وضعیت چرخه عمر رکورد یا فیلتر جست‌وجو."),
      Map.entry("provider", "نام provider منبع خارجی در صورت وجود."),
      Map.entry("assettype", "نوع دارایی Superset: DASHBOARD یا CHART."),
      Map.entry("title", "عنوان قابل نمایش گزارش، داشبورد یا منو."),
      Map.entry("urlpath", "مسیر same-origin دارایی Superset؛ URL مقصد عملیاتی نیست."),
      Map.entry("ownerexternalid", "شناسه خارجی طراح/مالک asset در صورت وجود."),
      Map.entry("published", "آماده‌بودن asset برای نمایش به دریافت‌کنندگان Grant."),
      Map.entry("instancecode", "کد نمونه عملیاتی Superset مالک asset."),
      Map.entry("level", "سطح دسترسی asset: VIEW، EDIT یا MANAGE."),
      Map.entry("zone", "ناحیه Superset: PUBLIC برای ورودی و OPERATION برای مقصد واقعی."),
      Map.entry("publicinstanceid", "شناسه نمونه Superset در ناحیه PUBLIC."),
      Map.entry("operationinstanceid", "شناسه نمونه Superset در ناحیه OPERATION."),
      Map.entry("publicpath", "prefix عمومی نمایش Superset در Super App."),
      Map.entry("isdefault", "نگاشت پیش‌فرض هنگام مشخص‌نشدن instance عمومی."),
      Map.entry("manifest", "snapshot JSON قرارداد UI شامل schemaVersion، moduleKey، routes و menus."),
      Map.entry("menuid", "شناسه منوی اعلام‌شده در Artifact فعال."),
      Map.entry("icon", "نام icon از مجموعه مورد تأیید Shell؛ کد HTML یا URL نیست."),
      Map.entry("order", "ترتیب نمایش منو."),
      Map.entry("hidden", "پنهان‌سازی راهبری منو بدون حذف تعریف ناشر."));

  static void apply(OpenAPI openApi) {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
    openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
      if (schema.getProperties() == null) return;
      schema.getProperties().forEach((propertyName, property) -> {
        String name = String.valueOf(propertyName);
        Schema<?> field = (Schema<?>) property;
        if (field.getDescription() == null || field.getDescription().isBlank()) {
          field.setDescription(description(name));
        }
        applyEnums(schemaName, name, field);
      });
    });
  }

  static void documentParameters(Operation operation) {
    if (operation.getParameters() == null) return;
    for (Parameter parameter : operation.getParameters()) {
      if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
        parameter.setDescription(parameterDescription(parameter.getName()));
      }
      if (parameter.getExample() == null) parameter.setExample(parameterExample(parameter.getName()));
    }
  }

  private static String description(String property) {
    return FIELDS.getOrDefault(normalize(property),
        "مقدار فیلد «" + property + "» مطابق schema و محدودیت‌های نمایش‌داده‌شده در قرارداد.");
  }

  private static String parameterDescription(String name) {
    String known = FIELDS.get(normalize(name));
    if (known != null) return known;
    return switch (normalize(name)) {
      case "xactor" -> "نام نمایشی عامل تغییر؛ BFF آن را از نشست معتبر تولید می‌کند.";
      case "xactorissuer" -> "issuer عامل؛ BFF از نشست معتبر می‌سازد و client نباید جعل کند.";
      case "xactorsubject" -> "subject تغییرناپذیر عامل؛ BFF از نشست معتبر می‌سازد.";
      case "search" -> "عبارت اختیاری جست‌وجو؛ رشته خالی یعنی بدون فیلتر متنی.";
      case "limit" -> "حداکثر تعداد رکورد بازگشتی.";
      case "page" -> "شماره صفحه با مبدأ صفر.";
      case "size" -> "اندازه صفحه در محدوده مجاز endpoint.";
      case "publicinstance" -> "کد نمونه عمومی Superset که باید به نمونه عملیاتی فعال نگاشت شود.";
      case "instance" -> "کد نمونه عملیاتی Superset؛ در صورت حذف مقدار پیش‌فرض endpoint اعمال می‌شود.";
      case "query" -> "query string پالایش‌شده برای resolve دسترسی runtime.";
      default -> "پارامتر «" + name + "» مطابق قرارداد این عملیات.";
    };
  }

  private static Object parameterExample(String name) {
    return switch (normalize(name)) {
      case "id", "panelid", "routeid", "artifactid", "assetid", "grantid", "subjectid",
          "resourceid", "actionid", "groupid", "ruleid", "targetid" ->
          "3691d12f-253f-4bce-924c-e23dc8ff6b37";
      case "version" -> 0;
      case "issuer" -> "http://localhost:8180/realms/aurevia";
      case "subject" -> "8e3a7fd6-demo-user";
      case "search" -> "finance";
      case "path" -> "/finance-micro/api/payments/42";
      case "method" -> "GET";
      case "publicinstance" -> "public-default";
      case "instance" -> "operation-default";
      case "limit" -> 100;
      case "page" -> 0;
      case "size" -> 50;
      case "xactor" -> "administrator";
      default -> null;
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void applyEnums(String schemaName, String propertyName, Schema field) {
    String property = normalize(propertyName);
    if (property.equals("rulecombiner")) field.setEnum(List.of("ANY_OF", "ALL_OF"));
    else if (property.equals("matchmode")) field.setEnum(List.of("EXACT", "SUBTREE"));
    else if (property.equals("zone")) field.setEnum(List.of("PUBLIC", "OPERATION"));
    else if (property.equals("assettype")) field.setEnum(List.of("DASHBOARD", "CHART"));
    else if (property.equals("level")) field.setEnum(List.of("VIEW", "EDIT", "MANAGE"));
    else if (property.equals("subjecttype") && schemaName.contains("RoleAssignment"))
      field.setEnum(List.of("USER", "DIRECTORY_GROUP", "ACCESS_GROUP"));
    else if (property.equals("subjecttype"))
      field.setEnum(List.of("USER", "GROUP", "ACCESS_GROUP", "ROLE"));
    else if (property.equals("type") && (schemaName.contains("ResourceRequest")
        || schemaName.contains("ResourceDefinition"))) field.setEnum(List.of(
            "APPLICATION", "MODULE", "PAGE", "UI_COMPONENT", "FIELD", "BUSINESS_RESOURCE",
            "EXTERNAL_RESOURCE", "API_RESOURCE", "DATA_RESOURCE", "DATA_GOVERNANCE_RESOURCE"));
    else if (property.equals("source") && schemaName.contains("ResourceRequest"))
      field.setEnum(List.of("APPLICATION_MANIFEST", "ADMIN", "EXTERNAL_SYNC", "SYSTEM"));
    else if (property.equals("requestformat")) field.setEnum(List.of(
        "FORM_URLENCODED", "JSON", "HTTP_BASIC", "OAUTH_CLIENT_CREDENTIALS"));
    else if (property.equals("credentialtransport")) field.setEnum(List.of(
        "USER_AUTHORIZATION_HEADER", "INTERNAL_LEGACY_HEADER"));
    else if (property.equals("authmode") && schemaName.contains("Profile"))
      field.setEnum(List.of("FORWARD_USER_TOKEN", "LEGACY_SERVICE_TOKEN"));
    else if (property.equals("authmode") && schemaName.contains("Instance"))
      field.setEnum(List.of("REMOTE_USER", "OIDC", "GUEST_TOKEN"));
    else if (property.equals("allowedmethods") && field instanceof ArraySchema array
        && array.getItems() != null) ((Schema) array.getItems()).setEnum(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replace("_", "").replace("-", "")
        .toLowerCase(Locale.ROOT);
  }
}
