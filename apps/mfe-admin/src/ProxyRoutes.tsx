import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert, Button, Card, Checkbox, Form, Input, InputNumber, Modal, Popconfirm, Select,
  Space, Table, Tag, Typography, message,
} from 'antd';

type Row = Record<string, any>;
export type AdminApi = <T>(path: string, init?: RequestInit) => Promise<T>;
type Section = 'targets' | 'routes' | 'operations';
type Probe = { kind: 'preview' | 'resolve'; route?: Row };

const required = [{ required: true, message: 'این فیلد الزامی است' }];
const methods = ['GET', 'HEAD', 'OPTIONS', 'POST', 'PUT', 'PATCH', 'DELETE'];

function normalizedActions(resource: Row): Row[] {
  if (Array.isArray(resource.actions)) return resource.actions;
  try {
    const parsed: unknown = JSON.parse(resource.actions_json ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

export function ProxyRouteManagement({ api, section }: { api: AdminApi; section: Section }) {
  const [targets, setTargets] = useState<Row[]>([]);
  const [authProfiles, setAuthProfiles] = useState<Row[]>([]);
  const [routes, setRoutes] = useState<Row[]>([]);
  const [panels, setPanels] = useState<Row[]>([]);
  const [resources, setResources] = useState<Row[]>([]);
  const [selectedRoute, setSelectedRoute] = useState<Row>();
  const [operations, setOperations] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [targetOpen, setTargetOpen] = useState(false);
  const [routeOpen, setRouteOpen] = useState(false);
  const [operationOpen, setOperationOpen] = useState(false);
  const [editingTarget, setEditingTarget] = useState<Row>();
  const [editingRoute, setEditingRoute] = useState<Row>();
  const [editingOperation, setEditingOperation] = useState<Row>();
  const [probe, setProbe] = useState<Probe>();
  const [targetForm] = Form.useForm();
  const [routeForm] = Form.useForm();
  const [operationForm] = Form.useForm();
  const [probeForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true); setError(undefined);
    try {
      const [nextTargets, nextProfiles, nextRoutes, nextPanels, nextResources] = await Promise.all([
        api<Row[]>('/service-targets'), api<Row[]>('/outbound-auth-profiles'),
        api<Row[]>('/proxy-routes'), api<Row[]>('/panels'), api<Row[]>('/resources'),
      ]);
      setTargets(nextTargets); setAuthProfiles(nextProfiles); setRoutes(nextRoutes);
      setPanels(nextPanels);
      setResources(nextResources.map(resource => ({ ...resource, actions: normalizedActions(resource) })));
    } catch (reason) { setError((reason as Error).message); }
    finally { setLoading(false); }
  }, [api]);

  const loadOperations = async (route: Row) => {
    try {
      setSelectedRoute(route);
      setOperations(await api<Row[]>(`/proxy-routes/${route.id}/operations`));
    } catch (reason) { message.error((reason as Error).message); }
  };

  useEffect(() => { void load(); }, [load]);

  const openTarget = (row?: Row) => {
    setEditingTarget(row);
    targetForm.setFieldsValue(row ? {
      ...row, gatewayBaseUrl: row.gateway_base_url, upstreamBasePath: row.upstream_base_path,
      tlsProfileRef: row.tls_profile_ref, secretRef: row.secret_ref,
      healthCheckPath: row.health_check_path, connectTimeoutMs: row.connect_timeout_ms,
      responseTimeoutMs: row.response_timeout_ms, maxResponseSize: row.max_response_size,
      outboundAuthProfileId: row.outbound_auth_profile_id,
    } : {
      active: true, environment: 'OPERATION', upstreamBasePath: '/', healthCheckPath: '/health',
      connectTimeoutMs: 3000, responseTimeoutMs: 10000, maxResponseSize: 10485760,
      outboundAuthProfileId: authProfiles.find(profile => profile.code === 'public-iam-forward')?.id,
    });
    setTargetOpen(true);
  };

  const saveTarget = async (values: Row) => {
    await api(editingTarget ? `/service-targets/${editingTarget.id}?version=${editingTarget.version}` : '/service-targets', {
      method: editingTarget ? 'PUT' : 'POST', body: JSON.stringify(values),
    });
    message.success('Service Target ذخیره شد'); setTargetOpen(false); await load();
  };

  const openRoute = (row?: Row) => {
    setEditingRoute(row);
    routeForm.setFieldsValue(row ? {
      ...row, panelId: row.panel_id, serviceTargetId: row.service_target_id,
      serviceSlug: row.service_slug, pathPrefix: row.path_prefix, stripPrefix: row.strip_prefix,
      rewritePattern: row.rewrite_pattern, rewriteReplacement: row.rewrite_replacement,
      allowedMethods: row.allowed_methods, preserveHost: row.preserve_host,
      retryEnabled: row.retry_enabled, maxRetries: row.max_retries,
    } : {
      active: true, stripPrefix: 0, priority: 0, allowedMethods: ['GET'],
      preserveHost: false, retryEnabled: false, maxRetries: 0,
    });
    setRouteOpen(true);
  };

  const saveRoute = async (values: Row) => {
    await api('/proxy-routes/validate', { method: 'POST', body: JSON.stringify(values) });
    await api(editingRoute ? `/proxy-routes/${editingRoute.id}?version=${editingRoute.version}` : '/proxy-routes', {
      method: editingRoute ? 'PUT' : 'POST', body: JSON.stringify(values),
    });
    message.success('مسیر پراکسی ذخیره شد'); setRouteOpen(false); await load();
  };

  const openOperation = (row?: Row) => {
    setEditingOperation(row);
    operationForm.setFieldsValue(row ? {
      httpMethod: row.http_method, pathPattern: row.path_pattern,
      resourceKey: row.resource_key, actionKey: row.action_key,
      authorizationRequired: row.authorization_required, dataPolicyKey: row.data_policy_key,
      active: row.active, maxBodyBytes: row.max_body_bytes,
    } : {
      httpMethod: 'GET', pathPattern: '/api/v1/', authorizationRequired: true,
      active: true, maxBodyBytes: 1048576,
    });
    setOperationOpen(true);
  };

  const saveOperation = async (values: Row) => {
    if (!selectedRoute) return;
    await api(editingOperation
      ? `/proxy-routes/${selectedRoute.id}/operations/${editingOperation.id}?version=${editingOperation.version}`
      : `/proxy-routes/${selectedRoute.id}/operations`, {
      method: editingOperation ? 'PUT' : 'POST', body: JSON.stringify(values),
    });
    message.success('عملیات مسیر ذخیره شد'); setOperationOpen(false);
    await loadOperations(selectedRoute);
  };

  const openProbe = (nextProbe: Probe) => {
    setProbe(nextProbe);
    probeForm.setFieldsValue(nextProbe.kind === 'preview'
      ? { path: `${nextProbe.route?.normalized_path_prefix ?? '/api/proxy/' }api/v1/employees` }
      : { path: '/hr-micro/api/v1/employees', method: 'GET' });
  };

  const runProbe = async (values: { path: string; method?: string }) => {
    if (!probe) return;
    try {
      if (probe.kind === 'preview') {
        const result = await api<Row>('/proxy-routes/preview', {
          method: 'POST', body: JSON.stringify({ routeId: probe.route?.id, path: values.path }),
        });
        Modal.info({ title: 'پیش‌نمایش تبدیل مسیر', content: <>
          <Typography.Text code>{result.incomingPath}</Typography.Text><br />↓<br />
          <Typography.Text code>{result.upstreamPath}</Typography.Text>
        </> });
      } else {
        const result = await api<unknown>('/proxy-routes/resolve-test', {
          method: 'POST', body: JSON.stringify({ path: values.path, method: values.method }),
        });
        Modal.info({ title: 'نتیجه Route Resolution', width: 720,
          content: <pre style={{ direction: 'ltr', whiteSpace: 'pre-wrap' }}>{JSON.stringify(result, null, 2)}</pre> });
      }
      setProbe(undefined);
    } catch (reason) { message.error((reason as Error).message); }
  };

  const targetTab = <Card title="Service Targets" extra={<Space>
    <Button onClick={() => void load()}>بازخوانی</Button>
    <Button type="primary" onClick={() => openTarget()}>Target جدید</Button>
  </Space>}>
    <Alert showIcon type="info" message="مقصدها فقط Gatewayهای allowlisted هستند"
      description="Secret و کلید خصوصی ذخیره نمی‌شود؛ فقط reference امن TLS/Secret Store ثبت می‌گردد."
      style={{ marginBottom: 16 }} />
    <Table rowKey="id" loading={loading} dataSource={targets} pagination={{ pageSize: 10 }} columns={[
      { title: 'کد', dataIndex: 'code' }, { title: 'نام', dataIndex: 'name' },
      { title: 'Gateway', dataIndex: 'gateway_base_url' }, { title: 'محیط', dataIndex: 'environment' },
      { title: 'مسیرها', dataIndex: 'route_count' },
      { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
      { title: 'عملیات', render: (_, row) => <Space>
        <Button onClick={() => openTarget(row)}>ویرایش</Button>
        <Button onClick={() => void api<Row>(`/service-targets/${row.id}/health-check`, { method: 'POST' })
          .then(result => message[result.healthy ? 'success' : 'warning'](`Gateway: HTTP ${result.status} — ${result.latencyMs}ms`))
          .catch(reason => message.error(reason.message))}>تست اتصال</Button>
        <Popconfirm title="وضعیت تغییر کند؟" onConfirm={() => api<void>(
          `/service-targets/${row.id}/status?version=${row.version}`,
          { method: 'PATCH', body: JSON.stringify({ active: !row.active }) }).then(load)
          .catch(reason => message.error(reason.message))}>
          <Button>{row.active ? 'غیرفعال' : 'فعال'}</Button>
        </Popconfirm>
      </Space> },
    ]} />
  </Card>;

  const routeTab = <Card title="Proxy Routes" extra={<Space>
    <Button onClick={() => openProbe({ kind: 'resolve' })}>تست Resolution</Button>
    <Button type="primary" onClick={() => openRoute()}>مسیر جدید</Button>
  </Space>}>
    <Table rowKey="id" loading={loading} dataSource={routes} pagination={{ pageSize: 10 }} columns={[
      { title: 'کد', dataIndex: 'code' }, { title: 'پنل', dataIndex: 'panel_name' },
      { title: 'Prefix', dataIndex: 'normalized_path_prefix' }, { title: 'Target', dataIndex: 'target_code' },
      { title: 'اولویت', dataIndex: 'priority' },
      { title: 'Methodها', render: (_, row) => <Space wrap>{(row.allowed_methods ?? []).map((method: string) => <Tag key={method}>{method}</Tag>)}</Space> },
      { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
      { title: 'عملیات', render: (_, row) => <Space>
        <Button onClick={() => openRoute(row)}>ویرایش</Button>
        <Button onClick={() => openProbe({ kind: 'preview', route: row })}>Preview</Button>
        <Button type="primary" ghost onClick={() => void loadOperations(row)}>Operationها</Button>
      </Space> },
    ]} />
  </Card>;

  const operationTab = <Card title={selectedRoute ? `Route Operations — ${selectedRoute.code}` : 'Route Operations'} extra={<Space>
    <Select style={{ width: 280 }} placeholder="انتخاب Route" value={selectedRoute?.id}
      options={routes.map(route => ({ value: route.id, label: `${route.code} — ${route.normalized_path_prefix}` }))}
      onChange={id => { const route = routes.find(item => item.id === id); if (route) void loadOperations(route); }} />
    <Button type="primary" disabled={!selectedRoute} onClick={() => openOperation()}>Operation جدید</Button>
  </Space>}>
    {!selectedRoute ? <Alert type="info" showIcon message="ابتدا یک Route انتخاب کنید" />
      : <Table rowKey="id" dataSource={operations} pagination={false} columns={[
        { title: 'Method', dataIndex: 'http_method' }, { title: 'Pattern', dataIndex: 'normalized_path_pattern' },
        { title: 'Resource : Action', render: (_, row) => <Typography.Text code>{row.resource_key}:{row.action_key}</Typography.Text> },
        { title: 'مجوز', render: (_, row) => row.authorization_required ? <Tag color="blue">OpenFGA</Tag> : <Tag>Public</Tag> },
        { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
        { title: 'عملیات', render: (_, row) => <Space>
          <Button onClick={() => openOperation(row)}>ویرایش</Button>
          <Popconfirm title="Operation غیرفعال شود؟" onConfirm={() => api<void>(
            `/proxy-routes/${selectedRoute.id}/operations/${row.id}?version=${row.version}`,
            { method: 'DELETE' }).then(() => loadOperations(selectedRoute)).catch(reason => message.error(reason.message))}>
            <Button danger>غیرفعال</Button>
          </Popconfirm>
        </Space> },
      ]} />}
  </Card>;

  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    {error && <Alert showIcon type="error" message="خطا در دریافت تنظیمات Proxy" description={error}
      action={<Button onClick={() => void load()}>تلاش مجدد</Button>} />}
    {{ targets: targetTab, routes: routeTab, operations: operationTab }[section]}

    <Modal open={targetOpen} title={editingTarget ? 'ویرایش Service Target' : 'Service Target جدید'}
      onCancel={() => setTargetOpen(false)} onOk={() => targetForm.submit()} width={800}>
      <Form form={targetForm} layout="vertical" onFinish={values => void saveTarget(values).catch(reason => message.error(reason.message))}>
        <Space wrap align="start">
          <Form.Item name="code" label="کد" rules={required}><Input /></Form.Item>
          <Form.Item name="name" label="نام" rules={required}><Input /></Form.Item>
          <Form.Item name="environment" label="محیط" rules={required}><Select style={{ width: 180 }} options={['OPERATION', 'STAGING'].map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name="outboundAuthProfileId" label="Outbound Auth Profile" rules={required}><Select style={{ width: 300 }} options={authProfiles.filter(profile => profile.active).map(profile => ({ value: profile.id, label: `${profile.name} (${profile.auth_mode})` }))} /></Form.Item>
          <Form.Item name="gatewayBaseUrl" label="آدرس کامل Gateway" rules={[...required, { type: 'url' }]}><Input style={{ width: 360 }} placeholder="http://operation-gateway:80" /></Form.Item>
          <Form.Item name="upstreamBasePath" label="Upstream Base Path" rules={required}><Input /></Form.Item>
          <Form.Item name="healthCheckPath" label="Health Path" rules={required}><Input /></Form.Item>
          <Form.Item name="tlsProfileRef" label="TLS Profile Ref"><Input placeholder="tls://gateway-client" /></Form.Item>
          <Form.Item name="secretRef" label="Secret Ref"><Input placeholder="secret://gateway-client" /></Form.Item>
          <Form.Item name="connectTimeoutMs" label="Connect Timeout"><InputNumber min={100} /></Form.Item>
          <Form.Item name="responseTimeoutMs" label="Response Timeout"><InputNumber min={100} /></Form.Item>
          <Form.Item name="maxResponseSize" label="Max Response Bytes"><InputNumber min={1024} /></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
        <Form.Item name="description" label="توضیح"><Input.TextArea /></Form.Item>
      </Form>
    </Modal>

    <Modal open={routeOpen} title={editingRoute ? 'ویرایش Proxy Route' : 'Proxy Route جدید'}
      onCancel={() => setRouteOpen(false)} onOk={() => routeForm.submit()} width={850}>
      <Form form={routeForm} layout="vertical" onFinish={values => void saveRoute(values).catch(reason => message.error(reason.message))}>
        <Space wrap align="start">
          <Form.Item name="code" label="کد" rules={required}><Input /></Form.Item>
          <Form.Item name="panelId" label="Panel" rules={required}><Select style={{ width: 250 }} options={panels.map(panel => ({ value: panel.id, label: `${panel.name_fa} (${panel.slug})` }))} /></Form.Item>
          <Form.Item name="serviceTargetId" label="Service Target" rules={required}><Select style={{ width: 250 }} options={targets.map(target => ({ value: target.id, label: target.code }))} /></Form.Item>
          <Form.Item name="serviceSlug" label="Service Slug" rules={[...required, { pattern: /^[a-z][a-z0-9-]{1,49}$/, message: 'حروف کوچک، عدد و خط تیره' }]} extra="namespace پایدار زیر /api/proxy/"><Input placeholder="legacy-payroll" /></Form.Item>
          <Form.Item name="pathPrefix" label="Path Prefix" rules={required}><Input placeholder="/api/proxy/legacy-payroll" /></Form.Item>
          <Form.Item name="stripPrefix" label="Strip Segments"><InputNumber min={0} max={20} /></Form.Item>
          <Form.Item name="priority" label="Priority"><InputNumber /></Form.Item>
          <Form.Item name="allowedMethods" label="Allowed Methods" rules={required}><Select mode="multiple" style={{ width: 360 }} options={methods.map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name="rewritePattern" label="Rewrite Prefix"><Input placeholder="^/api/proxy/legacy-payroll" /></Form.Item>
          <Form.Item name="rewriteReplacement" label="Rewrite Replacement"><Input placeholder="/legacy-payroll" /></Form.Item>
          <Form.Item name="retryEnabled" valuePropName="checked"><Checkbox>Retry فقط برای روش‌های امن</Checkbox></Form.Item>
          <Form.Item name="maxRetries" label="Max Retries"><InputNumber min={0} max={3} /></Form.Item>
          <Form.Item name="preserveHost" valuePropName="checked"><Checkbox>Preserve Host</Checkbox></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
      </Form>
    </Modal>

    <Modal open={operationOpen} title={editingOperation ? 'ویرایش Route Operation' : 'Route Operation جدید'}
      onCancel={() => setOperationOpen(false)} onOk={() => operationForm.submit()} width={760}>
      <Form form={operationForm} layout="vertical" onFinish={values => void saveOperation(values).catch(reason => message.error(reason.message))}>
        <Space wrap align="start">
          <Form.Item name="httpMethod" label="HTTP Method" rules={required}><Select style={{ width: 150 }} options={methods.map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name="pathPattern" label="Path Pattern" rules={required}><Input style={{ width: 300 }} placeholder="/api/v1/employees/{id}" /></Form.Item>
          <Form.Item name="resourceKey" label="Resource" rules={required}><Select showSearch optionFilterProp="label" style={{ width: 320 }} options={resources.map(resource => ({ value: resource.resource_key, label: `${resource.name_fa} (${resource.resource_key})` }))} onChange={() => operationForm.setFieldValue('actionKey', undefined)} /></Form.Item>
          <Form.Item noStyle shouldUpdate>{() => { const resource = resources.find(item => item.resource_key === operationForm.getFieldValue('resourceKey')); return <Form.Item name="actionKey" label="Action" rules={required}><Select style={{ width: 220 }} options={(resource?.actions ?? []).map((action: Row) => ({ value: action.key ?? action.action_key, label: `${action.nameFa ?? action.name_fa} (${action.key ?? action.action_key})` }))} /></Form.Item>; }}</Form.Item>
          <Form.Item name="dataPolicyKey" label="Data Policy"><Input /></Form.Item>
          <Form.Item name="maxBodyBytes" label="Max Body Bytes"><InputNumber min={0} /></Form.Item>
          <Form.Item name="authorizationRequired" valuePropName="checked"><Checkbox>نیازمند OpenFGA</Checkbox></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
      </Form>
    </Modal>

    <Modal open={Boolean(probe)} title={probe?.kind === 'preview' ? 'پیش‌نمایش تبدیل مسیر' : 'آزمایش Route Resolution'}
      onCancel={() => setProbe(undefined)} onOk={() => probeForm.submit()} destroyOnHidden>
      <Form form={probeForm} layout="vertical" onFinish={runProbe}>
        <Form.Item name="path" label="مسیر ورودی" rules={required}><Input style={{ direction: 'ltr' }} /></Form.Item>
        {probe?.kind === 'resolve' && <Form.Item name="method" label="HTTP Method" rules={required}>
          <Select options={methods.map(value => ({ value, label: value }))} />
        </Form.Item>}
      </Form>
    </Modal>
  </Space>;
}
