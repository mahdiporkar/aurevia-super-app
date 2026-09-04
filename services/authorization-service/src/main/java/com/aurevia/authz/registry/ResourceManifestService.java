package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

  private final ResourceManifestRepository resources;
  private final ObjectMapper json;

  public ResourceManifestService(ResourceManifestRepository resources,ObjectMapper json) {
    this.resources=resources;this.json=json;
  }

  public DefinitionManifest definition(String application) {
    String root="application:"+application;
    List<ResourceDefinition> tree=resources.definitionTree(root);
    if(tree.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"application manifest not found");
    }
    return new DefinitionManifest(application,resources.latestVersion(root),tree);
  }

  @Transactional
  public SyncResult sync(String application,DefinitionManifest manifest,String actor) {
    if(!application.equals(manifest.application())) {
      throw new IllegalArgumentException("path application must match manifest application");
    }
    String root="application:"+application;
    validate(manifest,root);
    String checksum=checksum(manifest);
    if(resources.importExists(root,manifest.manifestVersion(),checksum)) {
      return new SyncResult(0,0,0,true,checksum);
    }
    Set<String> pending=new LinkedHashSet<>();
    manifest.resources().forEach(resource->pending.add(resource.key()));
    int created=0;
    int updated=0;
    while(!pending.isEmpty()) {
      boolean progressed=false;
      for(ResourceDefinition resource:manifest.resources()) {
        if(!pending.contains(resource.key())
            ||(resource.parent()!=null&&!resources.resourceExists(resource.parent()))) continue;
        boolean exists=resources.resourceExists(resource.key());
        upsert(resource);
        replaceActions(resource);
        pending.remove(resource.key());
        if(exists) updated++; else created++;
        progressed=true;
      }
      if(!progressed) {
        throw new IllegalArgumentException(
            "manifest contains a missing parent or cycle: "+pending);
      }
    }
    String[] keys=manifest.resources().stream().map(ResourceDefinition::key)
        .toArray(String[]::new);
    int deprecated=resources.deprecateMissing(root,keys);
    resources.insertImport(root,manifest.manifestVersion(),checksum,actor,write(manifest));
    return new SyncResult(created,updated,deprecated,false,checksum);
  }

  public void validate(DefinitionManifest manifest,String root) {
    Map<String,ResourceDefinition> definitions=new HashMap<>();
    for(ResourceDefinition resource:manifest.resources()) {
      if(definitions.put(resource.key(),resource)!=null) {
        throw new IllegalArgumentException("duplicate resource key: "+resource.key());
      }
      if(!TYPES.contains(resource.type())) {
        throw new IllegalArgumentException("unsupported resource type: "+resource.type());
      }
      if(resource.key().matches("(?i).*(create|delete|edit|export|approve|reject)[-_]?button.*")) {
        throw new IllegalArgumentException("action buttons are not resources: "+resource.key());
      }
      if(resource.key().contains("/api/")
          ||resource.key().matches("(?i)^(GET|POST|PUT|PATCH|DELETE)-.*")) {
        throw new IllegalArgumentException("API URLs are bindings, not resources");
      }
      String prefix=PREFIXES.get(resource.type());
      if(!resource.key().matches("^[a-z][a-z0-9_-]*:[a-z0-9][a-z0-9._/-]*$")
          ||prefix==null||!resource.key().startsWith(prefix)) {
        throw new IllegalArgumentException(
            "resource key must be normalized and match its semantic type: "+resource.key());
      }
      if("EXTERNAL_RESOURCE".equals(resource.type())
          &&(blank(resource.provider())||blank(resource.externalType())
              ||blank(resource.externalId()))) {
        throw new IllegalArgumentException("external resource binding is required");
      }
    }
    if(!definitions.containsKey(root)||!"APPLICATION".equals(definitions.get(root).type())) {
      throw new IllegalArgumentException("manifest must contain its application root");
    }
  }

  private void upsert(ResourceDefinition resource) {
    resources.resourceType(resource.key()).ifPresent(stored->{
      if(!stored.equals(resource.type())) {
        throw new IllegalArgumentException("resource type is immutable for "+resource.key());
      }
    });
    UUID parentId=resource.parent()==null?null:resources.resourceId(resource.parent())
        .orElseThrow(()->new IllegalArgumentException("resource parent does not exist"));
    String metadata=write(resource.metadata());
    resources.upsertResource(resource,parentId,metadata);
    if("EXTERNAL_RESOURCE".equals(resource.type())) {
      resources.upsertExternalBinding(resource,metadata);
    }
  }

  private void replaceActions(ResourceDefinition resource) {
    UUID id=resources.resourceId(resource.key()).orElseThrow();
    resources.clearActions(id);
    for(String action:resource.actions()) {
      if(!resources.addAction(id,action)) {
        throw new IllegalArgumentException("unknown or duplicate action: "+action);
      }
    }
  }

  private String checksum(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(write(value).getBytes(StandardCharsets.UTF_8)));
    } catch(Exception failure) { throw new IllegalStateException(failure); }
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch(Exception failure) { throw new IllegalArgumentException("invalid manifest",failure); }
  }

  private static boolean blank(String value) { return value==null||value.isBlank(); }
}
