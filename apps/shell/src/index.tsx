import React,{Component,useEffect,useRef,useState,type ReactNode} from 'react';
import {createRoot} from 'react-dom/client';
import {Alert,Button,ConfigProvider,Layout,Menu,Spin,Typography} from 'antd';
import faIR from 'antd/locale/fa_IR';import enUS from 'antd/locale/en_US';
import type{EffectiveManifest,Locale,PanelManifest}from'@aurevia/contracts';
import{direction,t}from'@aurevia/i18n';import{SHManifestProvider}from'@aurevia/sh-core-ui';import{loadRemote}from'./remote-loader';

const empty:EffectiveManifest={version:'none',expiresAt:new Date(Date.now()+60000).toISOString(),panels:[],permissions:{}};
class RemoteBoundary extends Component<{children:ReactNode;message:string},{failed:boolean}>{state={failed:false};static getDerivedStateFromError(){return{failed:true}}render(){return this.state.failed?<Alert type="error" message={this.props.message}/>:this.props.children}}
function RemoteHost({panel,manifest,locale}:{panel:PanelManifest;manifest:EffectiveManifest;locale:Locale}){
  const host=useRef<HTMLDivElement>(null),[error,setError]=useState(''),[loading,setLoading]=useState(true);
  useEffect(()=>{let cleanup:(()=>void)|undefined,cancelled=false;setLoading(true);setError('');const scope=`aurevia_${panel.slug.replace(/^mfe-/,'').replaceAll('-','_')}`;loadRemote(scope,panel.remoteEntry,panel.exposedModule,manifest.panels.map(p=>p.remoteEntry)).then(remote=>{if(!cancelled&&host.current)cleanup=remote.mount(host.current,{locale,manifest,correlationId:()=>crypto.randomUUID()})}).catch(e=>setError(e instanceof Error?e.message:String(e))).finally(()=>setLoading(false));return()=>{cancelled=true;cleanup?.()}},[panel,manifest,locale]);
  if(error)return <Alert type="error" message={t(locale,'remoteUnavailable')} description={error}/>;return <>{loading&&<Spin tip={t(locale,'loading')}/>}<div ref={host}/></>;
}
function App(){
  const[locale,setLocale]=useState<Locale>('fa-IR'),[manifest,setManifest]=useState<EffectiveManifest>(),[selected,setSelected]=useState<string>(),[error,setError]=useState('');
  useEffect(()=>{
    fetch('/api/v1/me/manifest',{credentials:'same-origin',headers:{'X-Correlation-ID':crypto.randomUUID()}})
      .then(r=>{if(!r.ok)throw new Error(String(r.status));return r.json()})
      .then((m:EffectiveManifest)=>{setManifest(m);setSelected(m.panels[0]?.slug)})
      .catch(e=>setError(e instanceof Error?e.message:String(e)));
  },[]);
  const panel=manifest?.panels.find(p=>p.slug===selected);
  return <ConfigProvider direction={direction(locale)} locale={locale==='fa-IR'?faIR:enUS}><SHManifestProvider initial={manifest??empty}><Layout style={{minHeight:'100vh'}}><Layout.Header style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}><Typography.Title level={3} style={{color:'white',margin:0}}>{t(locale,'appName')}</Typography.Title><Button onClick={()=>setLocale(locale==='fa-IR'?'en-US':'fa-IR')}>{t(locale,'language')}</Button></Layout.Header><Layout><Layout.Sider><Menu theme="dark" selectedKeys={selected?[selected]:[]} onSelect={({key})=>setSelected(key)} items={(manifest?.panels??[]).map(p=>({key:p.slug,label:locale==='fa-IR'?p.nameFa:p.nameEn}))}/></Layout.Sider><Layout.Content style={{padding:24}}>{error?<Alert type="error" message={error}/>:!manifest?<Spin tip={t(locale,'loading')}/>:panel?<RemoteBoundary message={t(locale,'remoteUnavailable')}><RemoteHost panel={panel} manifest={manifest} locale={locale}/></RemoteBoundary>:<Typography.Title level={2}>{t(locale,'appName')}</Typography.Title>}</Layout.Content></Layout></Layout></SHManifestProvider></ConfigProvider>;
}
createRoot(document.getElementById('root')!).render(<App/>);
