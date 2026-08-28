package com.aurevia.authz.sync;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Claims outbox rows with SKIP LOCKED. Unknown events remain pending for an owning adapter. */
@Component public class OutboxReconciler {
 private final JdbcClient db; public OutboxReconciler(JdbcClient db){this.db=db;}
 @Scheduled(fixedDelayString="${aurevia.outbox.interval-ms:5000}") @Transactional
 public void reconcile(){
  var rows=db.sql("select id,event_type from outbox_event where processed_at is null and available_at<=now() order by created_at for update skip locked limit 50").query().listOfRows();
  for(var row:rows){String event=(String)row.get("event_type");UUID id=(UUID)row.get("id");
   if(event.startsWith("PANEL_")) db.sql("update outbox_event set processed_at=now(),attempts=attempts+1,last_error=null where id=:id").param("id",id).update();
   else db.sql("update outbox_event set attempts=attempts+1,available_at=now()+interval '30 seconds',last_error='adapter not configured; fail closed' where id=:id").param("id",id).update();
  }
 }
}
