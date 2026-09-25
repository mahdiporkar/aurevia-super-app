import {grantTo,revokeAll,ctxB,drain,ids} from './grant.mjs';
for(const [name,res] of [['B: module only','module'],['C: page A only','pageA'],['D: section A1 only','sectionA1']]){
  await revokeAll(); await drain();
  const g=await grantTo(ids[res]);
  await drain();
  const c=await ctxB();
  console.log('=== SCENARIO '+name+' (grant '+g.status+') ===');
  console.log('panels:',JSON.stringify(c.panels));
  console.log('permissions:',JSON.stringify(Object.keys(c.permDetail).map(k=>k.replace('e2e-hybrid-test-micro',''))));
  console.log('uiRoutes:',JSON.stringify(c.uiModules.map(m=>m.routes)));
  console.log('uiNav:',JSON.stringify(c.uiModules.map(m=>m.nav)));
}
