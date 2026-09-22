package com.aurevia.bff.api;

import com.aurevia.bff.identity.KeycloakAdminException;
import com.aurevia.bff.observability.CorrelationIds;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * Canonical browser-facing error body {@code {"code","message","correlationId"}} for every error a
 * BFF controller raises. Documented once as the OpenAPI {@code ApiError} schema. Security-layer
 * 401 responses (no session) are produced before controllers and carry no body, which the
 * documentation states explicitly.
 */
@RestControllerAdvice(basePackages = "com.aurevia.bff.api")
public class BffExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(BffExceptionHandler.class);

  @ExceptionHandler(KeycloakAdminException.class)
  ResponseEntity<Map<String, String>> keycloak(KeycloakAdminException error, ServerWebExchange exchange) {
    return respond(error.status(), error.code(), error.getMessage(), exchange);
  }

  @ExceptionHandler(WebExchangeBindException.class)
  ResponseEntity<Map<String, String>> invalidBody(WebExchangeBindException error, ServerWebExchange exchange) {
    // Binding diagnostics can echo rejected values (passwords); only field names leave the service.
    String fields = error.getFieldErrors().stream().map(org.springframework.validation.FieldError::getField)
        .distinct().sorted().collect(Collectors.joining(", "));
    return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Validation failed for fields: " + fields, exchange);
  }

  @ExceptionHandler(ServerWebInputException.class)
  ResponseEntity<Map<String, String>> unreadable(ServerWebInputException error, ServerWebExchange exchange) {
    return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body or parameters are malformed", exchange);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException error, ServerWebExchange exchange) {
    return respond(error.getStatusCode(), codeFor(error.getStatusCode()), error.getReason(), exchange);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> unexpected(Exception error, ServerWebExchange exchange) {
    String correlation = correlation(exchange);
    log.error("Unhandled BFF error correlation={} type={}", correlation, error.getClass().getName(), error);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("INTERNAL_ERROR",
        "Unexpected internal error; use the correlationId to investigate", correlation));
  }

  public static String codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "INVALID_REQUEST";
      case 401 -> "AUTHENTICATION_REQUIRED";
      case 403 -> "ACCESS_DENIED";
      case 404 -> "NOT_FOUND";
      case 409 -> "CONFLICT";
      case 502 -> "UPSTREAM_ERROR";
      case 503 -> "SERVICE_UNAVAILABLE";
      case 504 -> "UPSTREAM_TIMEOUT";
      default -> status.value() >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
    };
  }

  static String correlation(ServerWebExchange exchange) {
    String header = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER);
    if (header == null || header.isBlank()) header = exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER);
    return CorrelationIds.normalize(header);
  }

  static Map<String, String> body(String code, String message, String correlation) {
    return Map.of("code", code, "message", message == null || message.isBlank() ? code : message, "correlationId", correlation);
  }

  private static ResponseEntity<Map<String, String>> respond(HttpStatusCode status, String code, String message,
      ServerWebExchange exchange) {
    return ResponseEntity.status(status).body(body(code, message, correlation(exchange)));
  }
}
