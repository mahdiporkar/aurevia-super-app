package com.aurevia.authz.api;

import com.aurevia.authz.directory.DirectoryDnParser;
import com.aurevia.authz.directory.OuAccessService;
import com.aurevia.authz.directory.OuRuleEvaluator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Narrow, validated administration surface for directory-derived application access. */
@RestController
@RequestMapping("/internal/v1/registry/ou-access")
public class OuAccessAdminController {
  private static final Set<String> MODES=Set.of("EXACT","SUBTREE");
  private static final Set<String> COMBINERS=Set.of("ANY_OF","ALL_OF");
  private final JdbcClient db;private final OuAccessService calculator;
  public OuAccessAdminController(JdbcClient db,OuAccessService calculator){this.db=db;this.calculator=calculator;}

  @GetMapping("/ous") public List<Map<String,Object>> ous(){return db.sql("""
    select o.*,count(a.id) filter(where a.active) user_count from directory_ou o
    left join user_ou_assignment a on a.ou_id=o.id group by o.id order by external_path
    """).query().listOfRows();}
  @GetMapping("/groups") public List<Map<String,Object>> groups(){return db.sql("""
    select g.*,count(distinct m.user_id) filter(where m.active) member_count from access_group g
    left join effective_group_membership m on m.access_group_id=g.id group by g.id order by g.code
    """).query().listOfRows();}
  @GetMapping("/groups/{id}/rules") public List<Map<String,Object>> rules(@PathVariable UUID id){return db.sql("select r.*,o.name ou_name,o.external_path,o.external_dn from access_group_ou_rule r join directory_ou o on o.id=r.ou_id where r.access_group_id=:id order by o.external_path").param("id",id).query().listOfRows();}
  @GetMapping("/groups/{id}/members") public List<Map<String,Object>> members(@PathVariable UUID id){return db.sql("select u.id user_id,u.username,u.display_name,m.source_type,m.source_id,m.calculated_at,m.membership_version from effective_group_membership m join app_user u on u.id=m.user_id where m.access_group_id=:id and m.active order by u.username").param("id",id).query().listOfRows();}

  @PostMapping("/groups") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createGroup(@Valid @RequestBody GroupWrite x,@RequestHeader("X-Actor") String actor){validateGroup(x);UUID id=UUID.randomUUID();db.sql("insert into access_group(id,code,name,description,group_type,rule_combiner,created_by) values(:id,:code,:name,:description,'CALCULATED',cast(:combiner as rule_combiner),:actor)").param("id",id).param("code",x.code()).param("name",x.name()).param("description",x.description()).param("combiner",x.ruleCombiner()).param("actor",actor).update();audit(actor,"ACCESS_GROUP_CREATED","access_group",x.code(),Map.of("combiner",x.ruleCombiner()));return Map.of("id",id,"version",0);}
  @PutMapping("/groups/{id}") @Transactional
  public Map<String,Object> updateGroup(@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody GroupWrite x,@RequestHeader("X-Actor") String actor){validateGroup(x);String currentCode=db.sql("select code from access_group where id=:id").param("id",id).query(String.class).single();if(!currentCode.equals(x.code()))throw new IllegalArgumentException("access group code is immutable");int n=db.sql("update access_group set name=:name,description=:description,rule_combiner=cast(:combiner as rule_combiner),active=:active,version=version+1,updated_at=now() where id=:id and version=:version").param("id",id).param("version",version).param("name",x.name()).param("description",x.description()).param("combiner",x.ruleCombiner()).param("active",x.active()).update();if(n==0)throw new org.springframework.dao.OptimisticLockingFailureException("access group changed or missing");recalculateAll();audit(actor,"ACCESS_GROUP_UPDATED","access_group",x.code(),Map.of("version",version+1));return Map.of("id",id,"version",version+1);}

  @PostMapping("/groups/{id}/rules") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> addRule(@PathVariable UUID id,@Valid @RequestBody RuleWrite x,@RequestHeader("X-Actor") String actor){String mode=x.matchMode().toUpperCase(Locale.ROOT);if(!MODES.contains(mode))throw new IllegalArgumentException("matchMode must be EXACT or SUBTREE");UUID rule=UUID.randomUUID();db.sql("insert into access_group_ou_rule(id,access_group_id,ou_id,match_mode,created_by) values(:rule,:group,:ou,cast(:mode as ou_match_mode),:actor)").param("rule",rule).param("group",id).param("ou",x.ouId()).param("mode",mode).param("actor",actor).update();recalculateAll();audit(actor,"OU_RULE_CREATED","access_group",id.toString(),Map.of("ruleId",rule,"matchMode",mode));return Map.of("id",rule,"version",0);}
  @DeleteMapping("/groups/{groupId}/rules/{ruleId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void removeRule(@PathVariable UUID groupId,@PathVariable UUID ruleId,@RequestHeader("X-Actor") String actor){db.sql("update access_group_ou_rule set active=false,version=version+1,updated_at=now() where id=:rule and access_group_id=:group").param("rule",ruleId).param("group",groupId).update();recalculateAll();audit(actor,"OU_RULE_DISABLED","access_group",groupId.toString(),Map.of("ruleId",ruleId));}

  @PostMapping("/groups/{id}/preview") public Map<String,Object> preview(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> ignored){String combiner=db.sql("select rule_combiner::text from access_group where id=:id").param("id",id).query(String.class).single();var rules=db.sql("select r.match_mode::text mode,o.external_dn from access_group_ou_rule r join directory_ou o on o.id=r.ou_id where r.access_group_id=:id and r.active and o.active").param("id",id).query().listOfRows();var candidates=db.sql("select u.id,u.username,u.display_name,o.external_dn from app_user u join user_ou_assignment a on a.user_id=u.id and a.active join directory_ou o on o.id=a.ou_id and o.active order by u.username").query().listOfRows();var members=candidates.stream().filter(user->OuRuleEvaluator.combine(rules.stream().map(rule->OuRuleEvaluator.matches((String)user.get("external_dn"),(String)rule.get("external_dn"),(String)rule.get("mode"))).toList(),combiner)).map(user->{Map<String,Object> row=new LinkedHashMap<>(user);row.remove("external_dn");return row;}).toList();Set<UUID> current=new HashSet<>(db.sql("select distinct user_id from effective_group_membership where access_group_id=:group and active").param("group",id).query(UUID.class).list());long added=members.stream().map(x->(UUID)x.get("id")).filter(x->!current.contains(x)).count();long removed=current.stream().filter(x->members.stream().noneMatch(m->x.equals(m.get("id")))).count();return Map.of("members",members,"memberCount",members.size(),"wouldAdd",added,"wouldRemove",removed,"combiner",combiner);}

  @GetMapping("/application-grants") public List<Map<String,Object>> grants(){return db.sql("select g.*,p.code application_code,p.name_fa application_name,a.code group_code,a.name group_name from application_group_grant g join panel p on p.id=g.application_id join access_group a on a.id=g.access_group_id order by p.code,a.code,g.granted_at desc").query().listOfRows();}
  @PostMapping("/application-grants") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> grant(@Valid @RequestBody GrantWrite x,@RequestHeader("X-Actor") String actor){var target=db.sql("select p.slug,a.code from panel p cross join access_group a where p.id=:panel and a.id=:group and p.active and a.active").param("panel",x.applicationId()).param("group",x.accessGroupId()).query().singleRow();UUID id=UUID.randomUUID();db.sql("insert into application_group_grant(id,application_id,access_group_id,granted_by) values(:id,:panel,:group,:actor)").param("id",id).param("panel",x.applicationId()).param("group",x.accessGroupId()).param("actor",actor).update();enqueueGrant(id,"APPLICATION_GROUP_GRANT_WRITE",(String)target.get("code"),(String)target.get("slug"));audit(actor,"APPLICATION_GROUP_GRANTED","application_group_grant",id.toString(),Map.of());return Map.of("id",id,"status","PENDING","version",0);}
  @DeleteMapping("/application-grants/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void revoke(@PathVariable UUID id,@RequestHeader("X-Actor") String actor){var target=db.sql("select a.code,p.slug from application_group_grant g join access_group a on a.id=g.access_group_id join panel p on p.id=g.application_id where g.id=:id and g.revoked_at is null").param("id",id).query().singleRow();db.sql("update application_group_grant set status='PENDING',revoked_by=:actor,revoked_at=now(),version=version+1 where id=:id").param("id",id).param("actor",actor).update();enqueueGrant(id,"APPLICATION_GROUP_GRANT_DELETE",(String)target.get("code"),(String)target.get("slug"));audit(actor,"APPLICATION_GROUP_REVOKED","application_group_grant",id.toString(),Map.of());}

  @GetMapping("/users/{id}/explain") public Map<String,Object> explain(@PathVariable UUID id){var user=db.sql("select id,username,display_name,membership_version from app_user where id=:id").param("id",id).query().singleRow();var ouRows=db.sql("select o.id,o.external_path,o.external_dn,o.last_synced_at from user_ou_assignment a join directory_ou o on o.id=a.ou_id where a.user_id=:id and a.active").param("id",id).query().listOfRows();Map<String,Object> ou=ouRows.isEmpty()?Map.of():ouRows.getFirst();var paths=db.sql("select g.code,g.name,r.match_mode,o.external_path rule_path,m.source_type,m.source_id,m.calculated_at from effective_group_membership m join access_group g on g.id=m.access_group_id left join access_group_ou_rule r on r.id=m.source_id left join directory_ou o on o.id=r.ou_id where m.user_id=:id and m.active order by g.code").param("id",id).query().listOfRows();var applications=db.sql("select distinct p.code,p.name_fa,g.code group_code,ag.status from effective_group_membership m join access_group g on g.id=m.access_group_id join application_group_grant ag on ag.access_group_id=g.id and ag.revoked_at is null join panel p on p.id=ag.application_id where m.user_id=:id and m.active order by p.code").param("id",id).query().listOfRows();return Map.of("user",user,"ou",ou,"membershipPaths",paths,"applications",applications);}

  private void validateGroup(GroupWrite x){if(x.code()==null||!x.code().matches("^[A-Z][A-Z0-9_]{2,159}$"))throw new IllegalArgumentException("invalid access group code");if(!COMBINERS.contains(x.ruleCombiner()))throw new IllegalArgumentException("ruleCombiner must be ANY_OF or ALL_OF");}
  private void recalculateAll(){db.sql("select id from app_user").query(UUID.class).list().forEach(calculator::recalculateUser);}
  private void enqueueGrant(UUID id,String event,String code,String slug){db.sql("insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key) values('application-group-grant',:id,:event,jsonb_build_object('user','group:'||lower(:code)||'#member','relation','viewer','object','application:aurevia/'||:slug),:event||':'||:id||':'||(select version from application_group_grant where id=:id))").param("id",id).param("event",event).param("code",code).param("slug",slug).update();}
  private void audit(String actor,String event,String type,String key,Map<String,?> details){db.sql("insert into audit_event(actor_key,event_type,target_type,target_key,correlation_id,safe_details) values(:actor,:event,:type,:key,:correlation,cast(:details as jsonb))").param("actor",actor).param("event",event).param("type",type).param("key",key).param("correlation",UUID.randomUUID().toString()).param("details",json(details)).update();}
  private static String json(Map<String,?> m){return m.entrySet().stream().map(e->"\""+e.getKey()+"\":\""+String.valueOf(e.getValue()).replace("\"","\\\"")+"\"").collect(java.util.stream.Collectors.joining(",","{","}"));}
  public record GroupWrite(@NotBlank String code,@NotBlank String name,String description,@NotBlank String ruleCombiner,boolean active){}
  public record RuleWrite(@NotNull UUID ouId,@NotBlank String matchMode){}
  public record GrantWrite(@NotNull UUID applicationId,@NotNull UUID accessGroupId){}
}
