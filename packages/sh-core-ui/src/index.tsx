import React,{createContext,useContext,useMemo,useState,type PropsWithChildren,type ReactNode} from 'react';
import type {EffectiveManifest,PresentationMode} from '@aurevia/contracts';

type ManifestState={manifest?:EffectiveManifest;loading:boolean;replace:(next:EffectiveManifest)=>void};
const Context=createContext<ManifestState|undefined>(undefined);
export function SHCoreProvider(props:PropsWithChildren){return <>{props.children}</>}
export function SHManifestProvider({initial,children}:{initial?:EffectiveManifest;children:ReactNode}){
  const [manifest,setManifest]=useState(initial); const value=useMemo(()=>({manifest,loading:false,replace:setManifest}),[manifest]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function useSHManifest(){const v=useContext(Context);if(!v)throw new Error('SHManifestProvider is required');return v}
export function useSHPolicy(resource:string,action:string){
  const {manifest,loading}=useSHManifest();
  if(loading)return {allowed:false,state:'loading' as const};
  if(!manifest)return {allowed:false,state:'missing' as const};
  if(Date.parse(manifest.expiresAt)<=Date.now())return {allowed:false,state:'expired' as const};
  const actions=manifest.permissions[resource]; if(!actions)return {allowed:false,state:'unknown' as const};
  return {allowed:actions.includes(action),state:actions.includes(action)?'allowed' as const:'denied' as const,mode:manifest.presentation?.[`${resource}:${action}`]};
}
export function SHAccessDenied({children='Access denied'}:{children?:ReactNode}){return <div role="alert">{children}</div>}
export function SHCan({resource,action,fallback=null,children}:{resource:string;action:string;fallback?:ReactNode;children:ReactNode}){return useSHPolicy(resource,action).allowed?<>{children}</>:<>{fallback}</>}
export function SHRouteGuard(props:{resource:string;action:string;children:ReactNode}){return <SHCan {...props} fallback={<SHAccessDenied/>}/>}
export function SHAction({resource,action,mode='hide',children}:{resource:string;action:string;mode?:PresentationMode;children:React.ReactElement}){
  const policy=useSHPolicy(resource,action); if(policy.allowed)return children; const effective=policy.mode??mode;
  if(effective==='hide')return null;
  return React.cloneElement(children as React.ReactElement<Record<string,unknown>>,effective==='disable'?{disabled:true,'aria-disabled':true}:{readOnly:true,'aria-readonly':true});
}
