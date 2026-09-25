import {admin} from './lib.mjs';
const s=await admin();
for(const p of ['/api/v1/admin/outbound-auth-profiles','/api/v1/admin/service-targets','/api/v1/admin/proxy-routes']){
  const r=await s.json(p);
  console.log(p,'->',r.status, Array.isArray(r.body)?('n='+r.body.length+' '+JSON.stringify(r.body[0]||{}).slice(0,220)):JSON.stringify(r.body).slice(0,220));
}
