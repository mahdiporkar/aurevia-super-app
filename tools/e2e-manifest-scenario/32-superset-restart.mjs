import {admin,state} from './lib.mjs';
const s=await admin(); const st=state();
const i=await s.json('/api/v1/admin/superset-instances');
console.log('instances after restart:',JSON.stringify(i.body.filter(x=>x.code.startsWith('e2e-')).map(x=>({c:x.code,z:x.zone,u:x.base_url,proxy:x.proxy_mode}))));
const a=await s.json('/api/v1/admin/superset-assets');
console.log('assets after restart:',JSON.stringify((a.body||[]).filter(x=>x.title.startsWith('E2E')).map(x=>x.title)));
const m=await s.json('/api/v1/admin/superset-instances/mappings');
console.log('mappings:',(m.body||[]).length);
