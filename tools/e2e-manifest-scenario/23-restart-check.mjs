import {userB,summarize} from './lib2.mjs';
const s=await userB();
const f=await s.json('/api/proxy/e2e-hybrid-test-micro/api/test/whoami');
console.log('AFTER RESTART forward ->',f.status,JSON.stringify(f.body).slice(0,150));
const l=await s.json('/api/proxy/e2e-hybrid-legacy/api/test/whoami');
console.log('AFTER RESTART legacy  ->',l.status,JSON.stringify(l.body).slice(0,150));
const c=summarize(await s.json('/api/v1/me/manifest'));
console.log('AFTER RESTART context ->',JSON.stringify({panels:c.panels,perms:Object.keys(c.permDetail)}));
