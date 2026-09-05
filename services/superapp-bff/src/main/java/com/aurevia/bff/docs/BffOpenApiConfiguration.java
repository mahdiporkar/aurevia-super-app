package com.aurevia.bff.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Persian OpenAPI contract for the browser-facing BFF adapter. */
@Configuration
public class BffOpenApiConfiguration {
  private static final Map<String, String> TAGS = Map.of(
      "CsrfController", "01 - نشست و CSRF",
      "MeController", "02 - کاربر جاری و Manifest",
      "ReportsController", "03 - گزارش‌های قابل مشاهده",
      "AdminProxyController", "04 - راهبری و آزمون اتصال",
      "OperationalProxyController", "05 - پراکسی سرویس‌های عملیاتی",
      "OperationSupersetProxyController", "06 - پراکسی امن Superset");

  private static final Map<String, String> SUMMARIES = summaries();

  static String summary(String key) { return SUMMARIES.get(key); }

  @Bean
  OpenAPI bffOpenApi() {
    var error = new ObjectSchema()
        .addProperty("code", new StringSchema().example("ACCESS_DENIED"))
        .addProperty("message", new StringSchema().example("دسترسی به این عملیات مجاز نیست"))
        .addProperty("correlationId", new StringSchema()
            .example("5e4ddf32-1e7e-4e20-a9f3-64de1c938f97"));
    var session = new SecurityScheme().type(SecurityScheme.Type.APIKEY)
        .in(SecurityScheme.In.COOKIE).name("AUREVIA_SESSION")
        .description("شناسه opaque نشست HttpOnly/Secure؛ حاوی access token یا refresh token نیست.");
    var csrf = new SecurityScheme().type(SecurityScheme.Type.APIKEY)
        .in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")
        .description("برای POST/PUT/PATCH/DELETE ابتدا GET /api/v1/csrf فراخوانی و مقدار token در این header ارسال شود.");
    return new OpenAPI()
        .info(new Info().title("API درگاه امن سوپر اپ Aurevia")
            .version("1.0.0")
            .description("قرارداد مرورگر با الگوی BFF. مرورگر فقط cookie نشست را نگه می‌دارد؛ BFF توکن Keycloak یا Legacy را از vault رمز‌شده Redis دریافت و فقط به مقصد مجاز متصل می‌کند. Swagger در profile=prod غیرفعال است.")
            .contact(new Contact().name("تیم پلتفرم Aurevia")))
        .servers(List.of(new Server().url("/").description("همان origin سوپر اپ؛ نمونه local: http://localhost:8443")))
        .components(new Components().addSchemas("ApiError", error)
            .addSecuritySchemes("browserSession", session)
            .addSecuritySchemes("csrfToken", csrf))
        .addSecurityItem(new SecurityRequirement().addList("browserSession"))
        .tags(TAGS.entrySet().stream().map(entry -> new Tag().name(entry.getValue())
            .description(tagDescription(entry.getKey()))).toList());
  }

  @Bean
  OperationCustomizer bffOperationDocumentation() {
    return (operation, handlerMethod) -> {
      if (!handlerMethod.getBeanType().getPackageName().startsWith("com.aurevia.bff.api")) {
        return operation;
      }
      String controller = handlerMethod.getBeanType().getSimpleName();
      String method = handlerMethod.getMethod().getName();
      String key = controller + "#" + method;
      String summary = SUMMARIES.get(key);
      if (summary == null) {
        throw new IllegalStateException("Missing Persian OpenAPI documentation for " + key);
      }
      operation.setTags(List.of(TAGS.getOrDefault(controller, "سایر APIهای BFF")));
      operation.setSummary(summary);
      operation.setDescription(description(key, summary));
      documentParameters(operation);
      if (isMutation(handlerMethod.getMethod())) {
        operation.setSecurity(List.of(new SecurityRequirement()
            .addList("browserSession").addList("csrfToken")));
      }
      if (operation.getResponses() != null) {
        operation.getResponses().putIfAbsent("401", errorResponse("نشست وجود ندارد یا منقضی شده است"));
        operation.getResponses().putIfAbsent("403", errorResponse("مجوز منبع/action یا CSRF معتبر نیست"));
        operation.getResponses().putIfAbsent("502", errorResponse("پاسخ مقصد عملیاتی یا سرویس مجوزدهی نامعتبر است"));
        operation.getResponses().putIfAbsent("504", errorResponse("مهلت پاسخ مقصد پایان یافته است"));
        addResponseExample(operation, key);
      }
      return operation;
    };
  }

  private static void documentParameters(io.swagger.v3.oas.models.Operation operation) {
    if (operation.getParameters() == null) return;
    operation.getParameters().forEach(parameter -> {
      String name = parameter.getName();
      if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
        parameter.setDescription(switch (name) {
          case "id" -> "شناسه UUID پروفایل احراز هویت خروجی یا مقصد سرویس.";
          case "instance", "publicInstance" -> "کد عمومی Superset؛ قبل از forward به نمونه عملیاتی resolve می‌شود.";
          case "panelSlug" -> "slug میکروفرانت که Route فعال آن در رجیستری resolve می‌شود.";
          case "path" -> "ادامه مسیر مقصد؛ BFF آن را normalize کرده و با operation ثبت‌شده تطبیق می‌دهد.";
          default -> "پارامتر «" + name + "» مطابق قرارداد endpoint.";
        });
      }
      if (parameter.getExample() == null) {
        parameter.setExample(switch (name) {
          case "id" -> "95dc9e52-7ca5-4ad9-858d-a2d78ae1e5bd";
          case "instance", "publicInstance" -> "public-default";
          case "panelSlug" -> "finance-micro";
          case "path" -> "/api/payments/42";
          default -> null;
        });
      }
    });
  }

  private static boolean isMutation(java.lang.reflect.Method method) {
    return method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class);
  }

  private static ApiResponse errorResponse(String description) {
    var media = new io.swagger.v3.oas.models.media.MediaType()
        .schema(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ApiError"));
    return new ApiResponse().description(description)
        .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json", media));
  }

  private static void addResponseExample(io.swagger.v3.oas.models.Operation operation, String key) {
    Object example = switch (key) {
      case "CsrfController#csrf" -> map("headerName", "X-CSRF-TOKEN", "parameterName", "_csrf",
          "token", "f6e84940-demo-csrf-value");
      case "MeController#me" -> map("issuer", "http://localhost:8180/realms/aurevia",
          "subject", "8e3a7fd6-demo-user", "username", "ali.rezaei", "groups", List.of());
      case "MeController#manifest" -> map("manifestType", "USER_ACCESS_MANIFEST",
          "version", "W/\"manifest-42\"", "panels", List.of(map("code", "finance", "slug", "finance")),
          "permissions", map("page:finance.payments", List.of("view", "approve")));
      case "ReportsController#reports" -> List.of(map("externalId", "dashboard:42",
          "assetType", "DASHBOARD", "title", "داشبورد فروش روزانه", "level", "VIEWER"));
      case "AdminProxyController#tokenTest" -> map("success", true, "latencyMs", 126,
          "code", "TOKEN_ACQUIRED");
      case "AdminProxyController#connectionTest" -> map("success", true, "latencyMs", 41,
          "code", "CONNECTION_APPROVED");
      case "AdminProxyController#invalidate" -> map("success", true, "code", "CACHE_INVALIDATED");
      case "AdminProxyController#cacheStatus" -> map("cached", true);
      default -> null;
    };
    if (example == null) return;
    ApiResponse success = operation.getResponses().get("200");
    if (success == null || success.getContent() == null) return;
    success.getContent().values().forEach(media -> media.addExamples("پاسخ موفق",
        new io.swagger.v3.oas.models.examples.Example().value(example)));
  }

  private static String description(String key, String summary) {
    if (key.startsWith("OperationalProxyController#")) {
      return summary + ". BFF پس از resolve مسیر و check مجوز، توکن مناسب را فقط سمت سرور از Redis می‌خواند؛ headerهای امنیتی ورودی پاک‌سازی و پاسخ مقصد بدون افشای token بازگردانده می‌شود.";
    }
    if (key.startsWith("OperationSupersetProxyController#")) {
      return summary + ". نگاشت publicInstance به operationInstance و سطح دسترسی گزارش/داشبورد پیش از forward بررسی می‌شود. مرورگر هیچ credential عملیاتی دریافت نمی‌کند.";
    }
    if (key.startsWith("AdminProxyController#proxy")) {
      return summary + ". path ادامه مسیر /internal/v1/registry است. DTO، validation و پاسخ اصلی authorization-service بدون تغییر معنایی عبور می‌کند؛ برای mutation مقدار CSRF لازم است.";
    }
    return summary + ". درخواست به نشست معتبر نیاز دارد. عملیات تغییردهنده علاوه بر cookie نشست، header ضد-CSRF می‌خواهند.";
  }

  private static String tagDescription(String controller) {
    return switch (controller) {
      case "CsrfController" -> "دریافت token ضد-CSRF مرتبط با نشست جاری؛ این مقدار access token نیست.";
      case "MeController" -> "هویت حداقلی نشست و Manifest مجوزهای قابل مصرف در Shell و میکروفرانت.";
      case "ReportsController" -> "فهرست فیلترشده گزارش‌ها و داشبوردهای Superset برای کاربر جاری.";
      case "AdminProxyController" -> "درگاه راهبری و آزمون امن connection/token بدون نمایش مقدار credential یا token.";
      case "OperationalProxyController" -> "پراکسی پویا برای SSO bearer و Legacy token؛ مسیر و operation از رجیستری resolve می‌شوند.";
      default -> "پراکسی same-origin به Superset عملیاتی با کنترل instance و asset.";
    };
  }

  private static Map<String, String> summaries() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("CsrfController#csrf", "دریافت CSRF token نشست جاری");
    m.put("MeController#me", "دریافت هویت کاربر جاری");
    m.put("MeController#manifest", "دریافت Manifest دسترسی کاربر جاری");
    m.put("ReportsController#reports", "فهرست گزارش‌ها و داشبوردهای مجاز");
    m.put("AdminProxyController#tokenTest", "آزمون دریافت token پروفایل Legacy بدون افشای token");
    m.put("AdminProxyController#connectionTest", "آزمون policy و اتصال پروفایل Legacy");
    m.put("AdminProxyController#invalidate", "ابطال token cache‌شده پروفایل Legacy");
    m.put("AdminProxyController#cacheStatus", "بررسی وجود token معتبر در cache بدون نمایش مقدار");
    m.put("AdminProxyController#health", "Health check مقصد سرویس از شبکه سرور");
    m.put("AdminProxyController#proxy", "پراکسی APIهای پنل راهبری به authorization-service");
    m.put("OperationalProxyController#proxy", "هدایت مجاز درخواست میکروفرانت به سرویس عملیاتی");
    m.put("OperationSupersetProxyController#proxy", "هدایت درخواست به Superset پیش‌فرض");
    m.put("OperationSupersetProxyController#proxyInstance", "هدایت درخواست به Superset عمومی نام‌گذاری‌شده");
    return Map.copyOf(m);
  }

  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }
}
