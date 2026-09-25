import {Session} from '../e2e-auth/session.mjs';
import fs from 'node:fs';
export const STATE_PATH=process.env.AUREVIA_SCENARIO_STATE??'.tmp/e2e-manual/state.json';
export const ADMIN_BASE=process.env.AUREVIA_SCENARIO_BASE??'http://localhost:8443';
export const ADMIN_PW=fs.readFileSync(process.env.TEMP+'/admpw.txt','utf8').trim();
export const SLUG='e2e-hybrid-test-micro';
export async function admin(){const s=new Session(ADMIN_BASE);await s.login('administrator',ADMIN_PW);return s;}
export function show(label,r){console.log(label,'->',r.status,typeof r.body==='object'?JSON.stringify(r.body).slice(0,600):r.body);return r;}
export const state=()=>JSON.parse(fs.readFileSync(STATE_PATH,'utf8'));
export const save=o=>fs.writeFileSync(STATE_PATH,JSON.stringify(o,null,2));
