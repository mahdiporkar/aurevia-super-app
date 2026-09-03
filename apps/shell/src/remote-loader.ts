import type {LoadedRemoteModule} from '@aurevia/contracts';
interface FederationContainer{init(scope:unknown):Promise<void>|void;get(module:string):Promise<()=>unknown>}
declare global{interface Window{[key:string]:unknown}}
declare const __webpack_init_sharing__:(scope:string)=>Promise<void>;declare const __webpack_share_scopes__:{default:unknown};
const loads=new Map<string,Promise<LoadedRemoteModule>>(),initialized=new WeakSet<object>();
export function validateRemoteDescriptor(scope:string,url:string,allowed:string[],integrity?:string):URL{
  if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(scope))throw new Error('Remote scope is invalid');let remoteUrl:URL;
  try{remoteUrl=new URL(url)}catch{throw new Error('Remote Entry must be a complete http(s) URL')}
  if(!['http:','https:'].includes(remoteUrl.protocol)||!allowed.includes(url))throw new Error('Remote URL is not allowlisted');
  if(typeof location!=='undefined'&&location.protocol==='https:'&&remoteUrl.protocol!=='https:')throw new Error('Remote Entry must use HTTPS');
  if(integrity&&!/^sha(256|384|512)-[A-Za-z0-9+/]+={0,2}$/.test(integrity))throw new Error('Remote integrity is invalid');return remoteUrl;
}
function inject(scope:string,url:string,integrity:string|undefined,timeoutMs:number):Promise<void>{return new Promise((resolve,reject)=>{
  const existing=Array.from(document.scripts).find(script=>script.dataset.remote===scope);
  if(existing){if(existing.src!==url||existing.integrity!==(integrity??''))return reject(new Error('Remote scope is already bound to a different artifact'));if(window[scope])return resolve();existing.addEventListener('load',()=>resolve(),{once:true});existing.addEventListener('error',()=>reject(new Error('Remote failed integrity or network validation')),{once:true});return}
  const script=document.createElement('script');script.src=url;script.dataset.remote=scope;script.async=true;if(integrity){script.integrity=integrity;script.crossOrigin='anonymous'}
  const timer=window.setTimeout(()=>{script.remove();reject(new Error('Remote loading timed out'))},timeoutMs);script.onload=()=>{clearTimeout(timer);resolve()};script.onerror=()=>{clearTimeout(timer);script.remove();reject(new Error('Remote failed integrity or network validation'))};document.head.appendChild(script);
})}
function isContainer(value:unknown):value is FederationContainer{return typeof value==='object'&&value!==null&&'init'in value&&'get'in value}
function isRemote(value:unknown):value is LoadedRemoteModule{if(typeof value!=='object'||value===null||!('contractVersion'in value))return false;const candidate=value as Record<string,unknown>;return(candidate.contractVersion==='1.0'&&typeof candidate.App==='function')||(candidate.contractVersion==='1'&&typeof candidate.mount==='function')}
export function clearRemoteCache(scope?:string){if(!scope){loads.clear();return}for(const key of loads.keys())if(key.startsWith(`${scope}|`))loads.delete(key)}
export function loadRemote(scope:string,url:string,module:string,allowed:string[],integrity?:string,timeoutMs=15000):Promise<LoadedRemoteModule>{
  validateRemoteDescriptor(scope,url,allowed,integrity);const key=[scope,url,module,integrity??''].join('|'),cached=loads.get(key);if(cached)return cached;
  const promise=(async()=>{await inject(scope,url,integrity,timeoutMs);await __webpack_init_sharing__('default');const container=window[scope];if(!isContainer(container))throw new Error('Remote container was not registered');if(!initialized.has(container)){await container.init(__webpack_share_scopes__.default);initialized.add(container)}const loaded=(await container.get(module))();if(!isRemote(loaded))throw new Error('Remote plugin export is invalid');return loaded})().catch(error=>{loads.delete(key);throw error});loads.set(key,promise);return promise;
}
