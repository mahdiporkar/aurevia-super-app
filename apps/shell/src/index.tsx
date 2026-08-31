import React,{Component,useEffect,useRef,useState,type ReactNode} from 'react';
import {createRoot} from 'react-dom/client';
import {Alert,Button,ConfigProvider,Layout,Menu,Space,Spin,Tag,Typography} from 'antd';
import faIR from 'antd/locale/fa_IR';import enUS from 'antd/locale/en_US';
import type{EffectiveManifest,Locale,PanelManifest}from'@aurevia/contracts';
import{direction,t}from'@aurevia/i18n';import{SHManifestProvider}from'@aurevia/sh-core-ui';import{loadRemote}from'./remote-loader';
import'./styles.css';

const empty:EffectiveManifest={version:'none',expiresAt:new Date(Date.now()+60000).toISOString(),panels:[],permissions:{}};
class RemoteBoundary extends Component<{children:ReactNode;message:string},{failed:boolean}>{state={failed:false};static getDerivedStateFromError(){return{failed:true}}render(){return this.state.failed?<Alert type="error" message={this.props.message}/>:this.props.children}}
function RemoteHost({panel,manifest,locale}:{panel:PanelManifest;manifest:EffectiveManifest;locale:Locale}){
  const host=useRef<HTMLDivElement>(null),[error,setError]=useState(''),[loading,setLoading]=useState(true);
  useEffect(()=>{let cleanup:(()=>void)|undefined,cancelled=false;setLoading(true);setError('');const scope=`aurevia_${panel.slug.replace(/^mfe-/,'').replaceAll('-','_')}`;loadRemote(scope,panel.remoteEntry,panel.exposedModule,manifest.panels.map(p=>p.remoteEntry),panel.integrity).then(remote=>{if(!cancelled&&host.current)cleanup=remote.mount(host.current,{locale,manifest,correlationId:()=>crypto.randomUUID()})}).catch(e=>setError(e instanceof Error?e.message:String(e))).finally(()=>setLoading(false));return()=>{cancelled=true;cleanup?.()}},[panel,manifest,locale]);
  if(error)return <Alert type="error" message={t(locale,'remoteUnavailable')} description={error}/>;return <>{loading&&<Spin tip={t(locale,'loading')}/>}<div ref={host}/></>;
}
function App(){
  const[locale,setLocale]=useState<Locale>('fa-IR'),[manifest,setManifest]=useState<EffectiveManifest>(),[selected,setSelected]=useState<string>(),[error,setError]=useState('');
  useEffect(()=>{
    fetch('/api/v1/me/manifest',{credentials:'same-origin',redirect:'manual',headers:{'X-Correlation-ID':crypto.randomUUID()}})
      .then(r=>{if(r.status===401||r.status===302||r.type==='opaqueredirect'){window.location.assign('/oauth2/authorization/public-iam');throw new Error('AUTH_REDIRECT')}if(!r.ok)throw new Error(String(r.status));return r.json()})
      .then((m:EffectiveManifest)=>{setManifest(m);setSelected(m.panels[0]?.slug)})
      .catch(e=>{if(!(e instanceof Error&&e.message==='AUTH_REDIRECT'))setError(e instanceof Error?e.message:String(e))});
  },[]);
  const panel=manifest?.panels.find(p=>p.slug===selected);
  return <ConfigProvider direction={direction(locale)} locale={locale==='fa-IR'?faIR:enUS} theme={{token:{colorPrimary:'#6d5dfc',borderRadius:12,fontFamily:'Tahoma, Arial, sans-serif'}}}><SHManifestProvider initial={manifest??empty}><Layout className="app-shell"><Layout.Header className="app-header"><div className="brand"><span className="brand-mark">A</span><div><Typography.Title level={3}>{t(locale,'appName')}</Typography.Title><span>Enterprise workspace</span></div></div><Space><Tag color="green">Online</Tag><Button ghost onClick={()=>setLocale(locale==='fa-IR'?'en-US':'fa-IR')}>{t(locale,'language')}</Button><Button ghost href="/auth/logout">خروج</Button></Space></Layout.Header><Layout><Layout.Sider className="app-sidebar" breakpoint="lg" collapsedWidth="0"><div className="nav-caption">فضاهای کاری</div><Menu mode="inline" selectedKeys={selected?[selected]:[]} onSelect={({key})=>setSelected(key)} items={(manifest?.panels??[]).map((p,index)=>({key:p.slug,icon:<span className="nav-icon">{['⚙','◫','◈','▥'][index]??'•'}</span>,label:locale==='fa-IR'?p.nameFa:p.nameEn}))}/></Layout.Sider><Layout.Content className="app-content"><div className="page-heading"><div><span className="eyebrow">AUREVIA / {panel?.code??'HOME'}</span><Typography.Title level={2}>{panel?(locale==='fa-IR'?panel.nameFa:panel.nameEn):t(locale,'appName')}</Typography.Title></div><Tag>{manifest?.version??'...'}</Tag></div><main className="remote-surface">{error?<Alert showIcon type="error" message="خطا در بارگذاری" description={error} action={<Button onClick={()=>location.reload()}>تلاش مجدد</Button>}/>:!manifest?<div className="center-state"><Spin size="large" tip={t(locale,'loading')}/></div>:panel?<RemoteBoundary message={t(locale,'remoteUnavailable')}><RemoteHost panel={panel} manifest={manifest} locale={locale}/></RemoteBoundary>:<Typography.Title level={2}>{t(locale,'appName')}</Typography.Title>}</main></Layout.Content></Layout></Layout></SHManifestProvider></ConfigProvider>;
}
createRoot(document.getElementById('root')!).render(<App/>);
