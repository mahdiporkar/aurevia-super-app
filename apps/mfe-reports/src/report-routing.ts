// The Shell mounts this microfrontend under a manifest-driven route prefix
// (`/<prefix>/*`) and does not inject its router into the remote contract.
// Viewer state therefore lives in the real URL and is derived from the mount
// location, so refresh, deep links and browser Back all keep working without
// depending on a router instance the remote cannot see.
const VIEW_SEGMENT = '/view/';

function stripTrailingSlash(value: string): string {
  return value.length > 1 ? value.replace(/\/+$/, '') : value;
}

function normalizeBase(basePath: string): string {
  const stripped = stripTrailingSlash(basePath);
  return stripped === '/' ? '' : stripped;
}

export type ReportLocation = { basePath: string; reportId?: string };

export function splitReportLocation(pathname: string): ReportLocation {
  const index = pathname.indexOf(VIEW_SEGMENT);
  if (index < 0) return { basePath: normalizeBase(pathname) };
  const basePath = normalizeBase(pathname.slice(0, index));
  const encoded = pathname.slice(index + VIEW_SEGMENT.length).split('/')[0];
  if (!encoded) return { basePath };
  try {
    const reportId = decodeURIComponent(encoded);
    return reportId ? { basePath, reportId } : { basePath };
  } catch {
    // A malformed percent-escape is treated as "no report selected" rather than
    // surfacing raw URL input further into the application.
    return { basePath };
  }
}

export function reportViewerPath(basePath: string, reportId: string): string {
  return `${normalizeBase(basePath)}${VIEW_SEGMENT}${encodeURIComponent(reportId)}`;
}

export function reportCatalogPath(basePath: string): string {
  return normalizeBase(basePath) || '/';
}
