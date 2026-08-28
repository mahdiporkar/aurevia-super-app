import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';

type Asset = Record<string, any>;
let csrf: { headerName: string; token: string } | undefined;

async function adminApi(path: string, init: RequestInit = {}) {
  const method = init.method ?? 'GET';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (method !== 'GET') {
    const token = csrf ?? await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then((response) => response.json());
    csrf = token;
    headers[token.headerName] = token.token;
  }
  const response = await fetch(`/api/v1/admin${path}`, { ...init, headers, credentials: 'same-origin' });
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}

export function SupersetAssets() {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const load = () => adminApi('/superset-assets').then(setAssets).catch((error) => message.error(error.message));

  useEffect(() => { void load(); }, []);

  const save = async (values: Asset) => {
    setSaving(true);
    try {
      await adminApi('/superset-assets', {
        method: 'POST',
        body: JSON.stringify({ ...values, ownerExternalId: values.ownerExternalId || null }),
      });
      message.success('دارایی Superset ثبت و به درخت دسترسی افزوده شد');
      form.resetFields();
      setOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Alert showIcon type="info" message="هر دارایی یک گره با عملیات مشاهده در درخت دسترسی می‌سازد. سپس از تب «دسترسی کاربران» آن را به فرد موردنظر تخصیص دهید." />
    <Card title="کاتالوگ Superset" extra={<Button type="primary" onClick={() => setOpen(true)}>ثبت گزارش یا داشبورد</Button>}>
      <Table rowKey="id" dataSource={assets} pagination={false} columns={[
        { title: 'عنوان', dataIndex: 'title' },
        { title: 'نوع', dataIndex: 'asset_type', render: (value) => <Tag color={value === 'DASHBOARD' ? 'blue' : 'purple'}>{value}</Tag> },
        { title: 'شناسه Superset', dataIndex: 'external_id' },
        { title: 'مسیر', dataIndex: 'url_path' },
        { title: 'گره دسترسی', dataIndex: 'resource_key' },
        { title: 'وضعیت', dataIndex: 'published', render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? 'منتشرشده' : 'پیش‌نویس'}</Tag> },
      ]} />
    </Card>
    <Modal open={open} title="ثبت دارایی Superset" okText="ثبت و افزودن به درخت" cancelText="انصراف" confirmLoading={saving} onCancel={() => setOpen(false)} onOk={() => form.submit()}>
      <Form form={form} layout="vertical" initialValues={{ assetType: 'DASHBOARD', published: true }} onFinish={save}>
        <Form.Item name="title" label="عنوان" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="externalId" label="شناسه در Superset" rules={[{ required: true }]}><Input placeholder="42 یا sales-overview" /></Form.Item>
        <Form.Item name="assetType" label="نوع" rules={[{ required: true }]}><Select options={[{ value: 'DASHBOARD', label: 'داشبورد' }, { value: 'CHART', label: 'گزارش / نمودار' }]} /></Form.Item>
        <Form.Item name="urlPath" label="مسیر Superset" rules={[{ required: true }]}><Input placeholder="/superset/dashboard/42/" /></Form.Item>
        <Form.Item name="ownerExternalId" label="شناسه مالک"><Input placeholder="report-designer" /></Form.Item>
        <Form.Item name="published" valuePropName="checked"><Checkbox>منتشرشده و قابل تخصیص</Checkbox></Form.Item>
      </Form>
    </Modal>
  </Space>;
}
