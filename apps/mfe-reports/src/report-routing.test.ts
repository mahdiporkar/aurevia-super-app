import { describe, expect, it } from 'vitest';
import { reportCatalogPath, reportViewerPath, splitReportLocation } from './report-routing';

describe('report viewer routing', () => {
  it('treats a plain module path as the catalog', () => {
    expect(splitReportLocation('/reports')).toEqual({ basePath: '/reports' });
    expect(splitReportLocation('/reports/')).toEqual({ basePath: '/reports' });
  });

  it('extracts the selected report from a viewer path', () => {
    expect(splitReportLocation('/reports/view/abc-123'))
      .toEqual({ basePath: '/reports', reportId: 'abc-123' });
  });

  it('keeps the module base path when the Shell mounts under another prefix', () => {
    expect(splitReportLocation('/analytics-micro/view/9'))
      .toEqual({ basePath: '/analytics-micro', reportId: '9' });
  });

  it('round-trips identifiers that need percent-encoding', () => {
    const path = reportViewerPath('/reports', 'a/b c');
    expect(path).toBe('/reports/view/a%2Fb%20c');
    expect(splitReportLocation(path)).toEqual({ basePath: '/reports', reportId: 'a/b c' });
  });

  it('falls back to the catalog for empty or malformed selections', () => {
    expect(splitReportLocation('/reports/view/')).toEqual({ basePath: '/reports' });
    expect(splitReportLocation('/reports/view/%E0%A4%A')).toEqual({ basePath: '/reports' });
  });

  it('never produces a protocol-relative path from a root mount', () => {
    expect(reportViewerPath('/', '1')).toBe('/view/1');
    expect(reportCatalogPath('/reports/')).toBe('/reports');
    expect(reportCatalogPath('/')).toBe('/');
  });
});
