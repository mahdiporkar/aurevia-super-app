package com.aurevia.authz.api;

import com.aurevia.authz.observability.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * The one error body every Authorization Service HTTP error carries:
 * {@code {"code": "...", "message": "...", "correlationId": "..."}}. Documented once as the
 * OpenAPI {@code ApiError} schema; {@link ApiExceptionHandler}, the admin interceptor and the
 * authentication entry point all produce exactly this shape.
 */
public record ApiError(String code, String message, String correlationId) {
  public static final String REQUEST_ATTRIBUTE = "aurevia.correlationId";

  public static ApiError of(String code, String message, HttpServletRequest request) {
    return new ApiError(code, message == null || message.isBlank() ? code : message, correlation(request));
  }

  /** The correlation id already normalized by the logging filter, else derived from the request header. */
  public static String correlation(HttpServletRequest request) {
    if (request == null) return CorrelationIds.normalize(null);
    Object known = request.getAttribute(REQUEST_ATTRIBUTE);
    if (known instanceof String value && !value.isBlank()) return value;
    return CorrelationIds.normalize(request.getHeader(CorrelationIds.HEADER));
  }

  /** Writes this error as JSON; used outside the MVC exception pipeline (security filter, interceptor). */
  public void write(HttpServletResponse response, int status) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"code\":" + json(code) + ",\"message\":" + json(message)
        + ",\"correlationId\":" + json(correlationId) + "}");
  }

  private static String json(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : String.valueOf(value).toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
      }
    }
    return out.append('"').toString();
  }
}
