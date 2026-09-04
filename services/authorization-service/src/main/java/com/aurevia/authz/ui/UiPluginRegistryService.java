package com.aurevia.authz.ui;

import static com.aurevia.authz.api.dto.UiPluginDtos.*;

import com.aurevia.authz.observability.AuditTrail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
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
        request.hidden(),safeActor);
    audit.success("UI_REGISTRY","UI_MENU_OVERRIDE_CHANGED",null,null,"PANEL",
        panelId.toString(),menuId,"UPDATE",null,Map.of("menuId",menuId));
    return new MenuOverrideResponse(menuId);
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
      if(!request.exposedModule().matches("^\\./[A-Za-z][A-Za-z0-9_./-]*$")) {
        throw new IllegalArgumentException("invalid exposedModule");
      }
      if(!"1.0".equals(request.contractVersion())) {
        throw new IllegalArgumentException("unsupported contractVersion");
      }
      String moduleKey=plugins.activePanelSlug(panelId).orElseThrow(()->
          new IllegalArgumentException("active panel not found"));
      JsonNode root=json.readTree(request.manifest());
      if(!"1.0".equals(root.path("schemaVersion").asText())
          ||!moduleKey.equals(root.path("moduleKey").asText())
          ||!root.path("routes").isArray()||!root.path("menus").isArray()) {
        throw new IllegalArgumentException("invalid manifest schema or moduleKey");
      }
      Set<String> routes=new HashSet<>();
      for(JsonNode route:root.path("routes")) {
        String id=route.path("id").asText();
        String path=route.path("path").asText();
        String resource=route.path("resource").asText();
        String action=route.path("action").asText();
        if(id.isBlank()||!routes.add(id)||path.startsWith("/")||path.contains("..")
            ||resource.isBlank()||action.isBlank()) {
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
      return root;
    } catch(IllegalArgumentException failure) { throw failure; }
    catch(Exception failure) {
      throw new IllegalArgumentException("manifest is not valid JSON",failure);
    }
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

  private static String actor(String value) {
    if(value==null||value.isBlank()||value.length()>255) {
      throw new IllegalArgumentException("invalid actor");
    }
    return value;
  }
}
