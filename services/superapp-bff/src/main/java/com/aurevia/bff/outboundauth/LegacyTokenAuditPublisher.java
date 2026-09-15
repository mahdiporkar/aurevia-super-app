package com.aurevia.bff.outboundauth;
import io.micrometer.core.instrument.MeterRegistry;import org.springframework.stereotype.Component;
/** Emits labels only; credential and token values are structurally impossible inputs. */
@Component class LegacyTokenAuditPublisher {private final MeterRegistry metrics;LegacyTokenAuditPublisher(MeterRegistry m){metrics=m;}void event(String event,String profile,String outcome){metrics.counter("aurevia.legacy.token", "event",event,"profile",profile,"outcome",outcome).increment();}}
