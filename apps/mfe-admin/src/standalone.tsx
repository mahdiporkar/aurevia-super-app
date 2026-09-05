import React from'react';
import{createRoot}from'react-dom/client';
import{BrowserRouter}from'react-router-dom';
import type{EffectiveManifest,HostRuntime}from'@aurevia/contracts';
import{App}from'./bootstrap';

const root=document.getElementById('root');
if(root) {
  const manifest:EffectiveManifest={version:'standalone',expiresAt:new Date(Date.now()+3600000).toISOString(),panels:[],permissions:{}};
  const runtime:HostRuntime={mode:'standalone',moduleKey:'admin',routePrefix:'',
    http:{get:<T,>(path:string)=>fetch(`/api/v1/admin${path}`,{credentials:'same-origin'}).then(response=>response.json()as Promise<T>),post:<T,B>(path:string,body:B)=>fetch(`/api/v1/admin${path}`,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(response=>response.json()as Promise<T>),put:<T,B>(path:string,body:B)=>fetch(`/api/v1/admin${path}`,{method:'PUT',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(response=>response.json()as Promise<T>)},
    navigation:{navigate:path=>window.history.pushState({},'',`/${path}`),getModuleBasePath:()=>''},
    session:{getCurrentUser:()=>null,subscribe:()=>()=>{}},notifications:{success:()=>{},error:()=>{}},
    events:{emit:()=>{},subscribe:()=>()=>{}},sharedState:{get:()=>undefined,subscribe:()=>()=>{}},
    theme:{locale:'fa-IR',direction:'rtl'}};
  createRoot(root).render(<BrowserRouter><App runtime={runtime} manifest={manifest}/></BrowserRouter>);
}
