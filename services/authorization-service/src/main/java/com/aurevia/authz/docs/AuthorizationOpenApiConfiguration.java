package com.aurevia.authz.docs;

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
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestBody;

/** OpenAPI is an HTTP-adapter concern and intentionally stays outside domain services. */
@Configuration
public class AuthorizationOpenApiConfiguration {

  @Bean
  public OpenAPI authorizationOpenApi() {
    // Mirrors com.aurevia.authz.api.ApiError exactly; ApiErrorContractTest proves runtime == schema.
    var error = new ObjectSchema().description("بدنهٔ استاندارد خطا در تمام پاسخ‌های ناموفق این سرویس")
        .addProperty("code", new StringSchema().description(
            "کد پایدار و قابل پردازش خطا: INVALID_REQUEST، AUTHENTICATION_REQUIRED، ACCESS_DENIED، NOT_FOUND، CONFLICT، OPTIMISTIC_LOCK_CONFLICT، DATA_CONFLICT، INTERNAL_ERROR")
            .example("INVALID_REQUEST"))
        .addProperty("message", new StringSchema().description("پیام قابل فهم برای راهبر؛ هرگز مقدار حساس ندارد")
            .example("resourceKey must be normalized and use its canonical prefix"))
        .addProperty("correlationId", new StringSchema().description(
            "شناسهٔ رهگیری همین درخواست؛ برابر هدر X-Correlation-ID پاسخ و قابل جست‌وجو در لاگ API/Audit")
            .example("5e4ddf32-1e7e-4e20-a9f3-64de1c938f97"))
        .required(List.of("code", "message", "correlationId"));
    var session = new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
        .name("AUREVIA_SESSION")
        .description("در پرتال یکپارچه، نشست امن BFF احراز هویت را انجام می‌دهد؛ توکن Keycloak به مرورگر بازگردانده نمی‌شود.");
    var csrf = new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
        .name("X-CSRF-TOKEN")
        .description("برای اجرای عملیات تغییردهنده از پرتال، مقدار GET /api/v1/csrf را وارد کنید.");

    return new OpenAPI()
        .info(new Info().title("API سرویس مجوزدهی و راهبری Aurevia")
            .version("1.0.0")
            .description("قرارداد کامل Control Plane و Runtime مجوزدهی. در محیط توسعه، درخواست‌های Try it out از مسیر امن BFF ارسال می‌شوند. دسترسی مستقیم داخلی در local با Basic و در production با mTLS است؛ خود Swagger در production غیرفعال است.")
            .contact(new Contact().name("تیم پلتفرم Aurevia")))
        .servers(List.of(new Server().url("/api/v1/docs/authorization/execute")
            .description("پراکسی توسعه‌ای امن BFF؛ فقط با نشست معتبر و خارج از profile=prod")))
        .components(new Components().addSchemas("ApiError", error)
            .addSecuritySchemes("browserSession", session)
            .addSecuritySchemes("csrfToken", csrf))
        .addSecurityItem(new SecurityRequirement().addList("browserSession"))
        .tags(ApiDocumentationCatalog.tags().entrySet().stream()
            .map(entry -> new Tag().name(entry.getValue()).description(
                ApiDocumentationCatalog.tagDescription(entry.getKey())))
            .toList());
  }

  @Bean
  OperationCustomizer authorizationOperationDocumentation() {
    return (operation, handlerMethod) -> {
      if (!handlerMethod.getBeanType().getPackageName().startsWith("com.aurevia.authz.api")) {
        return operation;
      }
      String controller = handlerMethod.getBeanType().getSimpleName();
      String method = handlerMethod.getMethod().getName();
      String key = controller + "#" + method;
      String tag = ApiDocumentationCatalog.tags().getOrDefault(controller, "سایر عملیات داخلی");
      String summary = ApiDocumentationCatalog.summary(key);
      operation.setOperationId(controller.replace("Controller", "") + "_" + method);
      operation.setTags(List.of(tag));
      operation.setSummary(summary);
      operation.setDescription(ApiDocumentationCatalog.description(key, summary));
      ApiSchemaDocumentation.documentParameters(operation);
      if (isMutation(handlerMethod.getMethod())) {
        operation.setSecurity(List.of(new SecurityRequirement()
            .addList("browserSession").addList("csrfToken")));
      }
      addStandardResponses(operation, handlerMethod, key);
      addRequestExample(operation, handlerMethod.getMethod().getParameters(), key);
      return operation;
    };
  }

  @Bean
  OpenApiCustomizer authorizationSchemaDocumentation() {
    return ApiSchemaDocumentation::apply;
  }

  private static boolean isMutation(java.lang.reflect.Method method) {
    return method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class);
  }

  private static final java.util.regex.Pattern PERSIAN = java.util.regex.Pattern.compile("[\u0600-\u06FF]");

  /** Operations that resolve a registry entry by a non-path argument and answer 404 when it is unknown. */
  private static final java.util.Set<String> LOOKUP_OPERATIONS = java.util.Set.of(
      "IdentityProviderController#route", "IdentityProviderController#runtime",
      "OutboundConnectionController#resolve", "OutboundAuthProfileController#runtime",
      "ResourceManifestController#definition", "RouteResolutionController#resolve",
      "SupersetProxyResolutionController#resolve", "SupersetProxyResolutionController#resolveIntegration",
      "LogQueryController#apiDetail", "LogQueryController#auditDetail", "AuthorizationDiagnosticsController#explain");

  /**
   * Only the errors an operation can really produce are documented (all as ApiError):
   * 400 when input exists, 401 always (internal Basic/mTLS), 403 on the admin-guarded registry
   * paths, 404 for lookups by id/key, 409 for creates/updates (version or data conflicts).
   */
  private static void addStandardResponses(io.swagger.v3.oas.models.Operation operation,
      org.springframework.web.method.HandlerMethod handlerMethod, String key) {
    if (operation.getResponses() == null) return;
    var method = handlerMethod.getMethod();
    // springdoc labels the success response "OK"; give it Persian meaning derived from the contract.
    operation.getResponses().forEach((code, response) -> {
      if (!code.startsWith("2") || (response.getDescription() != null && PERSIAN.matcher(response.getDescription()).find())) return;
      response.setDescription(switch (code) {
        case "201" -> "رکورد ایجاد شد؛ بدنه شامل شناسه و نسخهٔ اولیه است";
        case "204" -> "عملیات انجام شد و پاسخ بدنه ندارد";
        default -> operation.getRequestBody() == null ? "پاسخ موفق با بدنهٔ مستندشده" : "درخواست پذیرفته و پردازش شد";
      });
    });
    boolean hasInput = operation.getRequestBody() != null
        || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    boolean hasPathVariable = operation.getParameters() != null && operation.getParameters().stream()
        .anyMatch(parameter -> "path".equals(parameter.getIn()));
    boolean creates = method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class);
    boolean updates = method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class);
    if (hasInput) operation.getResponses().putIfAbsent("400", errorResponse("درخواست یا مقدار یکی از فیلدها نامعتبر است (INVALID_REQUEST)"));
    operation.getResponses().putIfAbsent("401", errorResponse("هویت سرویس/نشست معتبر نیست (AUTHENTICATION_REQUIRED)"));
    if (adminGuarded(handlerMethod)) operation.getResponses().putIfAbsent("403",
        errorResponse("عامل درخواست مجوز راهبری لازم را در OpenFGA ندارد (ACCESS_DENIED)"));
    if (hasPathVariable || LOOKUP_OPERATIONS.contains(key)) operation.getResponses().putIfAbsent("404",
        errorResponse("رکورد یا کلید درخواستی وجود ندارد (NOT_FOUND)"));
    if (creates || updates || key.equals("IdentityProviderController#route")) operation.getResponses().putIfAbsent("409",
        errorResponse(updates ? "تعارض نسخهٔ خوش‌بینانه یا دادهٔ تکراری (OPTIMISTIC_LOCK_CONFLICT / DATA_CONFLICT)"
            : "دادهٔ تکراری یا انتخاب مبهم (DATA_CONFLICT / CONFLICT)"));
  }

  /** The admin interceptor guards /internal/v1/registry/** except the two runtime subject lookups. */
  private static boolean adminGuarded(org.springframework.web.method.HandlerMethod handlerMethod) {
    var mapping = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
        handlerMethod.getMethod(), org.springframework.web.bind.annotation.RequestMapping.class);
    var type = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
        handlerMethod.getBeanType(), org.springframework.web.bind.annotation.RequestMapping.class);
    String prefix = type == null || type.path().length == 0 ? "" : type.path()[0];
    for (String path : mapping == null ? new String[0] : mapping.path()) {
      String full = prefix + path;
      if (full.startsWith("/internal/v1/registry/") && !full.matches("/internal/v1/registry/subjects/[^/]+/superset-(assets|access)"))
        return true;
    }
    return false;
  }

  private static ApiResponse errorResponse(String description) {
    var media = new io.swagger.v3.oas.models.media.MediaType()
        .schema(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ApiError"));
    return new ApiResponse().description(description)
        .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json", media));
  }

  private static void addRequestExample(io.swagger.v3.oas.models.Operation operation,
      Parameter[] parameters, String operationKey) {
    if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
      return;
    }
    Object example = ApiDocumentationExamples.forOperation(operationKey);
    if (example == null) {
      for (Parameter parameter : parameters) {
        if (parameter.isAnnotationPresent(RequestBody.class)) {
          example = ApiDocumentationExamples.forType(parameter.getType());
          break;
        }
      }
    }
    if (example == null) {
      return;
    }
    for (var mediaType : operation.getRequestBody().getContent().values()) {
      mediaType.addExamples("نمونه معتبر", new io.swagger.v3.oas.models.examples.Example()
          .summary("درخواست پیشنهادی برای محیط توسعه").value(example));
    }
  }
}
