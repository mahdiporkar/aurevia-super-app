import {admin,state,save} from './lib.mjs';
const s=await admin(); const st=state();
const mk=async(code,ext,title,path)=>{const r=await s.json('/api/v1/admin/superset-assets','POST',
  {externalId:ext,assetType:'DASHBOARD',title,urlPath:path,ownerExternalId:null,published:true,instanceCode:code});
 console.log('ASSET',code,title,'->',r.status,JSON.stringify(r.body).slice(0,140));return r.body;};
const opA=await mk('e2e-superset-operation','11','E2E Operation Dashboard A','/superset/dashboard/11/');
const opB=await mk('e2e-superset-operation','1','E2E Operation Dashboard B','/superset/dashboard/1/');
const puA=await mk('e2e-superset-public','2','E2E Public Dashboard A','/superset/dashboard/2/');
const puB=await mk('e2e-superset-public','3','E2E Public Dashboard B','/superset/dashboard/3/');
const all=await s.json('/api/v1/admin/superset-assets');
console.log('CATALOG:',JSON.stringify((all.body||[]).map(a=>({id:a.id,t:a.title,ext:a.externalId??a.external_id,inst:a.instanceCode??a.instance_code}))));
save({...st,assets:{opA:opA?.id,opB:opB?.id,puA:puA?.id,puB:puB?.id}});
