import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { readEnv } from '../env-file.mjs';
import { Session, safeResponse } from './session.mjs';
import { verifyAuthMicrofrontendsInChrome } from './browser.mjs';

const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';
assert.equal(new URL(origin).hostname,'localhost','This provisioning runner is restricted to the local demo');
const env=readEnv('.env').values;
const userPasswords=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));
const legacySecret=JSON.parse(readFileSync('.tmp/e2e-auth/secrets/e2e/legacy.json','utf8'));
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const adminPassword=process.env.AUREVIA_DEMO_PASSWORD??realm.users.find(user=>user.username==='administrator').credentials[0].value;
const core=['compose','--env-file','.env','-f','infra/docker-compose/compose.yml','-f','infra/docker-compose/compose.e2e-auth-core.yml'];
const demo=['compose','--env-file','.tmp/e2e-auth/demo.env','-f','infra/docker-compose/compose.e2e-auth.yml'];
const results=[];
const secrets=[legacySecret.password,adminPassword,...Object.values(userPasswords),
  ...[...env.entries()].filter(([key,value])=>/(PASSWORD|SECRET|KEY_BASE64)$/.test(key)&&value.length>5).map(([,value])=>value)];
const evidence={};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const cases=[
  'ADMIN-SSO','ADMIN-LEGACY','ADMIN-UNAUTHORIZED','AUTH-DUAL-SSO','AUTH-DUAL-LEGACY','AUTH-SSO-SSO','AUTH-SSO-LEGACY',
  'AUTH-LEGACY-SSO','AUTH-LEGACY-LEGACY','AUTH-NONE-SSO','AUTH-NONE-LEGACY',
  'SSO-DATA','LEGACY-DATA','ROUTE-UNKNOWN','ROUTE-METHOD','ROUTE-DISABLE','ROUTE-ENABLE',
  'ROUTE-WRONG-DESTINATION','ROUTE-CONFIG-CHANGE','ROUTE-CONFIG-RESTORE','ROUTE-UNAUTHENTICATED',
  'SSO-MISSING','SSO-INVALID','SSO-EXPIRED','SSO-LEGACY-CREDENTIAL',
  'LEGACY-MISSING','LEGACY-INVALID','LEGACY-SSO-CREDENTIAL','LEGACY-TOKEN-ENDPOINT',
  'LEGACY-CACHE','LEGACY-MISSING-SECRET','LEGACY-RESTORE-SECRET',
  'SECURITY-RESPONSES','SECURITY-LOGS','SECURITY-SERVER-SESSION','SECURITY-NETWORK-ISOLATION','AUDIT-CORRELATION',
  'BROWSER-DUAL','BROWSER-SSO-ONLY','BROWSER-LEGACY-ONLY','BROWSER-NONE'
];
function diagnostic(error) {
  let message=String(error.message??error.name).slice(0,1000);
  for(const secret of secrets)message=message.replaceAll(secret,'[REDACTED]');
  return message.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|legacy_[A-Za-z0-9_-]{30,}/g,'[REDACTED]');
}
function command(args,options={}) {
  const result=spawnSync('docker',args,{encoding:'utf8',shell:false,windowsHide:true,timeout:30000,...options});
  assert.equal(result.status,0,'Docker test dependency unavailable');
  return result.stdout;
}
async function test(id,expected,fn) {
  try {
    const actual=await fn();
    results.push({id,expected,actual,status:'PASS'});
    console.log(id+' PASS');
    return true;
  } catch(error) {
    // Never serialize HTTP requests, browser/CDP events, assertion operands or secrets.
    const status=error.code==='E2E_DEPENDENCY'?'BLOCKED':'FAIL';
    results.push({id,expected,actual:diagnostic(error),status});
    console.log(id+' '+status+' ('+(error.code??error.name)+')');
    return false;
  }
}
async function adminApi(session,path,method='GET',body) {
  const response=await session.json('/api/v1/admin'+path,method,body);
  if(![200,201,204].includes(response.status)) {
    console.log('Admin operation rejected: '+method+' '+path.split('?')[0]+' HTTP '+response.status);
    throw new Error('Admin API rejected');
  }
  return response.body;
}
function internal(url,{method='GET',bearer,form}={}) {
  const config=['url = '+JSON.stringify(url),'request = '+JSON.stringify(method),'silent','show-error',
    'write-out = "\\n%{http_code}"'];
  if(bearer)config.push('header = '+JSON.stringify('Authorization: Bearer '+bearer));
  if(form)for(const [key,value] of Object.entries(form))config.push('data-urlencode = '+JSON.stringify(key+'='+value));
  // The SSO API remains isolated from the BFF's token-endpoint network.
  // A one-shot trusted-network probe tests the resource server directly.
  const args=url.startsWith('http://test-sso-service:')
    ? ['run','--rm','-i','--network','aurevia_operation-services','--entrypoint','curl','aurevia/superapp-bff:local','--config','-']
    : [...core,'exec','-T','aurevia-bff','curl','--config','-'];
  const output=command(args,{input:config.join('\n')+'\n'});
  const i=output.lastIndexOf('\n');
  let body;try{body=JSON.parse(output.slice(0,i));}catch{body=null;}
  return {status:Number(output.slice(i+1)),body};
}
async function keycloak(path,{method='GET',token,body,form}={}) {
  const response=await fetch('http://localhost:8180'+path,{method,signal:AbortSignal.timeout(15000),
    headers:{...(token?{Authorization:'Bearer '+token}:{}),...(form?{'Content-Type':'application/x-www-form-urlencoded'}:body?{'Content-Type':'application/json'}:{})},
    ...(form?{body:new URLSearchParams(form)}:body?{body:JSON.stringify(body)}:{})});
  assert([200,201,204].includes(response.status),'Keycloak admin dependency rejected');
  if(response.status===204||response.status===201)return null;
  return response.json();
}
async function provisionUsers() {
  const login=await keycloak('/realms/master/protocol/openid-connect/token',{method:'POST',form:{
    client_id:'admin-cli',grant_type:'password',username:env.get('KEYCLOAK_ADMIN')??'admin',password:env.get('KEYCLOAK_ADMIN_PASSWORD')??'change-me'}});
  const token=login.access_token;secrets.push(token);
  for(const [username,password] of Object.entries(userPasswords)) {
    const users=await keycloak('/admin/realms/aurevia/users?exact=true&username='+encodeURIComponent(username),{token});
    if(!users.length)await keycloak('/admin/realms/aurevia/users',{method:'POST',token,body:{
      username,enabled:true,firstName:'E2E',lastName:username,email:username+'@aurevia.local',emailVerified:true,
      credentials:[{type:'password',value:password,temporary:false}]}});
    else await keycloak('/admin/realms/aurevia/users/'+users[0].id+'/reset-password',{method:'PUT',token,body:{type:'password',value:password,temporary:false}});
  }
  const clients=await keycloak('/admin/realms/aurevia/clients?clientId=e2e-token-negative',{token});
  const client={clientId:'e2e-token-negative',enabled:true,publicClient:true,directAccessGrantsEnabled:true,
    standardFlowEnabled:false,attributes:{'access.token.lifespan':'10'}};
  if(!clients.length)await keycloak('/admin/realms/aurevia/clients',{method:'POST',token,body:client});
  else await keycloak('/admin/realms/aurevia/clients/'+clients[0].id,{method:'PUT',token,body:client});
}
async function register(session,mode) {
  const key='test-'+mode, code='TEST_'+mode.toUpperCase();
  const port=mode==='sso'?3011:3012;
  const panels=await adminApi(session,'/panels');
  let panel=panels.find(panel=>panel.code===code);
  const request={code,nameFa:'آزمایش '+mode,nameEn:mode+' Test',description:'Independent authentication E2E fixture',
    slug:key,serviceSlug:key,remoteName:'aurevia_test_'+mode,defaultRouteId:'home',
    remoteEntry:'http://localhost:'+port+'/remoteEntry.js',exposedModule:'./plugin',routeBasePath:'/'+key,
    semanticVersion:'0.1.0',contractVersion:'1.0',resourceDefinitionMode:'MANIFEST',classification:'DEMO',
    mfManifestUrl:'http://localhost:'+port+'/mf-manifest.json',resourceManifestUrl:'http://localhost:'+port+'/resource-manifest.json',active:true,sortOrder:110};
  if(!panel)panel=await adminApi(session,'/panels','POST',request);
  else await adminApi(session,'/panels/'+panel.id+'?version='+panel.version,'PUT',request);
  const draft=await adminApi(session,'/panels/'+panel.id+'/resource-manifests/fetch','POST');
  if(draft.workflowStatus==='DRAFT')await adminApi(session,'/panels/'+panel.id+'/resource-manifests/drafts/'+draft.id+'/publish','POST');
  const sync=await adminApi(session,'/panels/'+panel.id+'/frontend-manifests/sync','POST');
  assert.equal(sync.status,'SUCCESS');
  if(mode==='legacy') {
    const connections=await adminApi(session,'/outbound-connections');
    if(!connections.some(connection=>connection.connection_ref==='connection://e2e/legacy'))
      await adminApi(session,'/outbound-connections','POST',{connectionRef:'connection://e2e/legacy',name:'E2E legacy',
        baseUrl:'http://test-legacy-service:8092',tlsRequired:false,active:true,version:0});
  }
  const profiles=await adminApi(session,'/outbound-auth-profiles');
  let profile=profiles.find(profile=>profile.code==='e2e-'+mode);
  const profileRequest={code:'e2e-'+mode,name:'E2E '+mode,authMode:mode==='sso'?'FORWARD_USER_TOKEN':'LEGACY_SERVICE_TOKEN',
    tokenConnectionRef:mode==='legacy'?'connection://e2e/legacy':null,tokenEndpointPath:mode==='legacy'?'/auth/token':null,
    requestFormat:'FORM_URLENCODED',credentialSecretRef:mode==='legacy'?'secret://e2e/legacy':null,
    tokenResponsePointer:'/access_token',expiresInResponsePointer:'/expires_in',tokenTypeResponsePointer:'/token_type',
    authorizationScheme:'Bearer',credentialTransport:mode==='legacy'?'INTERNAL_LEGACY_HEADER':'USER_AUTHORIZATION_HEADER',
    expirySkewSeconds:5,connectTimeoutMs:3000,responseTimeoutMs:5000,maxTokenResponseSize:16384,active:true};
  if(!profile)profile=await adminApi(session,'/outbound-auth-profiles','POST',profileRequest);
  else profile=await adminApi(session,'/outbound-auth-profiles/'+profile.id+'?version='+profile.version,'PUT',profileRequest);
  const targets=await adminApi(session,'/service-targets');
  let target=targets.find(target=>target.code==='e2e-'+mode);
  const targetRequest={code:'e2e-'+mode,name:key+'-service',gatewayBaseUrl:'http://operation-gateway:80',
    upstreamBasePath:'/'+key+'-service',environment:'DEMO',healthCheckPath:'/health',connectTimeoutMs:3000,
    responseTimeoutMs:10000,maxResponseSize:65536,outboundAuthProfileId:profile.id,active:true};
  if(!target)target=await adminApi(session,'/service-targets','POST',targetRequest);
  else target=await adminApi(session,'/service-targets/'+target.id+'?version='+target.version,'PUT',targetRequest);
  const routes=await adminApi(session,'/proxy-routes');
  let route=routes.find(route=>route.code==='e2e-'+mode);
  const routeRequest={code:'e2e-'+mode,panelId:panel.id,serviceTargetId:target.id,serviceSlug:key,pathPrefix:'/api/proxy/'+key,
    stripPrefix:0,rewritePattern:'^/api/proxy/'+key,rewriteReplacement:'/'+key+'-service',
    priority:100,allowedMethods:['GET'],preserveHost:false,retryEnabled:false,maxRetries:0,active:true};
  if(!route)route=await adminApi(session,'/proxy-routes','POST',routeRequest);
  else route=await adminApi(session,'/proxy-routes/'+route.id+'?version='+route.version,'PUT',routeRequest);
  const operations=await adminApi(session,'/proxy-routes/'+route.id+'/operations');
  for(const endpoint of ['whoami','data'])if(!operations.some(operation=>operation.path_pattern==='/api/test/'+endpoint))
    await adminApi(session,'/proxy-routes/'+route.id+'/operations','POST',{httpMethod:'GET',pathPattern:'/api/test/'+endpoint,
      resourceKey:'page:'+key+'.home',actionKey:'view',authorizationRequired:true,active:true,maxBodyBytes:0});
  return {panel,profile,target,route,routeRequest,profileRequest,targetRequest};
}
async function main() {
  command(['version','--format','{{.Server.Version}}']);
  const ready=await fetch(origin+'/api/v1/me',{signal:AbortSignal.timeout(5000)});
  assert.equal(ready.status,401,'Core nginx/BFF unavailable');
  await provisionUsers();
  const admin=new Session(origin);
  await admin.login('administrator',adminPassword);
  const registrations={};
  for(const mode of ['sso','legacy']) {
    const ok=await test('ADMIN-'+mode.toUpperCase(),'Register panel, publish resources, sync UI, create profile/target/route/operations',async()=>{
      registrations[mode]=await register(admin,mode);return 'Admin APIs successful';});
    assert(ok,'Admin provisioning prerequisite failed');
  }
  const sessions={};
  const access={'dual-access':['sso','legacy'],'sso-only':['sso'],'legacy-only':['legacy'],'none':[]};
  const resources=await adminApi(admin,'/resource-tree'),actions=await adminApi(admin,'/actions');
  const view=actions.find(action=>action.action_key==='view');
  for(const [suffix,modes] of Object.entries(access)) {
    const username='e2e.'+suffix,session=new Session(origin);
    const identity=await session.login(username,userPasswords[username]);sessions[suffix]=session;
    const users=await adminApi(admin,'/users');
    const user=users.find(user=>user.issuer===identity.issuer&&user.external_id===identity.subject);
    assert(user,'Canonical login synchronization did not register test identity');
    const existing=await adminApi(admin,'/users/'+user.id+'/grants');
    for(const grant of existing.filter(grant=>/test-(sso|legacy)/.test(grant.resource_key)&&grant.status==='ACTIVE'))
      await adminApi(admin,'/grants/'+grant.id,'DELETE');
    for(const mode of modes) {
      // Panel visibility is an application can_view check; API checks the page separately.
      for(const key of ['application:aurevia/test-'+mode,'page:test-'+mode+'.home']) {
        const resource=resources.find(resource=>resource.resource_key===key);assert(resource);
        await adminApi(admin,'/grants','POST',{subjectType:'USER',subjectId:user.id,resourceId:resource.id,actionId:view.id});
      }
    }
  }
  // Poll the real projected context instead of assuming a fixed outbox delay.
  for(let i=0;i<60;i++) {
    const context=await sessions['dual-access'].json('/api/me/context');
    if(context.status===200&&['test-sso','test-legacy'].every(key=>context.body.allowedApplications?.includes(key)))break;
    await delay(1000);
  }
  const diagnostics=[];
  for(const [suffix,modes] of Object.entries(access))for(const mode of ['sso','legacy']) {
    const id='AUTH-'+({'dual-access':'DUAL','sso-only':'SSO','legacy-only':'LEGACY','none':'NONE'}[suffix])+'-'+mode.toUpperCase();
    await test(id,modes.includes(mode)?'Visible in effective context + downstream 200':'Absent from context + backend 403',async()=>{
      const context=await sessions[suffix].json('/api/me/context');assert.equal(context.status,200);
      const visible=context.body.allowedApplications.includes('test-'+mode);
      assert.equal(visible,modes.includes(mode));
      const response=await sessions[suffix].json('/api/proxy/test-'+mode+'/api/test/whoami');
      assert.equal(response.status,modes.includes(mode)?200:403);
      if(response.status===200) {
        assert.equal(response.body.authMode,mode==='sso'?'SSO':'LEGACY');
        assert.equal(response.body.service,'test-'+mode+'-service');assert.equal(response.body.authenticated,true);
        assert.equal(response.body.correlationId,response.correlationId);
        if(mode==='sso') {assert.equal(response.body.subject,context.body.identity.subject);assert.equal(response.body.username,'e2e.'+suffix);}
        diagnostics.push(response);
      }
      if(suffix==='dual-access')evidence.context=context.body;
      return {contextVisible:visible,httpStatus:response.status,authMode:response.body?.authMode,correlationId:response.correlationId};
    });
  }
  const dual=sessions['dual-access'];
  await test('ADMIN-UNAUTHORIZED','403 for non-admin route creation',async()=>{
    const r=await sessions['sso-only'].json('/api/v1/admin/proxy-routes','POST',
      {...registrations.sso.routeRequest,code:'e2e-denied-admin'});
    assert.equal(r.status,403);return r.status;
  });
  for(const mode of ['sso','legacy'])await test(mode.toUpperCase()+'-DATA','200',async()=>{
    const response=await dual.json('/api/proxy/test-'+mode+'/api/test/data');assert.equal(response.status,200);return response.status;});
  await test('ROUTE-UNKNOWN','404',async()=>{const r=await dual.json('/api/proxy/not-registered/api/test/whoami');assert.equal(r.status,404);return r.status;});
  await test('ROUTE-METHOD','404 (no registered POST operation; CSRF included)',async()=>{const r=await dual.json('/api/proxy/test-sso/api/test/whoami','POST',{});assert.equal(r.status,404);return r.status;});
  const legacy=registrations.legacy,sso=registrations.sso;
  await test('ROUTE-DISABLE','404 without rebuild/restart',async()=>{
    legacy.route=await adminApi(admin,'/proxy-routes/'+legacy.route.id+'/status?version='+legacy.route.version,'PATCH',{active:false});
    try {const r=await dual.json('/api/proxy/test-legacy/api/test/whoami');assert.equal(r.status,404);return r.status;}
    finally {legacy.route=await adminApi(admin,'/proxy-routes/'+legacy.route.id+'/status?version='+legacy.route.version,'PATCH',{active:true});}
  });
  await test('ROUTE-ENABLE','200 without rebuild/restart',async()=>{const r=await dual.json('/api/proxy/test-legacy/api/test/whoami');assert.equal(r.status,200);return r.status;});
  await test('ROUTE-WRONG-DESTINATION','400 unapproved destination',async()=>{
    const r=await admin.json('/api/v1/admin/service-targets','POST',{...sso.targetRequest,code:'e2e-forbidden',gatewayBaseUrl:'http://169.254.169.254'});
    assert.equal(r.status,400);return r.status;});
  await test('ROUTE-CONFIG-CHANGE','404 after rewriting to nonexistent service path',async()=>{
    sso.route=await adminApi(admin,'/proxy-routes/'+sso.route.id+'?version='+sso.route.version,'PUT',{...sso.routeRequest,rewriteReplacement:'/missing-e2e-destination'});
    try {const r=await dual.json('/api/proxy/test-sso/api/test/whoami');assert.equal(r.status,404);return r.status;}
    finally {sso.route=await adminApi(admin,'/proxy-routes/'+sso.route.id+'?version='+sso.route.version,'PUT',sso.routeRequest);}
  });
  await test('ROUTE-CONFIG-RESTORE','200',async()=>{const r=await dual.json('/api/proxy/test-sso/api/test/whoami');assert.equal(r.status,200);return r.status;});
  await test('ROUTE-UNAUTHENTICATED','401',async()=>{const r=await new Session(origin).json('/api/proxy/test-sso/api/test/whoami');assert.equal(r.status,401);return r.status;});
  const tokenResponse=await keycloak('/realms/aurevia/protocol/openid-connect/token',{method:'POST',form:{
    grant_type:'password',client_id:'e2e-token-negative',username:'e2e.dual-access',password:userPasswords['e2e.dual-access']}});
  const ssoToken=tokenResponse.access_token;secrets.push(ssoToken);
  const legacyTokenResponse=internal('http://test-legacy-service:8092/auth/token',{method:'POST',form:{username:legacySecret.username,password:legacySecret.password}});
  assert.equal(legacyTokenResponse.status,200);const legacyToken=legacyTokenResponse.body.access_token;secrets.push(legacyToken);
  for(const [id,url,bearer] of [
    ['SSO-MISSING','http://test-sso-service:8091/api/test/whoami',undefined],
    ['SSO-INVALID','http://test-sso-service:8091/api/test/whoami',ssoToken.slice(0,ssoToken.lastIndexOf('.')+1)+'invalidsignature'],
    ['SSO-LEGACY-CREDENTIAL','http://test-sso-service:8091/api/test/whoami',legacyToken],
    ['LEGACY-MISSING','http://test-legacy-service:8092/api/test/whoami',undefined],
    ['LEGACY-INVALID','http://test-legacy-service:8092/api/test/whoami','legacy_invalid'],
    ['LEGACY-SSO-CREDENTIAL','http://test-legacy-service:8092/api/test/whoami',ssoToken]
  ])await test(id,'401',async()=>{const r=internal(url,{bearer});assert.equal(r.status,401);return r.status;});
  await test('LEGACY-TOKEN-ENDPOINT','401 incorrect service password',async()=>{const r=internal('http://test-legacy-service:8092/auth/token',{method:'POST',form:{username:legacySecret.username,password:'incorrect'}});assert.equal(r.status,401);return r.status;});
  await test('SSO-EXPIRED','401 for a real Keycloak-issued expired access token',async()=>{
    const claims=JSON.parse(Buffer.from(ssoToken.split('.')[1],'base64url').toString());
    while(Date.now()<=claims.exp*1000+1000)await delay(1000);
    const r=internal('http://test-sso-service:8091/api/test/whoami',{bearer:ssoToken});assert.equal(r.status,401);return r.status;});
  await test('LEGACY-CACHE','Server cache present after successful calls',async()=>{
    await adminApi(admin,'/outbound-auth-profiles/'+legacy.profile.id+'/invalidate-token','POST');
    for(let i=0;i<2;i++)assert.equal((await dual.json('/api/proxy/test-legacy/api/test/whoami')).status,200);
    const r=await adminApi(admin,'/outbound-auth-profiles/'+legacy.profile.id+'/cache-status');assert.equal(r.cached,true);
    const logs=command([...core,'logs','--no-color','aurevia-bff']);
    assert(logs.includes('cache=miss profile='+legacy.profile.id));assert(logs.includes('cache=hit profile='+legacy.profile.id));
    return {cached:true,missAndHit:true};});
  await test('LEGACY-MISSING-SECRET','502; fail closed',async()=>{
    legacy.profile=await adminApi(admin,'/outbound-auth-profiles/'+legacy.profile.id+'?version='+legacy.profile.version,'PUT',
      {...legacy.profileRequest,credentialSecretRef:'secret://e2e/missing'});
    try {const r=await dual.json('/api/proxy/test-legacy/api/test/whoami');assert.equal(r.status,502);return r.status;}
    finally {legacy.profile=await adminApi(admin,'/outbound-auth-profiles/'+legacy.profile.id+'?version='+legacy.profile.version,'PUT',legacy.profileRequest);}
  });
  await test('LEGACY-RESTORE-SECRET','200',async()=>{const r=await dual.json('/api/proxy/test-legacy/api/test/whoami');assert.equal(r.status,200);return r.status;});
  await test('SECURITY-RESPONSES','No downstream credentials in responses/context',async()=>{safeResponse([diagnostics,evidence.context],secrets);return 'No credential material';});
  await test('SECURITY-SERVER-SESSION','Opaque HttpOnly cookie resolves to token-free Redis identity',async()=>{
    const cookie=dual.cookie('AUREVIA_SESSION');assert(cookie.httpOnly);
    const candidates=new Set([cookie.value,decodeURIComponent(cookie.value),Buffer.from(cookie.value,'base64url').toString('utf8')]);
    const redis=(...args)=>command([...core,'exec','-T','-e','REDISCLI_AUTH','redis','redis-cli','--raw',...args],
      {env:{...process.env,REDISCLI_AUTH:env.get('REDIS_PASSWORD')??'change-me'}});
    let serialized;
    for(const candidate of candidates)if(/^[0-9a-f-]{36}$/i.test(candidate)) {
      const key='aurevia:session:v2:sessions:'+candidate;
      if(redis('EXISTS',key).trim()==='1')serialized=redis('HVALS',key);
    }
    assert(serialized?.includes('SessionIdentity'));
    assert(!/OidcIdToken|DefaultOidcUser|OidcUserAuthority|OAuth2AuthorizedClient|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\./.test(serialized));
    return {opaqueSession:true,tokenMaterialInRedisSession:false};
  });
  await test('SECURITY-NETWORK-ISOLATION','No backend or gateway host port',async()=>{
    const containers=command(['ps','--format','json']).trim().split('\n').filter(Boolean).map(line=>JSON.parse(line));
    for(const name of ['test-sso-service','test-legacy-service','operation-gateway']) {
      const container=containers.find(container=>container.Names.includes(name));assert(container);
      assert(!/->/.test(container.Ports),'Protected service has a published host port');
    }
    return 'No published downstream/gateway port';
  });
  await test('AUDIT-CORRELATION','BFF decision/target/mode/result and downstream share correlation',async()=>{
    const correlationId=randomUUID();const response=await dual.json('/api/proxy/test-sso/api/test/whoami','GET',undefined,correlationId);
    assert.equal(response.status,200);assert.equal(response.body.correlationId,correlationId);
    const logs=command([...core,'logs','--no-color','aurevia-bff']);
    assert(logs.split('\n').some(line=>line.includes(correlationId)&&line.includes('PROXY_AUDIT')&&line.includes('authMode=FORWARD_USER_TOKEN')&&line.includes('status=200')));
    const downstream=command([...demo,'logs','--no-color','test-sso-service']);assert(downstream.includes(correlationId));
    const stored=await adminApi(admin,'/logs/correlation/'+correlationId);assert(stored.items?.length>0);
    evidence.audit={correlationId,bff:true,downstream:true,storedEntries:stored.items.length};
    return evidence.audit;
  });
  for(const suffix of Object.keys(access)) {
    const id='BROWSER-'+({'dual-access':'DUAL','sso-only':'SSO-ONLY','legacy-only':'LEGACY-ONLY','none':'NONE'}[suffix]);
    if(process.env.AUREVIA_BROWSER_E2E==='false')results.push({id,expected:'Real Shell login, menus, MF requests, storage and network checks',actual:'Explicitly disabled with AUREVIA_BROWSER_E2E=false',status:'BLOCKED'});
    else await test(id,'Real Shell login, menus, MF requests, storage and network checks',async()=>{
      const result=await verifyAuthMicrofrontendsInChrome({origin,username:'e2e.'+suffix,password:userPasswords['e2e.'+suffix],allowedModes:access[suffix]});
      evidence[id]=result;return result;
    });
  }
  await test('SECURITY-LOGS','No known secrets or raw credentials in application/edge/identity logs',async()=>{
    const logs=command([...core,'logs','--no-color','aurevia-bff','authorization-service','operation-gateway','nginx'])+
      command([...demo,'logs','--no-color','test-sso-service','test-legacy-service'])+
      command(['compose','--env-file','.env','-f','infra/docker-compose/compose.identity-demo.yml','logs','--no-color','keycloak']);
    assert(!/(?:eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|legacy_[A-Za-z0-9_-]{30,}|Authorization: Bearer)/i.test(logs));
    for(const secret of secrets)assert(!logs.includes(secret));
    return 'No credential material found';
  });
}
try { await main(); }
catch(error) {
  const reason=diagnostic(error);
  console.log('Prerequisite failed: '+reason+'; remaining cases BLOCKED.');
  for(const id of cases)if(!results.some(result=>result.id===id))results.push({id,expected:'Execute real flow',actual:reason,status:'BLOCKED'});
}
for(const id of cases)if(!results.some(result=>result.id===id))results.push({id,expected:'Execute real flow',actual:'Prerequisite prevented execution',status:'BLOCKED'});
const counts=Object.fromEntries(['PASS','FAIL','BLOCKED'].map(status=>[status,results.filter(result=>result.status===status).length]));
mkdirSync('target/e2e-auth',{recursive:true});
writeFileSync('target/e2e-auth/results.json',JSON.stringify(safeResponse({runAt:new Date().toISOString(),counts,results,evidence},secrets),null,2)+'\n');
console.log(JSON.stringify(counts));
if(counts.FAIL||counts.BLOCKED)process.exitCode=1;
