import React,{useEffect,useState}from'react';
import{Alert,Button,Card,Form,Input,Modal,Popconfirm,Select,Space,Switch,Table,Tag,message}from'antd';
import{adminApi}from'./api';

type Row=Record<string,any>;
const required=[{required:true,message:'این فیلد الزامی است'}];
const csv=(value?:string)=>value?.split(',').map(item=>item.trim()).filter(Boolean)??[];

export function IdentityAndRoles(){
  const[users,setUsers]=useState<Row[]>([]),[groups,setGroups]=useState<Row[]>([]);
  const[accessGroups,setAccessGroups]=useState<Row[]>([]),[roles,setRoles]=useState<Row[]>([]);
  const[assignments,setAssignments]=useState<Row[]>([]),[providers,setProviders]=useState<Row[]>([]);
  const[roleOpen,setRoleOpen]=useState(false),[providerOpen,setProviderOpen]=useState(false);
  const[editingProvider,setEditingProvider]=useState<Row>(),[selectedUser,setSelectedUser]=useState<string>();
  const[externalIdentities,setExternalIdentities]=useState<Row[]>([]);
  const[roleForm]=Form.useForm(),[assignmentForm]=Form.useForm(),[providerForm]=Form.useForm(),
    [identityForm]=Form.useForm();
  const load=()=>Promise.all([adminApi<Row[]>('/users'),adminApi<Row[]>('/directory-groups'),
    adminApi<Row[]>('/ou-access/access-groups'),adminApi<Row[]>('/roles'),
    adminApi<Row[]>('/role-assignments'),adminApi<Row[]>('/identity-providers')])
    .then(([u,g,ag,r,a,p])=>{setUsers(u);setGroups(g);setAccessGroups(ag);setRoles(r);
      setAssignments(a);setProviders(p)}).catch(error=>message.error(error.message));
  useEffect(()=>{void load()},[]);
  const createRole=async(values:Row)=>{try{await adminApi('/roles',{method:'POST',body:JSON.stringify(values)});
    setRoleOpen(false);roleForm.resetFields();await load();message.success('نقش کاربردی ایجاد شد')}
    catch(error){message.error((error as Error).message)}};
  const assign=async(values:Row)=>{try{await adminApi('/role-assignments',{method:'POST',
    body:JSON.stringify({...values,expiresAt:values.expiresAt||null})});assignmentForm.resetFields();
    await load();message.success('نقش تخصیص یافت')}catch(error){message.error((error as Error).message)}};
  const subjectOptions=(type:string)=>(type==='DIRECTORY_GROUP'?groups:type==='ACCESS_GROUP'?accessGroups:users)
    .map(item=>({value:item.id,label:type==='DIRECTORY_GROUP'?`${item.display_name} — ${item.normalized_path}`:
      type==='ACCESS_GROUP'?`${item.name} — ${item.code}`:`${item.display_name??item.username} — ${item.username}`}));
  const openProvider=(row?:Row)=>{setEditingProvider(row);providerForm.setFieldsValue(row?{
    code:row.code,name:row.name,type:row.type,issuerUrl:row.issuer_url,
    authorizationEndpoint:row.authorization_endpoint,tokenEndpoint:row.token_endpoint,
    jwksUri:row.jwks_uri,userInfoEndpoint:row.user_info_endpoint,clientId:row.client_id,
    clientSecretReference:row.client_secret_reference,enabled:row.enabled,tenantId:row.tenant_id,
    domains:(row.domains??[]).join(','),scopes:(row.scopes??[]).join(','),
    audiences:(row.audiences??[]).join(','),subjectClaim:row.subject_claim,
    usernameClaim:row.username_claim,groupsClaim:row.groups_claim}:{
    type:'OIDC',enabled:true,scopes:'openid,profile,email',subjectClaim:'sub',
    usernameClaim:'preferred_username',groupsClaim:'groups'});setProviderOpen(true)};
  const saveProvider=async(values:Row)=>{try{const body={...values,domains:csv(values.domains),
    scopes:csv(values.scopes),audiences:csv(values.audiences)};await adminApi(editingProvider?
    `/identity-providers/${editingProvider.id}?version=${editingProvider.version}`:'/identity-providers',
    {method:editingProvider?'PUT':'POST',body:JSON.stringify(body)});setProviderOpen(false);
    providerForm.resetFields();await load();message.success('Identity Provider ذخیره شد')}
    catch(error){message.error((error as Error).message)}};
  const toggleProvider=(row:Row)=>adminApi(`/identity-providers/${row.id}/status?version=${row.version}`,
    {method:'PATCH',body:JSON.stringify({enabled:!row.enabled})}).then(load)
    .catch(error=>message.error(error.message));
  const checkProvider=(row:Row)=>adminApi(`/identity-providers/${row.id}/health-check`,{method:'POST'})
    .then(load).catch(error=>message.error(error.message));
  const loadExternal=async(userId:string)=>{setSelectedUser(userId);
    setExternalIdentities(await adminApi<Row[]>(`/users/${userId}/external-identities`))};
  const linkExternal=async(values:Row)=>{if(!selectedUser)return;try{await adminApi(
    `/users/${selectedUser}/external-identities`,{method:'POST',body:JSON.stringify(values)});
    identityForm.resetFields();await loadExternal(selectedUser);message.success('هویت خارجی متصل شد')}
    catch(error){message.error((error as Error).message)}};

  return <Space direction="vertical" size={16} style={{width:'100%'}}>
    <Alert showIcon type="info" message="Authentication از Authorization جدا است"
      description="Identity Provider فقط هویت را اثبات می‌کند. هر issuer/sub به کاربر canonical متصل می‌شود و OpenFGA فقط شناسه canonical را دریافت می‌کند."/>
    <Card title="Identity Provider Registry" extra={<Button type="primary" onClick={()=>openProvider()}>Provider جدید</Button>}>
      <Table rowKey="id" dataSource={providers} pagination={{pageSize:8}} columns={[
        {title:'نام',render:(_,row)=><><b>{row.name}</b><br/><code>{row.code}</code></>},
        {title:'نوع / Tenant',render:(_,row)=><>{row.type}<br/>{row.tenant_id??'عمومی'}</>},
        {title:'Issuer',dataIndex:'issuer_url'},
        {title:'وضعیت اتصال',render:(_,row)=><><Tag color={row.connection_status==='ACTIVE'?'green':row.connection_status==='UNREACHABLE'?'red':'default'}>{row.connection_status}</Tag><br/>{row.last_health_check_at?new Date(row.last_health_check_at).toLocaleString('fa-IR'):'بررسی نشده'}</>},
        {title:'فعال',render:(_,row)=><Switch checked={row.enabled} onChange={()=>void toggleProvider(row)}/>},
        {title:'عملیات',render:(_,row)=><Space><Button onClick={()=>openProvider(row)}>ویرایش</Button><Button onClick={()=>void checkProvider(row)}>Health Check</Button></Space>}
      ]}/>
    </Card>
    <Card title="نگاشت هویت خارجی به کاربر Canonical">
      <Space direction="vertical" style={{width:'100%'}}>
        <Select showSearch optionFilterProp="label" style={{width:420}} placeholder="کاربر canonical"
          options={users.map(user=>({value:user.id,label:`${user.display_name??user.username} — ${user.subject_key}`}))}
          onChange={value=>void loadExternal(value)}/>
        {selectedUser&&<><Form form={identityForm} layout="inline" onFinish={linkExternal}>
          <Form.Item name="providerCode" rules={required}><Select style={{width:240}} placeholder="Identity Provider"
            options={providers.filter(item=>item.enabled).map(item=>({value:item.code,label:item.name}))}/></Form.Item>
          <Form.Item name="subject" rules={required}><Input style={{width:300}} placeholder="OIDC subject (sub)"/></Form.Item>
          <Button type="primary" htmlType="submit">اتصال هویت</Button>
        </Form><Table rowKey="id" pagination={false} dataSource={externalIdentities} columns={[
          {title:'Provider',dataIndex:'provider_code'},{title:'Issuer',dataIndex:'issuer'},
          {title:'Subject',dataIndex:'subject'},{title:'OpenFGA Subject',dataIndex:'canonical_user_id',render:value=><code>{`user:${value}`}</code>},
          {title:'',render:(_,row)=><Popconfirm title="این alias حذف شود؟" onConfirm={()=>adminApi(
            `/users/${selectedUser}/external-identities/${row.id}`,{method:'DELETE'}).then(()=>loadExternal(selectedUser))}>
            <Button danger>حذف</Button></Popconfirm>}
        ]}/></>}
      </Space>
    </Card>
    <Card title="گروه‌های سازمانی همگام‌شده"><Table rowKey="id" dataSource={groups} pagination={{pageSize:8}} columns={[
      {title:'نام',dataIndex:'display_name'},{title:'مسیر پایدار',dataIndex:'normalized_path'},
      {title:'شناسه خارجی',dataIndex:'external_id'},{title:'وضعیت',render:(_,row)=><Tag color={row.status==='ACTIVE'?'green':'default'}>{row.status}</Tag>},
      {title:'آخرین همگام‌سازی',dataIndex:'sync_at',render:value=>value?new Date(value).toLocaleString('fa-IR'):'—'}]}/></Card>
    <Card title="نقش‌های کاربردی" extra={<Button type="primary" onClick={()=>setRoleOpen(true)}>نقش جدید</Button>}>
      <Table rowKey="id" dataSource={roles} pagination={false} columns={[{title:'کلید نقش',dataIndex:'role_key'},
        {title:'نام فارسی',dataIndex:'name_fa'},{title:'نام انگلیسی',dataIndex:'name_en'},{title:'وضعیت',dataIndex:'status'}]}/></Card>
    <Card title="تخصیص نقش"><Form form={assignmentForm} layout="inline" onFinish={assign} initialValues={{subjectType:'USER'}}>
      <Form.Item name="subjectType" rules={required}><Select style={{width:180}} options={[{value:'USER',label:'کاربر'},
        {value:'DIRECTORY_GROUP',label:'گروه LDAP'},{value:'ACCESS_GROUP',label:'گروه OU'}]}/></Form.Item>
      <Form.Item noStyle shouldUpdate={(before,after)=>before.subjectType!==after.subjectType}>{()=>{const type=assignmentForm.getFieldValue('subjectType');return <Form.Item name="subjectId" rules={required}><Select showSearch optionFilterProp="label" style={{width:340}} placeholder={type==='USER'?'انتخاب کاربر':'انتخاب گروه'} options={subjectOptions(type)}/></Form.Item>}}</Form.Item>
      <Form.Item name="roleId" rules={required}><Select style={{width:260}} placeholder="نقش" options={roles.filter(role=>role.status==='ACTIVE').map(role=>({value:role.id,label:`${role.name_fa} (${role.role_key})`}))}/></Form.Item><Button type="primary" htmlType="submit">تخصیص</Button>
      </Form><Table style={{marginTop:16}} rowKey={row=>`${row.subject_type}:${row.subject_id}:${row.role_id}`} dataSource={assignments} pagination={{pageSize:8}} columns={[
        {title:'نوع',dataIndex:'subject_type',render:value=>value==='USER'?'کاربر':value==='ACCESS_GROUP'?'گروه OU':'گروه LDAP'},
        {title:'کاربر/گروه',dataIndex:'subject_name'},{title:'نقش',dataIndex:'role_key'},
        {title:'انقضا',dataIndex:'expires_at',render:value=>value?new Date(value).toLocaleString('fa-IR'):'بدون انقضا'},
        {title:'',render:(_,row)=><Popconfirm title="این تخصیص لغو شود؟" onConfirm={()=>adminApi(`/role-assignments/${row.subject_type}/${row.subject_id}/${row.role_id}`,{method:'DELETE'}).then(load).catch(error=>message.error(error.message))}><Button danger>لغو</Button></Popconfirm>}]}/></Card>
    <Modal open={roleOpen} title="تعریف نقش کاربردی" onCancel={()=>setRoleOpen(false)} onOk={()=>roleForm.submit()}><Form form={roleForm} layout="vertical" onFinish={createRole}><Form.Item name="roleKey" label="کلید پایدار نقش" rules={required}><Input placeholder="hr-supervisor"/></Form.Item><Form.Item name="nameFa" label="نام فارسی" rules={required}><Input/></Form.Item><Form.Item name="nameEn" label="نام انگلیسی" rules={required}><Input/></Form.Item></Form></Modal>
    <Modal width={820} open={providerOpen} title={editingProvider?'ویرایش Identity Provider':'Identity Provider جدید'} onCancel={()=>setProviderOpen(false)} onOk={()=>providerForm.submit()}>
      <Form form={providerForm} layout="vertical" onFinish={saveProvider}>
        <Space wrap><Form.Item name="code" label="کد پایدار" rules={required}><Input disabled={!!editingProvider} placeholder="bank-a-keycloak"/></Form.Item>
          <Form.Item name="name" label="نام" rules={required}><Input/></Form.Item><Form.Item name="type" label="نوع" rules={required}><Select style={{width:180}} options={['OIDC','KEYCLOAK','AZURE_AD','OKTA','AUTH0','GOOGLE_WORKSPACE'].map(value=>({value,label:value}))}/></Form.Item>
          <Form.Item name="tenantId" label="Tenant"><Input/></Form.Item><Form.Item name="enabled" label="فعال" valuePropName="checked"><Switch/></Form.Item></Space>
        <Form.Item name="issuerUrl" label="Issuer URL" rules={required}><Input placeholder="https://sso.bank-a.com/realms/main"/></Form.Item>
        <Form.Item name="authorizationEndpoint" label="Authorization Endpoint" rules={required}><Input/></Form.Item>
        <Form.Item name="tokenEndpoint" label="Token Endpoint" rules={required}><Input/></Form.Item>
        <Form.Item name="jwksUri" label="JWKS URI" rules={required}><Input/></Form.Item>
        <Form.Item name="userInfoEndpoint" label="UserInfo Endpoint"><Input/></Form.Item>
        <Space wrap><Form.Item name="clientId" label="Client ID" rules={required}><Input/></Form.Item>
          <Form.Item name="clientSecretReference" label="Secret Reference" rules={required}><Input placeholder="secret://identity/bank-a"/></Form.Item>
          <Form.Item name="domains" label="دامنه‌ها (CSV)"><Input placeholder="bank-a.com"/></Form.Item></Space>
        <Space wrap><Form.Item name="scopes" label="Scopes"><Input/></Form.Item><Form.Item name="audiences" label="Audienceهای اضافی"><Input/></Form.Item>
          <Form.Item name="subjectClaim" label="Subject Claim"><Input/></Form.Item><Form.Item name="usernameClaim" label="Username Claim"><Input/></Form.Item><Form.Item name="groupsClaim" label="Groups Claim"><Input/></Form.Item></Space>
      </Form>
    </Modal>
  </Space>;
}
