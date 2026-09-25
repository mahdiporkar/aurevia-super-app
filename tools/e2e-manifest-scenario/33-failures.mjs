import {admin,state} from './lib.mjs';
const s=await admin(); const st=state(); const P=st.panelId;
const show=(l,r)=>console.log(l.padEnd(42),'->',r.status,JSON.stringify(r.body).slice(0,120));
// --- Manifest failures ---
show('invalid manifest (bad JSON shape)',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts`,'POST',{nope:true}));
show('duplicate resource keys',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts`,'POST',
 {schemaVersion:'1.0',module:{key:'e2e-hybrid-test-micro',name:'x',version:'9.9.9'},
  resources:[{key:'page:e2e-hybrid-test-micro.dup',type:'PAGE',actions:['view']},{key:'page:e2e-hybrid-test-micro.dup',type:'PAGE',actions:['view']}]}));
show('malformed hierarchy (missing parent)',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts`,'POST',
 {schemaVersion:'1.0',module:{key:'e2e-hybrid-test-micro',name:'x',version:'9.9.8'},
  resources:[{key:'component:e2e-hybrid-test-micro.orphan',type:'UI_COMPONENT',parent:'page:does-not-exist',actions:['view']}]}));
show('unknown action in manifest',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts`,'POST',
 {schemaVersion:'1.0',module:{key:'e2e-hybrid-test-micro',name:'x',version:'9.9.7'},
  resources:[{key:'page:e2e-hybrid-test-micro.bad',type:'PAGE',actions:['not-a-real-action']}]}));
show('conflicting version (same v, diff content)',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/drafts`,'POST',
 {schemaVersion:'1.0',module:{key:'e2e-hybrid-test-micro',name:'x',version:'1.0.0'},
  resources:[{key:'page:e2e-hybrid-test-micro.other',type:'PAGE',actions:['view']}]}));
show('activate a non-existent revision',await s.json(`/api/v1/admin/panels/${P}/resource-manifests/00000000-0000-0000-0000-000000000000/activate`,'POST'));
// --- Route/service failures ---
show('route to unknown target',await s.json('/api/v1/admin/proxy-routes','POST',
 {code:'bad-route',panelId:P,serviceTargetId:'00000000-0000-0000-0000-000000000000',
  outboundAuthProfileId:st.fwdProfileId,serviceSlug:'x',pathPrefix:'/api/proxy/bad',stripPrefix:0,
  rewritePattern:null,rewriteReplacement:null,priority:1,allowedMethods:['GET'],preserveHost:false,
  retryEnabled:false,maxRetries:0,active:true}));
show('duplicate route path prefix',await s.json('/api/v1/admin/proxy-routes','POST',
 {code:'dup-route',panelId:P,serviceTargetId:st.fwdTargetId,outboundAuthProfileId:st.fwdProfileId,
  serviceSlug:'e2e-hybrid-test-micro',pathPrefix:'/api/proxy/e2e-hybrid-test-micro',stripPrefix:3,
  rewritePattern:null,rewriteReplacement:null,priority:100,allowedMethods:['GET'],preserveHost:false,
  retryEnabled:false,maxRetries:0,active:true}));
// --- Superset failures ---
show('superset instance invalid base url',await s.json('/api/v1/admin/superset-instances','POST',
 {code:'e2e-bad-superset',name:'bad',zone:'OPERATION',baseUrl:'not-a-url',connectionRef:null,
  authMode:'REMOTE_USER',tlsRequired:false,active:true,proxyMode:true,metadata:{},version:0}));
show('duplicate external asset',await s.json('/api/v1/admin/superset-assets','POST',
 {externalId:'11',assetType:'DASHBOARD',title:'dup',urlPath:'/superset/dashboard/11/',
  ownerExternalId:null,published:true,instanceCode:'e2e-superset-operation'}));
