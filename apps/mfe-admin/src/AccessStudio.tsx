import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Badge, Button, Card, Col, Descriptions, Divider, Drawer, Empty, Form, Input,
  InputNumber, Modal, Progress, Row, Segmented, Select, Space, Statistic, Switch, Table,
  Tag, Tree, Typography, message,
} from 'antd';

type RowData = Record<string, any>;
type SubjectType = 'USER' | 'GROUP' | 'ROLE';
const resourceTypes = ['APPLICATION','MODULE','PAGE','UI_COMPONENT','FIELD','BUSINESS_RESOURCE','EXTERNAL_RESOURCE'] as const;
type ResourceMeta={label:string;color:string;icon:string;hint:string};
const typeMeta: Record<string,ResourceMeta> & Record<(typeof resourceTypes)[number],ResourceMeta> = {
  APPLICATION:{label:'اپلیکیشن',color:'purple',icon:'◆',hint:'ریشه یک محصول یا پنل مستقل'},
  MODULE:{label:'ماژول',color:'geekblue',icon:'▦',hint:'یک قابلیت سطح‌بالا در اپلیکیشن'},
  PAGE:{label:'صفحه',color:'blue',icon:'▤',hint:'مسیر یا صفحه قابل مشاهده'},
  UI_COMPONENT:{label:'بخش محافظت‌شده UI',color:'cyan',icon:'◫',hint:'فقط یک بخش حساس با تصمیم دسترسی مستقل؛ دکمه و گرید معمولی Resource نیست'},
  FIELD:{label:'فیلد حساس',color:'red',icon:'▥',hint:'فقط فیلدی که entitlement مستقل دارد؛ masking عمومی باید Data Policy باشد'},
  BUSINESS_RESOURCE:{label:'منبع کسب‌وکار',color:'gold',icon:'●',hint:'موجودیت دامنه مانند کارمند یا پرداخت'},
  EXTERNAL_RESOURCE:{label:'منبع خارجی',color:'magenta',icon:'↗',hint:'سامانه، گزارش یا دارایی بیرونی'},
};
const relationFor:Record<string,string>={view:'viewer',list:'viewer',create:'creator',update:'editor',approve:'editor',reject:'editor',delete:'deleter',admin:'manager',manage:'manager',share:'sharer',export:'exporter'};
let csrf:{headerName:string;token:string}|undefined;

async function api(path:string,init:RequestInit={}){
  const method=init.method??'GET',headers:Record<string,string>={'Content-Type':'application/json'};
  if(method!=='GET'){
    const token=csrf??await fetch('/api/v1/csrf',{credentials:'same-origin'}).then(r=>r.json());
    csrf=token;headers[token.headerName]=token.token;
  }
  const response=await fetch(`/api/v1/admin${path}`,{...init,headers,credentials:'same-origin'});
  if(!response.ok)throw new Error((await response.text())||`HTTP ${response.status}`);
  return response.status===204?undefined:response.json();
}

function TypeTag({type}:{type:string}){const meta=typeMeta[type]??{label:type,color:'default',icon:'•'};return <Tag color={meta.color}>{meta.icon} {meta.label}</Tag>}

export function AccessStudio(){
  const[resources,setResources]=useState<RowData[]>([]),[actions,setActions]=useState<RowData[]>([]);
  const[users,setUsers]=useState<RowData[]>([]),[groups,setGroups]=useState<RowData[]>([]),[roles,setRoles]=useState<RowData[]>([]);
  const[selected,setSelected]=useState<RowData>(),[loading,setLoading]=useState(true),[error,setError]=useState<string>();
  const[query,setQuery]=useState(''),[typeFilter,setTypeFilter]=useState<string>('ALL'),[editorOpen,setEditorOpen]=useState(false);
  const[editing,setEditing]=useState<RowData>(),[form]=Form.useForm();
  const[subjectType,setSubjectType]=useState<SubjectType>('USER'),[subjectId,setSubjectId]=useState<string>();
  const[grants,setGrants]=useState<RowData[]>([]),[grantLoading,setGrantLoading]=useState(false);

  const load=useCallback(async()=>{setLoading(true);setError(undefined);try{
    const[r,a,u,g,ro]=await Promise.all([api('/resource-tree'),api('/actions'),api('/users'),api('/groups'),api('/roles')]);
    setResources(r.map((item:RowData)=>({...item,actions:JSON.parse(item.actions_json??'[]')})));setActions(a);setUsers(u);setGroups(g);setRoles(ro);
  }catch(reason){setError((reason as Error).message)}finally{setLoading(false)}},[]);
  useEffect(()=>{void load()},[load]);

  const filteredIds=useMemo(()=>new Set(resources.filter(r=>(typeFilter==='ALL'||r.type===typeFilter)&&(!query||`${r.name_fa} ${r.name_en} ${r.resource_key}`.toLowerCase().includes(query.toLowerCase()))).map(r=>r.id)),[resources,query,typeFilter]);
  const visibleIds=useMemo(()=>{if(!query&&typeFilter==='ALL')return new Set(resources.map(r=>r.id));const ids=new Set(filteredIds),byId=new Map(resources.map(r=>[r.id,r]));for(const id of filteredIds){let p=byId.get(id)?.parent_id;while(p){ids.add(p);p=byId.get(p)?.parent_id}}return ids},[resources,filteredIds,query,typeFilter]);
  const tree=useMemo(()=>{const nodes=new Map<string,any>();resources.filter(r=>visibleIds.has(r.id)).forEach(r=>nodes.set(r.id,{key:r.id,title:<Space size={6}><span style={{color:typeMeta[r.type]?.color}}>{typeMeta[r.type]?.icon}</span><span>{r.name_fa}</span><Typography.Text type="secondary" style={{fontSize:11}}>{r.resource_key}</Typography.Text>{r.grant_count>0&&<Badge count={r.grant_count} color="#1677ff"/>}</Space>,children:[]}));const roots:any[]=[];resources.filter(r=>visibleIds.has(r.id)).forEach(r=>{const node=nodes.get(r.id);if(r.parent_id&&nodes.has(r.parent_id))nodes.get(r.parent_id).children.push(node);else roots.push(node)});return roots},[resources,visibleIds]);
  const stats=useMemo(()=>({total:resources.length,assigned:resources.filter(r=>r.grant_count>0).length,coverage:resources.length?Math.round(resources.filter(r=>r.grant_count>0).length/resources.length*100):0}),[resources]);
  const subjects=subjectType==='USER'?users:subjectType==='GROUP'?groups:roles;
  const subjectLabel=(s:RowData)=>subjectType==='USER'?(s.display_name||s.username):subjectType==='GROUP'?s.display_name:(s.name_fa||s.role_key);

  const openEditor=(resource?:RowData,parent?:RowData)=>{setEditing(resource);form.setFieldsValue(resource?{
    resourceKey:resource.resource_key,type:resource.type,parentId:resource.parent_id,nameFa:resource.name_fa,nameEn:resource.name_en,ownerDomain:resource.owner_domain,classification:resource.classification,externalSystem:resource.external_system,externalType:resource.external_type,externalId:resource.external_id,source:resource.source,
  }:{type:parent?'PAGE':'APPLICATION',parentId:parent?.id,classification:'INTERNAL',source:'ADMIN'});setEditorOpen(true)};
  const save=async(values:RowData)=>{try{await api(editing?`/resources/${editing.id}?version=${editing.version}`:'/resources',{method:editing?'PUT':'POST',body:JSON.stringify(values)});message.success('منبع با موفقیت ذخیره شد');setEditorOpen(false);form.resetFields();await load()}catch(reason){message.error((reason as Error).message)}};
  const toggleAction=async(action:RowData,on:boolean)=>{if(!selected)return;try{await api(`/resources/${selected.id}/actions/${action.id}`,{method:on?'PUT':'DELETE'});await load();setSelected((await api('/resource-tree')).map((r:RowData)=>({...r,actions:JSON.parse(r.actions_json??'[]')})).find((r:RowData)=>r.id===selected.id));message.success('عملیات منبع به‌روزرسانی شد')}catch(reason){message.error((reason as Error).message)}};
  const loadGrants=async(type:SubjectType,id:string)=>{setGrantLoading(true);try{setGrants(await api(`/subjects/${type}/${id}/grants`))}catch(reason){setGrants([]);message.error((reason as Error).message)}finally{setGrantLoading(false)}};
  const chooseSubject=(id:string)=>{setSubjectId(id);void loadGrants(subjectType,id)};
  const changeSubjectType=(value:string|number)=>{const next=value as SubjectType;setSubjectType(next);setSubjectId(undefined);setGrants([])};
  const grant=async(action:RowData)=>{if(!selected||!subjectId)return;try{await api('/grants',{method:'POST',body:JSON.stringify({subjectType,subjectId,resourceId:selected.id,actionId:action.id,relation:relationFor[action.action_key]??action.action_key,expiresAt:null})});message.success('دسترسی ثبت شد و برای همگام‌سازی با OpenFGA در صف قرار گرفت');await loadGrants(subjectType,subjectId);await load()}catch(reason){message.error((reason as Error).message)}};
  const revoke=async(grantId:string)=>{try{await api(`/grants/${grantId}`,{method:'DELETE'});message.success('لغو دسترسی در صف همگام‌سازی OpenFGA قرار گرفت');if(subjectId)await loadGrants(subjectType,subjectId);await load()}catch(reason){message.error((reason as Error).message)}};
  const selectedGrants=grants.filter(g=>g.resource_id===selected?.id),grantedActions=new Set(selectedGrants.map(g=>g.action_id));

  return <Space direction="vertical" size={16} style={{width:'100%'}}>
    <Card styles={{body:{padding:20}}} style={{background:'linear-gradient(135deg,#f0f5ff 0%,#fff 55%,#f9f0ff 100%)'}}>
      <Row gutter={[20,16]} align="middle"><Col flex="auto"><Typography.Title level={3} style={{margin:0}}>استودیوی دسترسی OpenFGA</Typography.Title><Typography.Text type="secondary">طراحی درخت منابع، تعریف عملیات و تخصیص دسترسی به کاربر، گروه یا نقش در یک نمای واحد</Typography.Text></Col><Col><Button onClick={()=>void load()} loading={loading}>همگام‌سازی نما</Button></Col><Col><Button type="primary" onClick={()=>openEditor()}>+ منبع جدید</Button></Col></Row>
    </Card>
    {error&&<Alert showIcon type="error" message="دریافت درخت منابع ناموفق بود" description={error} action={<Button onClick={()=>void load()}>تلاش مجدد</Button>}/>} 
    <Row gutter={12}><Col xs={24} md={8}><Card size="small"><Statistic title="کل منابع" value={stats.total}/></Card></Col><Col xs={24} md={8}><Card size="small"><Statistic title="منابع دارای تخصیص" value={stats.assigned}/></Card></Col><Col xs={24} md={8}><Card size="small"><Typography.Text type="secondary">پوشش دسترسی</Typography.Text><Progress percent={stats.coverage} strokeColor={{'0%':'#722ed1','100%':'#1677ff'}}/></Card></Col></Row>
    <Row gutter={16} align="stretch">
      <Col xs={24} lg={10}><Card title="درخت Manifest" loading={loading} styles={{body:{minHeight:560}}} extra={<Tag color="green">Service-backed</Tag>}>
        <Space direction="vertical" style={{width:'100%'}} size={12}><Input.Search allowClear placeholder="جست‌وجوی نام یا کلید منبع…" onChange={e=>setQuery(e.target.value)}/><Select value={typeFilter} onChange={setTypeFilter} style={{width:'100%'}} options={[{value:'ALL',label:'همه انواع منابع'},...resourceTypes.map(type=>({value:type,label:`${typeMeta[type].icon} ${typeMeta[type].label}`}))]}/>{tree.length?<Tree blockNode showLine defaultExpandAll treeData={tree} selectedKeys={selected?[selected.id]:[]} onSelect={keys=>setSelected(resources.find(r=>r.id===keys[0]))}/>:<Empty description="منبعی مطابق فیلتر پیدا نشد"/>}</Space>
      </Card></Col>
      <Col xs={24} lg={14}>{selected?<Space direction="vertical" size={16} style={{width:'100%'}}>
        <Card title={<Space><TypeTag type={selected.type}/><span>{selected.name_fa}</span></Space>} extra={<Space><Button onClick={()=>openEditor(undefined,selected)}>افزودن فرزند</Button><Button onClick={()=>openEditor(selected)}>ویرایش</Button></Space>}>
          <Descriptions size="small" column={{xs:1,md:2}} items={[{key:'key',label:'کلید canonical',children:<Typography.Text copyable code>{selected.resource_key}</Typography.Text>},{key:'owner',label:'دامنه مالک',children:selected.owner_domain||'—'},{key:'class',label:'طبقه‌بندی',children:<Tag>{selected.classification||'تعریف‌نشده'}</Tag>},{key:'status',label:'وضعیت',children:<Badge status="success" text={selected.status}/>}]}/>
          <Divider orientation="right">عملیات مجاز روی این منبع</Divider><Row gutter={[10,10]}>{actions.map(action=>{const on=selected.actions.some((a:RowData)=>a.id===action.id);return <Col xs={12} md={8} key={action.id}><Card size="small"><Space><Switch size="small" checked={on} onChange={checked=>void toggleAction(action,checked)}/><span>{action.name_fa}</span><Typography.Text type="secondary">{action.action_key}</Typography.Text></Space></Card></Col>})}</Row>
        </Card>
        <Card title="تخصیص دسترسی" extra={<Tag color="blue">OpenFGA + Outbox</Tag>}>
          <Alert type="info" showIcon message="ابتدا نوع و هویت را انتخاب کنید؛ سپس عملیات همین منبع را فعال یا لغو کنید." style={{marginBottom:16}}/>
          <Row gutter={[12,12]}><Col xs={24} md={8}><Segmented block value={subjectType} onChange={changeSubjectType} options={[{label:'کاربر',value:'USER'},{label:'گروه',value:'GROUP'},{label:'نقش',value:'ROLE'}]}/></Col><Col xs={24} md={16}><Select showSearch optionFilterProp="label" value={subjectId} onChange={chooseSubject} style={{width:'100%'}} placeholder={`انتخاب ${subjectType==='USER'?'کاربر':subjectType==='GROUP'?'گروه':'نقش'}`} options={subjects.map(s=>({value:s.id,label:subjectLabel(s)}))}/></Col></Row>
          <Divider/>{!subjectId?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="برای مشاهده و ویرایش دسترسی‌ها یک هویت انتخاب کنید"/>:<Table<RowData> size="small" loading={grantLoading} pagination={false} rowKey="id" dataSource={selected.actions as RowData[]} columns={[{title:'عملیات',render:(_,a)=><Space><strong>{a.nameFa}</strong><Tag>{a.key}</Tag></Space>},{title:'وضعیت',render:(_,a)=>grantedActions.has(a.id)?<Tag color="green">اعطا شده</Tag>:<Tag>بدون دسترسی</Tag>},{title:'',align:'left',render:(_,a)=>{const existing=selectedGrants.find(g=>g.action_id===a.id);return existing?<Button danger size="small" onClick={()=>void revoke(existing.id)}>لغو</Button>:<Button type="primary" ghost size="small" onClick={()=>void grant({id:a.id,action_key:a.key})}>اعطا</Button>}}]}/>} 
        </Card>
      </Space>:<Card styles={{body:{minHeight:560,display:'grid',placeItems:'center'}}}><Empty description="یک گره از درخت انتخاب کنید"><Button type="primary" onClick={()=>openEditor()}>ساخت اولین منبع</Button></Empty></Card>}</Col>
    </Row>
    <Drawer width={560} open={editorOpen} title={editing?'ویرایش منبع':'تعریف منبع جدید'} onClose={()=>setEditorOpen(false)} extra={<Button type="primary" onClick={()=>form.submit()}>ذخیره</Button>}>
      <Form form={form} layout="vertical" onFinish={save} requiredMark="optional"><Form.Item name="type" label="نوع منبع" rules={[{required:true}]}><Select optionRender={option=><Space><span>{typeMeta[String(option.value)]?.icon}</span><span>{option.label}</span></Space>} options={resourceTypes.map(type=>({value:type,label:typeMeta[type].label}))}/></Form.Item><Form.Item noStyle shouldUpdate>{()=>{const type=form.getFieldValue('type');return type&&<Alert type="info" showIcon message={typeMeta[type]?.label} description={typeMeta[type]?.hint} style={{marginBottom:16}}/>}}</Form.Item><Form.Item name="parentId" label="والد"><Select allowClear showSearch optionFilterProp="label" options={resources.filter(r=>r.id!==editing?.id).map(r=>({value:r.id,label:`${r.name_fa} — ${r.resource_key}`}))}/></Form.Item><Form.Item name="resourceKey" label="کلید canonical" rules={[{required:true,message:'کلید یکتا الزامی است'},{pattern:/^[a-z][a-z0-9_.:/-]+$/,message:'فقط حروف کوچک انگلیسی، عدد و . _ : / -'}]}><Input placeholder="page:hr.employees"/></Form.Item><Row gutter={12}><Col span={12}><Form.Item name="nameFa" label="نام فارسی" rules={[{required:true}]}><Input/></Form.Item></Col><Col span={12}><Form.Item name="nameEn" label="نام انگلیسی" rules={[{required:true}]}><Input/></Form.Item></Col></Row><Row gutter={12}><Col span={12}><Form.Item name="ownerDomain" label="دامنه مالک"><Input placeholder="hr"/></Form.Item></Col><Col span={12}><Form.Item name="classification" label="طبقه‌بندی"><Select allowClear options={['PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'].map(value=>({value,label:value}))}/></Form.Item></Col></Row><Form.Item noStyle shouldUpdate>{()=>form.getFieldValue('type')==='EXTERNAL_RESOURCE'&&<Card size="small" title="شناسه منبع خارجی"><Form.Item name="externalSystem" label="سامانه"><Input placeholder="superset"/></Form.Item><Row gutter={12}><Col span={12}><Form.Item name="externalType" label="نوع خارجی"><Input placeholder="dashboard"/></Form.Item></Col><Col span={12}><Form.Item name="externalId" label="شناسه خارجی"><Input/></Form.Item></Col></Row></Card>}</Form.Item></Form>
    </Drawer>
  </Space>
}
