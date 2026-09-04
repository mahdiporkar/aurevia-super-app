package com.aurevia.authz.sync;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxRepository implements OutboxRepository {
  private final JdbcClient database;
  JdbcOutboxRepository(JdbcClient database) { this.database=database; }

  @Override public List<Event> claim(UUID owner,int timeout,int limit) {
    return database.sql("""
        with candidates as (
          select candidate.id from outbox_event candidate
          where candidate.processed_at is null and candidate.dead_lettered_at is null
            and candidate.available_at <= now()
            and (candidate.claimed_at is null
              or candidate.claimed_at < now()-make_interval(secs => :claimTimeout))
            and not exists (
              select 1 from outbox_event older
              where older.aggregate_type=candidate.aggregate_type
                and older.aggregate_id=candidate.aggregate_id
                and older.sequence<candidate.sequence and older.processed_at is null)
          order by candidate.sequence for update skip locked limit :limit
        )
        update outbox_event event set claimed_at=now(),claim_owner=:owner
        from candidates where event.id=candidates.id
        returning event.id,event.event_type as "eventType",event.payload->>'user' as "user",
          event.payload->>'relation' as relation,event.payload->>'object' as object
        """).param("claimTimeout",timeout).param("limit",limit).param("owner",owner)
        .query(Event.class).list();
  }

  @Override public boolean markApplied(UUID id,UUID owner,int maxAttempts) {
    int updated=database.sql("""
        update outbox_event set processed_at=now(),attempts=attempts+1,last_error=null,
          claimed_at=null,claim_owner=null where id=:id and claim_owner=:owner
        """).param("id",id).param("owner",owner).update();
    if(updated==1) updateGrantProjection(id,false,maxAttempts);
    return updated==1;
  }

  @Override public void markRetry(UUID id,UUID owner,String error,int maxAttempts) {
    database.sql("""
        update outbox_event set attempts=attempts+1,
          available_at=now()+make_interval(secs=>least(300,
            cast(power(2,least(attempts,10)) as integer))),
          dead_lettered_at=case when attempts+1>=:max then now() else null end,
          last_error=:error,claimed_at=null,claim_owner=null
        where id=:id and claim_owner=:owner
        """).param("id",id).param("owner",owner).param("max",maxAttempts)
        .param("error",error).update();
    updateGrantProjection(id,true,maxAttempts);
  }

  private void updateGrantProjection(UUID eventId,boolean failure,int maxAttempts) {
    database.sql("""
        update application_group_grant set status=case
          when :failure and (select attempts from outbox_event where id=:id)>=:max
            then 'FAILED'::projection_status
          when :failure then 'RETRYING'::projection_status
          when revoked_at is null then 'APPLIED'::projection_status
          else 'REVOKED'::projection_status end
        where id=(select aggregate_id from outbox_event where id=:id)
          and (select aggregate_type from outbox_event where id=:id)='application-group-grant'
        """).param("id",eventId).param("max",maxAttempts).param("failure",failure).update();
  }
}
