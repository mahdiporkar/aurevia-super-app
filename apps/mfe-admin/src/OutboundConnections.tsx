import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Form, Input, Modal, Space, Table, Tag, message } from 'antd';

type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
type ConnectionRow = {
  id: string;
  connection_ref: string;
  name: string;
  kind: 'LEGACY_TOKEN';
  base_url: string;
  tls_required: boolean;
  active: boolean;
  version: number;
};
type ConnectionWrite = {
  connectionRef: string;
  name: string;
  baseUrl: string;
  tlsRequired: boolean;
  active: boolean;
  version: number;
};

const required = [{ required: true, message: 'این فیلد الزامی است' }];

/** Manages approved non-secret token endpoints. Credentials stay in the secret store. */
export function OutboundConnections({ api }: { api: AdminApi }) {
  const [rows, setRows] = useState<ConnectionRow[]>([]);
  const [editing, setEditing] = useState<ConnectionRow>();
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form] = Form.useForm<ConnectionWrite>();

  const load = useCallback(async () => {
    setLoading(true);
    try { setRows(await api('/outbound-connections')); }
    catch (error) { message.error((error as Error).message); }
    finally { setLoading(false); }
  }, [api]);

  useEffect(() => { void load(); }, [load]);

  const show = (row?: ConnectionRow) => {
    setEditing(row);
    form.setFieldsValue(row ? {
      connectionRef: row.connection_ref,
      name: row.name,
      baseUrl: row.base_url,
      tlsRequired: row.tls_required,
      active: row.active,
      version: row.version,
    } : {
      connectionRef: 'connection://legacy/',
      name: '',
      baseUrl: 'https://',
      tlsRequired: true,
      active: true,
      version: 0,
    });
    setOpen(true);
  };

  const save = async (value: ConnectionWrite) => {
    await api(editing ? `/outbound-connections/${editing.id}` : '/outbound-connections', {
      method: editing ? 'PUT' : 'POST',
      body: JSON.stringify(value),
    });
    setOpen(false);
    message.success('اتصال تأییدشده ذخیره شد');
    await load();
  };

  return <Card title="اتصال‌های خروجی تأییدشده" extra={
    <Button type="primary" onClick={() => show()}>اتصال جدید</Button>
  }>
    <Alert
      showIcon
      type="info"
      message="آدرس endpoint از Secret جدا است"
      description="در این بخش فقط origin مجاز ثبت می‌شود. نام کاربری و رمز در Secret Store نگهداری می‌شوند و BFF نیز host/port را با allowlist استقرار کنترل می‌کند."
      style={{ marginBottom: 16 }}
    />
    <Table<ConnectionRow>
      rowKey="id"
      loading={loading}
      dataSource={rows}
      columns={[
        { title: 'نام', dataIndex: 'name' },
        { title: 'Reference', dataIndex: 'connection_ref' },
        { title: 'Origin', dataIndex: 'base_url' },
        { title: 'TLS', render: (_, row) => <Tag color={row.tls_required ? 'green' : 'orange'}>{row.tls_required ? 'اجباری' : 'اختیاری'}</Tag> },
        { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
        { title: 'عملیات', render: (_, row) => <Button onClick={() => show(row)}>ویرایش</Button> },
      ]}
    />
    <Modal
      open={open}
      title={editing ? 'ویرایش اتصال خروجی' : 'اتصال خروجی جدید'}
      onCancel={() => setOpen(false)}
      onOk={() => form.submit()}
    >
      <Form form={form} layout="vertical" onFinish={value => void save(value).catch(error => message.error(error.message))}>
        <Form.Item name="name" label="نام" rules={required}><Input /></Form.Item>
        <Form.Item name="connectionRef" label="Reference پایدار" rules={required}>
          <Input disabled={Boolean(editing)} placeholder="connection://legacy/payroll" />
        </Form.Item>
        <Form.Item name="baseUrl" label="Origin سرویس توکن" rules={[...required, { type: 'url' }]}>
          <Input placeholder="https://identity.legacy.example:443" />
        </Form.Item>
        <Space>
          <Form.Item name="tlsRequired" valuePropName="checked"><Checkbox>TLS اجباری</Checkbox></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
        <Form.Item name="version" hidden><Input /></Form.Item>
      </Form>
    </Modal>
  </Card>;
}
