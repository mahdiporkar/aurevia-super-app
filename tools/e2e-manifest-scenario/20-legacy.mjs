import {admin,state,save} from './lib.mjs';
const s=await admin(); const st=state();
const c=await s.json('/api/v1/admin/outbound-connections','POST',{
  connectionRef:'connection://e2emanual/legacy',name:'E2E Manual Legacy Endpoint',
  baseUrl:'http://test-legacy-service:8092',tlsRequired:false,active:true,version:0});
console.log('CREATE connection ->',c.status,JSON.stringify(c.body).slice(0,150));
const p=await s.json('/api/v1/admin/outbound-auth-profiles','POST',{
  code:'e2e-manual-legacy',name:'E2E Manual Legacy Profile',description:'legacy login + bearer token',
  authMode:'LEGACY_SERVICE_TOKEN',tokenConnectionRef:'connection://e2emanual/legacy',
  tokenEndpointPath:'/auth/token',requestFormat:'FORM_URLENCODED',
  credentialSecretRef:'secret://e2emanual/legacy',scope:null,audience:null,
  tokenResponsePointer:'/access_token',expiresInResponsePointer:'/expires_in',
  tokenTypeResponsePointer:'/token_type',authorizationScheme:'Bearer',
  credentialTransport:'INTERNAL_LEGACY_HEADER',expirySkewSeconds:30,
  connectTimeoutMs:3000,responseTimeoutMs:10000,maxTokenResponseSize:65536,active:true});
console.log('CREATE profile ->',p.status,JSON.stringify(p.body).slice(0,200));
save({...st,legacyProfileId:p.body?.id});
