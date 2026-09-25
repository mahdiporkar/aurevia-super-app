import {admin} from './lib.mjs';
import {userB} from './lib2.mjs';
const s=await admin();
const rt=await s.json('/api/v1/admin/proxy-routes/resolve-test','POST',{method:'GET',path:'/api/proxy/e2e-hybrid-test-micro/api/test/whoami'});
console.log('resolve-test ->',rt.status,JSON.stringify(rt.body).slice(0,260));
const b=await userB();
const r=await b.json('/api/proxy/e2e-hybrid-test-micro/api/test/whoami');
console.log('LIVE CALL after duplicate route created ->',r.status,JSON.stringify(r.body).slice(0,160));
