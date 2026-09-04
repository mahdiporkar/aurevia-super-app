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
  const [artifactPanel,setArtifactPanel]=useState<PanelRow>();
  const [artifacts,setArtifacts]=useState<PanelRow[]>([]);
  const [artifactForm]=Form.useForm();

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
      contract_version: '1.0',
      exposed_module: './bootstrap',
      service_slug: '',
      remote_name: '',
      default_route_id: 'index',
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
        description: values.description || null,
        slug: values.slug,
        serviceSlug: values.service_slug || values.slug,
        remoteName: values.remote_name || `aurevia_${String(values.slug).replaceAll('-', '_')}`,
        defaultRouteId: values.default_route_id || 'index',
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
  const openArtifacts=async(row:PanelRow)=>{setArtifactPanel(row);setArtifacts(await panelsApi(`/panels/${row.id}/artifacts`));artifactForm.setFieldsValue({artifactVersion:row.semantic_version,remoteEntryUrl:row.remote_entry_path,remoteName:row.remote_name,exposedModule:'./plugin',contractVersion:'1.0',manifest:JSON.stringify({schemaVersion:'1.0',moduleKey:row.slug,defaultRouteId:'index',routes:[{id:'index',path:'',title:row.name_fa,resource:`application:aurevia/${row.slug}`,action:'view'}],menus:[{id:'main',routeId:'index',title:row.name_fa,order:10}]},null,2)});};
  const publish=async(values:PanelRow)=>{await panelsApi(`/panels/${artifactPanel!.id}/artifacts`,{method:'POST',body:JSON.stringify(values)});message.success('نسخه معتبر منتشر شد');await openArtifacts(artifactPanel!)};
  const activate=async(id:string)=>{const row=artifacts.find(item=>item.id===id);await panelsApi(`/panels/${artifactPanel!.id}/artifacts/${id}/activate?version=${row?.panel_version??artifactPanel!.version}`,{method:'POST'});message.success('نسخه فعال شد؛ Catalog تغییر کرد');await Promise.all([openArtifacts(artifactPanel!),load()])};

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
          <Button onClick={()=>void openArtifacts(row)}>نسخه‌ها</Button>
          <Popconfirm title="غیرفعال شود؟" onConfirm={() => panelsApi(`/panels/${row.id}?version=${row.version}`, { method: 'DELETE' }).then(load).catch(error => message.error((error as Error).message))}>
            <Button danger>غیرفعال</Button>
          </Popconfirm>
        </Space> },
      ]}
    />
    <Modal open={!!artifactPanel} title={`نسخه‌ها و Manifest — ${artifactPanel?.name_fa??''}`} onCancel={()=>setArtifactPanel(undefined)} footer={null} width={1000}>
      <Table rowKey="id" size="small" dataSource={artifacts} pagination={false} columns={[{title:'نسخه',dataIndex:'artifact_version'},{title:'Remote Name',dataIndex:'remote_name'},{title:'Contract',dataIndex:'contract_version'},{title:'Validation',dataIndex:'validation_status',render:value=><Tag color={value==='VALID'?'green':'red'}>{value}</Tag>},{title:'وضعیت',render:(_,row)=>row.active?<Tag color="blue">فعال</Tag>:<Button disabled={row.validation_status!=='VALID'} onClick={()=>void activate(row.id)}>Activate / Rollback</Button>}]}/>
      <Card size="small" title="انتشار Artifact immutable" style={{marginTop:16}}><Form form={artifactForm} layout="vertical" onFinish={values=>publish(values).catch(reason=>message.error(reason.message))}><Space wrap align="start"><Form.Item name="artifactVersion" label="نسخه" rules={required}><Input/></Form.Item><Form.Item name="remoteEntryUrl" label="Remote Entry URL" rules={required}><Input style={{width:380}}/></Form.Item><Form.Item name="remoteName" label="Remote Name" rules={required}><Input/></Form.Item><Form.Item name="exposedModule" label="Exposed Module" rules={required}><Input/></Form.Item><Form.Item name="contractVersion" label="Contract" rules={required}><Input/></Form.Item></Space><Form.Item name="integrity" label="SRI (اختیاری)"><Input/></Form.Item><Form.Item name="manifest" label="Manifest Snapshot" rules={required}><Input.TextArea rows={10} style={{direction:'ltr'}}/></Form.Item><Button type="primary" htmlType="submit">Validate و Publish</Button></Form></Card>
    </Modal>
    <Modal open={open} title={editing ? 'ویرایش میکروفرانت' : 'تعریف میکروفرانت'} onCancel={() => setOpen(false)} onOk={() => form.submit()} width={760}>
      <Form form={form} layout="vertical" onFinish={save}>
        <Space wrap align="start">
          <Form.Item name="code" label="کد" rules={required}><Input /></Form.Item>
          <Form.Item name="slug" label="Slug" rules={required}><Input /></Form.Item>
          <Form.Item name="name_fa" label="نام فارسی" rules={required}><Input /></Form.Item>
          <Form.Item name="name_en" label="نام انگلیسی" rules={required}><Input /></Form.Item>
          <Form.Item name="description" label="توضیحات"><Input style={{ width: 390 }} /></Form.Item>
          <Form.Item name="service_slug" label="Service Slug" rules={[...required,{pattern:/^[a-z][a-z0-9-]{1,49}$/,message:'حروف کوچک لاتین، عدد و خط تیره'}]}><Input placeholder="hr" /></Form.Item>
          <Form.Item name="remote_name" label="Remote Name" rules={[...required,{pattern:/^[A-Za-z][A-Za-z0-9_]*$/,message:'نام container معتبر نیست'}]}><Input placeholder="hr_ui_1_4_2" /></Form.Item>
          <Form.Item name="remote_entry_path" label="آدرس کامل Remote Entry" rules={[
            ...required,
            { type: 'url', message: 'آدرس کامل با http:// یا https:// وارد کنید' },
            { validator: (_, value) => !value || /^https?:\/\//i.test(value) ? Promise.resolve() : Promise.reject(new Error('فقط http و https مجاز است')) },
          ]} extra="مثال: http://localhost:3001/remoteEntry.js"><Input placeholder="http://localhost:3001/remoteEntry.js" style={{ width: 390 }} /></Form.Item>
          <Form.Item name="exposed_module" label="Exposed Module" rules={required}><Input /></Form.Item>
          <Form.Item name="route_base_path" label="Route Prefix" rules={[...required,{pattern:/^\/[a-z][a-z0-9-]{1,49}$/,message:'مانند /hr2 وارد کنید'},{validator:(_,value)=>!['/login','/admin','/settings','/api','/assets','/error'].includes(value)?Promise.resolve():Promise.reject(new Error('این prefix رزروشده است'))}]} extra="مثال: /hr2؛ مستقل از Service Slug"><Input /></Form.Item>
          <Form.Item name="default_route_id" label="Default Route ID" rules={required}><Input placeholder="employee-list" /></Form.Item>
          <Form.Item name="semantic_version" label="نسخه" rules={required}><Input /></Form.Item>
          <Form.Item name="contract_version" label="نسخه قرارداد" rules={required}><Input /></Form.Item>
          <Form.Item name="sort_order" label="ترتیب"><InputNumber /></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
      </Form>
    </Modal>
  </Card>;
}
