import {admin} from './lib.mjs';
const s=await admin();
const rs=await s.json('/api/v1/admin/proxy-routes');
const dup=rs.body.find(r=>r.code==='dup-route');
if(dup){const r=await s.json(`/api/v1/admin/proxy-routes/${dup.id}/status?version=${dup.version}`,'PATCH',{active:false});
  console.log('deactivated dup-route ->',r.status);}
console.log('active routes on prefix:',JSON.stringify(rs.body.filter(r=>r.path_prefix==='/api/proxy/e2e-hybrid-test-micro').map(r=>({c:r.code,a:r.active}))));
