import {Session} from '../e2e-auth/session.mjs';
import {ADMIN_BASE} from './lib.mjs';
import fs from 'node:fs';
export const PW_B=fs.readFileSync(process.env.TEMP+'/userbpw.txt','utf8').trim();
export async function userB(){const s=new Session(ADMIN_BASE);await s.login('e2e-user-b',PW_B);return s;}
export function summarize(ctx,slug='e2e-hybrid-test-micro'){
  const b=ctx.body||{};
  const panels=(b.panels||[]).map(p=>p.slug??p.code);
  const perms=Object.keys(b.permissions||{}).filter(k=>k.includes(slug));
  const mods=(b.uiCatalog?.modules||[]).filter(m=>(m.moduleKey||'').includes(slug));
  return {status:ctx.status,panels,permKeys:perms,permDetail:Object.fromEntries(Object.entries(b.permissions||{}).filter(([k])=>k.includes(slug))),
    uiModules:mods.map(m=>({key:m.moduleKey,routes:(m.routes||[]).map(r=>r.id),nav:(m.navigation||[]).map(n=>n.key??n.id)}))};
}
