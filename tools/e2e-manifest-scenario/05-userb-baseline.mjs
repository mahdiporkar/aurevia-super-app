import {userB} from './lib2.mjs';
import {summarize} from './lib2.mjs';
const s=await userB();
const me=await s.json('/api/v1/me');
console.log('USER B LOGIN', me.status, me.body?.username, 'sub=', me.body?.subject??me.body?.sub);
const ctx=await s.json('/api/v1/me/manifest');
console.log('BASELINE CONTEXT:',JSON.stringify(summarize(ctx),null,1));
console.log('ALL PANELS:',JSON.stringify((ctx.body?.panels||[]).map(p=>p.slug)));
