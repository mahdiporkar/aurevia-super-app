package com.aurevia.authz.registry;

/** Outbound port for retrieving a configured, allow-listed resource manifest. */
public interface ResourceManifestFetcher {
  String fetch(String url);
}
