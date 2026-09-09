package com.aurevia.authz.registry;

/** Resource-manifest port retained for source compatibility with the resource workflow. */
// public interface ResourceManifestFetcher extends ManifestFetcher {}
public interface ResourceManifestFetcher {
  String fetch(String sourceUrl);
}
