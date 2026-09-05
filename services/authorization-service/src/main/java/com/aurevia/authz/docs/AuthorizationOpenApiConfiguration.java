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
  OpenAPI authorizationOpenApi() {
    var error = new ObjectSchema()
        .addProperty("code", new StringSchema().description("کد پایدار و قابل پردازش خطا")
            .example("INVALID_REQUEST"))
        .addProperty("message", new StringSchema().description("پیام قابل فهم برای راهبر")
            .example("مقدار فیلد ارسالی معتبر نیست"))
        .addProperty("correlationId", new StringSchema().description("شناسه رهگیری بین سرویس‌ها")
            .example("5e4ddf32-1e7e-4e20-a9f3-64de1c938f97"));
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
      addStandardResponses(operation);
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

  private static void addStandardResponses(io.swagger.v3.oas.models.Operation operation) {
    if (operation.getResponses() == null) {
      return;
    }
    operation.getResponses().putIfAbsent("400", errorResponse("درخواست یا مقدار یکی از فیلدها نامعتبر است"));
    operation.getResponses().putIfAbsent("401", errorResponse("هویت سرویس/نشست معتبر نیست"));
    operation.getResponses().putIfAbsent("403", errorResponse("هویت احراز شده مجوز این عملیات را ندارد"));
    operation.getResponses().putIfAbsent("409", errorResponse("تعارض نسخه خوش‌بینانه یا داده تکراری"));
    operation.getResponses().putIfAbsent("500", errorResponse("خطای کنترل‌نشده داخلی؛ correlationId را پیگیری کنید"));
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
