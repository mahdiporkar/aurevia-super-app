import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { Session, safeResponse } from './e2e-auth/session.mjs';
import { verifySwaggerInChrome } from './swagger-browser-e2e.mjs';

const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';
assert.equal(new URL(origin).hostname,'localhost','The Swagger verifier is restricted to the local environment');
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const password=process.env.AUREVIA_DEMO_PASSWORD??realm.users.find(u=>u.username==='administrator').credentials[0].value;
const results=[], evidence={};
const cases=['LOGIN-REDIRECT','JSON-UNAUTHENTICATED','SWAGGER-HTML','SWAGGER-ASSETS','SWAGGER-CONFIG',
  'BFF-CONTRACT','AUTHORIZATION-CONTRACT','BFF-PUBLIC-LOGIN','BFF-PROXY-CSRF','IDENTITY-CONTRACT',
  'SPEC-EXAMPLES','ACTOR-HEADERS-AUTO','READ-TRY-OUT','WRITE-CSRF-REJECT','WRITE-TRY-OUT','EXECUTE-PATH-REJECT',
  'EXECUTE-NONADMIN-REJECT','BROWSER-SWAGGER','NO-RAW-CREDENTIALS'];
async function test(id,expected,fn) {
  try {const actual=await fn();results.push({id,expected,actual,status:'PASS'});console.log(id+' PASS');return actual;}
  catch(error) {
    const status=error.code==='E2E_DEPENDENCY'?'BLOCKED':'FAIL';
    const actual=String(error.message??error.name).replaceAll(password,'[REDACTED]')
      .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|legacy_[A-Za-z0-9_-]{30,}/g,'[REDACTED]').slice(0,1000);
    results.push({id,expected,actual,status});console.log(id+' '+status);return undefined;
  }
}
const methods=['get','post','put','patch','delete','head','options','trace'];
function operations(doc) {return Object.entries(doc.paths).flatMap(([path,item])=>Object.entries(item)
  .filter(([method])=>methods.includes(method)).map(([method,operation])=>({path,method,...operation})));}
function pointer(doc,ref) {
  return ref.slice(2).split('/').reduce((value,key)=>value?.[key.replaceAll('~1','/').replaceAll('~0','~')],doc);
}
function validateContract(doc) {
  assert.match(String(doc.openapi),/^3\./);assert(doc.paths&&doc.components?.schemas);
  const inventory=operations(doc);assert(inventory.length>0);
  const ids=new Set();
  for(const op of inventory) {
    assert.match(op.summary??'',/[\u0600-\u06ff]/,'Missing Persian summary: '+op.path);
    assert(op.operationId&&!ids.has(op.operationId),'Duplicate/missing operationId: '+op.operationId);ids.add(op.operationId);
    const parameters=[...(doc.paths[op.path].parameters??[]),...(op.parameters??[])];
    for(const match of op.path.matchAll(/\{([^}]+)\}/g))
      assert(parameters.some(p=>p.in==='path'&&p.name===match[1]&&p.required),'Missing path parameter: '+op.path+' '+match[1]);
    assert(op.responses&&Object.keys(op.responses).length,'Missing responses: '+op.path);
  }
  function walk(value) {
    if(!value||typeof value!=='object')return;
    if(value.$ref?.startsWith('#/'))assert(pointer(doc,value.$ref)!==undefined,'Unresolved reference: '+value.$ref);
    for(const child of Object.values(value))walk(child);
  }
  walk(doc);
  return {operations:inventory.length,uniqueOperationIds:ids.size,brokenReferences:0};
}
function validateExamples(doc) {
  let count=0;
  function check(value,schema,path,depth=0) {
    assert(depth<40,'Example schema too deep');
    if(!schema||value===null||value===undefined)return;
    if(schema.$ref) return check(value,pointer(doc,schema.$ref),path,depth+1);
    for(const part of schema.allOf??[])check(value,part,path,depth+1);
    if(schema.enum)assert(schema.enum.includes(value),'Invalid example enum: '+path);
    if(Array.isArray(value)){for(const item of value)check(item,schema.items,path+'[]',depth+1);return;}
    if(typeof value==='object') {
      for(const key of schema.required??[])assert(Object.hasOwn(value,key),'Missing example field: '+path+'.'+key);
      for(const [key,child] of Object.entries(value))if(schema.properties?.[key])check(child,schema.properties[key],path+'.'+key,depth+1);
    }
  }
  for(const op of operations(doc))for(const media of Object.values(op.requestBody?.content??{}))
    for(const example of Object.values(media.examples??{})){check(example.value,media.schema,op.path);count++;}
  assert(count>0,'No request examples found');return count;
}
async function main() {
  await test('LOGIN-REDIRECT','Anonymous Swagger navigation redirects to BFF login',async()=>{
    const r=await fetch(origin+'/swagger-ui.html',{redirect:'manual'});assert.equal(r.status,302);
    assert.equal(new URL(r.headers.get('location'),origin).pathname,'/auth/login');return 302;});
  await test('JSON-UNAUTHENTICATED','Authorization JSON façade returns 401 without session',async()=>{
    const r=await fetch(origin+'/api/v1/docs/authorization/openapi',{redirect:'manual'});assert.equal(r.status,401);return 401;});
  const session=new Session(origin);const identity=await session.login('administrator',password);
  let html='',htmlUrl='';
  await test('SWAGGER-HTML','Swagger HTML is served after login',async()=>{
    const r=await session.request('/swagger-ui.html');html=await r.text();htmlUrl=r.url;
    assert.equal(r.status,200);assert.match(html,/Swagger UI/);assert(!html.includes('app-shell'));
    return {httpStatus:200,path:new URL(r.url).pathname};});
  await test('SWAGGER-ASSETS','All scripts and styles load as their actual content type',async()=>{
    const paths=[...html.matchAll(/<(?:script|link)\b[^>]*(?:src|href)=["']([^"']+)["']/g)].map(m=>new URL(m[1],htmlUrl));
    assert(paths.length>=3);
    for(const url of paths){assert.equal(url.origin,origin);const r=await session.request(url);await r.arrayBuffer();
      assert.equal(r.status,200,'Asset failed: '+url.pathname);
      if(url.pathname.endsWith('.js'))assert.match(r.headers.get('content-type')??'',/javascript/);
      if(url.pathname.endsWith('.css'))assert.match(r.headers.get('content-type')??'',/text\/css/);}
    return {assets:paths.length,failures:0};});
  await test('SWAGGER-CONFIG','Two same-origin contracts; no persisted authorization or external validator',async()=>{
    const r=await session.json('/v3/api-docs/swagger-config');assert.equal(r.status,200);
    assert.equal(r.body.persistAuthorization,false);assert.equal(r.body.urls.length,2);
    assert.notEqual(r.body.filter,'true','String true hides all operations in Swagger UI');
    assert.equal(r.body.validatorUrl,'');for(const spec of r.body.urls)assert.equal(new URL(spec.url,origin).origin,origin);
    evidence.config={urls:r.body.urls,persistAuthorization:r.body.persistAuthorization};return evidence.config;});
  const contracts={};
  for(const [id,label,path] of [['BFF-CONTRACT','bff','/v3/api-docs'],['AUTHORIZATION-CONTRACT','authorizationService','/api/v1/docs/authorization/openapi']]) {
    await test(id,'Valid runtime OpenAPI with unique IDs, path parameters and references',async()=>{
      const r=await session.json(path);assert.equal(r.status,200,'Specification HTTP status');
      contracts[label]=r.body;const summary=validateContract(r.body);evidence[label]=summary;return summary;});
  }
  assert(contracts.bff&&contracts.authorizationService,'Runtime specifications unavailable');
  await test('BFF-PUBLIC-LOGIN','Login/provider discovery is anonymous and login documents HTTP 302',async()=>{
    for(const path of ['/auth/providers','/auth/login'])assert.deepEqual(contracts.bff.paths[path].get.security,[]);
    assert(contracts.bff.paths['/auth/login'].get.responses['302']);return 'Anonymous + 302';});
  await test('BFF-PROXY-CSRF','Mutations require session AND CSRF; Superset retains its own CSRF model',async()=>{
    let checked=0;
    for(const op of operations(contracts.bff).filter(op=>['post','put','patch','delete'].includes(op.method))) {
      if(/^\/api\/(?:v1\/superset(?:-instances)?\/|integrations\/superset\/)/.test(op.path))continue;
      assert(op.security?.some(requirement=>Object.hasOwn(requirement,'browserSession')&&Object.hasOwn(requirement,'csrfToken')),op.path);
      checked++;
    }
    assert(checked>0);return {mutationsChecked:checked};});
  await test('IDENTITY-CONTRACT','All provider and external identity endpoints are documented',async()=>{
    const ids=new Set(operations(contracts.authorizationService).map(op=>op.operationId));
    for(const name of ['available','route','runtime','list','create','update','enabled','health'])assert(ids.has('IdentityProvider_'+name));
    for(const name of ['list','link','unlink'])assert(ids.has('ExternalIdentityAdmin_'+name));return {providerEndpoints:8,externalIdentityEndpoints:3};});
  await test('SPEC-EXAMPLES','Request examples satisfy declared required fields and enums',async()=>{
    return {bff:validateExamples(contracts.bff),authorizationService:validateExamples(contracts.authorizationService)};});
  await test('ACTOR-HEADERS-AUTO','Swagger does not require caller-supplied actor headers generated by BFF',async()=>{
    let headers=0;
    for(const op of operations(contracts.authorizationService))for(const parameter of op.parameters??[])
      if(parameter.in==='header'&&/^X-Actor(?:-Issuer|-Subject)?$/i.test(parameter.name)){
        assert.equal(parameter.required,false);headers++;
      }
    assert(headers>0);return {headersChecked:headers};});
  await test('READ-TRY-OUT','Read internal panels through the development façade',async()=>{
    const r=await session.json('/api/v1/docs/authorization/execute/internal/v1/registry/panels');assert.equal(r.status,200);assert(Array.isArray(r.body));return 200;});
  const check={subjectId:identity.subject,issuer:identity.issuer,resource:'application:aurevia/admin',action:'view',context:{channel:'swagger-test'},correlationId:crypto.randomUUID()};
  await test('WRITE-CSRF-REJECT','POST through Swagger façade without CSRF is rejected',async()=>{
    const r=await session.request('/api/v1/docs/authorization/execute/internal/v1/authorize/check',
      {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(check)});assert.equal(r.status,403);return 403;});
  await test('WRITE-TRY-OUT','POST with valid CSRF succeeds through the façade',async()=>{
    const r=await session.json('/api/v1/docs/authorization/execute/internal/v1/authorize/check','POST',check);assert.equal(r.status,200);assert.equal(r.body.result,'ALLOW');return {httpStatus:200,decision:r.body.result};});
  await test('EXECUTE-PATH-REJECT','Façade refuses paths outside /internal/v1/',async()=>{
    const r=await session.json('/api/v1/docs/authorization/execute/actuator/health');assert.equal(r.status,400);return 400;});
  await test('EXECUTE-NONADMIN-REJECT','An authenticated user without admin manage cannot execute internal APIs',async()=>{
    let username,userPassword;
    try{const users=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));username='e2e.sso-only';userPassword=users[username];}catch{}
    if(!userPassword){const user=realm.users.find(u=>u.username!=='administrator');username=user?.username;userPassword=user?.credentials?.[0]?.value;}
    if(!username||!userPassword){const error=new Error('No local non-admin test user configured');error.code='E2E_DEPENDENCY';throw error;}
    const other=new Session(origin);await other.login(username,userPassword);
    const r=await other.json('/api/v1/docs/authorization/execute/internal/v1/registry/panels');assert.equal(r.status,403);return 403;});
  await test('BROWSER-SWAGGER','Real Chrome: both contracts, GET and CSRF-protected POST Execute, storage and network',async()=>{
    const browser=await verifySwaggerInChrome({origin,username:'administrator',password});evidence.browser=browser;return browser;});
  await test('NO-RAW-CREDENTIALS','No actual credentials in contracts or sanitized evidence',async()=>{
    safeResponse([contracts.bff,contracts.authorizationService,evidence],[password]);return 'No raw credentials';});
}
try{await main();}catch(error){
  console.log('Prerequisite failed; remaining Swagger checks are BLOCKED.');
  for(const id of cases)if(!results.some(r=>r.id===id))results.push({id,expected:'Execute real Swagger check',actual:'Runtime/login prerequisite unavailable',status:'BLOCKED'});
}
const counts=Object.fromEntries(['PASS','FAIL','BLOCKED'].map(status=>[status,results.filter(r=>r.status===status).length]));
mkdirSync('target/swagger',{recursive:true});
writeFileSync('target/swagger/results.json',JSON.stringify(safeResponse({runAt:new Date().toISOString(),counts,results,evidence},[password]),null,2)+'\n');
console.log(JSON.stringify(counts));if(counts.FAIL||counts.BLOCKED)process.exitCode=1;
