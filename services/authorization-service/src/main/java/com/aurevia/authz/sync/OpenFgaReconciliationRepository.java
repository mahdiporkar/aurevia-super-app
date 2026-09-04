package com.aurevia.authz.sync;

import java.util.Set;

interface OpenFgaReconciliationRepository {
  Set<ReconciliationTuple> expectedTuples();
}
