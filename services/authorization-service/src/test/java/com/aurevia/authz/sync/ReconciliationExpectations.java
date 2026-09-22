package com.aurevia.authz.sync;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Test access to the reconciler's table-derived OpenFGA expectations for an arbitrary schema. */
public final class ReconciliationExpectations {
  private ReconciliationExpectations() {}
  public static List<String> tuples(JdbcClient database) {
    return new JdbcOpenFgaReconciliationRepository(database).expectedTuples().stream()
        .map(tuple -> tuple.user() + "|" + tuple.relation() + "|" + tuple.object()).sorted().toList();
  }
}
