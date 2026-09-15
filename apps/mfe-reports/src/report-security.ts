export function parseReportTags(value?: string): string[] {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed)
      ? parsed.filter((tag): tag is string => typeof tag === 'string').slice(0, 20)
      : [];
  } catch {
    return [];
  }
}

export function safeReportPath(value: string): string | undefined {
  if (!value.startsWith('/') || value.startsWith('//')) return undefined;
  try {
    const parsed = new URL(value, window.location.origin);
    return parsed.origin === window.location.origin ? `${parsed.pathname}${parsed.search}${parsed.hash}` : undefined;
  } catch {
    return undefined;
  }
}
