import {admin,show,state,save} from './lib.mjs';
import {grantTo,revokeAll,ctxB,drain,ids} from './grant.mjs';
const s=await admin(); const st=state(); const P=st.panelId;
// Keep a stable grant on page-a to prove grants survive versioning.
await revokeAll(); await drain(); await grantTo(ids.pageA); await drain();
console.log('PRE-V2 ctx:',JSON.stringify((await ctxB()).permKeys));
const f=show('fetch v2',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/fetch`,'POST'));
const rev2=f.body.id;
console.log('v2 changes:',JSON.stringify(f.body.changes?.map(c=>c.changeType+':'+c.resourceKey.replace('e2e-hybrid-test-micro',''))));
show('publish v2',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts/${rev2}/publish`,'POST'));
show('MF sync v2',await s.json(`/api/v1/admin/panels/${P}/frontend-manifests/sync`,'POST'));
await drain();
save({...st,v2RevisionId:rev2});
console.log('POST-V2 ctx:',JSON.stringify(await ctxB(),null,1));
