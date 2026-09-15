package com.aurevia.authz.directory;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Transactional enqueue boundary for potentially large membership recalculations. */
@Component
public final class OuRecalculationQueue {
  private final OuDirectoryRepository directory;
  public OuRecalculationQueue(OuDirectoryRepository directory) { this.directory=directory; }

  public void enqueue(UUID accessGroupId,String actor) {
    directory.enqueueRecalculation(accessGroupId,actor);
  }
}
