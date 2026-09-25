import {admin,show,state,save} from './lib.mjs';
import fs from 'node:fs';
const pw=fs.readFileSync(process.env.TEMP+'/userbpw.txt','utf8').trim();
const s=await admin(); const st=state();
const r=await s.json('/api/v1/admin/keycloak-users','POST',{username:'e2e-user-b',firstName:'E2E',lastName:'UserB',
  email:'e2e-user-b@aurevia.test',enabled:true,initialPassword:pw});
console.log('CREATE user B ->',r.status,JSON.stringify(r.body));
save({...st,userBId:r.body?.id,userBUsername:'e2e-user-b'});
