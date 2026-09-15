package com.aurevia.authz.directory;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Resumable, bounded worker for recalculating OU-derived effective memberships. */
@Component
public final class OuRecalculationWorker {
  private final OuDirectoryRepository directory;
  private final OuAccessService access;
  private final int batchSize;
  private final int maxAttempts;

  public OuRecalculationWorker(OuDirectoryRepository directory,OuAccessService access,
      @Value("${aurevia.directory.recalculation-batch-size:100}") int batchSize,
      @Value("${aurevia.directory.recalculation-max-attempts:8}") int maxAttempts) {
    this.directory=directory;this.access=access;this.batchSize=batchSize;this.maxAttempts=maxAttempts;
  }

  @Scheduled(fixedDelayString="${aurevia.directory.recalculation-interval-ms:1000}")
  public void process() {
    UUID owner=UUID.randomUUID();
    var claim=directory.claimRecalculation(owner).orElse(null);
    if(claim==null) return;
    try {
      var users=directory.usersAfter(claim.lastUserId(),batchSize);
      users.forEach(access::recalculateUser);
      if(users.size()<batchSize) directory.completeRecalculation(claim.id(),owner,users.size());
      else directory.releaseRecalculation(claim.id(),owner,users.getLast(),users.size());
    } catch(RuntimeException failure) {
      String message=failure.getClass().getSimpleName()+": "+failure.getMessage();
      if(message.length()>900) message=message.substring(0,900);
      directory.retryRecalculation(claim.id(),owner,maxAttempts,message);
    }
  }
}
