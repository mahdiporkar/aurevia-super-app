package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.ui.UiArtifactPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Draft/preview/approval workflow for versioned MFE resource manifests. */
@Service
public class ResourceManifestService {
  static final Set<String> TYPES=Set.of("APPLICATION","MODULE","PAGE","UI_COMPONENT","FIELD",
      "BUSINESS_RESOURCE","EXTERNAL_RESOURCE","API_RESOURCE","DATA_RESOURCE",
      "DATA_GOVERNANCE_RESOURCE");
  static final Map<String,String> PREFIXES=Map.ofEntries(
      Map.entry("APPLICATION","application:"),Map.entry("MODULE","module:"),
      Map.entry("PAGE","page:"),Map.entry("UI_COMPONENT","component:"),
      Map.entry("FIELD","field:"),Map.entry("BUSINESS_RESOURCE","business:"),
      Map.entry("EXTERNAL_RESOURCE","external_resource:"),Map.entry("API_RESOURCE","api:"),
      Map.entry("DATA_RESOURCE","data:"),Map.entry("DATA_GOVERNANCE_RESOURCE","governance:"));
  private static final Map<String,Set<String>> PARENTS=Map.ofEntries(
      Map.entry("MODULE",Set.of("APPLICATION")),Map.entry("PAGE",Set.of("MODULE")),
      Map.entry("UI_COMPONENT",Set.of("PAGE","UI_COMPONENT")),
      Map.entry("FIELD",Set.of("UI_COMPONENT")),
      Map.entry("BUSINESS_RESOURCE",Set.of("APPLICATION","MODULE","BUSINESS_RESOURCE")),
      Map.entry("EXTERNAL_RESOURCE",Set.of("APPLICATION","MODULE","PAGE","BUSINESS_RESOURCE")),
      Map.entry("API_RESOURCE",Set.of("APPLICATION","MODULE","BUSINESS_RESOURCE")),
      Map.entry("DATA_RESOURCE",Set.of("APPLICATION","MODULE","BUSINESS_RESOURCE","DATA_RESOURCE")),
      Map.entry("DATA_GOVERNANCE_RESOURCE",Set.of("APPLICATION","MODULE","DATA_RESOURCE")));
  private static final TypeReference<List<ManifestChange>> CHANGES=new TypeReference<>() {};

  private final ResourceManifestRepository resources;
  private final ResourceManifestFetcher fetcher;
  private final UiArtifactPolicy locationPolicy;
  private final AuditTrail audit;
  private final ObjectMapper json;

  public ResourceManifestService(ResourceManifestRepository resources,ResourceManifestFetcher fetcher,
      UiArtifactPolicy locationPolicy,AuditTrail audit,ObjectMapper json) {
    this.resources=resources;this.fetcher=fetcher;this.locationPolicy=locationPolicy;
    this.audit=audit;this.json=json;
  }

  public DefinitionManifest definition(String application) {
    String root="application:"+application;
    List<ResourceDefinition> tree=resources.definitionTree(root);
    if(tree.isEmpty()) {
      root="application:aurevia/"+application;
      tree=resources.definitionTree(root);
    }
    if(tree.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,
        "application manifest not found");
    return new DefinitionManifest(application,resources.latestVersion(root),tree);
  }

  public List<ManifestDraftView> drafts(UUID panelId) {
    requirePanel(panelId);
    return resources.drafts(panelId).stream().map(this::view).toList();
  }

  public ManifestDraftView preview(UUID panelId,UUID draftId) {
    return view(requireDraft(panelId,draftId));
  }

  @Transactional
  public ManifestDraftView fetch(UUID panelId,String actor) {
    var panel=requirePanel(panelId);
    ensureManifestMode(panel);
    if(blank(panel.resourceManifestUrl())) {
      throw new IllegalArgumentException("resourceManifestUrl is not configured for this micro frontend");
    }
    String url=locationPolicy.validateManifestUrl(panel.resourceManifestUrl());
    return stage(panel,read(fetcher.fetch(url)),safeActor(actor),url);
  }

  @Transactional
  public ManifestDraftView stage(UUID panelId,MicroFrontendManifest manifest,String actor) {
    var panel=requirePanel(panelId);
    ensureManifestMode(panel);
    return stage(panel,manifest,safeActor(actor),null);
  }

  /** Backward-compatible endpoint behavior: legacy payloads are staged, never directly applied. */
  @Transactional
  public ManifestDraftView stageLegacy(String application,DefinitionManifest manifest,String actor) {
    if(!application.equals(manifest.application())) {
      throw new IllegalArgumentException("path application must match manifest application");
    }
    var panel=resources.panelSettingsBySlug(application).orElseThrow(()->
        new ResponseStatusException(HttpStatus.NOT_FOUND,"micro frontend registration not found"));
    Set<String> legacyApplications=manifest.resources().stream()
        .filter(value->"APPLICATION".equals(value.type())).map(ResourceDefinition::key)
        .collect(java.util.stream.Collectors.toSet());
    List<ManifestResource> definitions=manifest.resources().stream()
        .filter(value->!"APPLICATION".equals(value.type())).map(value->
        new ManifestResource(value.key(),value.type(),
            legacyApplications.contains(value.parent())?null:value.parent(),null,null,value.nameFa(),
            value.nameEn(),value.ownerDomain(),value.classification(),value.actions(),
            value.metadata(),value.provider(),value.externalType(),value.externalId())).toList();
    String moduleName=panel.nameEn()==null?panel.slug():panel.nameEn();
    return stage(panel,new MicroFrontendManifest("1.0",
        new ModuleMetadata(panel.slug(),moduleName,panel.nameFa(),panel.nameEn(),
            manifest.manifestVersion()),List.of(),definitions,List.of()),safeActor(actor),null);
  }

  @Transactional
  public PublishResult publish(UUID panelId,UUID draftId,String actor) {
    String safeActor=safeActor(actor);
    var panel=requirePanel(panelId);
    ensureManifestMode(panel);
    var draft=requireDraft(panelId,draftId);
    if(!"DRAFT".equals(draft.workflowStatus())) {
      throw new IllegalArgumentException("only a DRAFT manifest can be published");
    }
    MicroFrontendManifest manifest=read(draft.payload());
    DefinitionManifest normalized=normalize(panel,manifest);
    if(!checksum(manifest).equals(draft.checksum())) {
      throw new IllegalArgumentException("stored manifest checksum mismatch");
    }
    List<ManifestChange> changes=diff(normalized);
    if(changes.stream().anyMatch(change->"CONFLICT".equals(change.changeType()))) {
      throw new IllegalArgumentException("manifest has ownership or type conflicts; publish refused");
    }
    String root=normalized.resources().stream().filter(value->"APPLICATION".equals(value.type()))
        .map(ResourceDefinition::key).findFirst().orElseThrow();
    Set<String> pending=new LinkedHashSet<>();
    normalized.resources().forEach(resource->pending.add(resource.key()));
    int created=0;
    int updated=0;
    while(!pending.isEmpty()) {
      boolean progressed=false;
      for(ResourceDefinition resource:normalized.resources()) {
        if(!pending.contains(resource.key())
            ||(resource.parent()!=null&&!resources.resourceExists(resource.parent())))continue;
        var ownership=resources.resourceOwnership(resource.key());
        boolean exists=ownership.isPresent();
        if(exists)assertManifestOwnership(resource,panel.id(),ownership.orElseThrow());
        UUID previousParent=ownership.map(ResourceManifestRepository.ResourceOwnership::parentId)
            .orElse(null);
        UUID effectiveParent=upsert(resource,panel.id(),normalized.manifestVersion());
        if(!Objects.equals(previousParent,effectiveParent)) {
          UUID resourceId=resources.resourceId(resource.key()).orElseThrow();
          resources.enqueueParent(resourceId,previousParent,"RESOURCE_PARENT_DELETE");
          resources.enqueueParent(resourceId,effectiveParent,"RESOURCE_PARENT_WRITE");
        }
        replaceActions(resource);
        pending.remove(resource.key());
        if(exists)updated++;else created++;
        progressed=true;
      }
      if(!progressed)throw new IllegalArgumentException(
          "manifest contains a missing parent or cycle: "+pending);
    }
    String[] keys=normalized.resources().stream().map(ResourceDefinition::key)
        .toArray(String[]::new);
    int deprecated=resources.deprecateMissing(panel.id(),root,keys);
    if(!resources.markPublished(draftId,safeActor)) {
      throw new IllegalStateException("manifest draft changed while publishing");
    }
    if(audit!=null)audit.success("RESOURCE_CATALOG","manifest.published",null,null,
        "PANEL",panelId.toString(),draftId.toString(),"PUBLISH",null,
        Map.of("created",created,"updated",updated,"deprecated",deprecated,
            "manifestVersion",normalized.manifestVersion()));
    return new PublishResult(draftId,"PUBLISHED",created,updated,deprecated,draft.checksum());
  }

  public void validate(DefinitionManifest manifest,String root) {
    Map<String,ResourceDefinition> definitions=new LinkedHashMap<>();
    for(ResourceDefinition resource:manifest.resources()) {
      String type=resource.type().toUpperCase(Locale.ROOT);
      if(definitions.put(resource.key(),resource)!=null)
        throw new IllegalArgumentException("duplicate resource key: "+resource.key());
      if(resource.key().matches("(?i).*(create|delete|edit|export|approve|reject)[-_]?button.*"))
        throw new IllegalArgumentException("action buttons are not resources: "+resource.key());
      if(resource.key().contains("/api/")
          ||resource.key().matches("(?i)^(GET|POST|PUT|PATCH|DELETE)-.*"))
        throw new IllegalArgumentException("API URLs are bindings, not resources");
      validateIdentity(resource.key(),type);
      if("EXTERNAL_RESOURCE".equals(type)
          &&(blank(resource.provider())||blank(resource.externalType())||blank(resource.externalId())))
        throw new IllegalArgumentException("external resource binding is required");
    }
    ResourceDefinition rootDefinition=definitions.get(root);
    if(rootDefinition==null||!"APPLICATION".equals(rootDefinition.type()))
      throw new IllegalArgumentException("manifest must contain its application root");
    if(rootDefinition.parent()!=null)
      throw new IllegalArgumentException("APPLICATION root must have parent=null");
    for(ResourceDefinition value:definitions.values()) {
      if(value.key().equals(root))continue;
      if(value.parent()==null)throw new IllegalArgumentException(value.key()+" requires a parent");
      ResourceDefinition parent=definitions.get(value.parent());
      if(parent==null)throw new IllegalArgumentException("missing parent: "+value.parent());
      if(value.key().equals(value.parent()))throw new IllegalArgumentException("resource cannot parent itself");
      if(!PARENTS.getOrDefault(value.type(),Set.of()).contains(parent.type()))
        throw new IllegalArgumentException("invalid parent type "+parent.type()+" for "+value.type());
      assertAcyclic(value.key(),definitions);
    }
  }

  private ManifestDraftView stage(ResourceManifestRepository.PanelManifestSettings panel,
      MicroFrontendManifest manifest,String actor,String sourceUrl) {
    DefinitionManifest normalized=normalize(panel,manifest);
    List<ManifestChange> changes=diff(normalized);
    String checksum=checksum(manifest);
    var existing=resources.revisionByVersion(panel.id(),normalized.manifestVersion());
    if(existing.isPresent()) {
      var revision=existing.orElseThrow();
      if(checksum.equals(revision.checksum()))return view(revision);
      throw new IllegalArgumentException(
          "manifest version is immutable and already has different content");
    }
    UUID id=UUID.randomUUID();
    String root=normalized.resources().stream().filter(value->"APPLICATION".equals(value.type()))
        .map(ResourceDefinition::key).findFirst().orElseThrow();
    boolean inserted=resources.insertDraft(new ResourceManifestRepository.DraftInsert(id,panel.id(),root,
        normalized.manifestVersion(),manifest.schemaVersion(),checksum,actor,sourceUrl,
        write(manifest),write(changes)));
    if(!inserted) {
      var winner=resources.revisionByVersion(panel.id(),normalized.manifestVersion())
          .orElseThrow(()->new IllegalStateException("manifest draft insert conflicted"));
      if(checksum.equals(winner.checksum()))return view(winner);
      throw new IllegalArgumentException(
          "manifest version is immutable and already has different content");
    }
    if(audit!=null)audit.success("RESOURCE_CATALOG","manifest.draft.created",null,null,
        "PANEL",panel.id().toString(),id.toString(),"CREATE",null,
        Map.of("manifestVersion",normalized.manifestVersion(),"changes",changes.size()));
    return preview(panel.id(),id);
  }

  private DefinitionManifest normalize(ResourceManifestRepository.PanelManifestSettings panel,
      MicroFrontendManifest manifest) {
    validateContract(panel,manifest);
    String root="application:aurevia/"+panel.slug();
    String moduleKey="module:"+manifest.module().key();
    Map<String,ResourceDefinition> definitions=new LinkedHashMap<>();
    definitions.put(root,new ResourceDefinition(root,"APPLICATION",null,panel.nameFa(),
        panel.nameEn(),panel.slug(),"INTERNAL",List.of("view"),"ACTIVE","MANIFEST",
        Map.of("moduleKey",manifest.module().key()),null,null,null));
    definitions.put(moduleKey,new ResourceDefinition(moduleKey,"MODULE",root,
        first(manifest.module().nameFa(),manifest.module().name()),
        first(manifest.module().nameEn(),manifest.module().name()),panel.slug(),"INTERNAL",
        List.of("view"),"ACTIVE","MANIFEST",Map.of(),null,null,null));
    for(ManifestResource value:manifest.resources()) {
      String type=value.type().toUpperCase(Locale.ROOT);
      String parent=value.effectiveParentKey();
      if(parent==null)parent=switch(type) {
        case "APPLICATION" -> null;
        case "MODULE" -> root;
        default -> moduleKey;
      };
      ResourceDefinition definition=new ResourceDefinition(value.key(),type,parent,
          value.effectiveNameFa(),value.effectiveNameEn(),first(value.ownerDomain(),panel.slug()),
          first(value.classification(),"INTERNAL"),value.actions(),"ACTIVE","MANIFEST",
          value.metadata(),value.provider(),value.externalType(),value.externalId());
      ResourceDefinition prior=definitions.put(value.key(),definition);
      if(prior!=null&&!syntheticEquivalent(prior,definition))
        throw new IllegalArgumentException("duplicate resource key: "+value.key());
    }
    Map<String,LinkedHashSet<String>> routeActions=new HashMap<>();
    for(ManifestRoute route:manifest.routes()) {
      routeActions.computeIfAbsent(route.effectiveResourceKey(),ignored->new LinkedHashSet<>())
          .add(route.effectiveAction());
    }
    routeActions.forEach((key,actions)->{
      ResourceDefinition current=definitions.get(key);
      if(current==null) {
        if(!"HYBRID".equals(panel.resourceDefinitionMode())
            ||actions.stream().anyMatch(action->!resources.resourceActionExists(key,action)))
          throw new IllegalArgumentException("route references unknown resource action: "+key);
        return;
      }
      LinkedHashSet<String> merged=new LinkedHashSet<>(current.actions());merged.addAll(actions);
      definitions.put(key,new ResourceDefinition(current.key(),current.type(),current.parent(),
          current.nameFa(),current.nameEn(),current.ownerDomain(),current.classification(),
          List.copyOf(merged),current.status(),current.source(),current.metadata(),
          current.provider(),current.externalType(),current.externalId()));
    });
    definitions.values().forEach(definition->{
      Set<String> unique=new LinkedHashSet<>();
      for(String action:definition.actions()) {
        if(blank(action)||!unique.add(action))
          throw new IllegalArgumentException(
              "resource contains a blank or duplicate action: "+definition.key());
        if(!resources.actionExists(action))
          throw new IllegalArgumentException("unknown action in manifest: "+action);
      }
    });
    DefinitionManifest result=new DefinitionManifest(panel.slug(),manifest.module().version(),
        List.copyOf(definitions.values()));
    validate(result,root);
    return result;
  }

  private void validateContract(ResourceManifestRepository.PanelManifestSettings panel,
      MicroFrontendManifest manifest) {
    if(manifest==null||manifest.module()==null)
      throw new IllegalArgumentException("manifest module is required");
    if(!"1.0".equals(manifest.schemaVersion()))
      throw new IllegalArgumentException("unsupported manifest schemaVersion");
    if(blank(manifest.module().key())||blank(manifest.module().name())
        ||blank(manifest.module().version()))
      throw new IllegalArgumentException("module key, name, and version are required");
    if(!panel.slug().equals(manifest.module().key()))
      throw new IllegalArgumentException("module.key must match the registered panel slug");
    if(!manifest.module().key().matches("^[a-z][a-z0-9-]{1,79}$"))
      throw new IllegalArgumentException("module.key must be lowercase kebab-case");
    if(!manifest.module().version().matches("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$"))
      throw new IllegalArgumentException("module.version must be SemVer");
    Set<String> routeKeys=new LinkedHashSet<>();
    for(ManifestRoute route:manifest.routes()) {
      if(route==null)throw new IllegalArgumentException("manifest route cannot be null");
      String key=route.effectiveKey();
      if(blank(key)||!routeKeys.add(key))throw new IllegalArgumentException("invalid or duplicate route key");
      if(blank(route.effectiveResourceKey()))throw new IllegalArgumentException("route resourceKey is required");
      if(route.path()==null||route.path().contains("..")||route.path().contains("://")
          ||route.path().startsWith("//"))
        throw new IllegalArgumentException("route path must be local to the micro frontend");
    }
    for(ManifestResource resource:manifest.resources()) {
      if(resource==null||blank(resource.key())||blank(resource.type()))
        throw new IllegalArgumentException("resource key and type are required");
    }
    Map<String,NavigationNode> navigation=new LinkedHashMap<>();
    for(NavigationNode node:manifest.navigation()) {
      if(node==null||blank(node.type())||blank(node.title()))
        throw new IllegalArgumentException("navigation type and title are required");
      String key=node.effectiveKey();
      String type=node.type().toUpperCase(Locale.ROOT);
      if(blank(key)||navigation.put(key,node)!=null)
        throw new IllegalArgumentException("invalid or duplicate navigation key");
      if(!Set.of("GROUP","PAGE","EXTERNAL_LINK").contains(type))
        throw new IllegalArgumentException("unsupported navigation node type: "+type);
      if("PAGE".equals(type)&&!routeKeys.contains(node.effectivePageKey()))
        throw new IllegalArgumentException("navigation PAGE references an unknown route");
      if("GROUP".equals(type)
          &&(!blank(node.effectivePageKey())||!blank(node.externalUrl())))
        throw new IllegalArgumentException("navigation GROUP cannot have a route or URL");
      if("PAGE".equals(type)&&!blank(node.externalUrl()))
        throw new IllegalArgumentException("navigation PAGE cannot have an external URL");
      if("EXTERNAL_LINK".equals(type))validateExternalUrl(node.externalUrl());
      if("EXTERNAL_LINK".equals(type)&&!blank(node.effectivePageKey()))
        throw new IllegalArgumentException("EXTERNAL_LINK cannot reference a route");
    }
    for(var entry:navigation.entrySet()) {
      String parent=entry.getValue().effectiveParentKey();
      if(parent!=null&&!navigation.containsKey(parent))
        throw new IllegalArgumentException("navigation parent does not exist: "+parent);
      if(parent!=null&&!"GROUP".equals(navigation.get(parent).type().toUpperCase(Locale.ROOT)))
        throw new IllegalArgumentException("navigation parent must be a GROUP: "+parent);
      assertNavigationAcyclic(entry.getKey(),navigation);
    }
  }

  private List<ManifestChange> diff(DefinitionManifest manifest) {
    String root=manifest.resources().stream().filter(value->"APPLICATION".equals(value.type()))
        .map(ResourceDefinition::key).findFirst().orElseThrow();
    Map<String,ResourceDefinition> current=new LinkedHashMap<>();
    resources.definitionTree(root).forEach(value->current.put(value.key(),value));
    List<ManifestChange> changes=new ArrayList<>();
    for(ResourceDefinition value:manifest.resources()) {
      ResourceDefinition before=current.remove(value.key());
      if(before==null) {
        changes.add(new ManifestChange(value.key(),"CREATE",null,value.type(),"new manifest resource"));
      } else if(!"MANIFEST".equals(before.source())) {
        changes.add(new ManifestChange(value.key(),"CONFLICT",before.type(),value.type(),
            "resource key is owned by an administrator"));
      } else if(!Objects.equals(before.type(),value.type())) {
        changes.add(new ManifestChange(value.key(),"CONFLICT",before.type(),value.type(),
            "resource type is immutable"));
      } else if(!sameDefinition(before,value)) {
        changes.add(new ManifestChange(value.key(),"UPDATE",before.type(),value.type(),
            "metadata, hierarchy, lifecycle, or actions changed"));
      } else {
        changes.add(new ManifestChange(value.key(),"UNCHANGED",before.type(),value.type(),"no change"));
      }
    }
    current.values().stream().filter(value->"MANIFEST".equals(value.source())).forEach(value->
        changes.add(new ManifestChange(value.key(),"DEPRECATE",value.type(),null,
            "missing from the new manifest; retained for grants and audit history")));
    return List.copyOf(changes);
  }

  private UUID upsert(ResourceDefinition resource,UUID panelId,String manifestVersion) {
    UUID parentId=resource.parent()==null?null:resources.resourceId(resource.parent())
        .orElseThrow(()->new IllegalArgumentException("resource parent does not exist"));
    String metadata=write(resource.metadata());
    resources.upsertResource(resource,parentId,metadata,panelId,manifestVersion);
    if("EXTERNAL_RESOURCE".equals(resource.type()))resources.upsertExternalBinding(resource,metadata);
    return parentId;
  }

  private void replaceActions(ResourceDefinition resource) {
    UUID id=resources.resourceId(resource.key()).orElseThrow();
    resources.clearActions(id);
    for(String action:resource.actions())if(!resources.addAction(id,action))
      throw new IllegalArgumentException("unknown or duplicate action: "+action);
  }

  private void assertManifestOwnership(ResourceDefinition resource,UUID panelId,
      ResourceManifestRepository.ResourceOwnership stored) {
    if(!"MANIFEST".equals(stored.source()))
      throw new IllegalArgumentException("resource is administrator-owned: "+resource.key());
    if(stored.panelId()!=null&&!panelId.equals(stored.panelId()))
      throw new IllegalArgumentException("resource belongs to another micro frontend: "+resource.key());
    if(!stored.type().equals(resource.type()))
      throw new IllegalArgumentException("resource type is immutable for "+resource.key());
  }

  private ManifestDraftView view(ResourceManifestRepository.DraftRecord value) {
    try {
      MicroFrontendManifest manifest=read(value.payload());
      return new ManifestDraftView(value.id(),value.panelId(),manifest.module().key(),
          value.manifestVersion(),value.schemaVersion(),value.checksum(),value.workflowStatus(),
          value.sourceUrl(),json.readValue(value.diffSummary(),CHANGES),value.createdAt(),
          value.importedBy(),value.publishedAt(),value.publishedBy());
    } catch(Exception failure) {
      throw new IllegalStateException("stored resource manifest is invalid",failure);
    }
  }

  private ResourceManifestRepository.PanelManifestSettings requirePanel(UUID panelId) {
    return resources.panelSettings(panelId).orElseThrow(()->
        new ResponseStatusException(HttpStatus.NOT_FOUND,"micro frontend registration not found"));
  }

  private ResourceManifestRepository.DraftRecord requireDraft(UUID panelId,UUID draftId) {
    return resources.draft(panelId,draftId).orElseThrow(()->
        new ResponseStatusException(HttpStatus.NOT_FOUND,"manifest draft not found"));
  }

  private static void ensureManifestMode(ResourceManifestRepository.PanelManifestSettings panel) {
    if("MANUAL".equals(panel.resourceDefinitionMode()))
      throw new IllegalArgumentException("manifest import is disabled in MANUAL mode");
  }

  private void validateIdentity(String key,String type) {
    if(!TYPES.contains(type))throw new IllegalArgumentException("unsupported resource type: "+type);
    String prefix=PREFIXES.get(type);
    if(!key.matches("^[a-z][a-z0-9_-]*:[a-z0-9][a-z0-9._/-]*$")
        ||prefix==null||!key.startsWith(prefix))
      throw new IllegalArgumentException(
          "resource key must be normalized and match its semantic type: "+key);
  }

  private static void assertAcyclic(String key,Map<String,ResourceDefinition> definitions) {
    Set<String> visited=new LinkedHashSet<>();String cursor=key;
    while(cursor!=null) {
      if(!visited.add(cursor))throw new IllegalArgumentException("resource hierarchy contains a cycle: "+key);
      ResourceDefinition value=definitions.get(cursor);cursor=value==null?null:value.parent();
    }
  }

  private static void assertNavigationAcyclic(String key,Map<String,NavigationNode> nodes) {
    Set<String> visited=new LinkedHashSet<>();String cursor=key;
    while(cursor!=null) {
      if(!visited.add(cursor))throw new IllegalArgumentException("navigation hierarchy contains a cycle: "+key);
      NavigationNode value=nodes.get(cursor);cursor=value==null?null:value.effectiveParentKey();
    }
  }

  private static boolean sameDefinition(ResourceDefinition left,ResourceDefinition right) {
    return Objects.equals(left.type(),right.type())&&Objects.equals(left.parent(),right.parent())
        &&Objects.equals(left.nameFa(),right.nameFa())&&Objects.equals(left.nameEn(),right.nameEn())
        &&Objects.equals(left.ownerDomain(),right.ownerDomain())
        &&Objects.equals(left.classification(),right.classification())
        &&new LinkedHashSet<>(left.actions()).equals(new LinkedHashSet<>(right.actions()))
        &&Objects.equals(left.metadata(),right.metadata())&&Objects.equals(left.provider(),right.provider())
        &&Objects.equals(left.externalType(),right.externalType())
        &&Objects.equals(left.externalId(),right.externalId())
        &&!"DEPRECATED".equals(left.status());
  }

  private static boolean syntheticEquivalent(ResourceDefinition left,ResourceDefinition right) {
    return Objects.equals(left.key(),right.key())&&Objects.equals(left.type(),right.type())
        &&Objects.equals(left.parent(),right.parent());
  }

  private String checksum(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(write(value).getBytes(StandardCharsets.UTF_8)));
    } catch(Exception failure) { throw new IllegalStateException(failure); }
  }

  private MicroFrontendManifest read(String value) {
    try{return json.readValue(value,MicroFrontendManifest.class);}
    catch(Exception failure){throw new IllegalArgumentException("manifest is not valid JSON",failure);}
  }

  private String write(Object value) {
    try{return json.writeValueAsString(value);}
    catch(Exception failure){throw new IllegalArgumentException("invalid manifest",failure);}
  }

  private static String safeActor(String value) {
    if(blank(value)||value.length()>255)throw new IllegalArgumentException("invalid actor");
    return value;
  }

  private static String first(String... values) {
    for(String value:values)if(!blank(value))return value;
    return null;
  }

  private static boolean blank(String value){return value==null||value.isBlank();}

  private static void validateExternalUrl(String value) {
    if(blank(value))throw new IllegalArgumentException(
        "EXTERNAL_LINK requires a safe absolute HTTPS URL");
    try {
      var uri=java.net.URI.create(value).normalize();
      if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null
          ||uri.getUserInfo()!=null||uri.getFragment()!=null)
        throw new IllegalArgumentException("EXTERNAL_LINK requires a safe absolute HTTPS URL");
    } catch(IllegalArgumentException invalid) {
      throw new IllegalArgumentException("EXTERNAL_LINK requires a safe absolute HTTPS URL",invalid);
    }
  }
}
