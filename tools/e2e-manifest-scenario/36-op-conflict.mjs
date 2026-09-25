import {admin,state} from './lib.mjs';
const s=await admin(); const st=state();
const r=await s.json(`/api/v1/admin/proxy-routes/${st.fwdRouteId}/operations`,'POST',
 {httpMethod:'GET',pathPattern:'/api/test/whoami',resourceKey:'page:e2e-hybrid-test-micro.page-b',
  actionKey:'view',authorizationRequired:true,dataPolicyKey:null,active:true,maxBodyBytes:1024});
console.log('duplicate operation (same method+path) ->',r.status,JSON.stringify(r.body).slice(0,160));
const amb=await s.json('/api/v1/admin/proxy-routes/resolve-test','POST',{method:'GET',path:'/api/proxy/e2e-hybrid-test-micro/api/test/whoami'});
console.log('resolve still deterministic ->',amb.status,JSON.stringify(amb.body?.routeKey??amb.body).slice(0,80));
