import { afterEach, describe, expect, it, vi } from 'vitest';
import { parseReportTags, safeReportPath } from './report-security';

describe('report presentation security', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('tolerates malformed and non-array tag payloads', () => {
    expect(parseReportTags('{broken')).toEqual([]);
    expect(parseReportTags('{"tag":"finance"}')).toEqual([]);
    expect(parseReportTags('["finance", 42, "approved"]')).toEqual(['finance', 'approved']);
  });

  it('accepts only same-origin relative report paths', () => {
    vi.stubGlobal('window', { location: { origin: 'https://aurevia.example' } });
    expect(safeReportPath('/api/v1/superset/dashboard/1')).toBe('/api/v1/superset/dashboard/1');
    expect(safeReportPath('javascript:alert(1)')).toBeUndefined();
    expect(safeReportPath('//evil.example/report')).toBeUndefined();
    expect(safeReportPath('https://evil.example/report')).toBeUndefined();
  });
});
