import {admin,state} from './lib.mjs';
import {userB} from './lib2.mjs';
import {drain} from './grant.mjs';
const s=await admin(); const st=state();
const gs=await s.json(`/api/v1/admin/superset-assets/${st.assets.opA}/grants`);
console.log('grants before revoke:',JSON.stringify(gs.body).slice(0,200));
for(const g of (gs.body||[])){
  const r=await s.json(`/api/v1/admin/superset-assets/${st.assets.opA}/grants/${g.id}`,'DELETE');
  console.log('REVOKE',g.id,'->',r.status);
}
await drain();
const b=await userB();
await b.request('/api/v1/superset-instances/e2e-superset-public/superset/dashboard/11/');
const r=await b.request('/api/v1/superset/superset/dashboard/11/');
console.log('AFTER REVOKE dash 11 ->',r.status);
const ctx=await b.json('/api/v1/me/manifest');
console.log('context external resources:',JSON.stringify(Object.keys(ctx.body?.permissions||{}).filter(k=>k.includes('external_resource'))));
