import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportsCatalog } from './ReportsCatalog';
import { ReportViewer } from './ReportViewer';
import type { ReportAsset } from './report-security';

const dashboard: ReportAsset = {
  id: 'asset-1',
  external_id: '1',
  asset_type: 'DASHBOARD',
  title: 'داشبورد فروش',
  url_path: '/api/v1/superset-instances/superset-operation/superset/dashboard/1/',
};

// The repository has no DOM test runtime, so these assertions cover the rendered
// output contract (no external navigation, same-origin iframe). Click-through and
// Back behaviour are covered by the routing unit tests and the browser E2E run.
beforeEach(() => vi.stubGlobal('window', {
  location: { origin: 'https://aurevia.example', pathname: '/reports', search: '' },
  innerHeight: 900,
  addEventListener: () => {},
  removeEventListener: () => {},
}));
afterEach(() => vi.unstubAllGlobals());

describe('reports catalog presentation', () => {
  it('opens reports in place instead of a new browser tab', () => {
    const markup = renderToStaticMarkup(
      <ReportsCatalog reports={[dashboard]} loading={false} onRetry={() => {}} onOpen={() => {}} />,
    );
    expect(markup).toContain('نمایش گزارش');
    expect(markup).not.toContain('target="_blank"');
    expect(markup).not.toContain('href=');
  });
});

describe('report viewer presentation', () => {
  it('embeds the validated same-origin report path in an iframe', () => {
    const markup = renderToStaticMarkup(<ReportViewer report={dashboard} onBack={() => {}} />);
    expect(markup).toContain('<iframe');
    expect(markup).toContain(`src="${dashboard.url_path}"`);
    expect(markup).not.toContain('target="_blank"');
    expect(markup).toContain('بازگشت به گزارش‌ها');
  });

  it('refuses to embed a report whose path is not same-origin', () => {
    const markup = renderToStaticMarkup(
      <ReportViewer report={{ ...dashboard, url_path: 'https://evil.example/report' }} onBack={() => {}} />,
    );
    expect(markup).not.toContain('<iframe');
    expect(markup).toContain('نمایش این گزارش ممکن نیست');
  });
});
