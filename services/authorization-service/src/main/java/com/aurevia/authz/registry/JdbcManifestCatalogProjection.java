package com.aurevia.authz.registry;

import com.aurevia.authz.semantics.ResourceObjectKey;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Captures catalog-owned tuples and enqueues their delta in the caller's SQL transaction. */
@Component
public class JdbcManifestCatalogProjection {
  private final JdbcClient db;
  public JdbcManifestCatalogProjection(JdbcClient db) { this.db=db; }

  public Set<Tuple> snapshot(UUID panel) {
    Set<Tuple> result=new LinkedHashSet<>(db.sql("""
        select distinct t."user",t.relation,t.object from catalog_grant_tuple t
        join resource r on r.id=t.resource_id where r.panel_id=:panel and r.source='MANIFEST'
        """).param("panel",panel).query(Tuple.class).list());
    result.addAll(db.sql("""
        select 'group:'||lower(a.code)||'#member' as "user",'viewer' as relation,
          'application:aurevia/'||p.slug as object
        from application_group_grant g join access_group a on a.id=g.access_group_id
          join panel p on p.id=g.application_id
        where p.id=:panel and p.active and a.active and g.revoked_at is null
        """).param("panel",panel).query(Tuple.class).list());
    result.addAll(db.sql("""
        select c.resource_key,c.type::text,p.resource_key parent_key,p.type::text parent_type
        from resource c join resource p on p.id=c.parent_id
        where c.panel_id=:panel and c.source='MANIFEST' and c.status='ACTIVE'
        """).param("panel",panel).query((rs,row)->new Tuple(
            ResourceObjectKey.from(rs.getString("parent_type"),rs.getString("parent_key")),
            "parent",ResourceObjectKey.from(rs.getString("type"),rs.getString("resource_key")))).list());
    return result;
  }

  public void enqueueChanges(UUID panel,Set<Tuple> before) {
    Set<Tuple> after=snapshot(panel);
    before.stream().filter(t->!after.contains(t)).forEach(t->enqueue(panel,t,false));
    after.stream().filter(t->!before.contains(t)).forEach(t->enqueue(panel,t,true));
  }

  private void enqueue(UUID panel,Tuple tuple,boolean write) {
    String event=(tuple.relation().equals("parent")?"RESOURCE_PARENT_":"GRANT_")+(write?"WRITE":"DELETE");
    db.sql("""
        insert into outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
        values('manifest-projection',:panel,:event,jsonb_build_object(
          'user',:user,'relation',:relation,'object',:object,'manifestPanelId',cast(:panel as text)),:key)
        """).param("panel",panel).param("event",event).param("user",tuple.user())
        .param("relation",tuple.relation()).param("object",tuple.object())
        .param("key","manifest:"+UUID.randomUUID()).update();
  }

  public record Tuple(String user,String relation,String object) {}
}
