import {admin,state} from './lib.mjs';
import {userB,summarize} from './lib2.mjs';
const s=await admin(); const st=state();
const p=(await s.json('/api/v1/admin/panels')).body.find(x=>x.slug==='e2e-hybrid-test-micro');
console.log('PANEL          :',p.id,'semver',p.semantic_version,'version',p.version);
const revs=(await s.json(`/api/v1/admin/panels/${p.id}/resource-manifests`)).body;
console.log('REVISIONS      :',JSON.stringify(revs.map(r=>({v:r.manifestVersion,st:r.workflowStatus,active:r.active}))));
const arts=(await s.json(`/api/v1/admin/panels/${p.id}/artifacts`)).body;
console.log('ARTIFACTS      :',JSON.stringify(arts.map(a=>({v:a.artifact_version,active:a.active}))));
const b=await userB();
console.log('USER B CONTEXT :',JSON.stringify(summarize(await b.json('/api/v1/me/manifest'))));
for(const [l,u] of [['forward','/api/proxy/e2e-hybrid-test-micro/api/test/whoami'],['legacy','/api/proxy/e2e-hybrid-legacy/api/test/whoami']]){
  const r=await b.json(u); console.log('ROUTE '+l.padEnd(9),':',r.status,(r.body&&r.body.authMode)||'');
}
