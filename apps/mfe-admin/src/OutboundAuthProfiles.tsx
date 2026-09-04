import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert, Button, Card, Checkbox, Form, Input, InputNumber, Modal,
  Popconfirm, Select, Space, Table, Tag, message,
} from 'antd';

type Api = (path: string, init?: RequestInit) => Promise<any>;
type Row = Record<string, any>;
type Connection = { connection_ref: string; name: string; active: boolean };

const required = [{ required: true, message: 'این فیلد الزامی است' }];
const formats = ['FORM_URLENCODED', 'JSON', 'HTTP_BASIC', 'OAUTH_CLIENT_CREDENTIALS'];

export function OutboundAuthProfiles({ api }: { api: Api }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [connections, setConnections] = useState<Connection[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Row>();
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    try {
      const [profiles, endpoints] = await Promise.all([
        api('/outbound-auth-profiles'), api('/outbound-connections'),
      ]);
      setRows(profiles);setConnections(endpoints);
    } catch (error) { message.error((error as Error).message); }
  }, [api]);

  useEffect(() => { void load(); }, [load]);

  const show = (row?: Row) => {
    setEditing(row);
    form.setFieldsValue(row ? {
      code: row.code, name: row.name, description: row.description,
      authMode: row.auth_mode, tokenConnectionRef: row.token_connection_ref,
      tokenEndpointPath: row.token_endpoint_path, requestFormat: row.request_format,
      credentialSecretRef: row.credential_secret_ref, scope: row.scope,
      audience: row.audience, tokenResponsePointer: row.token_response_pointer,
      expiresInResponsePointer: row.expires_in_response_pointer,
      tokenTypeResponsePointer: row.token_type_response_pointer,
      authorizationScheme: row.authorization_scheme,
      credentialTransport: row.credential_transport,
      expirySkewSeconds: row.expiry_skew_seconds,
      connectTimeoutMs: row.connect_timeout_ms,
      responseTimeoutMs: row.response_timeout_ms,
      maxTokenResponseSize: row.max_token_response_size,
      active: row.active,
    } : {
      authMode: 'FORWARD_USER_TOKEN', requestFormat: 'FORM_URLENCODED',
      tokenResponsePointer: '/access_token', expiresInResponsePointer: '/expires_in',
      tokenTypeResponsePointer: '/token_type', authorizationScheme: 'Bearer',
      credentialTransport: 'USER_AUTHORIZATION_HEADER', expirySkewSeconds: 30,
      connectTimeoutMs: 3000, responseTimeoutMs: 10000,
      maxTokenResponseSize: 1048576, active: true,
    });
    setOpen(true);
  };

  const save = async (value: Row) => {
    if (editing && !window.confirm('تغییر endpoint یا Secret reference، توکن cache شده را باطل می‌کند. ادامه؟')) return;
    await api(editing
      ? `/outbound-auth-profiles/${editing.id}?version=${editing.version}`
      : '/outbound-auth-profiles', {
      method: editing ? 'PUT' : 'POST', body: JSON.stringify(value),
    });
    setOpen(false);message.success('پروفایل ذخیره شد');await load();
  };

  const action = async (row: Row, path: string, success: string) => {
    try {
      const result = await api(`/outbound-auth-profiles/${row.id}/${path}`, { method: 'POST' });
      message[result.success === false ? 'error' : 'success'](
        `${success}${result.latencyMs != null ? ` — ${result.latencyMs}ms` : ''}`);
    } catch (error) { message.error((error as Error).message); }
  };

  return <Card title="پروفایل‌های احراز هویت سرویس‌ها" extra={
    <Button type="primary" onClick={() => show()}>پروفایل جدید</Button>
  }>
    <Alert showIcon type="warning" message="فقط Secret reference ذخیره می‌شود"
      description="مقدار Secret و Token هرگز در مرورگر نمایش داده نمی‌شود. Legacy token پس از مجوز OpenFGA، داخل BFF دریافت و رمز‌شده در Redis نگهداری می‌شود."
      style={{ marginBottom: 16 }} />
    <Table rowKey="id" dataSource={rows} columns={[
      { title: 'کد', dataIndex: 'code' },
      { title: 'نام', dataIndex: 'name' },
      { title: 'حالت', dataIndex: 'auth_mode', render: value => <Tag color={value === 'LEGACY_SERVICE_TOKEN' ? 'orange' : 'blue'}>{value}</Tag> },
      { title: 'Connection', dataIndex: 'token_connection_ref', render: value => value || '—' },
      { title: 'Secret Ref', dataIndex: 'credential_secret_ref', render: value => value || '—' },
      { title: 'استفاده', dataIndex: 'usage_count' },
      { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
      { title: 'عملیات', render: (_, row) => <Space wrap>
        <Button onClick={() => show(row)}>ویرایش</Button>
        {row.auth_mode === 'LEGACY_SERVICE_TOKEN' && <>
          <Button onClick={() => void action(row, 'connection-test', 'تنظیم اتصال معتبر است')}>اعتبارسنجی اتصال</Button>
          <Button onClick={() => void action(row, 'token-test', 'دریافت توکن موفق')}>تست توکن</Button>
          <Button onClick={() => void api(`/outbound-auth-profiles/${row.id}/cache-status`)
            .then((result: Row) => message.info(result.cached ? 'توکن معتبر در cache وجود دارد' : 'cache خالی است'))}>Cache</Button>
          <Popconfirm title="توکن cache شده باطل شود؟" onConfirm={() => action(row, 'invalidate-token', 'توکن باطل شد')}>
            <Button danger>ابطال توکن</Button>
          </Popconfirm>
        </>}
        <Popconfirm title="وضعیت تغییر کند؟" onConfirm={() => api(
          `/outbound-auth-profiles/${row.id}/status?version=${row.version}`,
          { method: 'PATCH', body: JSON.stringify({ active: !row.active }) }).then(load)}>
          <Button>{row.active ? 'غیرفعال' : 'فعال'}</Button>
        </Popconfirm>
      </Space> },
    ]} />
    <Modal open={open} width={900} title={editing ? 'ویرایش پروفایل' : 'پروفایل جدید'}
      onCancel={() => setOpen(false)} onOk={() => form.submit()}>
      <Form form={form} layout="vertical" onFinish={value => void save(value).catch(error => message.error(error.message))}>
        <Space wrap align="start">
          <Form.Item name="code" label="کد" rules={required}><Input /></Form.Item>
          <Form.Item name="name" label="نام" rules={required}><Input /></Form.Item>
          <Form.Item name="authMode" label="Auth Mode" rules={required}>
            <Select style={{ width: 240 }} options={['FORWARD_USER_TOKEN', 'LEGACY_SERVICE_TOKEN'].map(value => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="tokenConnectionRef" label="Token Connection">
            <Select allowClear style={{ width: 300 }} options={connections.filter(item => item.active)
              .map(item => ({ value: item.connection_ref, label: `${item.name} (${item.connection_ref})` }))} />
          </Form.Item>
          <Form.Item name="tokenEndpointPath" label="Token Endpoint Path"><Input placeholder="/oauth/token" /></Form.Item>
          <Form.Item name="requestFormat" label="Request Adapter" rules={required}>
            <Select style={{ width: 230 }} options={formats.map(value => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="credentialSecretRef" label="Credential Secret Ref"><Input placeholder="secret://legacy/service-account" /></Form.Item>
          <Form.Item name="scope" label="Scope"><Input /></Form.Item>
          <Form.Item name="audience" label="Audience"><Input /></Form.Item>
          <Form.Item name="tokenResponsePointer" label="Token JSON Pointer" rules={required}><Input /></Form.Item>
          <Form.Item name="expiresInResponsePointer" label="Expires JSON Pointer" rules={required}><Input /></Form.Item>
          <Form.Item name="tokenTypeResponsePointer" label="Token Type Pointer" rules={required}><Input /></Form.Item>
          <Form.Item name="authorizationScheme" label="Scheme" rules={required}><Input /></Form.Item>
          <Form.Item name="credentialTransport" label="Credential Transport" rules={required}>
            <Select style={{ width: 260 }} options={['USER_AUTHORIZATION_HEADER', 'INTERNAL_LEGACY_HEADER'].map(value => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="expirySkewSeconds" label="Expiry Skew"><InputNumber min={5} max={600} /></Form.Item>
          <Form.Item name="connectTimeoutMs" label="Connect Timeout"><InputNumber min={100} /></Form.Item>
          <Form.Item name="responseTimeoutMs" label="Response Timeout"><InputNumber min={100} /></Form.Item>
          <Form.Item name="maxTokenResponseSize" label="Max Response"><InputNumber min={1024} /></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
        <Form.Item name="description" label="توضیح"><Input.TextArea /></Form.Item>
      </Form>
    </Modal>
  </Card>;
}
