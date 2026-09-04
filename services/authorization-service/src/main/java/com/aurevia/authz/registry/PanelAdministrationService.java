package com.aurevia.authz.registry;

import static com.aurevia.authz.registry.PanelModels.*;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.ui.UiArtifactPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PanelAdministrationService {
  private static final Set<String> RESERVED=Set.of("login","admin","settings","api","assets","error");
  private final PanelRepository repository;
  private final UiArtifactPolicy artifactPolicy;
  private final AuditTrail auditTrail;
  public PanelAdministrationService(PanelRepository repository,UiArtifactPolicy artifactPolicy,
      AuditTrail auditTrail){this.repository=repository;this.artifactPolicy=artifactPolicy;this.auditTrail=auditTrail;}
  public List<PanelView> panels(){return repository.panels();}
  public List<AuditView> audit(int limit){return repository.audit(Math.min(Math.max(limit,1),500));}

  @Transactional public MutationResult create(PanelCommand command,String actor){
    Normalized normalized=validate(command,null);UUID id=UUID.randomUUID();
    repository.create(id,command,normalized.serviceSlug(),normalized.remoteName(),normalized.defaultRouteId());
    repository.enqueue(id,"PANEL_CREATED",command.code(),0);audit(actor,"panel.created",id,command.code());
    return new MutationResult(id,0);
  }
  @Transactional public MutationResult update(UUID id,long version,PanelCommand command,String actor){
    Normalized normalized=validate(command,id);
    if(repository.update(id,version,command,normalized.serviceSlug(),normalized.remoteName(),normalized.defaultRouteId())!=1)
      throw new OptimisticLockingFailureException("panel changed or missing");
    repository.enqueue(id,"PANEL_UPDATED",command.code(),version+1);audit(actor,"panel.updated",id,command.code());
    return new MutationResult(id,version+1);
  }
  @Transactional public void archive(UUID id,long version,String actor){
    if(repository.archive(id,version)!=1)throw new OptimisticLockingFailureException("panel changed or missing");
    repository.enqueue(id,"PANEL_ARCHIVED",id.toString(),version+1);audit(actor,"panel.archived",id,id.toString());
  }
  private Normalized validate(PanelCommand p,UUID id){
    if(!p.code().matches("^[A-Z][A-Z0-9_-]{1,99}$"))throw new IllegalArgumentException("code must be uppercase and stable");
    if(!p.slug().matches("^[a-z][a-z0-9-]{1,49}$"))throw new IllegalArgumentException("slug must be lowercase kebab-case");
    String service=value(p.serviceSlug(),p.slug());if(!service.matches("^[a-z][a-z0-9-]{1,49}$"))throw new IllegalArgumentException("invalid serviceSlug");
    String remote=value(p.remoteName(),"aurevia_"+p.slug().replace("-","_"));if(!remote.matches("^[A-Za-z][A-Za-z0-9_]*$"))throw new IllegalArgumentException("invalid remoteName");
    String route=p.routeBasePath();String prefix=route.replaceFirst("^/","");if(!prefix.matches("^[a-z][a-z0-9-]{1,49}$"))throw new IllegalArgumentException("invalid routeBasePath");
    if(RESERVED.contains(prefix)&&!route.equals(repository.routePath(id).orElse(null)))throw new IllegalArgumentException("reserved routeBasePath");
    if(repository.routePathExists(route,id))throw new IllegalArgumentException("routeBasePath already exists");
    if(!p.exposedModule().matches("^\\./[A-Za-z][A-Za-z0-9_./-]*$"))throw new IllegalArgumentException("invalid exposedModule");
    if(!p.semanticVersion().matches("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$"))throw new IllegalArgumentException("semanticVersion must be SemVer");
    artifactPolicy.validate(p.remoteEntry(),p.integrity());
    return new Normalized(service,remote,value(p.defaultRouteId(),"index"));
  }
  private void audit(String actor,String event,UUID id,String name){auditTrail.success("UI_REGISTRY",event,null,null,"PANEL",id.toString(),name,event,null,Map.of("actor",value(actor,"unknown")));}
  private static String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
  private record Normalized(String serviceSlug,String remoteName,String defaultRouteId){}
}
