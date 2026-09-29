import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Skeleton, Space, Tag, Typography } from 'antd';
import { safeReportPath, type ReportAsset } from './report-security';

// Space kept below the iframe so the embedded dashboard never sits flush
// against the Shell's content padding.
const BOTTOM_GUTTER = 24;
const MIN_HEIGHT = 420;

type ReportViewerProps = { report: ReportAsset; onBack: () => void };

export function ReportViewer({ report, onBack }: ReportViewerProps) {
  const frameHost = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState(MIN_HEIGHT);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const reportPath = safeReportPath(report.url_path);

  // The Shell header, page heading and content padding are styled in the Shell,
  // not here. Measuring the real offset keeps the viewer correct if that layout
  // changes, instead of duplicating its dimensions as magic numbers.
  const measure = useCallback(() => {
    const host = frameHost.current;
    if (!host) return;
    const offset = host.getBoundingClientRect().top;
    setHeight(Math.max(MIN_HEIGHT, window.innerHeight - offset - BOTTOM_GUTTER));
  }, []);

  useEffect(() => {
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure]);

  useEffect(() => {
    setLoading(true);
    setFailed(false);
  }, [report.id, attempt]);

  const isDashboard = report.asset_type === 'DASHBOARD';

  return <Space direction="vertical" size={12} style={{ width: '100%' }}>
    <Space wrap align="center" size={12}>
      <Button onClick={onBack}>→ بازگشت به گزارش‌ها</Button>
      <Typography.Title level={4} style={{ margin: 0 }}>{report.title}</Typography.Title>
      <Tag color={isDashboard ? 'blue' : 'purple'}>{isDashboard ? 'داشبورد' : 'گزارش'}</Tag>
    </Space>

    {!reportPath
      ? <Alert showIcon type="error" message="نمایش این گزارش ممکن نیست"
          description="نشانی گزارش معتبر نیست و در محیط Aurevia قابل نمایش نمی‌باشد." />
      : <div ref={frameHost} style={{ position: 'relative', width: '100%', height }}>
          {failed && <Alert showIcon type="error" message="بارگذاری گزارش ناموفق بود"
            description="امکان نمایش این گزارش وجود ندارد. ممکن است دسترسی شما تغییر کرده یا سرویس گزارش‌ها در دسترس نباشد."
            action={<Button onClick={() => setAttempt(value => value + 1)}>تلاش مجدد</Button>} />}
          {!failed && <>
            {loading && <div style={{
              position: 'absolute', inset: 0, background: '#fff', borderRadius: 18,
              padding: 24, zIndex: 1,
            }}>
              <Typography.Text type="secondary">در حال بارگذاری گزارش...</Typography.Text>
              <Skeleton active paragraph={{ rows: 8 }} style={{ marginTop: 16 }} />
            </div>}
            <iframe
              key={`${report.id}:${attempt}`}
              src={reportPath}
              title={report.title}
              onLoad={() => setLoading(false)}
              onError={() => { setLoading(false); setFailed(true); }}
              style={{ width: '100%', height: '100%', border: 0, borderRadius: 18, background: '#fff', display: 'block' }}
            />
          </>}
        </div>}
  </Space>;
}
