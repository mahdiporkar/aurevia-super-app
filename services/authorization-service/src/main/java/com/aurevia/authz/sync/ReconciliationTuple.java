package com.aurevia.authz.sync;

/** Canonical OpenFGA relationship used by drift detection and its HTTP report. */
public record ReconciliationTuple(String user,String relation,String object) {}
