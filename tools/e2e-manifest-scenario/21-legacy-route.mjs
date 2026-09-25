import {admin,state,save} from './lib.mjs';
const s=await admin(); const st=state();
const t=await s.json('/api/v1/admin/service-targets','POST',{
  code:'e2e-legacy-target',name:'E2E Legacy Upstream',description:'test-legacy-service via gateway',
  gatewayBaseUrl:'http://operation-gateway:80',upstreamBasePath:'/test-legacy-service',environment:'DEMO',
  healthCheckPath:'/health',connectTimeoutMs:3000,responseTimeoutMs:10000,maxResponseSize:1048576,
  outboundAuthProfileId:st.legacyProfileId,active:true});
console.log('CREATE legacy target ->',t.status,t.body?.id);
const r=await s.json('/api/v1/admin/proxy-routes','POST',{
  code:'e2e-legacy-route',panelId:st.panelId,serviceTargetId:t.body.id,outboundAuthProfileId:st.legacyProfileId,
  serviceSlug:'e2e-hybrid-legacy',pathPrefix:'/api/proxy/e2e-hybrid-legacy',stripPrefix:3,
  rewritePattern:null,rewriteReplacement:null,priority:100,allowedMethods:['GET'],
  preserveHost:false,retryEnabled:false,maxRetries:0,active:true});
console.log('CREATE legacy route ->',r.status,r.body?.id);
const o=await s.json(`/api/v1/admin/proxy-routes/${r.body.id}/operations`,'POST',
  {httpMethod:'GET',pathPattern:'/api/test/whoami',resourceKey:'page:e2e-hybrid-test-micro.page-a',
   actionKey:'view',authorizationRequired:true,dataPolicyKey:null,active:true,maxBodyBytes:65536});
console.log('CREATE legacy op ->',o.status);
save({...st,legacyTargetId:t.body.id,legacyRouteId:r.body.id});
