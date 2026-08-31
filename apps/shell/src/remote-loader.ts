import type {RemoteModule} from '@aurevia/contracts';
declare global{interface Window{[key:string]:any}}
export function validateRemoteDescriptor(scope:string,url:string,allowed:string[],integrity?:string):URL{
  if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(scope))throw new Error('Remote scope is invalid');
  let remoteUrl:URL;
  try{remoteUrl=new URL(url)}catch{throw new Error('Remote Entry must be a complete http(s) URL')}
  if(!['http:','https:'].includes(remoteUrl.protocol)||!allowed.includes(url))throw new Error('Remote URL is not allowlisted');
  if(integrity&&!/^sha(256|384|512)-[A-Za-z0-9+/]+={0,2}$/.test(integrity))throw new Error('Remote integrity is invalid');
  return remoteUrl;
}
export async function loadRemote(scope:string,url:string,module:string,allowed:string[],integrity?:string):Promise<RemoteModule>{
  validateRemoteDescriptor(scope,url,allowed,integrity);
  await new Promise<void>((resolve,reject)=>{const existing=Array.from(document.scripts).find(script=>script.dataset.remote===scope);if(existing){if(existing.src!==url||existing.integrity!==(integrity??'')){reject(new Error('Remote scope is already bound to a different artifact'));return}resolve();return}const script=document.createElement('script');script.src=url;script.dataset.remote=scope;if(integrity){script.integrity=integrity;script.crossOrigin='anonymous'}script.onload=()=>resolve();script.onerror=()=>reject(new Error('Remote failed integrity or network validation'));document.head.appendChild(script)});
  await __webpack_init_sharing__('default'); const container=window[scope]; await container.init(__webpack_share_scopes__.default); const factory=await container.get(module); const loaded=factory() as RemoteModule;if(loaded.contractVersion!=='1')throw new Error('Incompatible remote contract');return loaded;
}
declare const __webpack_init_sharing__:(scope:string)=>Promise<void>;declare const __webpack_share_scopes__:{default:unknown};
