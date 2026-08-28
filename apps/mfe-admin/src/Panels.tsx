import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  message,
} from 'antd';

type PanelRow = Record<string, any>;
const required = [{ required: true, message: 'این فیلد الزامی است' }];
let csrf: { headerName: string; token: string } | undefined;

async function panelsApi(path: string, init: RequestInit = {}) {
  const method = init.method ?? 'GET';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };

  if (method !== 'GET') {
    const token = csrf ?? await fetch('/api/v1/csrf', { credentials: 'same-origin' })
      .then((response) => response.json());
    csrf = token;
    headers[token.headerName] = token.token;
  }

  const response = await fetch(`/api/v1/admin${path}`, {
    ...init,
    headers,
    credentials: 'same-origin',
  });
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}

export function PanelsView() {
  const [rows, setRows] = useState<PanelRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PanelRow>();
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await panelsApi('/panels'));
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const show = (row?: PanelRow) => {
    setEditing(row);
    form.setFieldsValue(row ?? {
      active: true,
      semantic_version: '0.1.0',
      contract_version: '1',
      exposed_module: './bootstrap',
      sort_order: 50,
    });
    setOpen(true);
  };

  const save = async (values: PanelRow) => {
    try {
      const body = {
        code: values.code,
        nameFa: values.name_fa,
        nameEn: values.name_en,
        slug: values.slug,
        remoteEntry: values.remote_entry_path,
        exposedModule: values.exposed_module,
        routeBasePath: values.route_base_path,
        semanticVersion: values.semantic_version,
        contractVersion: values.contract_version,
        integrity: values.integrity || null,
        active: values.active ?? true,
        sortOrder: values.sort_order ?? 0,
      };
      await panelsApi(editing ? `/panels/${editing.id}?version=${editing.version}` : '/panels', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      });
      message.success('میکروفرانت ذخیره شد');
      setOpen(false);
      await load();
    } catch (reason) {
      message.error((reason as Error).message);
    }
  };

  return <Card
    title="مدیریت میکروفرانت‌ها"
    extra={<Space>
      <Button onClick={() => void load()} loading={loading}>بارگذاری مجدد</Button>
      <Button type="primary" onClick={() => show()}>میکرو جدید</Button>
    </Space>}
  >
    {error && <Alert
      showIcon
      type="error"
      message="دریافت لیست میکروفرانت‌ها ناموفق بود"
      description={error}
      action={<Button onClick={() => void load()}>تلاش مجدد</Button>}
      style={{ marginBottom: 16 }}
    />}
    <Table
      rowKey="id"
      loading={loading}
      dataSource={rows}
      pagination={false}
      locale={{ emptyText: error ? 'ارتباط با سرویس برقرار نشد' : 'میکروفرانتی تعریف نشده است' }}
      columns={[
        { title: 'کد', dataIndex: 'code' },
        { title: 'نام', dataIndex: 'name_fa' },
        { title: 'مسیر', dataIndex: 'route_base_path' },
        { title: 'Remote Entry', dataIndex: 'remote_entry_path' },
        { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
        { title: 'عملیات', render: (_, row) => <Space>
          <Button onClick={() => show(row)}>ویرایش</Button>
          <Popconfirm title="غیرفعال شود؟" onConfirm={() => panelsApi(`/panels/${row.id}?version=${row.version}`, { method: 'DELETE' }).then(load)}>
            <Button danger>غیرفعال</Button>
          </Popconfirm>
        </Space> },
      ]}
    />
    <Modal open={open} title={editing ? 'ویرایش میکروفرانت' : 'تعریف میکروفرانت'} onCancel={() => setOpen(false)} onOk={() => form.submit()} width={760}>
      <Form form={form} layout="vertical" onFinish={save}>
        <Space wrap align="start">
          <Form.Item name="code" label="کد" rules={required}><Input /></Form.Item>
          <Form.Item name="slug" label="Slug" rules={required}><Input /></Form.Item>
          <Form.Item name="name_fa" label="نام فارسی" rules={required}><Input /></Form.Item>
          <Form.Item name="name_en" label="نام انگلیسی" rules={required}><Input /></Form.Item>
          <Form.Item name="remote_entry_path" label="Remote Entry" rules={required}><Input style={{ width: 300 }} /></Form.Item>
          <Form.Item name="exposed_module" label="Exposed Module" rules={required}><Input /></Form.Item>
          <Form.Item name="route_base_path" label="مسیر پایه" rules={required}><Input /></Form.Item>
          <Form.Item name="semantic_version" label="نسخه" rules={required}><Input /></Form.Item>
          <Form.Item name="contract_version" label="نسخه قرارداد" rules={required}><Input /></Form.Item>
          <Form.Item name="sort_order" label="ترتیب"><InputNumber /></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
      </Form>
    </Modal>
  </Card>;
}
