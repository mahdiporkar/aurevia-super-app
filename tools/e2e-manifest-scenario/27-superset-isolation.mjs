import {userB} from './lib2.mjs';
const b=await userB();
for(const [label,d] of [['GRANTED dashboard 11','11'],['NOT granted dashboard 1','1'],['NOT granted demo dash 7','7']]){
  const r=await b.request(`/api/v1/superset/superset/dashboard/${d}/`,{headers:{Accept:'text/html'}});
  console.log(label.padEnd(26),'->',r.status);
}
