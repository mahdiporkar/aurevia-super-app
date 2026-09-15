import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {request as httpsRequest} from 'node:https';
import {Session,safeResponse} from './e2e-auth/session.mjs';
import {verifyNativeSupersetInChrome} from './superset-native-browser-e2e.mjs';

const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';assert.equal(new URL(origin).hostname,'localhost');
const runtime=JSON.parse(readFileSync('.tmp/superset-native/runtime.json','utf8'));
const registration=JSON.parse(readFileSync('.tmp/superset-native/registration.json','utf8'));
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const adminPassword=process.env.AUREVIA_DEMO_PASSWORD??realm.users.find(user=>user.username==='administrator').credentials[0].value;
let passwords={};try{passwords=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));}catch{}
const viewerUsername=registration.viewer.username;
const viewerPassword=process.env.AUREVIA_SUPERSET_VIEWER_PASSWORD??passwords[viewerUsername]??realm.users.find(user=>user.username===viewerUsername)?.credentials?.[0]?.value;
const deniedUsername=process.env.AUREVIA_SUPERSET_DENIED_USER??(passwords['e2e.none']?'e2e.none':realm.users.find(user=>
  user.username!=='administrator'&&user.username!==viewerUsername)?.username);
const deniedPassword=process.env.AUREVIA_SUPERSET_DENIED_PASSWORD??passwords[deniedUsername]??realm.users.find(user=>user.username===deniedUsername)?.credentials?.[0]?.value;
const ca=readFileSync('.tmp/superset-native/tls/ca.pem');
const identity={ca,cert:readFileSync('.tmp/superset-native/tls/client.pem'),key:readFileSync('.tmp/superset-native/tls/client-key.pem')};
const results=[],evidence={};const codes=['NATIVE-PROCESSES','NATIVE-PUBLIC-HEALTH','NATIVE-OPERATION-HEALTH','NATIVE-MTLS-REJECT',
  'NATIVE-CERTIFICATE-VERIFY','PUBLIC-RUNTIME-REJECT','CORE-HEALTH','REGISTRY-MAPPING','REGISTRY-RESOURCES','BFF-HEALTH','ADMIN-REPORTS','VIEWER-REPORTS',
  'VIEWER-DASHBOARD','PUBLIC-ASSETS','BFF-QUERY','SUPERSET-CSRF-REJECT','DENIED-REPORTS','DENIED-DASHBOARD','DENIED-QUERY',
  'VIEWER-QUERY','UNGRANTED-CHART-REJECT','UNGRANTED-FAVORITE-REJECT','REVOKE-DASHBOARD','RESTORE-DASHBOARD','BROWSER-REPORT','NO-RAW-CREDENTIALS'];
async function test(id,expected,fn){try{const actual=await fn();results.push({id,expected,actual,status:'PASS'});console.log(id+' PASS');return actual;}
  catch(error){const actual=String(error.message).replaceAll(adminPassword,'[REDACTED]').slice(0,700);results.push({id,expected,actual,status:error.code==='E2E_DEPENDENCY'?'BLOCKED':'FAIL'});console.log(id+' '+results.at(-1).status);}}
function nativeRequest(url,tls={ca}){return new Promise((resolve,reject)=>{
  const request=httpsRequest(url,tls,response=>{response.resume();response.on('end',()=>resolve(response.statusCode));});
  request.setTimeout(5000,()=>request.destroy(new Error('Native HTTPS timeout')));request.on('error',reject);request.end();});}
const publicCode=registration.instances.public.code,operationCode=registration.instances.operation.code;
const prefix='/api/integrations/superset/'+publicCode;
const dashboard=registration.assets.find(asset=>asset.type==='DASHBOARD');
const admin=new Session(origin),viewer=new Session(origin),denied=new Session(origin);
async function api(path,method='GET',body){const response=await admin.json('/api/v1/admin'+path,method,body);assert([200,201,204].includes(response.status),path+' '+response.status);return response.body;}
async function dashboardStatus(expected){const started=Date.now();for(let i=0;i<60;i++){
  const r=await viewer.request(prefix+dashboard.path);await r.arrayBuffer();
  if(r.status===expected)return {httpStatus:expected,propagationMs:Date.now()-started};
  await new Promise(resolve=>setTimeout(resolve,250));
}throw new Error('Dashboard grant did not propagate through the outbox');}
function queryBody(){return {datasource:{id:registration.reports.datasetId,type:'table'},force:true,
  result_format:'json',result_type:'full',form_data:{slice_id:registration.reports.chartIds[1]},
  queries:[{columns:[],metrics:[{expressionType:'SIMPLE',column:{column_name:'amount',type:'BIGINT'},aggregate:'SUM',label:'Total Sales'}],
    filters:[],orderby:[],row_limit:100,time_range:'No filter',extras:{}}]};}
async function query(session,csrf=true){
  const token=await session.json(prefix+'/api/v1/security/csrf_token/');assert.equal(token.status,200,'Superset CSRF');
  const response=await session.request(prefix+'/api/v1/chart/data',{method:'POST',headers:{Accept:'application/json',
    'Content-Type':'application/json',...(csrf?{'X-CSRFToken':token.body.result}:{})},body:JSON.stringify(queryBody())});
  return {status:response.status,body:await response.json()};
}
let dashboardRevoked=false;
try{
  await test('NATIVE-PROCESSES','Both native Gunicorn processes are outside Docker cgroups',async()=>{
    const script='import json,pathlib; r=pathlib.Path('+JSON.stringify(runtime.nativeRoot)+'); print(json.dumps([{ "zone":z,"pid":int((r/z/"gunicorn.pid").read_text()),"cgroup":pathlib.Path("/proc/"+(r/z/"gunicorn.pid").read_text().strip()+"/cgroup").read_text().strip(),"analyticalAssets":{t:__import__("sqlite3").connect(r/z/"metadata.db").execute("select count(*) from "+t).fetchone()[0] for t in ("dashboards","slices","tables")} } for z in ("public","operation")]))';
    const processes=JSON.parse(process.platform==='win32'?execFileSync('wsl.exe',['-d',runtime.distro,'--exec','python3','-c',script],{encoding:'utf8'}):execFileSync('python3',['-c',script],{encoding:'utf8'}));
    for(const process of processes)assert(!/docker|kubepods|containerd/i.test(process.cgroup));
    assert(Object.values(processes.find(process=>process.zone==='public').analyticalAssets).every(count=>count===0));
    evidence.nativeProcesses=processes;return processes;
  });
  await test('NATIVE-PUBLIC-HEALTH','Independent public Superset HTTPS responds 200',async()=>{assert.equal(await nativeRequest(runtime.publicUrl+'/health'),200);return 200;});
  await test('NATIVE-OPERATION-HEALTH','Independent operation health requires BFF client identity',async()=>{assert.equal(await nativeRequest(runtime.operationUrl+'/health',identity),200);return 200;});
  await test('NATIVE-MTLS-REJECT','Operation rejects requests without client certificate',async()=>{await assert.rejects(nativeRequest(runtime.operationUrl+'/health'));return 'TLS handshake rejected';});
  await test('NATIVE-CERTIFICATE-VERIFY','Demo HTTPS certificate hostname is verified',async()=>{await assert.rejects(nativeRequest(runtime.publicUrl+'/health',{ca,servername:'wrong-native-host.invalid'}));return 'Hostname mismatch rejected';});
  await test('PUBLIC-RUNTIME-REJECT','Public instance cannot run dashboard or chart API',async()=>{assert.equal(await nativeRequest(runtime.publicUrl+'/superset/dashboard/1/'),404);assert.equal(await nativeRequest(runtime.publicUrl+'/api/v1/chart/data'),404);return 404;});
  await test('CORE-HEALTH','Core stays healthy independently of external Superset',async()=>{const r=await fetch(origin+'/actuator/health/readiness');assert.equal(r.status,200);return 200;});
  await admin.login('administrator',adminPassword);await viewer.login(viewerUsername,viewerPassword);
  assert(deniedUsername&&deniedPassword,'A local user without Superset grants is required');await denied.login(deniedUsername,deniedPassword);
  await test('REGISTRY-MAPPING','Default mapping resolves public integration to operation host',async()=>{const mappings=await api('/superset-instances/mappings');assert(mappings.some(mapping=>mapping.public_code===publicCode&&mapping.operation_code===operationCode&&mapping.is_default));return {publicCode,operationCode,default:true};});
  await test('REGISTRY-RESOURCES','Two integration resources and three assets persist with a valid database source',async()=>{const resources=await api('/resources');const keys=['application:'+publicCode,'application:'+operationCode];const created=resources.filter(resource=>keys.includes(resource.resource_key)||registration.assets.some(asset=>asset.resourceId===resource.id));assert.equal(created.length,5);for(const resource of created)assert.equal(resource.source,'ADMIN');return {registeredResources:5,source:'ADMIN'};});
  await test('BFF-HEALTH','BFF connects to native operation health with verified mTLS',async()=>{const r=await admin.json(prefix+'/health');assert.equal(r.status,200);assert.equal(r.body.status,'ACTIVE');return {httpStatus:200,status:r.body.status};});
  await test('ADMIN-REPORTS','Administrator sees all three registered native reports',async()=>{const r=await admin.json('/api/v1/reports');assert.equal(r.status,200);assert.equal(r.body.filter(asset=>asset.instance_code===operationCode).length,3);return 3;});
  await test('VIEWER-REPORTS','Viewer sees only the three granted reports, with same-origin URLs',async()=>{const r=await viewer.json('/api/v1/reports');assert.equal(r.status,200);assert.equal(r.body.length,3);for(const asset of r.body)assert.equal(new URL(asset.url_path,origin).origin,origin);return 3;});
  let html;
  await test('VIEWER-DASHBOARD','Viewer dashboard loads through BFF and Remote User SSO',async()=>{const r=await viewer.request(prefix+dashboard.path);html=await r.text();assert.equal(r.status,200);assert(!/input[^>]+name=["']password/.test(html));return 200;});
  await test('PUBLIC-ASSETS','BFF serves dashboard JavaScript from the independent public instance',async()=>{
    const asset=[...html.matchAll(/<script[^>]+src=["']([^"']+)["']/g)].map(match=>match[1]).find(path=>path.includes('/static/'));
    assert(asset,'No compiled Superset script in dashboard HTML');const before=JSON.parse(readFileSync('.tmp/superset-native/counters.json','utf8'));
    const r=await viewer.request(new URL(asset,origin));assert.equal(r.status,200);await r.arrayBuffer();
    const after=JSON.parse(readFileSync('.tmp/superset-native/counters.json','utf8'));assert(after.public.static>before.public.static);return {httpStatus:200,publicStaticRequests:after.public.static-before.public.static};});
  await test('BFF-QUERY','Chart data query is executed on operational Superset through BFF',async()=>{await admin.request(prefix+dashboard.path);const before=JSON.parse(readFileSync('.tmp/superset-native/counters.json','utf8'));const r=await query(admin);assert.equal(r.status,200);assert.equal(r.body.result[0].data[0]['Total Sales'],1150);const after=JSON.parse(readFileSync('.tmp/superset-native/counters.json','utf8'));assert(after.operation.chartData>before.operation.chartData);return {httpStatus:200,total:1150,operationQueryRequests:after.operation.chartData-before.operation.chartData};});
  await test('SUPERSET-CSRF-REJECT','Operational Superset rejects a query without its own CSRF',async()=>{const r=await query(admin,false);assert([400,403].includes(r.status));return r.status;});
  await test('DENIED-REPORTS','User without BI grants gets an empty report catalog',async()=>{const r=await denied.json('/api/v1/reports');assert.equal(r.status,200);assert.deepEqual(r.body,[]);return [];});
  await test('DENIED-DASHBOARD','Knowing the dashboard URL cannot bypass BFF grants',async()=>{const r=await denied.request(prefix+dashboard.path);assert.equal(r.status,403);await r.arrayBuffer();return 403;});
  await test('DENIED-QUERY','Chart data cannot be queried by an ungranted user',async()=>{const r=await denied.request(prefix+'/api/v1/chart/data',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(queryBody())});assert.equal(r.status,403);await r.arrayBuffer();return 403;});
  await test('VIEWER-QUERY','Granted viewer can run the matching chart data query',async()=>{const r=await query(viewer);assert.equal(r.status,200);assert.equal(r.body.result[0].data[0]['Total Sales'],1150);return {httpStatus:200,total:1150};});
  await test('UNGRANTED-CHART-REJECT','A known dataset cannot bypass an ungranted chart ID',async()=>{const payload=queryBody();payload.form_data.slice_id=99999;const r=await viewer.request(prefix+'/api/v1/chart/data',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});assert.equal(r.status,403);await r.arrayBuffer();return 403;});
  await test('UNGRANTED-FAVORITE-REJECT','Favorite status cannot include an ungranted dashboard ID',async()=>{const q=encodeURIComponent('!('+dashboard.externalId+',99999)');const r=await viewer.request(prefix+'/api/v1/dashboard/favorite_status/?q='+q);assert.equal(r.status,403);await r.arrayBuffer();return 403;});
  await test('REVOKE-DASHBOARD','Revoking the dashboard grant takes effect after outbox propagation',async()=>{const grants=await api('/superset-assets/'+dashboard.id+'/grants');const grant=grants.find(grant=>grant.subject_id===registration.viewer.userId&&grant.action_key==='view');assert(grant);await api('/superset-assets/'+dashboard.id+'/grants/'+grant.id,'DELETE');dashboardRevoked=true;return dashboardStatus(403);});
  await test('RESTORE-DASHBOARD','Restoring the view grant restores BFF access after outbox propagation',async()=>{const grant=await api('/superset-assets/'+dashboard.id+'/grants','POST',{subjectType:'USER',subjectId:registration.viewer.userId,level:'VIEW'});dashboardRevoked=false;registration.grants.find(item=>item.assetId===dashboard.id).grantId=grant.id??grant.grantId;writeFileSync('.tmp/superset-native/registration.json',JSON.stringify(safeResponse(registration,[adminPassword,viewerPassword]),null,2)+'\n');return dashboardStatus(200);});
  await test('BROWSER-REPORT','Real Chrome renders the catalog dashboard URL and fetches real chart data through BFF',async()=>{const browser=await verifyNativeSupersetInChrome({origin,username:viewerUsername,password:viewerPassword,path:dashboard.path});evidence.browser=browser;return browser;});
  await test('NO-RAW-CREDENTIALS','Evidence contains no OAuth tokens, passwords, or private keys',async()=>{safeResponse({registration,evidence},[adminPassword,viewerPassword,deniedPassword]);assert(!/-----BEGIN[^\n]*PRIVATE KEY/.test(JSON.stringify({registration,evidence})));return 'No raw credentials';});
}catch{for(const id of codes)if(!results.some(result=>result.id===id))results.push({id,expected:'Run native Superset demo test',actual:'Local runtime/login prerequisite unavailable',status:'BLOCKED'});}
finally{if(dashboardRevoked)await api('/superset-assets/'+dashboard.id+'/grants','POST',{subjectType:'USER',subjectId:registration.viewer.userId,level:'VIEW'});}
const counts=Object.fromEntries(['PASS','FAIL','BLOCKED'].map(status=>[status,results.filter(result=>result.status===status).length]));
mkdirSync('target/superset-native',{recursive:true});writeFileSync('target/superset-native/results.json',JSON.stringify(safeResponse({runAt:new Date().toISOString(),counts,results,evidence},[adminPassword,viewerPassword,deniedPassword]),null,2)+'\n');
console.log(JSON.stringify(counts));if(counts.FAIL||counts.BLOCKED)process.exitCode=1;
