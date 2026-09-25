import {admin,state} from './lib.mjs';
const s=await admin(); const st=state();
const r0=await s.json(`/api/v1/admin/proxy-routes/${st.fwdRouteId}`);
console.log('preview BEFORE:',JSON.stringify(await s.json('/api/v1/admin/proxy-routes/preview','POST',
  {routeId:st.fwdRouteId,path:'/api/proxy/e2e-hybrid-test-micro/api/test/whoami'})));
const upd=await s.json(`/api/v1/admin/proxy-routes/${st.fwdRouteId}?version=${r0.body.version}`,'PUT',{
  code:'e2e-fwd-route',panelId:st.panelId,serviceTargetId:st.fwdTargetId,outboundAuthProfileId:st.fwdProfileId,
  serviceSlug:'e2e-hybrid-test-micro',pathPrefix:'/api/proxy/e2e-hybrid-test-micro',stripPrefix:3,
  rewritePattern:null,rewriteReplacement:null,priority:100,allowedMethods:['GET','POST'],
  preserveHost:false,retryEnabled:false,maxRetries:0,active:true});
console.log('UPDATE route ->',upd.status,'version',upd.body?.version);
console.log('preview AFTER :',JSON.stringify(await s.json('/api/v1/admin/proxy-routes/preview','POST',
  {routeId:st.fwdRouteId,path:'/api/proxy/e2e-hybrid-test-micro/api/test/whoami'})));
