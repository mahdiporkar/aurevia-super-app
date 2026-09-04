package com.aurevia.authz.superset;

import static com.aurevia.authz.api.dto.SupersetInstanceDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupersetInstanceService {
  private static final Pattern CODE=Pattern.compile("^[a-z][a-z0-9-]{2,79}$");
  private static final Pattern CONNECTION=Pattern.compile("^connection://[a-zA-Z0-9._/-]+$");
  private static final Set<String> AUTH_MODES=Set.of("REMOTE_USER","OIDC","GUEST_TOKEN");
  private final SupersetInstanceRepository instances;
  private final AuditTrail audit;

  public SupersetInstanceService(SupersetInstanceRepository instances,AuditTrail audit) {
    this.instances=instances;this.audit=audit;
  }

  public List<InstanceView> instances() { return instances.instances(); }
  public List<MappingView> mappings() { return instances.mappings(); }

  @Transactional
  public VersionResponse create(InstanceRequest request,String actor) {
    String safeActor=actor(actor);
    var value=validate(request,UUID.randomUUID());
    instances.insert(value,safeActor);
    audit.success("SUPERSET","superset.instance.created",null,null,"SUPERSET_INSTANCE",
        value.id().toString(),value.code(),"CREATE",null,Map.of("zone",value.zone()));
    return new VersionResponse(value.id(),0);
  }

  @Transactional
  public VersionResponse update(UUID id,InstanceRequest request,String actor) {
    String safeActor=actor(actor);
    var value=validate(request,id);
    if(!instances.update(id,request.version(),value,request.active(),safeActor)) {
      throw new OptimisticLockingFailureException("VERSION_CONFLICT");
    }
    audit.success("SUPERSET","superset.instance.updated",null,null,"SUPERSET_INSTANCE",
        id.toString(),value.code(),"UPDATE",null,Map.of("zone",value.zone()));
    return new VersionResponse(id,request.version()+1);
  }

  @Transactional
  public IdResponse map(MappingRequest request,String actor) {
    String safeActor=actor(actor);
    assertZones(request.publicInstanceId(),request.operationInstanceId());
    String path=validatePath(request.publicPath());
    if(request.isDefault()) instances.clearDefaultMappings(safeActor);
    UUID id=instances.upsertMapping(UUID.randomUUID(),request.publicInstanceId(),
        request.operationInstanceId(),path,request.isDefault(),request.active(),safeActor);
    audit.success("SUPERSET","superset.mapping.changed",null,null,"SUPERSET_MAPPING",
        id.toString(),path,"UPSERT",null,Map.of("default",request.isDefault()));
    return new IdResponse(id);
  }

  private void assertZones(UUID publicId,UUID operationId) {
    List<String> zones=instances.activeZones(publicId,operationId);
    if(zones.size()!=2||!zones.contains("PUBLIC")||!zones.contains("OPERATION")) {
      throw new IllegalArgumentException(
          "Mapping requires one active PUBLIC and one active OPERATION instance");
    }
  }

  private static SupersetInstanceRepository.InstanceValue validate(InstanceRequest request,
      UUID id) {
    String code=request.code().trim().toLowerCase(Locale.ROOT);
    String zone=request.zone().trim().toUpperCase(Locale.ROOT);
    String auth=request.authMode().trim().toUpperCase(Locale.ROOT);
    if(!CODE.matcher(code).matches()) throw new IllegalArgumentException("Invalid Superset instance code");
    if(!Set.of("PUBLIC","OPERATION").contains(zone)) throw new IllegalArgumentException("Invalid Superset zone");
    if(!AUTH_MODES.contains(auth)) throw new IllegalArgumentException("Invalid Superset auth mode");
    if(!CONNECTION.matcher(request.connectionRef()).matches()) throw new IllegalArgumentException("Invalid connection reference");
    URI uri;
    try { uri=URI.create(request.baseUrl().trim()).normalize(); }
    catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid Superset base URL",invalid); }
    if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null
        ||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
        ||!(uri.getPath().isEmpty()||"/".equals(uri.getPath()))) {
      throw new IllegalArgumentException("Superset URL must be an absolute HTTP(S) origin");
    }
    if(request.tlsRequired()&&!"https".equals(uri.getScheme())) {
      throw new IllegalArgumentException("TLS-required Superset must use HTTPS");
    }
    String normalized=uri.getScheme()+"://"+uri.getHost()
        +(uri.getPort()<0?"":":"+uri.getPort());
    return new SupersetInstanceRepository.InstanceValue(id,code,request.name().trim(),zone,
        normalized,request.connectionRef().trim(),auth,request.tlsRequired(),request.active());
  }

  private static String validatePath(String value) {
    String path=value==null?"":value.trim();
    if(!path.matches("^/[a-zA-Z0-9/_-]*$")||path.contains("..")) {
      throw new IllegalArgumentException("Invalid public proxy path");
    }
    return path.length()>1&&path.endsWith("/")?path.substring(0,path.length()-1):path;
  }

  private static String actor(String value) {
    if(value==null||value.isBlank()||value.length()>500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid actor");
    }
    return value;
  }
}
