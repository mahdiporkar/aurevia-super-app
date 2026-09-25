import {admin,state,save} from './lib.mjs';
const s=await admin(); const st=state();
const existing=await s.json('/api/v1/admin/superset-instances');
console.log('existing instances:',JSON.stringify((existing.body||[]).map(i=>({code:i.code,zone:i.zone,url:i.base_url,active:i.active}))));
const mk=async(code,name,zone,url)=>{const r=await s.json('/api/v1/admin/superset-instances','POST',
  {code,name,zone,baseUrl:url,connectionRef:null,authMode:'REMOTE_USER',tlsRequired:false,active:true,proxyMode:false,metadata:{},version:0});
  console.log('CREATE',code,zone,'->',r.status,JSON.stringify(r.body).slice(0,120));return r;};
const pub=await mk('e2e-superset-public','E2E Superset Public','PUBLIC','http://host.docker.internal:8089');
const ope=await mk('e2e-superset-operation','E2E Superset Operation','OPERATION','http://host.docker.internal:8088');
const after=await s.json('/api/v1/admin/superset-instances');
const mine=(after.body||[]).filter(i=>i.code.startsWith('e2e-superset'));
console.log('MINE:',JSON.stringify(mine.map(i=>({id:i.id,code:i.code,zone:i.zone,url:i.base_url,active:i.active,proxy:i.proxy_mode}))));
save({...st,supersetPublicId:mine.find(i=>i.zone==='PUBLIC')?.id,supersetOperationId:mine.find(i=>i.zone==='OPERATION')?.id});
