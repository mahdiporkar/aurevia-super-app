package com.aurevia.authz.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditTrail {
  private final PublicZoneLogWriter writer;
  public AuditTrail(PublicZoneLogWriter writer){this.writer=writer;}
  public void success(String category,String event,String subjectType,String subjectId,String targetType,String targetId,String targetName,String action,Map<String,Object> before,Map<String,Object> after){
    HttpServletRequest request=current();String actor=request==null?"SYSTEM":value(request.getHeader("X-Actor"),"SYSTEM");String correlation=request==null?java.util.UUID.randomUUID().toString():CorrelationIds.normalize(request.getHeader(CorrelationIds.HEADER));
    writer.audit(new PublicZoneLogWriter.AuditEntry(Instant.now(),"SYSTEM".equals(actor)?"SYSTEM":"USER",actor,category,event,subjectType,subjectId,targetType,targetId,targetName,action,"SUCCESS",before,after,request==null?null:request.getRemoteAddr(),request==null?null:limit(request.getHeader("User-Agent"),1000),"authorization-service",correlation,Map.of()));
  }
  private static HttpServletRequest current(){var attributes=RequestContextHolder.getRequestAttributes();return attributes instanceof ServletRequestAttributes servlet?servlet.getRequest():null;}
  private static String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
  private static String limit(String value,int size){return value==null?null:value.substring(0,Math.min(size,value.length()));}
}
