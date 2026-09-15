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
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { adminApi } from './api';

type PanelRow = Record<string, any>;
const required = [{ required: true, message: 'این فیلد الزامی است' }];
const panelsApi = adminApi;

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
  const [resourceDrafts,setResourceDrafts]=useState<PanelRow[]>([]);
  const [resourceManifest,setResourceManifest]=useState('');
  const [navigationDefinitions,setNavigationDefinitions]=useState<PanelRow[]>([]);
  const [navigationForm]=Form.useForm();
  const navigationSource=Form.useWatch('source',navigationForm);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await panelsApi<PanelRow[]>('/panels'));
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
      resource_definition_mode: 'HYBRID',
      classification: 'REAL',
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
        resourceDefinitionMode: values.resource_definition_mode || 'HYBRID',
        classification: values.classification || 'REAL',
        mfManifestUrl: values.mf_manifest_url || null,
        resourceManifestUrl: values.resource_manifest_url || null,
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
  const manifestSample=(row:PanelRow)=>JSON.stringify({schemaVersion:'1.0',module:{key:row.slug,name:row.name_en,nameFa:row.name_fa,nameEn:row.name_en,version:row.semantic_version},resources:[{key:`page:${row.slug}.home`,type:'PAGE',name:'Home',nameFa:'صفحه اصلی',nameEn:'Home',actions:['view']}]},null,2);
  const openArtifacts=async(row:PanelRow)=>{setArtifactPanel(row);const[a,d,n]=await Promise.all([panelsApi<PanelRow[]>(`/panels/${row.id}/artifacts`),panelsApi<PanelRow[]>(`/panels/${row.id}/resource-manifests`),panelsApi<PanelRow[]>(`/panels/${row.id}/navigation-definitions`)]);setArtifacts(a);setResourceDrafts(d);setNavigationDefinitions(n);setResourceManifest(manifestSample(row));artifactForm.setFieldsValue({artifactVersion:row.semantic_version,remoteEntryUrl:row.remote_entry_path,remoteName:row.remote_name,exposedModule:row.exposed_module,contractVersion:row.contract_version,manifest:JSON.stringify({schemaVersion:'1.0',microfrontend:{key:row.slug,name:row.name_en,version:row.semantic_version},runtime:{remoteEntry:row.remote_entry_path,remoteName:row.remote_name,exposedModule:row.exposed_module,contractVersion:row.contract_version,apiBasePath:`/api/proxy/${row.service_slug}`},defaultRouteKey:'index',routes:[{key:'index',path:'',title:row.name_fa,requiredResource:`application:aurevia/${row.slug}`,requiredAction:'view'}],navigation:[{key:`${row.slug}.nav.main`,type:'PAGE',routeKey:'index',title:row.name_fa,order:10}]},null,2)});navigationForm.setFieldsValue({source:'ADMIN',nodeType:'GROUP',hidden:false,order:50})};
  const publish=async(values:PanelRow)=>{await panelsApi(`/panels/${artifactPanel!.id}/artifacts`,{method:'POST',body:JSON.stringify(values)});message.success('نسخه معتبر منتشر شد');await openArtifacts(artifactPanel!)};
  const activate=async(id:string)=>{const row=artifacts.find(item=>item.id===id);await panelsApi(`/panels/${artifactPanel!.id}/artifacts/${id}/activate?version=${row?.panel_version??artifactPanel!.version}`,{method:'POST'});message.success('نسخه فعال شد؛ Catalog تغییر کرد');await Promise.all([openArtifacts(artifactPanel!),load()])};
  const syncFrontendManifest=async()=>{try{const result=await panelsApi<PanelRow>(`/panels/${artifactPanel!.id}/frontend-manifests/sync`,{method:'POST'});message.success(result.idempotent?'MF Manifest از قبل همگام بود':`MF Manifest همگام شد؛ route جدید: ${result.routesAdded??0}، حذف‌شده: ${result.routesRemoved??0}`);await Promise.all([openArtifacts(artifactPanel!),load()])}catch(reason){message.error((reason as Error).message)}};
  const fetchResourceManifest=async()=>{try{await panelsApi(`/panels/${artifactPanel!.id}/resource-manifests/fetch`,{method:'POST'});message.success('Manifest دریافت و به صورت Draft ثبت شد');await openArtifacts(artifactPanel!)}catch(reason){message.error((reason as Error).message)}};
  const importResourceManifest=async()=>{try{const body=JSON.parse(resourceManifest);await panelsApi(`/panels/${artifactPanel!.id}/resource-manifests/drafts`,{method:'POST',body:JSON.stringify(body)});message.success('Draft و Diff بدون تغییر کاتالوگ production ساخته شد');await openArtifacts(artifactPanel!)}catch(reason){message.error((reason as Error).message)}};
  const publishResourceManifest=async(id:string)=>{try{await panelsApi(`/panels/${artifactPanel!.id}/resource-manifests/drafts/${id}/publish`,{method:'POST'});message.success('کاتالوگ پس از تأیید راهبر منتشر شد');await openArtifacts(artifactPanel!)}catch(reason){message.error((reason as Error).message)}};
  const saveNavigation=async(values:PanelRow)=>{try{const key=values.key;await panelsApi(`/panels/${artifactPanel!.id}/navigation-overrides/${encodeURIComponent(key)}`,{method:'PUT',body:JSON.stringify(values)});message.success('Navigation Overlay ذخیره شد');navigationForm.resetFields();navigationForm.setFieldsValue({source:'ADMIN',nodeType:'GROUP',hidden:false,order:50});await openArtifacts(artifactPanel!)}catch(reason){message.error((reason as Error).message)}};

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
        { title: 'تعریف منابع', dataIndex: 'resource_definition_mode', render: value => <Tag color="purple">{value}</Tag> },
        { title: 'رده', dataIndex: 'classification', render: value => <Tag color={value === 'DEMO' ? 'orange' : 'green'}>{value}</Tag> },
        { title: 'MF Manifest', dataIndex: 'mf_manifest_url', ellipsis: true },
        { title: 'Resource Manifest', dataIndex: 'resource_manifest_url', ellipsis: true },
        { title: 'وضعیت', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'فعال' : 'غیرفعال'}</Tag> },
        { title: 'عملیات', render: (_, row) => <Space>
          <Button onClick={() => show(row)}>ویرایش</Button>
          <Button onClick={()=>void openArtifacts(row)}>Artifact و Catalog</Button>
          <Popconfirm title="غیرفعال شود؟" onConfirm={() => panelsApi(`/panels/${row.id}?version=${row.version}`, { method: 'DELETE' }).then(load).catch(error => message.error((error as Error).message))}>
            <Button danger>غیرفعال</Button>
          </Popconfirm>
        </Space> },
      ]}
    />
    <Modal open={!!artifactPanel} title={`نسخه‌ها و Manifest — ${artifactPanel?.name_fa??''}`} onCancel={()=>setArtifactPanel(undefined)} footer={null} width={1000}>
      <Card size="small" title="MF Manifest — Runtime / Routes / Navigation" extra={<Button type="primary" disabled={!artifactPanel?.mf_manifest_url} onClick={()=>void syncFrontendManifest()}>Sync Frontend Manifest</Button>}>
        <Typography.Paragraph type="secondary">همگام‌سازی از URL ثبت‌شده server-side انجام می‌شود. تنظیمات deployment ادمین بر defaultهای runtime اولویت دارند و overrideهای navigation حفظ می‌شوند.</Typography.Paragraph>
      </Card>
      <Table rowKey="id" size="small" dataSource={artifacts} pagination={false} columns={[{title:'نسخه',dataIndex:'artifact_version'},{title:'Remote Name',dataIndex:'remote_name'},{title:'Contract',dataIndex:'contract_version'},{title:'منبع Sync',dataIndex:'source_url',ellipsis:true,render:value=>value??'انتشار دستی'},{title:'Checksum',dataIndex:'manifest_checksum',ellipsis:true},{title:'زمان Sync',dataIndex:'synchronized_at',render:value=>value?new Date(value).toLocaleString('fa-IR'):'—'},{title:'Validation',dataIndex:'validation_status',render:value=><Tag color={value==='VALID'?'green':'red'}>{value}</Tag>},{title:'وضعیت',render:(_,row)=>row.active?<Tag color="blue">فعال</Tag>:<Button disabled={row.validation_status!=='VALID'} onClick={()=>void activate(row.id)}>Activate / Rollback</Button>}]}/>
      <Card size="small" title="انتشار دستی Artifact immutable (پیشرفته)" style={{marginTop:16}}><Form form={artifactForm} layout="vertical" onFinish={values=>publish(values).catch(reason=>message.error(reason.message))}><Space wrap align="start"><Form.Item name="artifactVersion" label="نسخه" rules={required}><Input/></Form.Item><Form.Item name="remoteEntryUrl" label="Remote Entry URL" rules={required}><Input style={{width:380}}/></Form.Item><Form.Item name="remoteName" label="Remote Name" rules={required}><Input/></Form.Item><Form.Item name="exposedModule" label="Exposed Module" rules={required}><Input/></Form.Item><Form.Item name="contractVersion" label="Contract" rules={required}><Input/></Form.Item></Space><Form.Item name="integrity" label="SRI (اختیاری)"><Input/></Form.Item><Form.Item name="manifest" label="MF Manifest Snapshot" rules={required}><Input.TextArea rows={10} style={{direction:'ltr'}}/></Form.Item><Button type="primary" htmlType="submit">Validate و Publish</Button></Form></Card>
      <Card size="small" title="Resource Manifest — Draft / Diff / Approval" style={{marginTop:16}} extra={<Button disabled={artifactPanel?.resource_definition_mode==='MANUAL'} onClick={()=>void fetchResourceManifest()}>Fetch از URL ثبت‌شده</Button>}>
        <Typography.Paragraph type="secondary">Fetch یا Import فقط Draft می‌سازد. ستون تغییرات قبل از Publish نمایش داده می‌شود و حذف‌های Manifest به DEPRECATED تبدیل می‌شوند.</Typography.Paragraph>
        <Table rowKey="id" size="small" dataSource={resourceDrafts} pagination={false} columns={[{title:'نسخه',dataIndex:'manifestVersion'},{title:'وضعیت',dataIndex:'workflowStatus',render:value=><Tag color={value==='PUBLISHED'?'green':'gold'}>{value}</Tag>},{title:'Checksum',dataIndex:'checksum',ellipsis:true},{title:'تغییرات',dataIndex:'changes',render:(changes:PanelRow[]=[])=>changes.map(change=><Tag key={`${change.resourceKey}-${change.changeType}`} color={change.changeType==='CONFLICT'?'red':change.changeType==='DEPRECATE'?'orange':change.changeType==='CREATE'?'green':'blue'}>{change.changeType}: {change.resourceKey}</Tag>)},{title:'تأیید',render:(_,row)=>row.workflowStatus==='DRAFT'?<Popconfirm title="این Draft روی کاتالوگ منتشر شود؟" onConfirm={()=>void publishResourceManifest(row.id)}><Button type="primary" danger={row.changes?.some((change:PanelRow)=>change.changeType==='DEPRECATE')} disabled={row.changes?.some((change:PanelRow)=>change.changeType==='CONFLICT')}>Publish</Button></Popconfirm>:<Tag>اعمال‌شده</Tag>}]}/>
        <Input.TextArea value={resourceManifest} onChange={event=>setResourceManifest(event.target.value)} rows={12} style={{direction:'ltr',marginTop:12}} aria-label="Resource Manifest JSON"/>
        <Button type="primary" style={{marginTop:8}} disabled={artifactPanel?.resource_definition_mode==='MANUAL'} onClick={()=>void importResourceManifest()}>Validate و ایجاد Draft</Button>
      </Card>
      <Card size="small" title="Navigation Overlay مستقل از Resource Tree" style={{marginTop:16}}>
        <Table rowKey="key" size="small" dataSource={navigationDefinitions} pagination={false} columns={[{title:'Key',dataIndex:'key'},{title:'نوع',dataIndex:'type'},{title:'مالک',dataIndex:'ownership'},{title:'پیش‌فرض',dataIndex:'defaultTitle'},{title:'Override',dataIndex:'overrideTitle'},{title:'مؤثر',dataIndex:'effectiveTitle'},{title:'وضعیت منبع',dataIndex:'sourceState',render:value=><Tag color={value==='ACTIVE'?'green':'orange'}>{value}</Tag>},{title:'پنهان',dataIndex:'hidden',render:value=>value?'بله':'خیر'}]}/>
        <Form form={navigationForm} layout="vertical" onFinish={saveNavigation} style={{marginTop:12}}><Space wrap align="start"><Form.Item name="key" label="Navigation Key" rules={[...required,{pattern:/^[a-z][a-z0-9._-]{1,99}$/,message:'کلید lowercase و پایدار'}]} extra="منو Resource مجوزدهی نیست"><Input/></Form.Item><Form.Item name="source" label="مالک"><Select style={{width:130}} options={['ADMIN','MANIFEST'].map(value=>({value,label:value}))} onChange={value=>navigationForm.setFieldsValue(value==='MANIFEST'?{nodeType:undefined,parentKey:undefined,pageKey:undefined,externalUrl:undefined}:{nodeType:'GROUP'})}/></Form.Item><Form.Item name="nodeType" label="نوع" extra={navigationSource==='MANIFEST'?'ساختار گره Manifest تغییر نمی‌کند':undefined}><Select disabled={navigationSource==='MANIFEST'} style={{width:160}} options={['GROUP','PAGE','EXTERNAL_LINK'].map(value=>({value,label:value}))}/></Form.Item><Form.Item name="title" label="عنوان"><Input/></Form.Item><Form.Item name="parentKey" label="کلید والد"><Input disabled={navigationSource==='MANIFEST'}/></Form.Item><Form.Item name="pageKey" label="Route/Page Key"><Input disabled={navigationSource==='MANIFEST'}/></Form.Item><Form.Item name="externalUrl" label="External HTTPS URL"><Input disabled={navigationSource==='MANIFEST'}/></Form.Item><Form.Item name="order" label="ترتیب"><InputNumber/></Form.Item><Form.Item name="hidden" valuePropName="checked"><Checkbox>پنهان</Checkbox></Form.Item></Space><Button htmlType="submit">ذخیره Overlay / Node ادمین</Button></Form>
      </Card>
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
          <Form.Item name="resource_definition_mode" label="روش تعریف Resource" rules={required} extra="HYBRID: ساختار Manifest و Resourceهای تکمیلی مستقل با مالکیت ADMIN در کنار هم"><Select style={{width:220}} options={['HYBRID','MANIFEST','MANUAL'].map(value=>({value,label:value}))}/></Form.Item>
          <Form.Item name="classification" label="رده Micro Frontend" rules={required} extra="DEMO در production با demo-data.enabled=false وارد Catalog مؤثر نمی‌شود"><Select style={{width:160}} options={['REAL','DEMO'].map(value=>({value,label:value}))}/></Form.Item>
          <Form.Item name="mf_manifest_url" label="MF Manifest URL" extra="runtime، routeهای محلی و navigation پیش‌فرض؛ URL ثبت‌شده باید با policy شبکه محیط سازگار باشد"><Input placeholder="http://localhost:3001/mf-manifest.json" style={{width:390}}/></Form.Item>
          <Form.Item name="resource_manifest_url" label="Resource Manifest URL" extra="برای HYBRID اختیاری و برای MANIFEST الزامی؛ URL فایل JSON تابع policy شبکه محیط است"><Input placeholder="http://localhost:3001/resource-manifest.json" style={{width:390}}/></Form.Item>
          <Form.Item name="sort_order" label="ترتیب"><InputNumber /></Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>فعال</Checkbox></Form.Item>
        </Space>
      </Form>
    </Modal>
  </Card>;
}
