package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Administrative registry API. Panel metadata is deliberately independent from roles. */
@RestController @RequestMapping("/internal/v1/registry")
public class RegistryController {
  private static final Set<String> RESERVED=Set.of("login","admin","settings","api","assets","error");
  private final JdbcClient db;
  public RegistryController(JdbcClient db){this.db=db;}

  @GetMapping("/panels") public List<Map<String,Object>> panels(){
    return db.sql("select id,code,name_fa,name_en,description,slug,service_slug,remote_name,default_route_id,remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,integrity,active,sort_order,active_artifact_id,version from panel order by sort_order,code").query().listOfRows();
  }
  @PostMapping("/panels") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createPanel(@Valid @RequestBody PanelWrite p){
    validate(p,null);
    UUID id=UUID.randomUUID();
    db.sql("insert into panel(id,code,name_fa,name_en,description,slug,service_slug,remote_name,default_route_id,remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,integrity,active,sort_order) values(:id,:code,:fa,:en,:description,:slug,:serviceSlug,:remoteName,:defaultRoute,:remote,:module,:route,:semver,:contract,:integrity,:active,:sort)")
      .param("id",id).param("code",p.code()).param("fa",p.nameFa()).param("en",p.nameEn()).param("description",p.description()).param("slug",p.slug()).param("serviceSlug",value(p.serviceSlug(),p.slug())).param("remoteName",value(p.remoteName(),"aurevia_"+p.slug().replace("-","_"))).param("defaultRoute",value(p.defaultRouteId(),"index")).param("remote",p.remoteEntry()).param("module",p.exposedModule()).param("route",p.routeBasePath()).param("semver",p.semanticVersion()).param("contract",p.contractVersion()).param("integrity",p.integrity()).param("active",p.active()).param("sort",p.sortOrder()).update();
    outbox("panel",id,"PANEL_CREATED",p.code()); return Map.of("id",id,"version",0);
  }
  @PutMapping("/panels/{id}") @Transactional
  public Map<String,Object> updatePanel(@PathVariable("id") UUID id,@RequestParam("version") long version,@Valid @RequestBody PanelWrite p){
    validate(p,id);
    int n=db.sql("update panel set code=:code,name_fa=:fa,name_en=:en,description=:description,slug=:slug,service_slug=:serviceSlug,remote_name=:remoteName,default_route_id=:defaultRoute,remote_entry_path=:remote,exposed_module=:module,route_base_path=:route,semantic_version=:semver,contract_version=:contract,integrity=:integrity,active=:active,sort_order=:sort,version=version+1,updated_at=now() where id=:id and version=:version")
      .param("id",id).param("version",version).param("code",p.code()).param("fa",p.nameFa()).param("en",p.nameEn()).param("description",p.description()).param("slug",p.slug()).param("serviceSlug",value(p.serviceSlug(),p.slug())).param("remoteName",value(p.remoteName(),"aurevia_"+p.slug().replace("-","_"))).param("defaultRoute",value(p.defaultRouteId(),"index")).param("remote",p.remoteEntry()).param("module",p.exposedModule()).param("route",p.routeBasePath()).param("semver",p.semanticVersion()).param("contract",p.contractVersion()).param("integrity",p.integrity()).param("active",p.active()).param("sort",p.sortOrder()).update();
    if(n==0)throw new OptimisticLockingFailureException("panel changed or missing"); outbox("panel",id,"PANEL_UPDATED",p.code()); return Map.of("id",id,"version",version+1);
  }
  @DeleteMapping("/panels/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void archivePanel(@PathVariable("id") UUID id,@RequestParam("version") long version){
    int n=db.sql("update panel set active=false,version=version+1,updated_at=now() where id=:id and version=:version").param("id",id).param("version",version).update();
    if(n==0)throw new OptimisticLockingFailureException("panel changed or missing"); outbox("panel",id,"PANEL_ARCHIVED",id.toString());
  }
  @GetMapping("/audit") public List<Map<String,Object>> audit(@RequestParam(name="limit",defaultValue="100") int limit){return db.sql("select * from audit_event order by occurred_at desc limit :limit").param("limit",Math.min(Math.max(limit,1),500)).query().listOfRows();}
  private void outbox(String type,UUID id,String event,String key){
    db.sql("insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key) values(:type,:id,:event,jsonb_build_object('key',cast(:key as text)),:idem)").param("type",type).param("id",id).param("event",event).param("key",key).param("idem",event+":"+id+":"+UUID.randomUUID()).update();
  }
  private void validate(PanelWrite p,UUID id){String prefix=p.routeBasePath().replaceFirst("^/","");if(!prefix.matches("^[a-z][a-z0-9-]{1,49}$"))throw new IllegalArgumentException("invalid routePrefix");if(RESERVED.contains(prefix)){String old=id==null?null:db.sql("select route_base_path from panel where id=:id").param("id",id).query(String.class).optional().orElse(null);if(!p.routeBasePath().equals(old))throw new IllegalArgumentException("reserved routePrefix");}Long collision=id==null?db.sql("select count(*) from panel where route_base_path=:path").param("path","/"+prefix).query(Long.class).single():db.sql("select count(*) from panel where route_base_path=:path and id<>:id").param("path","/"+prefix).param("id",id).query(Long.class).single();if(collision>0)throw new IllegalArgumentException("routePrefix already exists");}
  private static String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
  public record PanelWrite(@NotBlank String code,@NotBlank String nameFa,@NotBlank String nameEn,String description,@NotBlank String slug,String serviceSlug,String remoteName,String defaultRouteId,
      @NotBlank @Pattern(regexp="https?://.+", message="remoteEntry must be a complete http(s) URL") String remoteEntry,
      @NotBlank String exposedModule,@NotBlank String routeBasePath,@NotBlank String semanticVersion,@NotBlank String contractVersion,String integrity,boolean active,int sortOrder){}
}
