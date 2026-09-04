import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Space, Table, Tag, Typography, message } from 'antd';

type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
type Row = Record<string, any>;
type Probe = {
  scenario: 'legacy' | 'oauth2';
  correlationId: string;
  status: number;
  durationMs: number;
  body: unknown;
};

const endpoints = {
  legacy: '/api/proxy/legacy-demo/ping',
  oauth2: '/api/proxy/oauth2-demo/ping',
} as const;

/** Runs both credential paths through browser -> BFF -> Gateway -> protected fixture. */
export function IntegrationTestLab({ api }: { api: AdminApi }) {
  const [targets, setTargets] = useState<Row[]>([]);
  const [routes, setRoutes] = useState<Row[]>([]);
  const [profiles, setProfiles] = useState<Row[]>([]);
  const [results, setResults] = useState<Probe[]>([]);
  const [running, setRunning] = useState<string>();

  const load = useCallback(async () => {
    try {
      const [targetRows, routeRows, profileRows] = await Promise.all([
        api('/service-targets?search=demo'),
        api('/proxy-routes?search=demo'),
        api('/outbound-auth-profiles?search=demo'),
      ]);
      setTargets(targetRows);setRoutes(routeRows);setProfiles(profileRows);
    } catch(error) { message.error((error as Error).message); }
  }, [api]);

  useEffect(() => { void load(); }, [load]);

  const run = async (scenario: Probe['scenario']) => {
    setRunning(scenario);
    const correlationId = globalThis.crypto?.randomUUID?.() ?? `probe-${Date.now()}`;
    const started = performance.now();
    try {
      const response = await fetch(endpoints[scenario], {
        credentials: 'same-origin',
        headers: { 'Accept': 'application/json', 'X-Correlation-ID': correlationId },
      });
      const raw = await response.text();
      let body: unknown = raw;
      try { body = raw ? JSON.parse(raw) : null; } catch { /* retain safe response text */ }
      setResults(current => [{ scenario, correlationId, status: response.status,
        durationMs: Math.round(performance.now() - started), body }, ...current].slice(0, 10));
    } finally { setRunning(undefined); }
  };

  const catalog: Row[] = targets.map(target => {
    const profile = profiles.find(item => item.id === target.outbound_auth_profile_id);
    const route = routes.find(item => item.service_target_id === target.id);
    return { ...target, profile_code: profile?.code, auth_mode: profile?.auth_mode,
      path_prefix: route?.path_prefix, route_active: route?.active };
  });

  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Alert showIcon type="info" message="آزمایش واقعی بدون افشای Token"
      description="هر دکمه درخواست را با Session مرورگر به Java BFF می‌فرستد. Legacy token در BFF از Secret reference گرفته و رمز‌شده در Redis cache می‌شود؛ در سناریوی OAuth2 همان access token ذخیره‌شده در vault توسط Keycloak اعتبارسنجی می‌شود. هیچ Tokenی به این صفحه برنمی‌گردد." />
    <Card title="رجیستری فعال سناریوهای نمونه" extra={<Button onClick={() => void load()}>بازخوانی</Button>}>
      <Table rowKey="id" dataSource={catalog} pagination={false} columns={[
        { title: 'سرویس', dataIndex: 'name' },
        { title: 'Target', dataIndex: 'code' },
        { title: 'Route', dataIndex: 'path_prefix' },
        { title: 'Auth profile', render: (_, row) => `${row.profile_code ?? '—'} (${row.auth_mode ?? '—'})` },
        { title: 'وضعیت', render: (_, row) => <Tag color={row.active && row.route_active ? 'green' : 'red'}>{row.active && row.route_active ? 'READY' : 'INACTIVE'}</Tag> },
      ]} />
    </Card>
    <Card title="اجرای end-to-end">
      <Space wrap>
        <Button type="primary" loading={running === 'legacy'} onClick={() => void run('legacy')}>اجرای Legacy</Button>
        <Button loading={running === 'oauth2'} onClick={() => void run('oauth2')}>اجرای OAuth2 / Keycloak</Button>
        <Button disabled={Boolean(running)} onClick={() => void (async () => { await run('legacy'); await run('legacy'); })()}>
          Legacy ×2 (بررسی Cache)
        </Button>
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
        برای تطبیق لاگ توسعه، مقدار Correlation ID نتیجه را با رویدادهای <code>DEV_TOKEN_EVIDENCE</code> در لاگ BFF جست‌وجو کنید.
      </Typography.Paragraph>
      <Table<Probe> rowKey={row => `${row.correlationId}:${row.scenario}`} dataSource={results} pagination={false} columns={[
        { title: 'سناریو', dataIndex: 'scenario', render: value => <Tag color={value === 'legacy' ? 'orange' : 'blue'}>{value.toUpperCase()}</Tag> },
        { title: 'HTTP', dataIndex: 'status', render: value => <Tag color={value === 200 ? 'green' : 'red'}>{value}</Tag> },
        { title: 'زمان', dataIndex: 'durationMs', render: value => `${value} ms` },
        { title: 'Correlation ID', dataIndex: 'correlationId' },
        { title: 'پاسخ امن سرویس', dataIndex: 'body', render: value => <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{JSON.stringify(value, null, 2)}</pre> },
      ]} />
    </Card>
  </Space>;
}
