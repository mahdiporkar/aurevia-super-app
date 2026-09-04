import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tabs,
  Tag, Typography, message,
} from 'antd';
import type { RemoteContext, RemoteModule } from '@aurevia/contracts';
import { AccessStudio } from './AccessStudio';
import { IntegrationTestLab } from './IntegrationTestLab';
import { LogsView } from './Logs';
import { OperatorGuide } from './OperatorGuide';
import { OuAccessManagement } from './OuAccessManagement';
import { OutboundAuthProfiles } from './OutboundAuthProfiles';
import { OutboundConnections } from './OutboundConnections';
import { PanelsView } from './Panels';
import { ProxyRouteManagement } from './ProxyRoutes';
import { SupersetAssets } from './SupersetAssets';
import { SupersetInstances } from './SupersetInstances';

export const contractVersion = '1' as const;
type Row = Record<string, any>;
let csrf: { headerName: string; token: string } | undefined;
const required = [{ required: true, message: 'این فیلد الزامی است' }];

async function api(path: string, init: RequestInit = {}) {
  const method = init.method ?? 'GET';
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> ?? {}),
  };
  if (method !== 'GET') {
    const token = csrf ?? await fetch('/api/v1/csrf', { credentials: 'same-origin' })
      .then(response => response.json());
    csrf = token;
    headers[token.headerName] = token.token;
  }
  const response = await fetch(`/api/v1/admin${path}`, {
    ...init, headers, credentials: 'same-origin',
  });
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}

function IdentityAndRoles() {
  const [users, setUsers] = useState<Row[]>([]);
  const [groups, setGroups] = useState<Row[]>([]);
  const [accessGroups, setAccessGroups] = useState<Row[]>([]);
  const [roles, setRoles] = useState<Row[]>([]);
  const [assignments, setAssignments] = useState<Row[]>([]);
  const [roleOpen, setRoleOpen] = useState(false);
  const [roleForm] = Form.useForm();
  const [assignmentForm] = Form.useForm();

  const load = () => Promise.all([
    api('/users'), api('/directory-groups'), api('/ou-access/access-groups'),
    api('/roles'), api('/role-assignments'),
  ]).then(([nextUsers, nextGroups, nextAccessGroups, nextRoles, nextAssignments]) => {
    setUsers(nextUsers);setGroups(nextGroups);setAccessGroups(nextAccessGroups);
    setRoles(nextRoles);setAssignments(nextAssignments);
  }).catch(error => message.error(error.message));

  useEffect(() => { void load(); }, []);

  const createRole = async (values: Row) => {
    try {
      await api('/roles', { method: 'POST', body: JSON.stringify(values) });
      setRoleOpen(false);roleForm.resetFields();await load();
      message.success('نقش کاربردی ایجاد شد');
    } catch(error) { message.error((error as Error).message); }
  };
  const assign = async (values: Row) => {
    try {
      await api('/role-assignments', {
        method: 'POST', body: JSON.stringify({ ...values, expiresAt: values.expiresAt || null }),
      });
      assignmentForm.resetFields();await load();message.success('نقش تخصیص یافت');
    } catch(error) { message.error((error as Error).message); }
  };
  const subjectOptions = (type: string) =>
    (type === 'DIRECTORY_GROUP' ? groups : type === 'ACCESS_GROUP' ? accessGroups : users)
      .map(item => ({
        value: item.id,
        label: type === 'DIRECTORY_GROUP'
          ? `${item.display_name} — ${item.normalized_path}`
          : type === 'ACCESS_GROUP'
            ? `${item.name} — ${item.code}`
            : `${item.display_name ?? item.username} — ${item.username}`,
      }));

  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Alert showIcon type="info" message="گروه‌های سازمانی نقش نیستند"
      description="گروه‌ها از Keycloak/LDAP همگام و فقط‌خواندنی‌اند. نقش‌های کاربردی بسته‌های قابلیت هستند و می‌توان آن‌ها را به کاربر یا گروه تخصیص داد." />
    <Card title="گروه‌های سازمانی همگام‌شده">
      <Table rowKey="id" dataSource={groups} pagination={{ pageSize: 8 }} columns={[
        { title: 'نام', dataIndex: 'display_name' },
        { title: 'مسیر پایدار', dataIndex: 'normalized_path' },
        { title: 'شناسه خارجی', dataIndex: 'external_id' },
        { title: 'وضعیت', render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{row.status}</Tag> },
        { title: 'آخرین همگام‌سازی', dataIndex: 'sync_at', render: value => value ? new Date(value).toLocaleString('fa-IR') : '—' },
      ]} />
    </Card>
    <Card title="نقش‌های کاربردی" extra={<Button type="primary" onClick={() => setRoleOpen(true)}>نقش جدید</Button>}>
      <Table rowKey="id" dataSource={roles} pagination={false} columns={[
        { title: 'کلید نقش', dataIndex: 'role_key' },
        { title: 'نام فارسی', dataIndex: 'name_fa' },
        { title: 'نام انگلیسی', dataIndex: 'name_en' },
        { title: 'وضعیت', dataIndex: 'status' },
      ]} />
    </Card>
    <Card title="تخصیص نقش">
      <Form form={assignmentForm} layout="inline" onFinish={assign} initialValues={{ subjectType: 'USER' }}>
        <Form.Item name="subjectType" rules={required}>
          <Select style={{ width: 180 }} options={[
            { value: 'USER', label: 'کاربر' },
            { value: 'DIRECTORY_GROUP', label: 'گروه LDAP' },
            { value: 'ACCESS_GROUP', label: 'گروه OU' },
          ]} />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(before, after) => before.subjectType !== after.subjectType}>
          {() => {
            const type = assignmentForm.getFieldValue('subjectType');
            return <Form.Item name="subjectId" rules={required}>
              <Select showSearch optionFilterProp="label" style={{ width: 340 }}
                placeholder={type === 'USER' ? 'انتخاب کاربر' : 'انتخاب گروه'}
                options={subjectOptions(type)} />
            </Form.Item>;
          }}
        </Form.Item>
        <Form.Item name="roleId" rules={required}>
          <Select style={{ width: 260 }} placeholder="نقش" options={roles
            .filter(role => role.status === 'ACTIVE')
            .map(role => ({ value: role.id, label: `${role.name_fa} (${role.role_key})` }))} />
        </Form.Item>
        <Button type="primary" htmlType="submit">تخصیص</Button>
      </Form>
      <Table style={{ marginTop: 16 }}
        rowKey={row => `${row.subject_type}:${row.subject_id}:${row.role_id}`}
        dataSource={assignments} pagination={{ pageSize: 8 }} columns={[
          { title: 'نوع', dataIndex: 'subject_type', render: value => value === 'USER' ? 'کاربر' : value === 'ACCESS_GROUP' ? 'گروه OU' : 'گروه LDAP' },
          { title: 'کاربر/گروه', dataIndex: 'subject_name' },
          { title: 'نقش', dataIndex: 'role_key' },
          { title: 'انقضا', dataIndex: 'expires_at', render: value => value ? new Date(value).toLocaleString('fa-IR') : 'بدون انقضا' },
          { title: '', render: (_, row) => <Popconfirm title="این تخصیص لغو شود؟"
            onConfirm={() => api(`/role-assignments/${row.subject_type}/${row.subject_id}/${row.role_id}`,
              { method: 'DELETE' }).then(load).catch(error => message.error((error as Error).message))}>
            <Button danger>لغو</Button>
          </Popconfirm> },
        ]} />
    </Card>
    <Modal open={roleOpen} title="تعریف نقش کاربردی" onCancel={() => setRoleOpen(false)}
      onOk={() => roleForm.submit()}>
      <Form form={roleForm} layout="vertical" onFinish={createRole}>
        <Form.Item name="roleKey" label="کلید پایدار نقش" rules={required}>
          <Input placeholder="hr-supervisor" />
        </Form.Item>
        <Form.Item name="nameFa" label="نام فارسی" rules={required}><Input /></Form.Item>
        <Form.Item name="nameEn" label="نام انگلیسی" rules={required}><Input /></Form.Item>
      </Form>
    </Modal>
  </Space>;
}

function App({ context }: { context: RemoteContext }) {
  const standalone = context.manifest.version === 'standalone';
  const permissions = context.manifest.permissions;
  const platformAdmin = standalone || (permissions['application:aurevia'] ?? []).includes('admin');
  const reportDesigner = platformAdmin ||
    (permissions['module:admin.superset-catalog'] ?? []).some(action =>
      ['view', 'admin', 'assign'].includes(action));
  const administration = [
    { key: 'operator-guide', label: 'راهنمای فرم‌ها', children: <OperatorGuide /> },
    { key: 'ou-access', label: 'دسترسی مبتنی بر OU', children: <OuAccessManagement /> },
    { key: 'access-studio', label: 'استودیوی دسترسی', children: <AccessStudio /> },
    { key: 'panels', label: 'میکروفرانت‌ها', children: <PanelsView /> },
    { key: 'proxy-routes', label: 'راهبری Proxy', children: <ProxyRouteManagement api={api} /> },
    { key: 'outbound-connections', label: 'اتصال‌های Legacy', children: <OutboundConnections api={api} /> },
    { key: 'outbound-auth', label: 'پروفایل‌های احراز هویت سرویس‌ها', children: <OutboundAuthProfiles api={api} /> },
    { key: 'integration-test', label: 'آزمایشگاه اتصال', children: <IntegrationTestLab api={api} /> },
    { key: 'superset-instances', label: 'محیط‌های Superset', children: <SupersetInstances api={api} /> },
    { key: 'identity', label: 'گروه‌ها و نقش‌ها', children: <IdentityAndRoles /> },
    { key: 'logs', label: 'لاگ‌ها', children: <LogsView /> },
  ];
  const items = [
    ...(platformAdmin ? administration : []),
    ...(reportDesigner ? [{ key: 'superset', label: 'گزارش‌ها و داشبوردها', children: <SupersetAssets /> }] : []),
  ];
  return <Card>
    <Typography.Title level={3}>مرکز مدیریت Aurevia</Typography.Title>
    <Alert showIcon type="info" message={platformAdmin
      ? 'تعریف میکروفرانت، مدل‌سازی منابع و مدیریت دسترسی مبتنی بر OU'
      : 'راهبری گزارش‌ها و داشبوردهای مجاز'} />
    <Tabs style={{ marginTop: 16 }} items={items} />
  </Card>;
}

export const mount: RemoteModule['mount'] = (element: HTMLElement, context: RemoteContext) => {
  const root = createRoot(element);
  root.render(<App context={context} />);
  return () => root.unmount();
};
