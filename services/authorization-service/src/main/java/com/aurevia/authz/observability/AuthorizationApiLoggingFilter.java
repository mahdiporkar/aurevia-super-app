package com.aurevia.authz.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class AuthorizationApiLoggingFilter extends OncePerRequestFilter {
  private final PublicZoneLogWriter logs;private final SafeErrorBodySerializer errors;
  public AuthorizationApiLoggingFilter(PublicZoneLogWriter logs,SafeErrorBodySerializer errors){this.logs=logs;this.errors=errors;}
  @Override protected boolean shouldNotFilter(HttpServletRequest r){return r.getRequestURI().startsWith("/actuator")||r.getRequestURI().equals("/internal/v1/logging/api");}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    long started=System.nanoTime();String correlation=CorrelationIds.normalize(request.getHeader(CorrelationIds.HEADER));
    response.setHeader(CorrelationIds.HEADER,correlation);ContentCachingResponseWrapper wrapped=new ContentCachingResponseWrapper(response);
    Throwable failure=null;try{chain.doFilter(request,wrapped);}
    catch(ServletException|IOException|RuntimeException|Error thrown){failure=thrown;throw thrown;}
    finally{byte[] body=wrapped.getContentAsByteArray();int status=wrapped.getStatus();var safe=errors.serialize(status,wrapped.getContentType(),body);
      Long requestSize=request.getContentLengthLong()>=0?request.getContentLengthLong():null;
      try{logs.api(new PublicZoneLogWriter.ApiEntry(Instant.now(),request.getHeader("X-Actor"),request.getHeader("X-Actor")==null?null:"USER","authorization-service",request.getMethod(),request.getRequestURI(),status,(System.nanoTime()-started)/1_000_000,clientIp(request),limit(request.getHeader("User-Agent"),1000),correlation,requestSize,(long)body.length,(String)request.getAttribute("authorizationResult"),(String)request.getAttribute("resourceType"),(String)request.getAttribute("resourceId"),(String)request.getAttribute("businessAction"),(Long)request.getAttribute("openfgaDurationMs"),null,null,null,null,failure==null?null:failure.getClass().getName(),safe.body(),safe.redacted(),safe.truncated()));}finally{wrapped.copyBodyToResponse();}}
  }
  private static String clientIp(HttpServletRequest r){String forwarded=r.getHeader("X-Forwarded-For");return limit(forwarded==null?r.getRemoteAddr():forwarded.split(",")[0].trim(),128);}
  private static String limit(String value,int max){return value==null?null:value.substring(0,Math.min(value.length(),max));}
}
