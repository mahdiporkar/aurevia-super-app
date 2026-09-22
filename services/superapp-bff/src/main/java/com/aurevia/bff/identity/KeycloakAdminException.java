package com.aurevia.bff.identity;

import org.springframework.http.HttpStatus;

/** Only application-owned messages enter responses/logs; upstream bodies and causes are excluded. */
public final class KeycloakAdminException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public KeycloakAdminException(HttpStatus status, String code, String message) {
    super(message);
    this.status=status;
    this.code=code;
  }

  public HttpStatus status() { return status; }
  public String code() { return code; }
}
