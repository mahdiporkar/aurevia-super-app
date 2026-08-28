package com.aurevia.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Administrative registry API. Panel metadata is deliberately independent from roles. */
@RestController @RequestMapping("/internal/v1/registry")
public class RegistryController {
  private final JdbcClient db;
  public RegistryController(JdbcClient db){this.db=db;}

  @GetMapping("/panels") public List<Map<String,Object>> panels(){
    return db.sql("select id,code,name_fa,name_en,slug,remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,integrity,active,sort_order,version from panel order by sort_order,code").query().listOfRows();
  }
  @PostMapping("/panels") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createPanel(@Valid @RequestBody PanelWrite p){
    UUID id=UUID.randomUUID();
    db.sql("insert into panel(id,code,name_fa,name_en,slug,remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,integrity,active,sort_order) values(:id,:code,:fa,:en,:slug,:remote,:module,:route,:semver,:contract,:integrity,:active,:sort)")
      .param("id",id).param("code",p.code()).param("fa",p.nameFa()).param("en",p.nameEn()).param("slug",p.slug()).param("remote",p.remoteEntry()).param("module",p.exposedModule()).param("route",p.routeBasePath()).param("semver",p.semanticVersion()).param("contract",p.contractVersion()).param("integrity",p.integrity()).param("active",p.active()).param("sort",p.sortOrder()).update();
    outbox("panel",id,"PANEL_CREATED",p.code()); return Map.of("id",id,"version",0);
  }
  @PutMapping("/panels/{id}") @Transactional
  public Map<String,Object> updatePanel(@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody PanelWrite p){
    int n=db.sql("update panel set code=:code,name_fa=:fa,name_en=:en,slug=:slug,remote_entry_path=:remote,exposed_module=:module,route_base_path=:route,semantic_version=:semver,contract_version=:contract,integrity=:integrity,active=:active,sort_order=:sort,version=version+1,updated_at=now() where id=:id and version=:version")
      .param("id",id).param("version",version).param("code",p.code()).param("fa",p.nameFa()).param("en",p.nameEn()).param("slug",p.slug()).param("remote",p.remoteEntry()).param("module",p.exposedModule()).param("route",p.routeBasePath()).param("semver",p.semanticVersion()).param("contract",p.contractVersion()).param("integrity",p.integrity()).param("active",p.active()).param("sort",p.sortOrder()).update();
    if(n==0)throw new OptimisticLockingFailureException("panel changed or missing"); outbox("panel",id,"PANEL_UPDATED",p.code()); return Map.of("id",id,"version",version+1);
  }
  @DeleteMapping("/panels/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void archivePanel(@PathVariable UUID id,@RequestParam long version){
    int n=db.sql("update panel set active=false,version=version+1,updated_at=now() where id=:id and version=:version").param("id",id).param("version",version).update();
    if(n==0)throw new OptimisticLockingFailureException("panel changed or missing"); outbox("panel",id,"PANEL_ARCHIVED",id.toString());
  }
  @GetMapping("/audit") public List<Map<String,Object>> audit(@RequestParam(defaultValue="100") int limit){return db.sql("select * from audit_event order by occurred_at desc limit :limit").param("limit",Math.min(Math.max(limit,1),500)).query().listOfRows();}
  private void outbox(String type,UUID id,String event,String key){
    db.sql("insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key) values(:type,:id,:event,jsonb_build_object('key',cast(:key as text)),:idem)").param("type",type).param("id",id).param("event",event).param("key",key).param("idem",event+":"+id+":"+UUID.randomUUID()).update();
  }
  public record PanelWrite(@NotBlank String code,@NotBlank String nameFa,@NotBlank String nameEn,@NotBlank String slug,@NotBlank String remoteEntry,@NotBlank String exposedModule,@NotBlank String routeBasePath,@NotBlank String semanticVersion,@NotBlank String contractVersion,String integrity,boolean active,int sortOrder){}
}
