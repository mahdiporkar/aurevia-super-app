package com.aurevia.authz.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Definition manifests describe capabilities. They never contain subject grants. */
@RestController
@RequestMapping("/internal/v1/registry/resource-definition-manifests")
public class ResourceManifestController {
  static final Set<String> TYPES=Set.of("APPLICATION","MODULE","PAGE","UI_COMPONENT","FIELD","BUSINESS_RESOURCE","EXTERNAL_RESOURCE");
  static final Map<String,String> PREFIXES=Map.of("APPLICATION","application:","MODULE","module:","PAGE","page:","UI_COMPONENT","component:","FIELD","field:","BUSINESS_RESOURCE","business:","EXTERNAL_RESOURCE","external:");
  private final JdbcClient db; private final ObjectMapper json;
  public ResourceManifestController(JdbcClient db,ObjectMapper json){this.db=db;this.json=json;}

  @GetMapping("/{application}")
  public DefinitionManifest definition(@PathVariable String application){
    String root="application:"+application;
    var resources=db.sql("""
      with recursive tree as (select * from resource where resource_key=:root union all
      select r.* from resource r join tree p on r.parent_id=p.id)
      select t.resource_key key,t.type::text type,p.resource_key parent,t.name_fa,t.name_en,
      t.owner_domain,t.classification,t.status::text status,t.source,t.metadata,
      coalesce(array_agg(a.action_key order by a.action_key) filter(where a.id is not null),array[]::varchar[]) actions,
      t.external_system provider,t.external_type,t.external_id
      from tree t left join resource p on p.id=t.parent_id left join resource_action ra on ra.resource_id=t.id
      left join action a on a.id=ra.action_id group by t.id,p.resource_key order by t.resource_key
      """).param("root",root).query(ResourceDefinition.class).list();
    if(resources.isEmpty())throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"application manifest not found");
    return new DefinitionManifest(application,latestVersion(root),resources);
  }

  @PutMapping("/{application}") @Transactional
  public SyncResult sync(@PathVariable String application,@Valid @RequestBody DefinitionManifest manifest,
      @RequestHeader(value="X-Actor",defaultValue="unknown") String actor){
    if(!application.equals(manifest.application()))throw new IllegalArgumentException("path application must match manifest application");
    String root="application:"+application;validate(manifest,root);
    String checksum=checksum(manifest);Long prior=db.sql("select count(*) from resource_manifest_import where application_key=:app and manifest_version=:version and checksum=:checksum")
        .param("app",root).param("version",manifest.manifestVersion()).param("checksum",checksum).query(Long.class).single();
    if(prior>0)return new SyncResult(0,0,0,true,checksum);
    Set<String> pending=new LinkedHashSet<>();manifest.resources().forEach(r->pending.add(r.key()));int created=0,updated=0;
    while(!pending.isEmpty()){
      boolean progressed=false;
      for(var resource:manifest.resources())if(pending.contains(resource.key())&&(resource.parent()==null||exists(resource.parent()))){
        boolean exists=exists(resource.key());upsert(resource);replaceActions(resource);pending.remove(resource.key());
        if(exists)updated++;else created++;progressed=true;
      }
      if(!progressed)throw new IllegalArgumentException("manifest contains a missing parent or cycle: "+pending);
    }
    var keys=manifest.resources().stream().map(ResourceDefinition::key).toList();
    int deprecated=db.sql("""
      with recursive tree as (select id,resource_key from resource where resource_key=:root union all
      select r.id,r.resource_key from resource r join tree p on r.parent_id=p.id)
      update resource set status='DEPRECATED',version=version+1,updated_at=now()
      where id in(select id from tree) and source='APPLICATION_MANIFEST' and not(resource_key=any(:keys)) and status<>'DEPRECATED'
      """).param("root",root).param("keys",keys.toArray(String[]::new)).update();
    db.sql("insert into resource_manifest_import(application_key,manifest_version,checksum,imported_by,payload) values(:app,:version,:checksum,:actor,cast(:payload as jsonb))")
        .param("app",root).param("version",manifest.manifestVersion()).param("checksum",checksum).param("actor",actor).param("payload",write(manifest)).update();
    return new SyncResult(created,updated,deprecated,false,checksum);
  }

  void validate(DefinitionManifest manifest,String root){
    Map<String,ResourceDefinition> resources=new HashMap<>();for(var r:manifest.resources()){
      if(resources.put(r.key(),r)!=null)throw new IllegalArgumentException("duplicate resource key: "+r.key());
      if(!TYPES.contains(r.type()))throw new IllegalArgumentException("unsupported resource type: "+r.type());
      if(r.key().matches("(?i).*(create|delete|edit|export|approve|reject)[-_]?button.*"))throw new IllegalArgumentException("action buttons are not resources: "+r.key());
      if(r.key().contains("/api/")||r.key().matches("(?i)^(GET|POST|PUT|PATCH|DELETE)-.*"))throw new IllegalArgumentException("API URLs are bindings, not resources");
      if(!r.key().matches("^[a-z][a-z0-9_-]*:[a-z0-9][a-z0-9._/-]*$")||!r.key().startsWith(PREFIXES.get(r.type())))throw new IllegalArgumentException("resource key must be normalized and match its semantic type: "+r.key());
      if("EXTERNAL_RESOURCE".equals(r.type())&&(blank(r.provider())||blank(r.externalType())||blank(r.externalId())))throw new IllegalArgumentException("external resource binding is required");
    }
    if(!resources.containsKey(root)||!"APPLICATION".equals(resources.get(root).type()))throw new IllegalArgumentException("manifest must contain its application root");
  }
  private void upsert(ResourceDefinition r){if(exists(r.key())){String stored=db.sql("select type::text from resource where resource_key=:key").param("key",r.key()).query(String.class).single();if(!stored.equals(r.type()))throw new IllegalArgumentException("resource type is immutable for "+r.key());}UUID parent=r.parent()==null?null:db.sql("select id from resource where resource_key=:key").param("key",r.parent()).query(UUID.class).single();db.sql("""
    insert into resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,status,source,metadata,external_system,external_type,external_id)
    values(:key,cast(:type as resource_type),:parent,:fa,:en,:owner,:classification,'ACTIVE','APPLICATION_MANIFEST',cast(:metadata as jsonb),:provider,:externalType,:externalId)
    on conflict(resource_key) do update set parent_id=excluded.parent_id,name_fa=excluded.name_fa,name_en=excluded.name_en,
    owner_domain=excluded.owner_domain,classification=excluded.classification,status='ACTIVE',metadata=excluded.metadata,
    external_system=excluded.external_system,external_type=excluded.external_type,external_id=excluded.external_id,version=resource.version+1,updated_at=now()
    """).param("key",r.key()).param("type",r.type()).param("parent",parent).param("fa",r.nameFa()).param("en",r.nameEn())
      .param("owner",r.ownerDomain()).param("classification",r.classification()).param("metadata",write(r.metadata()==null?Map.of():r.metadata()))
      .param("provider",r.provider()).param("externalType",r.externalType()).param("externalId",r.externalId()).update();
    if("EXTERNAL_RESOURCE".equals(r.type()))db.sql("""
      insert into resource_external_binding(resource_id,provider,external_type,external_id,metadata)
      select id,:provider,:externalType,:externalId,cast(:metadata as jsonb) from resource where resource_key=:key
      on conflict(provider,external_type,external_id) do update set resource_id=excluded.resource_id,metadata=excluded.metadata,active=true,version=resource_external_binding.version+1,updated_at=now()
      """).param("provider",r.provider()).param("externalType",r.externalType()).param("externalId",r.externalId()).param("metadata",write(r.metadata()==null?Map.of():r.metadata())).param("key",r.key()).update();}
  private void replaceActions(ResourceDefinition r){UUID id=db.sql("select id from resource where resource_key=:key").param("key",r.key()).query(UUID.class).single();db.sql("delete from resource_action where resource_id=:id").param("id",id).update();for(String action:r.actions()){int inserted=db.sql("insert into resource_action(resource_id,action_id) select :id,id from action where action_key=:action on conflict do nothing").param("id",id).param("action",action).update();if(inserted==0)throw new IllegalArgumentException("unknown or duplicate action: "+action);}}
  private boolean exists(String key){return db.sql("select count(*) from resource where resource_key=:key").param("key",key).query(Long.class).single()>0;}
  private String latestVersion(String root){return db.sql("select coalesce(max(manifest_version),'catalog') from resource_manifest_import where application_key=:root").param("root",root).query(String.class).single();}
  private String checksum(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("invalid manifest",e);}}
  private static boolean blank(String value){return value==null||value.isBlank();}

  public record DefinitionManifest(@NotBlank String application,@NotBlank String manifestVersion,@NotEmpty List<@Valid ResourceDefinition> resources){}
  public record ResourceDefinition(@NotBlank String key,@NotBlank String type,String parent,@NotBlank String nameFa,@NotBlank String nameEn,String ownerDomain,String classification,List<String> actions,String status,String source,Map<String,Object> metadata,String provider,String externalType,String externalId){public ResourceDefinition{actions=actions==null?List.of():List.copyOf(actions);}}
  public record SyncResult(int created,int updated,int deprecated,boolean idempotent,String checksum){}
}
