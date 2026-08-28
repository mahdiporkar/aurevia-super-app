import type {RemoteModule} from '@aurevia/contracts';
declare global{interface Window{[key:string]:any}}
export async function loadRemote(scope:string,url:string,module:string,allowed:string[]):Promise<RemoteModule>{
  if(!url.startsWith('/')||!allowed.includes(url))throw new Error('Remote URL is not allowlisted');
  await new Promise<void>((resolve,reject)=>{const existing=document.querySelector(`script[data-remote="${scope}"]`);if(existing){resolve();return}const script=document.createElement('script');script.src=url;script.dataset.remote=scope;script.integrity='';script.onload=()=>resolve();script.onerror=()=>reject(new Error('Remote failed'));document.head.appendChild(script)});
  await __webpack_init_sharing__('default'); const container=window[scope]; await container.init(__webpack_share_scopes__.default); const factory=await container.get(module); const loaded=factory() as RemoteModule;if(loaded.contractVersion!=='1')throw new Error('Incompatible remote contract');return loaded;
}
declare const __webpack_init_sharing__:(scope:string)=>Promise<void>;declare const __webpack_share_scopes__:{default:unknown};
