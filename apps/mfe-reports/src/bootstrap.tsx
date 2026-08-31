import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Alert, Button, Card, Col, Empty, Input, Row, Skeleton, Space, Tag, Typography } from 'antd';
import type { RemoteContext, RemoteModule } from '@aurevia/contracts';
import { parseReportTags, safeReportPath } from './report-security';

export const contractVersion = '1' as const;

type ReportAsset = {
  id: string;
  external_id: string;
  asset_type: 'DASHBOARD' | 'CHART';
  title: string;
  url_path: string;
  owner_external_id?: string;
  tags_json?: string;
};

function ReportsCatalog() {
  const [reports, setReports] = useState<ReportAsset[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/v1/reports', { credentials: 'same-origin', signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
        return response.json();
      })
      .then(setReports)
      .catch((reason) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return;
        setError(reason instanceof Error ? reason.message : 'خطای ناشناخته');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  const visibleReports = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('fa');
    if (!normalized) return reports;
    return reports.filter((report) =>
      `${report.title} ${report.owner_external_id ?? ''} ${report.tags_json ?? ''}`
        .toLocaleLowerCase('fa')
        .includes(normalized),
    );
  }, [query, reports]);

  return <Space direction="vertical" size={18} style={{ width: '100%' }}>
    <Card>
      <Row gutter={[16, 16]} align="middle">
        <Col flex="auto">
          <Typography.Title level={3} style={{ marginBottom: 4 }}>مرکز گزارش‌ها</Typography.Title>
          <Typography.Text type="secondary">داشبوردها و گزارش‌هایی که برای حساب شما مجوز مشاهده دارند</Typography.Text>
        </Col>
        <Col><Tag color="green">Superset متصل</Tag></Col>
      </Row>
      <Input.Search allowClear size="large" aria-label="جستجوی گزارش" placeholder="نام گزارش، مالک یا برچسب..." style={{ marginTop: 18, maxWidth: 620 }} onChange={(event) => setQuery(event.target.value)} />
    </Card>

    {error && <Alert showIcon type="error" message="دریافت کاتالوگ گزارش‌ها ناموفق بود" description={error} />}

    {loading ? <Row gutter={[16, 16]}>
      {[1, 2, 3].map((item) => <Col xs={24} md={12} xl={8} key={item}><Card><Skeleton active /></Card></Col>)}
    </Row> : visibleReports.length === 0 ? <Card><Empty description="گزارش تخصیص‌یافته‌ای پیدا نشد" /></Card> : <Row gutter={[16, 16]}>
      {visibleReports.map((report) => {
        const tags = parseReportTags(report.tags_json);
        const reportPath = safeReportPath(report.url_path);
        return <Col xs={24} md={12} xl={8} key={report.id}>
          <Card hoverable title={report.title} extra={<Tag color={report.asset_type === 'DASHBOARD' ? 'blue' : 'purple'}>{report.asset_type === 'DASHBOARD' ? 'داشبورد' : 'گزارش'}</Tag>} actions={[
            <Button type="link" key="open" href={reportPath} disabled={!reportPath} target="_blank" rel="noopener noreferrer">باز کردن در Superset</Button>,
          ]}>
            <Space direction="vertical">
              <Typography.Text type="secondary">مالک: {report.owner_external_id || 'تیم گزارشات'}</Typography.Text>
              <Space wrap>{tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}</Space>
            </Space>
          </Card>
        </Col>;
      })}
    </Row>}
  </Space>;
}

export const mount: RemoteModule['mount'] = (element: HTMLElement, _context: RemoteContext) => {
  const root = createRoot(element);
  root.render(<ReportsCatalog />);
  return () => root.unmount();
};
