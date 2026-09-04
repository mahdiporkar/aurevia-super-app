package com.aurevia.authz.sync;

interface OutboxMetricsRepository {
  double pending();
  double deadLettered();
  double retrying();
  double oldestPendingSeconds();
}
