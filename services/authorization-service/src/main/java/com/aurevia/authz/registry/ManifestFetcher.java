package com.aurevia.authz.registry;

/** Outbound port for retrieving a configured, allow-listed JSON manifest. */
public interface ManifestFetcher {
  String fetch(String url);
}
