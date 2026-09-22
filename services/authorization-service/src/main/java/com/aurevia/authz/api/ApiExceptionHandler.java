package com.aurevia.authz.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves the service as {@link ApiError}; status codes follow the documented contract. */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ApiError> conflict(OptimisticLockingFailureException e, HttpServletRequest request) {
    return respond(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT",
        "The record changed since it was read; reload and retry with the current version", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest request) {
    return respond(HttpStatus.CONFLICT, "DATA_CONFLICT", "The requested change conflicts with existing data", request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> badRequest(IllegalArgumentException e, HttpServletRequest request) {
    return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
    String fields = e.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted().distinct()
        .collect(Collectors.joining("; "));
    return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Validation failed: " + fields, request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> invalidParameters(HandlerMethodValidationException e, HttpServletRequest request) {
    return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Validation failed for request parameters", request);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class})
  ResponseEntity<ApiError> unreadable(Exception e, HttpServletRequest request) {
    // Framework messages can echo the rejected value; keep only the class of problem.
    String detail = e instanceof MissingServletRequestParameterException missing
        ? "Missing required parameter: " + missing.getParameterName()
        : e instanceof MethodArgumentTypeMismatchException mismatch
            ? "Invalid value for parameter: " + mismatch.getName()
            : "Request body is malformed or has the wrong type";
    return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail, request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiError> status(ResponseStatusException e, HttpServletRequest request) {
    return respond(e.getStatusCode(), codeFor(e.getStatusCode()), e.getReason(), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> notFound(NoResourceFoundException e, HttpServletRequest request) {
    return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint", request);
  }

  @ExceptionHandler(ErrorResponseException.class)
  ResponseEntity<ApiError> errorResponse(ErrorResponseException e, HttpServletRequest request) {
    return respond(e.getStatusCode(), codeFor(e.getStatusCode()), e.getBody().getDetail(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
    String correlation = ApiError.correlation(request);
    log.error("Unhandled error correlation={} type={}", correlation, e.getClass().getName(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError("INTERNAL_ERROR", "Unexpected internal error; use the correlationId to investigate", correlation));
  }

  public static String codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "INVALID_REQUEST";
      case 401 -> "AUTHENTICATION_REQUIRED";
      case 403 -> "ACCESS_DENIED";
      case 404 -> "NOT_FOUND";
      case 409 -> "CONFLICT";
      case 422 -> "UNPROCESSABLE";
      case 502 -> "UPSTREAM_ERROR";
      case 503 -> "SERVICE_UNAVAILABLE";
      case 504 -> "UPSTREAM_TIMEOUT";
      default -> status.value() >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
    };
  }

  private static ResponseEntity<ApiError> respond(HttpStatusCode status, String code, String message,
      HttpServletRequest request) {
    return ResponseEntity.status(status).body(ApiError.of(code, message, request));
  }
}
