import {admin,state} from './lib.mjs';
const s=await admin(); const st=state();
for(const [m,p,res,act] of [['GET','/api/test/whoami','page:e2e-hybrid-test-micro.page-a','view'],
                            ['GET','/api/test/data','page:e2e-hybrid-test-micro.page-b','view']]){
  const r=await s.json(`/api/v1/admin/proxy-routes/${st.fwdRouteId}/operations`,'POST',
    {httpMethod:m,pathPattern:p,resourceKey:res,actionKey:act,authorizationRequired:true,dataPolicyKey:null,active:true,maxBodyBytes:65536});
  console.log('OP',m,p,'->',r.status,JSON.stringify(r.body).slice(0,140));
}
