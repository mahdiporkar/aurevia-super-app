import React, { useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Empty, Input, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { parseReportTags, safeReportPath, type ReportAsset } from './report-security';

type ReportsCatalogProps = {
  reports: ReportAsset[];
  loading: boolean;
  error?: string;
  onRetry: () => void;
  onOpen: (report: ReportAsset) => void;
};

export function ReportsCatalog({ reports, loading, error, onRetry, onOpen }: ReportsCatalogProps) {
  const [query, setQuery] = useState('');

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

    {error && <Alert showIcon type="error" message="دریافت کاتالوگ گزارش‌ها ناموفق بود" description={error}
      action={<Button onClick={onRetry}>تلاش مجدد</Button>} />}

    {loading ? <Row gutter={[16, 16]}>
      {[1, 2, 3].map((item) => <Col xs={24} md={12} xl={8} key={item}><Card><Skeleton active /></Card></Col>)}
    </Row> : visibleReports.length === 0 ? <Card><Empty description="گزارش تخصیص‌یافته‌ای پیدا نشد" /></Card> : <Row gutter={[16, 16]}>
      {visibleReports.map((report) => {
        const tags = parseReportTags(report.tags_json);
        // The catalog stays the single place that decides a report is displayable;
        // the viewer re-validates the same path before embedding it.
        const reportPath = safeReportPath(report.url_path);
        return <Col xs={24} md={12} xl={8} key={report.id}>
          <Card hoverable title={report.title} extra={<Tag color={report.asset_type === 'DASHBOARD' ? 'blue' : 'purple'}>{report.asset_type === 'DASHBOARD' ? 'داشبورد' : 'گزارش'}</Tag>} actions={[
            <Button type="link" key="open" disabled={!reportPath} onClick={() => onOpen(report)}>نمایش گزارش</Button>,
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
