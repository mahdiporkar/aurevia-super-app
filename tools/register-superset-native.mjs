import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {Session,safeResponse} from './e2e-auth/session.mjs';

const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';
assert.equal(new URL(origin).hostname,'localhost','Native demo registration is restricted to local Aurevia');
const runtime=JSON.parse(readFileSync('.tmp/superset-native/runtime.json','utf8'));
const reports=JSON.parse(readFileSync('.tmp/superset-native/reports.json','utf8'));
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const password=process.env.AUREVIA_DEMO_PASSWORD??realm.users.find(user=>user.username==='administrator').credentials[0].value;
const admin=new Session(origin);await admin.login('administrator',password);
async function api(path,method='GET',body){
  const response=await admin.json('/api/v1/admin'+path,method,body);
  assert([200,201,204].includes(response.status),'Native Superset admin API failed: '+path+' '+response.status
    +(response.status>=400?' '+JSON.stringify(safeResponse(response.body,[password])):''));
  return response.body;
}
const existing=await api('/superset-instances');
const previousMappings=await api('/superset-instances/mappings');
if(!existsSync('.tmp/superset-native/registry-before.json'))writeFileSync('.tmp/superset-native/registry-before.json',
  JSON.stringify({instances:existing,mappings:previousMappings},null,2)+'\n');
const instances={};
for(const [zone,code,url]of [['PUBLIC','superset-native-public',runtime.publicUrl],['OPERATION','superset-native-operation',runtime.operationUrl]]){
  const current=existing.find(instance=>instance.code===code);
  const payload={code,name:zone==='PUBLIC'?'Superset عمومی دمو شبکه':'Superset عملیاتی دمو شبکه',zone,
    baseUrl:url,authMode:'REMOTE_USER',tlsRequired:true,active:true,proxyMode:true,
    metadata:{environment:'native-network-demo',outsideContainers:true,staticOnly:zone==='PUBLIC'},version:current?.version??0};
  const created=await api('/superset-instances'+(current?'/'+current.id:''),current?'PUT':'POST',payload);
  instances[zone.toLowerCase()]={id:created.id,code,url};
}
const mapping=await api('/superset-instances/mappings','POST',{
  publicInstanceId:instances.public.id,operationInstanceId:instances.operation.id,publicPath:'/superset-native',isDefault:true,active:true});
const assets=[];
for(const [type,id,title,path]of [
  ['DASHBOARD',reports.dashboardId,'Aurevia Native BI Demo','/superset/dashboard/'+reports.dashboardId+'/'],
  ['CHART',reports.chartIds[0],'Aurevia Department Sales','/explore/?slice_id='+reports.chartIds[0]],
  ['CHART',reports.chartIds[1],'Aurevia Total Sales','/explore/?slice_id='+reports.chartIds[1]],
]){
  const asset=await api('/superset-assets','POST',{externalId:String(id),assetType:type,title,urlPath:path,
    published:true,instanceCode:instances.operation.code});
  assets.push({id:asset.id,resourceId:asset.resourceId,externalId:String(id),type,path});
}
let passwords={};try{passwords=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));}catch{}
const viewerUsername=process.env.AUREVIA_SUPERSET_VIEWER??(passwords['e2e.dual-access']?'e2e.dual-access':
  realm.users.find(user=>user.username!=='administrator')?.username);
const viewerPassword=process.env.AUREVIA_SUPERSET_VIEWER_PASSWORD??passwords[viewerUsername]??
  realm.users.find(user=>user.username===viewerUsername)?.credentials?.[0]?.value;
assert(viewerUsername&&viewerPassword,'Configure a local non-admin Superset demo viewer');
const viewer=new Session(origin);const identity=await viewer.login(viewerUsername,viewerPassword);
const users=await api('/users');const user=users.find(user=>user.issuer===identity.issuer&&user.external_id===identity.subject);
assert(user,'The Superset demo viewer was not synchronized to the user registry');
const grants=[];
for(const asset of assets){
  const current=await api('/superset-assets/'+asset.id+'/grants');
  let grant=current.find(grant=>grant.subject_type==='USER'&&grant.subject_id===user.id&&grant.action_key==='view');
  if(!grant)grant=await api('/superset-assets/'+asset.id+'/grants','POST',{subjectType:'USER',subjectId:user.id,level:'VIEW'});
  grants.push({assetId:asset.id,grantId:grant.id??grant.grantId});
}
// The Reports MFE itself needs view permission in addition to the three BI assets.
const resources=await api('/resources');const actions=await api('/actions');const existingGrants=await api('/users/'+user.id+'/grants');
const reportsResource=resources.find(resource=>resource.resource_key==='application:aurevia/reports');
const view=actions.find(action=>action.action_key==='view');
if(reportsResource&&view&&!existingGrants.some(grant=>grant.resource_id===reportsResource.id&&grant.action_key==='view'&&grant.status==='ACTIVE'))
  await api('/grants','POST',{subjectType:'USER',subjectId:user.id,resourceId:reportsResource.id,actionId:view.id});
const result=safeResponse({registeredAt:new Date().toISOString(),instances,mappingId:mapping.id,assets,grants,
  viewer:{username:viewerUsername,userId:user.id,subject:identity.subject,issuer:identity.issuer},reports},[password,viewerPassword]);
writeFileSync('.tmp/superset-native/registration.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({instances:[instances.public.code,instances.operation.code],registeredAssets:assets.length,
  viewer:viewerUsername,defaultMapping:true,credentialsPrinted:false}));
