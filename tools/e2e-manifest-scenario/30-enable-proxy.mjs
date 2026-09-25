import {admin,state} from './lib.mjs';
const s=await admin(); const st=state();
const list=await s.json('/api/v1/admin/superset-instances');
for(const code of ['e2e-superset-public','e2e-superset-operation']){
  const i=list.body.find(x=>x.code===code);
  const r=await s.json(`/api/v1/admin/superset-instances/${i.id}`,'PUT',{
    code:i.code,name:i.name,zone:i.zone,baseUrl:i.base_url,connectionRef:i.connection_ref,
    authMode:i.auth_mode,tlsRequired:i.tls_required,active:true,proxyMode:true,metadata:{},version:i.version});
  console.log('UPDATE',code,'proxyMode=true ->',r.status,JSON.stringify(r.body).slice(0,90));
}
const after=await s.json('/api/v1/admin/superset-instances');
console.log(JSON.stringify(after.body.filter(x=>x.code.startsWith('e2e-')).map(x=>({c:x.code,proxy:x.proxy_mode,active:x.active}))));
