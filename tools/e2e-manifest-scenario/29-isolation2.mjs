import {userB} from './lib2.mjs';
const b=await userB();
// Select the instance the way the Reports UI does, then request dashboards directly.
const sel=await b.request('/api/v1/superset-instances/e2e-superset-public/superset/dashboard/11/');
console.log('instance select ->',sel.status);
for(const [label,d] of [['GRANTED   dash 11','11'],['DENIED    dash 1','1'],['DENIED    dash 7','7']]){
  const r=await b.request(`/api/v1/superset/superset/dashboard/${d}/`);
  console.log(label,'->',r.status);
}
// API-level access to an unauthorized dashboard's data
for(const d of ['11','1']){
  const r=await b.request(`/api/v1/superset/api/v1/dashboard/${d}`,{headers:{Accept:'application/json'}});
  console.log('api/v1/dashboard/'+d,'->',r.status);
}
