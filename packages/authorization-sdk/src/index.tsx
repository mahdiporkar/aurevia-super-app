import React,{createContext,useContext,type PropsWithChildren,type ReactNode}from'react';
import type{EffectiveUserContext}from'@aurevia/contracts';

export type AuthorizationDecision='allowed'|'denied'|'missing'|'expired';

/** Fail-closed, in-memory evaluator. It has no knowledge of IAM or authorization-service URLs. */
export class AuthorizationClient {
  private context?:EffectiveUserContext;
  setContext(context:EffectiveUserContext){this.context=context;}
  clear(){this.context=undefined;}
  getContext(){return this.context;}
  decision(resource:string,action:string,now=Date.now()):AuthorizationDecision{
    if(!this.context)return'missing';
    const expires=Date.parse(this.context.expiresAt);
    if(!Number.isFinite(expires)||expires<=now)return'expired';
    return this.context.actions[resource]?.includes(action)?'allowed':'denied';
  }
  can(resource:string,action:string){return this.decision(resource,action)==='allowed';}
}

export const authorization=new AuthorizationClient();
const AuthorizationContext=createContext<AuthorizationClient|undefined>(undefined);

export function AuthorizationProvider({context,client=authorization,children}:
  PropsWithChildren<{context:EffectiveUserContext;client?:AuthorizationClient}>){
  client.setContext(context);
  return <AuthorizationContext.Provider value={client}>{children}</AuthorizationContext.Provider>;
}

export function useAuthorization(){return useContext(AuthorizationContext)??authorization;}

export function Can({resource,action,fallback=null,children}:{resource:string;action:string;
  fallback?:ReactNode;children:ReactNode}){
  return useAuthorization().can(resource,action)?<>{children}</>:<>{fallback}</>;
}

export async function evaluateDynamicPolicy(resource:string,action:string,
  attributes:Record<string,unknown>,signal?:AbortSignal):Promise<boolean>{
  const csrfResponse=await fetch('/api/v1/csrf',{credentials:'same-origin',signal});
  if(!csrfResponse.ok)return false;
  const csrf=await csrfResponse.json()as{headerName:string;token:string};
  const response=await fetch('/api/v1/authorize/evaluate',{method:'POST',credentials:'same-origin',
    headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},
    body:JSON.stringify({resource,action,context:attributes}),signal});
  if(!response.ok)return false;
  const decision=await response.json()as{result?:string;allowed?:boolean};
  return decision.allowed===true||decision.result==='ALLOW';
}
