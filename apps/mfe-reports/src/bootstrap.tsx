import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Alert } from 'antd';
import type { RemoteContext, RemoteModule } from '@aurevia/contracts';
import { createJsonHttpClient } from '@aurevia/http-client';
import type { ReportAsset } from './report-security';
import { reportCatalogPath, reportViewerPath, splitReportLocation } from './report-routing';
import { ReportsCatalog } from './ReportsCatalog';
import { ReportViewer } from './ReportViewer';

export const contractVersion = '1' as const;
const reportsClient = createJsonHttpClient({ basePath: '/api/v1' });
const VIEWER_ENTRY = 'aureviaReportViewer';

function ReportsApp() {
  const [reports, setReports] = useState<ReportAsset[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const [location, setLocation] = useState(() => splitReportLocation(window.location.pathname));

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    reportsClient.get<ReportAsset[]>('/reports', { signal: controller.signal })
      .then(setReports)
      .catch((reason) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return;
        setError(reason instanceof Error ? reason.message : 'خطای ناشناخته');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [attempt]);

  // Back/forward must return to the catalog, and a refresh on a viewer URL must
  // reopen the same report. Both fall out of reading the location on popstate.
  useEffect(() => {
    const onPopState = () => setLocation(splitReportLocation(window.location.pathname));
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const openReport = useCallback((report: ReportAsset) => {
    const path = reportViewerPath(location.basePath, report.id);
    // The marker records that this session pushed the viewer entry itself, so
    // Back can be a real history step here and a replace on a cold deep link.
    window.history.pushState({ [VIEWER_ENTRY]: true }, '', path + window.location.search);
    setLocation(splitReportLocation(path));
  }, [location.basePath]);

  const backToCatalog = useCallback(() => {
    if ((window.history.state as Record<string, unknown> | null)?.[VIEWER_ENTRY]) {
      // Keep the browser's Back stack consistent with the toolbar action instead
      // of pushing a second catalog entry on top of it.
      window.history.back();
      return;
    }
    const path = reportCatalogPath(location.basePath);
    window.history.replaceState({}, '', path + window.location.search);
    setLocation(splitReportLocation(path));
  }, [location.basePath]);

  const selected = useMemo(
    () => location.reportId ? reports.find((report) => report.id === location.reportId) : undefined,
    [location.reportId, reports],
  );

  if (location.reportId && !loading && !error && !selected) {
    // The catalog only contains assets this subject is authorized to view, so an
    // unknown id is reported as unavailable rather than embedded optimistically.
    return <Alert showIcon type="warning" message="گزارش مورد نظر در دسترس نیست"
      description="این گزارش وجود ندارد یا برای حساب شما مجوز مشاهده ندارد." />;
  }

  if (selected) return <ReportViewer report={selected} onBack={backToCatalog} />;

  return <ReportsCatalog
    reports={reports}
    loading={loading || Boolean(location.reportId)}
    error={error}
    onRetry={() => setAttempt(value => value + 1)}
    onOpen={openReport}
  />;
}

export const mount: RemoteModule['mount'] = (element: HTMLElement, _context: RemoteContext) => {
  const root = createRoot(element);
  root.render(<ReportsApp />);
  return () => root.unmount();
};
