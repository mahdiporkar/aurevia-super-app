import {admin} from './lib.mjs';
import {userB,summarize} from './lib2.mjs';
import fs from 'node:fs';
export const ids=JSON.parse(fs.readFileSync(process.env.AUREVIA_SCENARIO_IDS??'.tmp/e2e-manual/ids.json','utf8'));
export async function grantTo(resourceId,actionId=ids.view){
  const s=await admin();
  const r=await s.json('/api/v1/admin/grants','POST',{userId:ids.userB,subjectType:'USER',subjectId:ids.userB,resourceId,actionId});
  return r;
}
export async function listGrants(){const s=await admin();return await s.json(`/api/v1/admin/users/${ids.userB}/grants`);}
export async function revokeAll(){const s=await admin();const g=await s.json(`/api/v1/admin/users/${ids.userB}/grants`);
  const out=[];for(const x of (g.body||[])){out.push(await s.json(`/api/v1/admin/grants/${x.id}`,'DELETE'));}return out;}
export async function ctxB(){const s=await userB();return summarize(await s.json('/api/v1/me/manifest'));}
export async function drain(ms=25000){const t=Date.now();while(Date.now()-t<ms){
  const n=parseInt(await pending(),10); if(n===0)return true; await new Promise(r=>setTimeout(r,1000));} return false;}
import {execSync} from 'node:child_process';
function pending(){return execSync(`docker exec aurevia-e2e-auth-db-1 psql -U aurevia -d aurevia_auth -t -A -c "select count(*) from outbox_event where processed_at is null;"`).toString().trim();}
