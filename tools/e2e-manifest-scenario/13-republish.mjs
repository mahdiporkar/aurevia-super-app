import {admin,state} from './lib.mjs';
const s=await admin(); const st=state(); const P=st.panelId;
const d=await s.json(`/api/v1/admin/panels/${P}/resource-manifests`);
const v2=d.body.find(x=>x.manifestVersion==='2.0.0');
console.log('v2 status:',v2.workflowStatus,'active:',v2.active);
const r=await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts/${v2.id}/publish`,'POST');
console.log('REPUBLISH PUBLISHED revision ->',r.status,JSON.stringify(r.body).slice(0,200));
