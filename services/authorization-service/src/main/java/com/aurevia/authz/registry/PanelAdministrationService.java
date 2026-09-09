package com.aurevia.authz.registry;

import static com.aurevia.authz.registry.PanelModels.*;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.ui.UiArtifactPolicy;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PanelAdministrationService {
  private static final Set<String> RESERVED=Set.of("login","admin","settings","api","assets","error");
  private static final Set<String> RESOURCE_MODES=Set.of("MANIFEST","MANUAL","HYBRID");
  private static final Set<String> CLASSIFICATIONS=Set.of("DEMO","REAL");
  private final PanelRepository repository;
  private final UiArtifactPolicy artifactPolicy;
  private final AuditTrail auditTrail;
  public PanelAdministrationService(PanelRepository repository,UiArtifactPolicy artifactPolicy,
      AuditTrail auditTrail){this.repository=repository;this.artifactPolicy=artifactPolicy;this.auditTrail=auditTrail;}
  public List<PanelView> panels(){return repository.panels();}
  public List<AuditView> audit(int limit){return repository.audit(Math.min(Math.max(limit,1),500));}

  @Transactional public MutationResult create(PanelCommand command,String actor){
    PanelSettings normalized=validate(command,null);UUID id=UUID.randomUUID();
    repository.create(id,command,normalized);repository.enqueue(id,"PANEL_CREATED",command.code(),0);
    audit(actor,"panel.created",id,command.code(),null,securityState(command,normalized));
    return new MutationResult(id,0);
  }
  @Transactional public MutationResult update(UUID id,long version,PanelCommand command,String actor){
    Map<String,Object> before=repository.panel(id).map(PanelAdministrationService::securityState)
        .orElse(null);
    PanelSettings normalized=validate(command,id);
    if(repository.update(id,version,command,normalized)!=1)
      throw new OptimisticLockingFailureException("panel changed or missing");
    repository.enqueue(id,"PANEL_UPDATED",command.code(),version+1);
    audit(actor,"panel.updated",id,command.code(),before,securityState(command,normalized));
    return new MutationResult(id,version+1);
  }
  @Transactional public void archive(UUID id,long version,String actor){
    Map<String,Object> before=repository.panel(id).map(PanelAdministrationService::securityState)
        .orElse(null);
    if(repository.archive(id,version)!=1)throw new OptimisticLockingFailureException("panel changed or missing");
    repository.enqueue(id,"PANEL_ARCHIVED",id.toString(),version+1);
    Map<String,Object> after=before==null?null:new LinkedHashMap<>(before);
    if(after!=null)after.put("active",false);
    audit(actor,"panel.archived",id,id.toString(),before,after);
  }
  private PanelSettings validate(PanelCommand p,UUID id){
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
    String mode=value(p.resourceDefinitionMode(),"HYBRID").toUpperCase(Locale.ROOT);
    if(!RESOURCE_MODES.contains(mode))throw new IllegalArgumentException("resourceDefinitionMode must be MANIFEST, MANUAL, or HYBRID");
    String classification=value(p.classification(),"REAL").toUpperCase(Locale.ROOT);
    if(!CLASSIFICATIONS.contains(classification))throw new IllegalArgumentException("classification must be DEMO or REAL");
    String mfManifestUrl=blankToNull(p.mfManifestUrl());
    if(mfManifestUrl!=null)mfManifestUrl=artifactPolicy.validateMicroFrontendManifestUrl(mfManifestUrl);
    String manifestUrl=blankToNull(p.resourceManifestUrl());
    if("MANIFEST".equals(mode)&&manifestUrl==null)throw new IllegalArgumentException("resourceManifestUrl is required in MANIFEST mode");
    if(manifestUrl!=null)manifestUrl=artifactPolicy.validateResourceManifestUrl(manifestUrl);
    return new PanelSettings(service,remote,value(p.defaultRouteId(),"index"),mode,classification,
        mfManifestUrl,manifestUrl);
  }
  private void audit(String actor,String event,UUID id,String name,Map<String,Object> before,
      Map<String,Object> after){
    Map<String,Object> auditedAfter=after==null?null:new LinkedHashMap<>(after);
    if(auditedAfter!=null)auditedAfter.put("actor",value(actor,"unknown"));
    auditTrail.success("UI_REGISTRY",event,null,null,"PANEL",id.toString(),name,event,before,
        auditedAfter);
  }
  private static Map<String,Object> securityState(PanelCommand panel,PanelSettings settings){
    Map<String,Object> state=new LinkedHashMap<>();
    state.put("remoteEntryUrl",panel.remoteEntry());
    state.put("mfManifestUrl",settings.mfManifestUrl());
    state.put("resourceManifestUrl",settings.resourceManifestUrl());
    state.put("integrityConfigured",panel.integrity()!=null&&!panel.integrity().isBlank());
    state.put("active",panel.active());
    return state;
  }
  private static Map<String,Object> securityState(PanelView panel){
    Map<String,Object> state=new LinkedHashMap<>();
    state.put("remoteEntryUrl",panel.remoteEntryPath());
    state.put("mfManifestUrl",panel.mfManifestUrl());
    state.put("resourceManifestUrl",panel.resourceManifestUrl());
    state.put("integrityConfigured",panel.integrity()!=null&&!panel.integrity().isBlank());
    state.put("active",panel.active());
    return state;
  }
  private static String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
  private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
