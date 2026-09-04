package com.aurevia.authz.sync;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
final class OutboxMetrics {
  OutboxMetrics(OutboxMetricsRepository outbox,MeterRegistry registry) {
    Gauge.builder("aurevia.outbox.pending",outbox,OutboxMetricsRepository::pending)
        .register(registry);
    Gauge.builder("aurevia.outbox.dead_letter",outbox,OutboxMetricsRepository::deadLettered)
        .register(registry);
    Gauge.builder("aurevia.outbox.retry",outbox,OutboxMetricsRepository::retrying)
        .register(registry);
    Gauge.builder("aurevia.outbox.oldest_pending_seconds",outbox,
        OutboxMetricsRepository::oldestPendingSeconds).register(registry);
  }
}
