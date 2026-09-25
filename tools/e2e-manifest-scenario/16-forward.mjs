import {admin,state,save} from './lib.mjs';
const s=await admin(); const st=state(); const P=st.panelId;
const profiles=await s.json('/api/v1/admin/outbound-auth-profiles');
const fwd=profiles.body.find(p=>p.auth_mode==='FORWARD_USER_TOKEN'&&p.code==='public-iam-forward');
console.log('forward profile:',fwd.code,fwd.id);
const t=await s.json('/api/v1/admin/service-targets','POST',{
  code:'e2e-fwd-target',name:'E2E Forward Token Upstream',description:'test-sso-service via gateway',
  gatewayBaseUrl:'http://operation-gateway:80',upstreamBasePath:'/test-sso-service',environment:'DEMO',
  healthCheckPath:'/health',connectTimeoutMs:3000,responseTimeoutMs:10000,maxResponseSize:1048576,
  outboundAuthProfileId:fwd.id,active:true});
console.log('CREATE target ->',t.status,JSON.stringify(t.body).slice(0,160));
const targetId=t.body.id;
const r=await s.json('/api/v1/admin/proxy-routes','POST',{
  code:'e2e-fwd-route',panelId:P,serviceTargetId:targetId,outboundAuthProfileId:fwd.id,
  serviceSlug:'e2e-hybrid-test-micro',pathPrefix:'/api/proxy/e2e-hybrid-test-micro',stripPrefix:0,
  rewritePattern:null,rewriteReplacement:null,priority:100,allowedMethods:['GET','POST'],
  preserveHost:false,retryEnabled:false,maxRetries:0,active:true});
console.log('CREATE route ->',r.status,JSON.stringify(r.body).slice(0,260));
save({...st,fwdTargetId:targetId,fwdRouteId:r.body?.id,fwdProfileId:fwd.id});
