import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Form, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';

type Row = Record<string, any>;
type Level = 'VIEW' | 'EDIT' | 'MANAGE';
type SubjectType = 'USER' | 'GROUP' | 'ROLE';
const levels: Record<Level, { action: string; relation: string; label: string }> = {
  VIEW: { action: 'view', relation: 'viewer', label: 'مشاهده' },
  EDIT: { action: 'update', relation: 'editor', label: 'ویرایش' },
  MANAGE: { action: 'admin', relation: 'manager', label: 'مدیریت' },
};
let csrf: { headerName: string; token: string } | undefined;

async function json(url: string, init: RequestInit = {}) {
  const response = await fetch(url, { ...init, credentials: 'same-origin' });
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}

async function admin(path: string, init: RequestInit = {}) {
  const method = init.method ?? 'GET';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (method !== 'GET') {
    const token = csrf ?? await json('/api/v1/csrf');
    csrf = token;
    headers[token.headerName] = token.token;
  }
  return json(`/api/v1/admin${path}`, { ...init, headers });
}

async function liveCatalog() {
  await fetch('/reports-runtime/superset/welcome/', { credentials: 'same-origin' });
  const query = '?q=(page:0,page_size:100)';
  const [dashboards, charts] = await Promise.all([
    json(`/reports-runtime/api/v1/dashboard/${query}`),
    json(`/reports-runtime/api/v1/chart/${query}`),
  ]);
  return [
    ...(dashboards.result ?? []).map((item: Row) => ({
      key: `dashboard:${item.id}`, externalId: `dashboard:${item.id}`,
      supersetId: String(item.id), assetType: 'DASHBOARD', title: item.dashboard_title,
      urlPath: `/superset/dashboard/${item.id}/`, published: item.published ?? true,
      detail: item.slug,
    })),
    ...(charts.result ?? []).map((item: Row) => ({
      key: `chart:${item.id}`, externalId: `chart:${item.id}`,
      supersetId: String(item.id), assetType: 'CHART', title: item.slice_name,
      urlPath: `/explore/?slice_id=${item.id}`, published: true, detail: item.viz_type,
    })),
  ];
}

export function SupersetAssets() {
  const [catalog, setCatalog] = useState<Row[]>([]);
  const [stored, setStored] = useState<Row[]>([]);
  const [users, setUsers] = useState<Row[]>([]);
  const [groups, setGroups] = useState<Row[]>([]);
  const [roles, setRoles] = useState<Row[]>([]);
  const [actions, setActions] = useState<Row[]>([]);
  const [selected, setSelected] = useState<Row>();
  const [grants, setGrants] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<string>();
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true); setError(undefined);
    try {
      const [live, assets, appUsers, appGroups, appRoles, appActions] = await Promise.all([
        liveCatalog(), admin('/superset-assets'), admin('/users'), admin('/groups'), admin('/roles'), admin('/actions'),
      ]);
      setCatalog(live); setStored(assets); setUsers(appUsers);setGroups(appGroups);setRoles(appRoles);setActions(appActions);
    } catch (reason) { setError((reason as Error).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const rows = useMemo<Row[]>(() => {
    const byExternalId = new Map(stored.map((asset) => [asset.external_id, asset]));
    const liveIds = new Set(catalog.map((asset) => asset.externalId));
    return [
      ...catalog.map((asset) => ({ ...asset, registered: byExternalId.get(asset.externalId) })),
      ...stored.filter((asset) => !liveIds.has(asset.external_id)).map((asset) => ({
        key: `stored:${asset.id}`, externalId: asset.external_id, supersetId: asset.external_id,
        assetType: asset.asset_type, title: asset.title, urlPath: asset.url_path,
        published: asset.published, registered: asset, missing: true,
      })),
    ];
  }, [catalog, stored]);

  const synchronize = async (asset: Row) => {
    setBusy(asset.key);
    try {
      await admin('/superset-assets', { method: 'POST', body: JSON.stringify({
        externalId: asset.externalId, assetType: asset.assetType, title: asset.title,
        urlPath: asset.urlPath, ownerExternalId: null, published: asset.published,
      }) });
      message.success('دارایی به درخت دسترسی افزوده شد'); await load();
    } catch (reason) { message.error((reason as Error).message); }
    finally { setBusy(undefined); }
  };

  const openAccess = async (asset: Row) => {
    setSelected(asset); form.resetFields();
    try { setGrants(await admin(`/superset-assets/${asset.registered.id}/grants`)); }
    catch (reason) { message.error((reason as Error).message); }
  };

  const refreshGrants = async () => {
    if (selected?.registered) setGrants(await admin(`/superset-assets/${selected.registered.id}/grants`));
  };

  const grant = async (values: { subjectType: SubjectType; subjectId: string; level: Level }) => {
    if (!selected?.registered) return;
    const level = levels[values.level];
    const action = actions.find((candidate) => candidate.action_key === level.action);
    if (!action) return void message.error(`عملیات ${level.action} تعریف نشده است`);
    setBusy('grant');
    try {
      await admin('/grants', { method: 'POST', body: JSON.stringify({
        subjectType:values.subjectType,subjectId:values.subjectId,resourceId: selected.registered.resource_id,
        actionId: action.id, relation: level.relation, expiresAt: null,
      }) });
      form.resetFields(); await refreshGrants(); message.success('سطح دسترسی اعطا شد');
    } catch (reason) { message.error((reason as Error).message); }
    finally { setBusy(undefined); }
  };

  const revoke = async (id: string) => {
    try { await admin(`/grants/${id}`, { method: 'DELETE' }); await refreshGrants(); message.success('دسترسی لغو شد'); }
    catch (reason) { message.error((reason as Error).message); }
  };

  const levelLabel = (action: string) => action === 'view' ? 'مشاهده' : action === 'update' ? 'ویرایش' : 'مدیریت';
  const selectedSubjectType=(Form.useWatch('subjectType',form)??'USER') as SubjectType;
  const subjectRows=selectedSubjectType==='USER'?users:selectedSubjectType==='GROUP'?groups:roles;
  const subjectLabel=(item:Row)=>selectedSubjectType==='USER'?`${item.display_name??item.username} — ${item.username}`:selectedSubjectType==='GROUP'?`${item.display_name} — ${item.normalized_path}`:`${item.name_fa} — ${item.role_key}`;
  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Alert showIcon type="info" message="کاتالوگ زنده Superset عملیاتی" description="فهرست مستقیماً از API خوانده می‌شود. هر دارایی را به درخت اضافه و سطح مشاهده، ویرایش یا مدیریت را به کاربران اعطا کنید." />
    {error && <Alert showIcon type="error" message="دریافت کاتالوگ ناموفق بود" description={error} action={<Button onClick={() => void load()}>تلاش مجدد</Button>} />}
    <Card title="گزارش‌ها و داشبوردها" extra={<Button loading={loading} onClick={() => void load()}>دریافت مجدد از API</Button>}>
      <Table rowKey="key" loading={loading} dataSource={rows} pagination={{ pageSize: 20 }} columns={[
        { title: 'عنوان', dataIndex: 'title' },
        { title: 'نوع', dataIndex: 'assetType', render: (value) => <Tag color={value === 'DASHBOARD' ? 'blue' : 'purple'}>{value === 'DASHBOARD' ? 'داشبورد' : 'نمودار / گزارش'}</Tag> },
        { title: 'شناسه', dataIndex: 'supersetId' },
        { title: 'جزئیات', dataIndex: 'detail', render: (value) => value || '—' },
        { title: 'دسترسی', render: (_, row) => <Tag color={row.registered ? 'green' : 'orange'}>{row.registered ? 'همگام‌شده' : 'ثبت‌نشده'}</Tag> },
        { title: 'API', render: (_, row) => <Tag color={row.missing ? 'red' : 'blue'}>{row.missing ? 'در Superset یافت نشد' : 'فعال'}</Tag> },
        { title: 'عملیات', render: (_, row) => row.registered
          ? <Button type="primary" onClick={() => void openAccess(row)}>سطوح دسترسی</Button>
          : <Button loading={busy === row.key} onClick={() => void synchronize(row)}>افزودن به درخت</Button> },
      ]} />
    </Card>
    <Modal open={Boolean(selected)} title={`سطوح دسترسی: ${selected?.title ?? ''}`} width={850} footer={null} onCancel={() => setSelected(undefined)}>
      <Typography.Paragraph type="secondary">سطح موردنظر را مستقیماً برای کاربر، گروه یا نقش انتخاب کنید. دسترسی روی همان دارایی گزارش ثبت و از طریق Outbox به OpenFGA منتقل می‌شود.</Typography.Paragraph>
      <Form form={form} layout="inline" onFinish={grant} initialValues={{subjectType:'USER'}} style={{ marginBottom: 20 }}>
        <Form.Item name="subjectType" rules={[{required:true}]}><Select style={{width:130}} onChange={()=>form.setFieldValue('subjectId',undefined)} options={[{value:'USER',label:'کاربر'},{value:'GROUP',label:'گروه'},{value:'ROLE',label:'نقش'}]}/></Form.Item>
        <Form.Item name="subjectId" rules={[{ required: true, message: 'دارنده دسترسی را انتخاب کنید' }]}><Select showSearch optionFilterProp="label" style={{ width: 310 }} placeholder="انتخاب دارنده دسترسی" options={subjectRows.map((item) => ({ value: item.id, label:subjectLabel(item) }))} /></Form.Item>
        <Form.Item name="level" rules={[{ required: true, message: 'سطح را انتخاب کنید' }]}><Select style={{ width: 180 }} placeholder="سطح دسترسی" options={Object.entries(levels).map(([value, level]) => ({ value, label: level.label }))} /></Form.Item>
        <Button type="primary" htmlType="submit" loading={busy === 'grant'}>اعطا</Button>
      </Form>
      <Table rowKey="id" dataSource={grants} pagination={false} locale={{ emptyText: 'دسترسی فعالی تعریف نشده است' }} columns={[
        { title: 'نوع',dataIndex:'subject_type',render:value=>value==='USER'?'کاربر':value==='GROUP'?'گروه':'نقش'},
        { title: 'دارنده دسترسی', dataIndex:'subject_name' },
        { title: 'شناسه', dataIndex:'subject_key' },
        { title: 'سطح', dataIndex: 'action_key', render: levelLabel },
        { title: 'رابطه', dataIndex: 'relation' },
        { title: '', render: (_, item) => <Popconfirm title="دسترسی لغو شود؟" onConfirm={() => void revoke(item.id)}><Button danger>لغو</Button></Popconfirm> },
      ]} />
    </Modal>
  </Space>;
}
