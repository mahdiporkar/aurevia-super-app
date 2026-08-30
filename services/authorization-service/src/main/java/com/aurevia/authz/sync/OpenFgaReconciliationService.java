package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenFgaReconciliationService {
  private final JdbcClient database;
  private final RelationshipAuthorizationPort relationships;
  private final RestClient openfga;
  private final String storeId;

  public OpenFgaReconciliationService(JdbcClient database,
      RelationshipAuthorizationPort relationships, RestClient.Builder rest,
      @Value("${aurevia.openfga.base-url}") String baseUrl,
      @Value("${aurevia.openfga.store-id}") String storeId) {
    this.database=database;this.relationships=relationships;
    this.openfga=rest.baseUrl(baseUrl).build();this.storeId=storeId;
  }

  public Report reconcile(boolean repair) {
    Set<Tuple> expected=expected();Set<Tuple> actual=actual();
    Set<Tuple> missing=new LinkedHashSet<>(expected);missing.removeAll(actual);
    Set<Tuple> unexpected=new LinkedHashSet<>(actual);unexpected.removeAll(expected);
    if(repair){missing.forEach(t->relationships.write(t.user(),t.relation(),t.object()));unexpected.forEach(t->relationships.delete(t.user(),t.relation(),t.object()));}
    return new Report(!repair,expected.size(),actual.size(),List.copyOf(missing),
        List.copyOf(unexpected),repair ? missing.size()+unexpected.size() : 0);
  }

  private Set<Tuple> expected(){
    Set<Tuple> tuples=new LinkedHashSet<>();
    tuples.addAll(database.sql("""
      select case g.subject_type when 'USER' then 'user:'||u.external_id
        when 'GROUP' then 'group:'||dg.external_id||'#member'
        when 'ROLE' then 'role:'||ar.role_key||'#assignee' end "user",
        g.relation,
        case r.type when 'APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','')
          when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
          else 'resource:'||replace(r.resource_key,':','/') end object
      from authorization_grant g left join app_user u on g.subject_type='USER' and u.id=g.subject_id
      left join directory_group dg on g.subject_type='GROUP' and dg.id=g.subject_id
      left join application_role ar on g.subject_type='ROLE' and ar.id=g.subject_id
      join resource r on r.id=g.resource_id
      where g.status='ACTIVE' and (g.expires_at is null or g.expires_at>now())
      """).query(Tuple.class).list());
    tuples.addAll(database.sql("""
      select case p.type when 'APPLICATION' then 'application:'||regexp_replace(p.resource_key,'^application:','')
        when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(p.resource_key,'^external_resource:',''),':','/')
        else 'resource:'||replace(p.resource_key,':','/') end "user",'parent' relation,
        case c.type when 'APPLICATION' then 'application:'||regexp_replace(c.resource_key,'^application:','')
        when 'EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(c.resource_key,'^external_resource:',''),':','/')
        else 'resource:'||replace(c.resource_key,':','/') end object
      from resource c join resource p on p.id=c.parent_id where c.status='ACTIVE'
      """).query(Tuple.class).list());
    tuples.addAll(database.sql("""
      select 'user:'||u.external_id "user",'member' relation,'group:'||g.external_id object
      from user_group_membership m join app_user u on u.id=m.user_id
      join directory_group g on g.id=m.group_id
      """).query(Tuple.class).list());
    tuples.addAll(database.sql("""
      select 'user:'||u.external_id "user",'assignee' relation,'role:'||r.role_key object
      from user_role_assignment x join app_user u on u.id=x.user_id
      join application_role r on r.id=x.role_id where x.expires_at is null or x.expires_at>now()
      union all
      select 'group:'||g.external_id||'#member','assignee','role:'||r.role_key
      from group_role_assignment x join directory_group g on g.id=x.group_id
      join application_role r on r.id=x.role_id where x.expires_at is null or x.expires_at>now()
      """).query(Tuple.class).list());
    return tuples;
  }

  private Set<Tuple> actual(){
    Set<Tuple> result=new LinkedHashSet<>();String token="";
    do {
      final String continuation=token;
      Map<String,Object> request=continuation.isBlank()?Map.of("page_size",100):Map.of("page_size",100,"continuation_token",continuation);
      JsonNode response=openfga.post().uri("/stores/{store}/read",storeId)
          .body(request).retrieve().body(JsonNode.class);
      if(response==null)throw new IllegalStateException("Empty OpenFGA tuple response");
      response.path("tuples").forEach(node->{JsonNode key=node.path("key");result.add(new Tuple(
          key.path("user").asText(),key.path("relation").asText(),key.path("object").asText()));});
      token=response.path("continuation_token").asText("");
    } while(!token.isBlank());
    return result;
  }

  public record Tuple(String user,String relation,String object){}
  public record Report(boolean dryRun,int expectedCount,int actualCount,List<Tuple> missing,
      List<Tuple> unexpected,int repairedCount){}
}
