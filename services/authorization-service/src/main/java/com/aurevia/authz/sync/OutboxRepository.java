package com.aurevia.authz.sync;

import java.util.List;
import java.util.UUID;

interface OutboxRepository {
  List<Event> claim(UUID owner,int claimTimeoutSeconds,int limit);
  boolean markApplied(UUID eventId,UUID owner,int maxAttempts);
  void markRetry(UUID eventId,UUID owner,String error,int maxAttempts);

  record Event(UUID id,String eventType,String user,String relation,String object) {}
}
