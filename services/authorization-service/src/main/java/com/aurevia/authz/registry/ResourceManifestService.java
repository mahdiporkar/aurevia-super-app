package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.ui.UiArtifactPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
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
    String url=locationPolicy.validateResourceManifestUrl(panel.resourceManifestUrl());
    return stage(panel,read(fetcher.fetch(url)),safeActor(actor),url);
  }

  @Transactional
  public ManifestDraftView stage(UUID panelId,ResourceManifest manifest,String actor) {
    var panel=requirePanel(panelId);
    ensureManifestMode(panel);
    return stage(panel,manifest,safeActor(actor),null);
  }

  @Transactional
  public ManifestDraftView stageJson(UUID panelId,JsonNode manifest,String actor) {
    if(manifest==null||manifest.isNull())throw new IllegalArgumentException("manifest is required");
    return stage(panelId,read(manifest.toString()),actor);
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
    return stage(panel,new ResourceManifest("1.0",
        new ModuleMetadata(panel.slug(),moduleName,panel.nameFa(),panel.nameEn(),
            manifest.manifestVersion()),definitions),safeActor(actor),null);
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
    ResourceManifest manifest=readStored(draft.payload());
    if(hasFrontendFields(draft.payload()))throw new IllegalArgumentException(
        "legacy mixed manifest drafts must be restaged as authorization-only resource manifests");
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
      ResourceManifest manifest,String actor,String sourceUrl) {
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
      ResourceManifest manifest) {
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
      ResourceManifest manifest) {
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
    for(ManifestResource resource:manifest.resources()) {
      if(resource==null||blank(resource.key())||blank(resource.type()))
        throw new IllegalArgumentException("resource key and type are required");
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
      ResourceManifest manifest=readStored(value.payload());
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

  private ResourceManifest read(String value) {
    try {
      var root=json.readTree(value);
      for(String forbidden:frontendFields()) {
        if(root.has(forbidden))throw new IllegalArgumentException(
            "resource manifest must not contain frontend field: "+forbidden);
      }
      if(root.path("resources").isArray())for(var resource:root.path("resources")) {
        for(String forbidden:resourceFrontendFields()) {
          if(resource.has(forbidden)||resource.path("metadata").has(forbidden))
            throw new IllegalArgumentException(
                "resource definition must not contain frontend field: "+forbidden);
        }
      }
      return json.treeToValue(root,ResourceManifest.class);
    }
    catch(IllegalArgumentException failure){throw failure;}
    catch(Exception failure){throw new IllegalArgumentException("manifest is not valid JSON",failure);}
  }

  /** Read-only compatibility for immutable revisions created before the contract split. */
  private ResourceManifest readStored(String value) {
    try {
      var root=json.readTree(value);
      if(root instanceof ObjectNode object)for(String field:frontendFields())object.remove(field);
      return json.treeToValue(root,ResourceManifest.class);
    } catch(Exception failure) {
      throw new IllegalStateException("stored resource manifest is invalid",failure);
    }
  }

  private boolean hasFrontendFields(String value) {
    try {
      var root=json.readTree(value);
      if(frontendFields().stream().anyMatch(root::has))return true;
      if(root.path("resources").isArray())for(JsonNode resource:root.path("resources"))
        if(resourceFrontendFields().stream().anyMatch(field->resource.has(field)
            ||resource.path("metadata").has(field)))return true;
      return false;
    } catch(Exception failure) { throw new IllegalStateException("stored manifest is invalid",failure); }
  }

  private static List<String> frontendFields() {
    return List.of("routes","navigation","menus","runtime","remoteEntry","remoteEntryUrl",
        "exposedModule","routePrefix","slug","component","components","icon","iconKey");
  }

  private static List<String> resourceFrontendFields() {
    return List.of("route","path","menu","navigation","icon","component","remoteEntry",
        "remoteEntryUrl","exposedModule","routePrefix","slug","label","iconKey");
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

}
