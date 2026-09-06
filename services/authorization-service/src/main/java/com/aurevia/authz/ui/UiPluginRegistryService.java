package com.aurevia.authz.ui;

import static com.aurevia.authz.api.dto.UiPluginDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
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
public class UiPluginRegistryService {
  private final UiPluginRepository plugins;
  private final ObjectMapper json;
  private final UiArtifactPolicy artifactPolicy;
  private final AuditTrail audit;

  public UiPluginRegistryService(UiPluginRepository plugins,ObjectMapper json,
      UiArtifactPolicy artifactPolicy,AuditTrail audit) {
    this.plugins=plugins;this.json=json;this.artifactPolicy=artifactPolicy;this.audit=audit;
  }

  public List<ArtifactView> artifacts(UUID panelId) { return plugins.artifacts(panelId); }
  public List<NavigationOverrideView> navigationOverrides(UUID panelId) {
    return plugins.navigationOverrides(panelId);
  }

  @Transactional
  public ArtifactPublishedResponse publish(UUID panelId,String actor,ArtifactRequest request) {
    String safeActor=actor(actor);
    JsonNode manifest=validate(panelId,request);
    UUID id=UUID.randomUUID();
    String remoteUrl=artifactPolicy.validate(request.remoteEntryUrl(),request.integrity());
    plugins.insertArtifact(new UiPluginRepository.ArtifactInsert(id,panelId,
        request.artifactVersion(),remoteUrl,request.remoteName(),request.exposedModule(),
        request.contractVersion(),manifest.path("schemaVersion").asText(),request.integrity(),
        manifest.toString(),safeActor));
    audit.success("UI_REGISTRY","UI_ARTIFACT_PUBLISHED",null,null,"PANEL",
        panelId.toString(),request.artifactVersion(),"CREATE",null,
        Map.of("artifactId",id.toString(),"version",request.artifactVersion()));
    return new ArtifactPublishedResponse(id,"VALID");
  }

  @Transactional
  public ArtifactActivatedResponse activate(UUID panelId,UUID artifactId,long version) {
    var artifact=plugins.validArtifact(panelId,artifactId).orElseThrow(()->
        new IllegalArgumentException("artifact is not valid for this module"));
    artifactPolicy.validate(artifact.remoteEntryUrl(),artifact.integrity());
    var prior=plugins.panelState(panelId);
    if(!plugins.activate(panelId,artifactId,version)) {
      throw new OptimisticLockingFailureException("VERSION_CONFLICT");
    }
    audit.success("UI_REGISTRY","UI_ARTIFACT_ACTIVATED",null,null,"PANEL",
        panelId.toString(),panelId.toString(),"ACTIVATE",
        Map.of("activeArtifactId",String.valueOf(prior.activeArtifactId())),
        Map.of("activeArtifactId",artifactId.toString()));
    return new ArtifactActivatedResponse(artifactId,version+1);
  }

  @Transactional
  public MenuOverrideResponse overrideMenu(UUID panelId,String menuId,String actor,
      MenuOverrideRequest request) {
    ensureMenu(panelId,menuId);
    String safeActor=actor(actor);
    plugins.upsertMenu(panelId,menuId,request.title(),request.icon(),request.order(),
        request.hidden(),"MANIFEST","PAGE",null,null,null,safeActor);
    audit.success("UI_REGISTRY","UI_MENU_OVERRIDE_CHANGED",null,null,"PANEL",
        panelId.toString(),menuId,"UPDATE",null,Map.of("menuId",menuId));
    return new MenuOverrideResponse(menuId);
  }

  @Transactional
  public MenuOverrideResponse overrideNavigation(UUID panelId,String key,String actor,
      NavigationOverrideRequest request) {
    if(key==null||!key.matches("^[a-z][a-z0-9._-]{1,99}$"))
      throw new IllegalArgumentException("navigation key must be stable lowercase text");
    String safeActor=actor(actor);
    Map<String,NavigationSpec> manifestNodes=manifestNavigation(panelId);
    String source=value(request.source(),manifestNodes.containsKey(key)?"MANIFEST":"ADMIN")
        .toUpperCase(Locale.ROOT);
    if(!Set.of("MANIFEST","ADMIN").contains(source))
      throw new IllegalArgumentException("navigation source must be MANIFEST or ADMIN");
    NavigationSpec declared=manifestNodes.get(key);
    if("MANIFEST".equals(source)&&declared==null)
      throw new IllegalArgumentException("manifest navigation node does not exist");
    if("ADMIN".equals(source)&&declared!=null)
      throw new IllegalArgumentException("manifest navigation ownership cannot be replaced");
    if("MANIFEST".equals(source)&&(request.nodeType()!=null||request.parentKey()!=null
        ||request.pageKey()!=null||request.externalUrl()!=null))
      throw new IllegalArgumentException(
          "manifest overlays can change presentation fields only");
    String type=value(request.nodeType(),declared==null?null:declared.type());
    if(type==null||!Set.of("GROUP","PAGE","EXTERNAL_LINK").contains(type.toUpperCase(Locale.ROOT)))
      throw new IllegalArgumentException("nodeType must be GROUP, PAGE, or EXTERNAL_LINK");
    type=type.toUpperCase(Locale.ROOT);
    String parent=value(request.parentKey(),declared==null?null:declared.parentKey());
    String page=value(request.pageKey(),declared==null?null:declared.pageKey());
    String external=value(request.externalUrl(),declared==null?null:declared.externalUrl());
    if(key.equals(parent))throw new IllegalArgumentException("navigation node cannot parent itself");
    Map<String,NavigationSpec> effective=new LinkedHashMap<>(manifestNodes);
    for(NavigationOverrideView current:plugins.navigationOverrides(panelId)) {
      if("ADMIN".equals(current.source()))effective.put(current.key(),new NavigationSpec(
          current.nodeType(),current.parentKey(),current.pageKey(),current.externalUrl()));
    }
    if(parent!=null&&!effective.containsKey(parent))
      throw new IllegalArgumentException("navigation parent does not exist");
    if(parent!=null&&!"GROUP".equalsIgnoreCase(effective.get(parent).type()))
      throw new IllegalArgumentException("navigation parent must be a GROUP");
    if("PAGE".equals(type)&&!manifestRoutes(panelId).contains(page))
      throw new IllegalArgumentException("PAGE navigation must reference a manifest route");
    if("GROUP".equals(type)&&(page!=null||external!=null))
      throw new IllegalArgumentException("GROUP navigation cannot have a route or URL");
    if("PAGE".equals(type)&&external!=null)
      throw new IllegalArgumentException("PAGE navigation cannot have an external URL");
    if("EXTERNAL_LINK".equals(type))validateExternalUrl(external);
    if("EXTERNAL_LINK".equals(type)&&page!=null)
      throw new IllegalArgumentException("EXTERNAL_LINK cannot reference a route");
    if("ADMIN".equals(source)&&(request.title()==null||request.title().isBlank()))
      throw new IllegalArgumentException("title is required for administrator navigation");
    effective.put(key,new NavigationSpec(type,parent,page,external));
    assertNavigationAcyclic(key,effective);
    plugins.upsertMenu(panelId,key,request.title(),request.icon(),request.order(),request.hidden(),
        source,type,parent,page,external,safeActor);
    audit.success("UI_REGISTRY","UI_NAVIGATION_OVERRIDE_CHANGED",null,null,"PANEL",
        panelId.toString(),key,"UPDATE",null,Map.of("navigationKey",key,"source",source));
    return new MenuOverrideResponse(key);
  }

  @Transactional
  public void removeNavigationOverride(UUID panelId,String key,String actor) {
    if(plugins.navigationHasChildren(panelId,key))
      throw new IllegalArgumentException("navigation node has administrator-owned children");
    plugins.deleteNavigationOverride(panelId,key);
    audit.success("UI_REGISTRY","UI_NAVIGATION_OVERRIDE_REMOVED",null,null,"PANEL",
        panelId.toString(),key,"DELETE",null,Map.of("navigationKey",key,"actor",actor(actor)));
  }

  private JsonNode validate(UUID panelId,ArtifactRequest request) {
    try {
      artifactPolicy.validate(request.remoteEntryUrl(),request.integrity());
      if(!request.artifactVersion().matches(
          "^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?$")) {
        throw new IllegalArgumentException("invalid semantic artifact version");
      }
      if(!request.remoteName().matches("^[A-Za-z][A-Za-z0-9_]*$")) {
        throw new IllegalArgumentException("invalid remoteName");
      }
      if(plugins.remoteNameBelongsToOtherPanel(request.remoteName(),panelId)) {
        throw new IllegalArgumentException("remoteName is already owned by another panel");
      }
      if(!request.exposedModule().matches("^\\./[A-Za-z][A-Za-z0-9_./-]*$")) {
        throw new IllegalArgumentException("invalid exposedModule");
      }
      if(!"1.0".equals(request.contractVersion())) {
        throw new IllegalArgumentException("unsupported contractVersion");
      }
      String moduleKey=plugins.activePanelSlug(panelId).orElseThrow(()->
          new IllegalArgumentException("active panel not found"));
      JsonNode root=json.readTree(request.manifest());
      String declaredModuleKey=root.path("moduleKey").asText(
          root.path("module").path("key").asText());
      if(!"1.0".equals(root.path("schemaVersion").asText())
          ||!moduleKey.equals(declaredModuleKey)||!root.path("routes").isArray()
          ||(!root.path("menus").isArray()&&!root.path("navigation").isArray())) {
        throw new IllegalArgumentException("invalid manifest schema or moduleKey");
      }
      JsonNode apiBasePath=root.path("runtime").path("apiBasePath");
      if(!apiBasePath.isMissingNode()&&!validApiBasePath(apiBasePath.asText())) {
        throw new IllegalArgumentException("invalid runtime apiBasePath");
      }
      Set<String> routes=new HashSet<>();
      for(JsonNode route:root.path("routes")) {
        String id=text(route,"key","id");
        String path=route.path("path").asText();
        String resource=text(route,"resourceKey","resource");
        String action=route.path("action").asText("view");
        if(id==null||id.isBlank()||!routes.add(id)||path.startsWith("//")||path.contains("..")
            ||path.contains("://")
            ||resource==null||resource.isBlank()||action.isBlank()) {
          throw new IllegalArgumentException("invalid or duplicate route");
        }
        if(!plugins.resourceActionExists(resource,action)) {
          throw new IllegalArgumentException(
              "manifest route references an undeclared resource action");
        }
      }
      Set<String> menus=new HashSet<>();
      for(JsonNode menu:root.path("menus")) {
        String id=menu.path("id").asText();
        if(id.isBlank()||!menus.add(id)||!routes.contains(menu.path("routeId").asText())) {
          throw new IllegalArgumentException("menu references an invalid route");
        }
      }
      Map<String,NavigationSpec> declaredNavigation=new LinkedHashMap<>();
      for(JsonNode node:root.path("navigation")) {
        String id=text(node,"key","id");
        String type=node.path("type").asText();
        if(id==null||!id.matches("^[a-z][a-z0-9._-]{1,99}$")||!menus.add(id)
            ||!Set.of("GROUP","PAGE","EXTERNAL_LINK").contains(type))
          throw new IllegalArgumentException("invalid or duplicate navigation node");
        NavigationSpec specification=new NavigationSpec(type,
            text(node,"parentKey","parentId"),text(node,"pageKey","routeId"),
            textOrNull(node,"externalUrl"));
        validateNavigationFields(specification,routes);
        declaredNavigation.put(id,specification);
      }
      for(Map.Entry<String,NavigationSpec> entry:declaredNavigation.entrySet()) {
        String parent=entry.getValue().parentKey();
        if(parent!=null&&!declaredNavigation.containsKey(parent))
          throw new IllegalArgumentException("navigation parent does not exist");
        if(parent!=null&&!"GROUP".equals(declaredNavigation.get(parent).type()))
          throw new IllegalArgumentException("navigation parent must be a GROUP");
        assertNavigationAcyclic(entry.getKey(),declaredNavigation);
      }
      return root;
    } catch(IllegalArgumentException failure) { throw failure; }
    catch(Exception failure) {
      throw new IllegalArgumentException("manifest is not valid JSON",failure);
    }
  }

  private static boolean validApiBasePath(String value) {
    return value!=null&&value.startsWith("/")&&!value.startsWith("//")
        &&!value.contains("://")&&!value.contains("..")&&!value.contains("\\")
        &&!value.contains("?")&&!value.contains("#");
  }

  private void ensureMenu(UUID panelId,String menuId) {
    try {
      for(JsonNode node:json.readTree(plugins.activeManifest(panelId)).path("menus")) {
        if(menuId.equals(node.path("id").asText())) return;
      }
    } catch(Exception failure) {
      throw new IllegalArgumentException("active manifest is not valid",failure);
    }
    throw new IllegalArgumentException("menuId is not declared by active manifest");
  }

  private Map<String,NavigationSpec> manifestNavigation(UUID panelId) {
    try {
      JsonNode root=json.readTree(plugins.activeManifest(panelId));
      Map<String,NavigationSpec> nodes=new LinkedHashMap<>();
      JsonNode navigation=root.path("navigation");
      if(navigation.isArray())for(JsonNode node:navigation) {
        String key=text(node,"key","id");
        nodes.put(key,new NavigationSpec(node.path("type").asText(),
            text(node,"parentKey","parentId"),text(node,"pageKey","routeId"),
            textOrNull(node,"externalUrl")));
      }
      if(nodes.isEmpty())for(JsonNode node:root.path("menus")) {
        String key=node.path("id").asText();
        nodes.put(key,new NavigationSpec("PAGE",textOrNull(node,"parentId"),
            node.path("routeId").asText(),null));
      }
      return nodes;
    } catch(Exception failure) {
      throw new IllegalArgumentException("active manifest is not valid",failure);
    }
  }

  private Set<String> manifestRoutes(UUID panelId) {
    try {
      Set<String> routes=new HashSet<>();
      for(JsonNode route:json.readTree(plugins.activeManifest(panelId)).path("routes"))
        routes.add(text(route,"key","id"));
      return routes;
    } catch(Exception failure) {
      throw new IllegalArgumentException("active manifest is not valid",failure);
    }
  }

  private static void assertNavigationAcyclic(String key,Map<String,NavigationSpec> nodes) {
    Set<String> visited=new HashSet<>();String cursor=key;
    while(cursor!=null) {
      if(!visited.add(cursor))throw new IllegalArgumentException("navigation hierarchy contains a cycle");
      NavigationSpec value=nodes.get(cursor);cursor=value==null?null:value.parentKey();
    }
  }

  private static String text(JsonNode node,String preferred,String legacy) {
    String value=textOrNull(node,preferred);return value==null?textOrNull(node,legacy):value;
  }

  private static String textOrNull(JsonNode node,String field) {
    JsonNode value=node.get(field);
    return value==null||value.isNull()||value.asText().isBlank()?null:value.asText();
  }

  private static String value(String value,String fallback) {
    return value==null||value.isBlank()?fallback:value;
  }

  private static String actor(String value) {
    if(value==null||value.isBlank()||value.length()>255) {
      throw new IllegalArgumentException("invalid actor");
    }
    return value;
  }

  private static void validateExternalUrl(String value) {
    if(value==null||value.isBlank())throw new IllegalArgumentException(
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

  private static void validateNavigationFields(NavigationSpec node,Set<String> routes) {
    if("GROUP".equals(node.type())) {
      if(node.pageKey()!=null||node.externalUrl()!=null)
        throw new IllegalArgumentException("GROUP navigation cannot have a route or URL");
      return;
    }
    if("PAGE".equals(node.type())) {
      if(node.pageKey()==null||!routes.contains(node.pageKey()))
        throw new IllegalArgumentException("navigation references an invalid route");
      if(node.externalUrl()!=null)
        throw new IllegalArgumentException("PAGE navigation cannot have an external URL");
      return;
    }
    if(node.pageKey()!=null)
      throw new IllegalArgumentException("EXTERNAL_LINK cannot reference a route");
    validateExternalUrl(node.externalUrl());
  }

  private record NavigationSpec(String type,String parentKey,String pageKey,String externalUrl) {}
}
