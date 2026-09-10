import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Card, Checkbox, Form, Input, Modal, Select, Space, Table, Tag, message,
} from 'antd';

export type AdminApi = (path: string, init?: RequestInit) => Promise<any>;

type Zone = 'PUBLIC' | 'OPERATION';
type InstanceRow = {
  id: string;
  code: string;
  name: string;
  zone: Zone;
  base_url: string;
  connection_ref: string;
  auth_mode: 'REMOTE_USER' | 'OIDC' | 'GUEST_TOKEN';
  tls_required: boolean;
  active: boolean;
  proxy_mode: boolean;
  health_status: 'UNKNOWN' | 'ACTIVE' | 'UNREACHABLE' | 'DISABLED';
  metadata: Record<string, unknown>;
  version: number;
};
type MappingRow = {
  id: string;
  public_instance_id: string;
  public_code: string;
  public_name: string;
  operation_instance_id: string;
  operation_code: string;
  operation_name: string;
  public_path: string;
  is_default: boolean;
  active: boolean;
};

export function SupersetInstances({ api, healthApi = api }:
    { api: AdminApi; healthApi?: AdminApi }) {
  const [instances, setInstances] = useState<InstanceRow[]>([]);
  const [mappings, setMappings] = useState<MappingRow[]>([]);
  const [editing, setEditing] = useState<InstanceRow>();
  const [instanceOpen, setInstanceOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [checking, setChecking] = useState<string>();
  const [instanceForm] = Form.useForm();
  const [mappingForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [nextInstances, nextMappings] = await Promise.all([
        api('/superset-instances'), api('/superset-instances/mappings'),
      ]);
      setInstances(nextInstances);
      setMappings(nextMappings);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => { void load(); }, [load]);

  const showInstance = (row?: InstanceRow) => {
    setEditing(row);
    instanceForm.setFieldsValue(row ? {
      code: row.code, name: row.name, zone: row.zone, baseUrl: row.base_url,
      connectionRef: row.connection_ref, authMode: row.auth_mode,
      tlsRequired: row.tls_required, active: row.active, proxyMode: row.proxy_mode,
      metadata: JSON.stringify(row.metadata ?? {}, null, 2), version: row.version,
    } : {
      zone: 'OPERATION', authMode: 'REMOTE_USER', tlsRequired: true,
      active: true, proxyMode: true, metadata: '{}', version: 0,
    });
    setInstanceOpen(true);
  };

  const saveInstance = async (values: Record<string, unknown>) => {
    try {
      const metadata = typeof values.metadata === 'string' && values.metadata.trim()
        ? JSON.parse(values.metadata) : {};
      await api(editing ? `/superset-instances/${editing.id}` : '/superset-instances', {
        method: editing ? 'PUT' : 'POST', body: JSON.stringify({ ...values, metadata }),
      });
      setInstanceOpen(false);
      await load();
      message.success('اتصال Superset ذخیره شد');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const saveMapping = async (values: Record<string, unknown>) => {
    try {
      await api('/superset-instances/mappings', {
        method: 'POST', body: JSON.stringify(values),
      });
      mappingForm.resetFields();
      await load();
      message.success('نگاشت Proxy عمومی به عملیاتی ذخیره شد');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const checkHealth = async (row: InstanceRow) => {
    setChecking(row.code);
    try {
      const result = await healthApi(`/api/integrations/superset/${row.code}/health`);
      await load();
      message.info(`${row.name}: ${result.status}`);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setChecking(undefined);
    }
  };

  const publicInstances = useMemo(
    () => instances.filter(item => item.zone === 'PUBLIC' && item.active), [instances]);
  const operationInstances = useMemo(
    () => instances.filter(item => item.zone === 'OPERATION' && item.active), [instances]);

  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Alert showIcon type="info" message="رجیستری اتصال‌های Superset"
      description="URL مقصد فقط در رجیستری ذخیره می‌شود و تغییر آن به restart یا rebuild Core نیاز ندارد. Production به‌طور پیش‌فرض فقط HTTPS عمومی را می‌پذیرد؛ شبکه خصوصی سازمان با policy و CIDR مصوب فعال می‌شود." />
    <Card title="محیط‌های Superset"
      extra={<Button type="primary" onClick={() => showInstance()}>محیط جدید</Button>}>
      <Table rowKey="id" loading={loading} dataSource={instances} pagination={false} columns={[
        { title: 'کد', dataIndex: 'code' },
        { title: 'نام', dataIndex: 'name' },
        { title: 'محیط', dataIndex: 'zone', render: value =>
          <Tag color={value === 'PUBLIC' ? 'blue' : 'purple'}>{value}</Tag> },
        { title: 'Origin', dataIndex: 'base_url' },
        { title: 'اتصال امن', dataIndex: 'connection_ref' },
        { title: 'Auth', dataIndex: 'auth_mode' },
        { title: 'TLS', render: (_, row) => row.tls_required ? 'اجباری' : 'محلی' },
        { title: 'Proxy', render: (_, row) => row.proxy_mode ? 'فعال' : 'غیرفعال' },
        { title: 'سلامت', dataIndex: 'health_status', render: value =>
          <Tag color={value === 'ACTIVE' ? 'green' : value === 'UNREACHABLE' ? 'red' : 'default'}>
            {value}</Tag> },
        { title: 'وضعیت', render: (_, row) =>
          <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
        { title: '', render: (_, row) => <Space>
          <Button loading={checking === row.code} onClick={() => void checkHealth(row)}>سلامت</Button>
          <Button onClick={() => showInstance(row)}>ویرایش</Button>
        </Space> },
      ]} />
    </Card>
    <Card title="نگاشت Proxy عمومی → عملیاتی">
      <Form form={mappingForm} layout="inline" onFinish={saveMapping}
        initialValues={{ publicPath: '/reports-runtime', isDefault: false, active: true }}>
        <Form.Item name="publicInstanceId" rules={[{ required: true }]}>
          <Select placeholder="محیط عمومی" style={{ width: 230 }} options={publicInstances.map(item =>
            ({ value: item.id, label: `${item.name} (${item.code})` }))} />
        </Form.Item>
        <Form.Item name="operationInstanceId" rules={[{ required: true }]}>
          <Select placeholder="محیط عملیاتی" style={{ width: 230 }} options={operationInstances.map(item =>
            ({ value: item.id, label: `${item.name} (${item.code})` }))} />
        </Form.Item>
        <Form.Item name="publicPath" rules={[{ required: true }]}>
          <Input placeholder="/reports-runtime" style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="isDefault" valuePropName="checked"><Checkbox>پیش‌فرض</Checkbox></Form.Item>
        <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        <Button type="primary" htmlType="submit">ذخیره نگاشت</Button>
      </Form>
      <Table style={{ marginTop: 16 }} rowKey="id" dataSource={mappings} pagination={false} columns={[
        { title: 'عمومی', render: (_, row) => `${row.public_name} (${row.public_code})` },
        { title: 'عملیاتی', render: (_, row) => `${row.operation_name} (${row.operation_code})` },
        { title: 'مسیر عمومی', dataIndex: 'public_path' },
        { title: 'URL ورود', render: (_, row) =>
          <code>{`/api/integrations/superset/${row.public_code}/`}</code> },
        { title: 'پیش‌فرض', render: (_, row) => row.is_default ? <Tag color="gold">پیش‌فرض</Tag> : '—' },
        { title: 'وضعیت', render: (_, row) =>
          <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
      ]} />
    </Card>
    <Modal open={instanceOpen} title={editing ? 'ویرایش محیط Superset' : 'محیط Superset جدید'}
      width={760} onCancel={() => setInstanceOpen(false)} onOk={() => instanceForm.submit()}>
      <Form form={instanceForm} layout="vertical" onFinish={saveInstance}>
        <Space wrap align="start">
          <Form.Item name="code" label="کد پایدار" rules={[{ required: true }]}>
            <Input disabled={Boolean(editing)} placeholder="operation-tehran" />
          </Form.Item>
          <Form.Item name="name" label="نام" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="zone" label="محیط" rules={[{ required: true }]}>
            <Select style={{ width: 180 }} options={[
              { value: 'PUBLIC', label: 'عمومی' }, { value: 'OPERATION', label: 'عملیاتی' },
            ]} />
          </Form.Item>
          <Form.Item name="baseUrl" label="URL پایه شامل scheme، host، port و base path"
            rules={[{ required: true, type: 'url' }]}>
            <Input style={{ width: 330 }} placeholder="https://superset.example.ir/bi" />
          </Form.Item>
          <Form.Item name="connectionRef" label="Connection reference (اختیاری)">
            <Input style={{ width: 300 }} placeholder="connection://superset/operation-tehran" />
          </Form.Item>
          <Form.Item name="authMode" label="روش احراز هویت" rules={[{ required: true }]}>
            <Select style={{ width: 190 }} options={['REMOTE_USER', 'OIDC', 'GUEST_TOKEN']
              .map(value => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="tlsRequired" valuePropName="checked"><Checkbox>TLS اجباری</Checkbox></Form.Item>
          <Form.Item name="proxyMode" valuePropName="checked"><Checkbox>Same-origin Proxy</Checkbox></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
          <Form.Item name="metadata" label="Metadata (JSON)" rules={[{
            validator: async (_, value) => { if (value) JSON.parse(value); },
          }]}>
            <Input.TextArea rows={4} style={{ width: 690 }} placeholder='{"owner":"BI"}' />
          </Form.Item>
          <Form.Item name="version" hidden><Input /></Form.Item>
        </Space>
      </Form>
    </Modal>
  </Space>;
}
