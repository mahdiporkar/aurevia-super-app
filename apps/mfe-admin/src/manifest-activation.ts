/** One backend operation coordinates the immutable UI/resource pair. */
export function artifactActivationPath(panelId: string, artifactId: string, version: number,
  resourceManifestId?: string): string {
  const query = new URLSearchParams({ version: String(version) });
  if (resourceManifestId) query.set('resourceManifestId', resourceManifestId);
  return `/panels/${panelId}/artifacts/${artifactId}/activate?${query}`;
}

export function resourceActivationPath(panelId: string, revisionId: string): string {
  return `/panels/${panelId}/resource-manifests/${revisionId}/activate`;
}
