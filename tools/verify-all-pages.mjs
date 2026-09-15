import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {pagesBrowser} from './pages-browser-e2e.mjs';
import {Session,safeResponse} from './e2e-auth/session.mjs';
import {verifyAuthMicrofrontendsInChrome} from './e2e-auth/browser.mjs';
import {verifyNativeSupersetInChrome} from './superset-native-browser-e2e.mjs';
const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';
const selectedCases=process.env.AUREVIA_PAGES_CASES?.split(',').filter(Boolean);
assert.equal(new URL(origin).hostname,'localhost','This demo runner is limited to localhost');
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const passwords=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));
const adminPassword=process.env.AUREVIA_DEMO_PASSWORD??realm.users.find(u=>u.username==='administrator').credentials[0].value;
const secrets=[adminPassword,...Object.values(passwords)];
const results=[],inventory=[],admin=new Session(origin),runId='e2e-'+randomUUID().replaceAll('-','').slice(0,12);
const nativeRegistration=JSON.parse(readFileSync('.tmp/superset-native/registration.json','utf8'));
function diagnostic(error){let text=String(error.message).slice(0,2200);
  for(const secret of secrets)text=text.replaceAll(secret,'[REDACTED]');
  return text.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED]');}
async function test(id,path,expected,fn){if(selectedCases&&!selectedCases.includes(id))return;
try{const actual=await fn();safeResponse(actual,secrets);
  results.push({id,path,expected,actual,status:'PASS'});console.log(id+' PASS');return actual;
}catch(error){results.push({id,path,expected,actual:diagnostic(error),status:error.code==='E2E_DEPENDENCY'?'BLOCKED':'FAIL'});console.log(id+' '+results.at(-1).status);}
finally{flush(false);}}
function flush(completed){mkdirSync('target/pages-e2e',{recursive:true});const counts=Object.fromEntries(['PASS','FAIL','BLOCKED'].map(status=>[status,results.filter(r=>r.status===status).length]));
  writeFileSync('target/pages-e2e/results.json',JSON.stringify(safeResponse({runAt:new Date().toISOString(),runId,origin,scope:selectedCases?'SUBSET':'ALL',completed,inventory,counts,results},secrets),null,2)+'\n');}
async function adminApi(path,method='GET',body){const r=await admin.json('/api/v1/admin'+path,method,body);assert([200,201,204].includes(r.status),'Admin operation HTTP '+r.status+' '+path);return r.body;}
const deps={
  'ou-access-ous':['/ou-access/ous'],
  'ou-access-groups':['/ou-access/access-groups','/ou-access/ous'],
  'ou-access-applications':['/ou-access/application-grants','/ou-access/access-groups','/panels'],
  'ou-access-explain':['/users'],
  'access-studio':['/resource-tree','/actions','/users','/directory-groups','/roles','/panels','/resource-tree/capabilities'],
  'panels':['/panels'],
  'proxy-targets':['/service-targets','/outbound-auth-profiles','/proxy-routes','/panels','/resources'],
  'proxy-routes':['/service-targets','/outbound-auth-profiles','/proxy-routes','/panels','/resources'],
  'proxy-operations':['/service-targets','/outbound-auth-profiles','/proxy-routes','/panels','/resources'],
  'outbound-connections':['/outbound-connections'],
  'outbound-auth':['/outbound-auth-profiles','/outbound-connections'],
  'integration-test':['/service-targets','/proxy-routes','/outbound-auth-profiles'],
  'superset-instances':['/superset-instances','/superset-instances/mappings'],
  'identity':['/users','/directory-groups','/roles','/role-assignments','/identity-providers','/ou-access/access-groups'],
  'logs-api':['/logs/api','/logs/api/summary'],
  'logs-audit':['/logs/audit'],
  'superset':['/superset-instances/mappings','/superset-assets','/superset-assets/access-options'],
};
const newActions={'ou-access-groups':'گروه جدید','panels':'میکرو جدید','proxy-targets':'Target جدید',
  'proxy-routes':'مسیر جدید','outbound-connections':'اتصال جدید','outbound-auth':'پروفایل جدید',
  'superset-instances':'محیط جدید','identity':'نقش جدید'};
async function validateModal(b,action){await b.text(action);await b.until('Boolean([...document.querySelectorAll(".ant-modal")].find(e=>e.getClientRects().length))','Form modal missing');
  await b.delay(350);
  const before=b.mark();await b.click('.ant-modal .ant-modal-footer .ant-btn-primary');
  await b.until('Boolean(document.querySelector(".ant-modal .ant-form-item-explain-error"))','Required fields were not validated');
  assert(!b.responses(before).some(r=>r.path.startsWith('/api/v1/admin')&&r.status===201),'Invalid form created a record');
  const validationCount=await b.read('document.querySelectorAll(".ant-modal .ant-form-item-explain-error").length');
  await b.click('.ant-modal .ant-modal-close');return {formOpened:true,requiredValidation:validationCount,invalidMutation:false};}
async function actionForAdmin(b,route){
  const path='/admin/'+route.path;
  if(newActions[route.id])return validateModal(b,newActions[route.id]);
  if(route.id==='operator-guide'){
    const link=await b.read('document.querySelector(".remote-surface a")?.getAttribute("href")');
    assert(link?.endsWith('/docs/operator-admin-form-field-guide-fa.md'));return {guideLinkValid:true};}
  if(route.id==='ou-access-ous'){
    const rows=await b.read('document.querySelectorAll(".remote-surface tbody tr[data-row-key]").length');
    if(rows){await b.click('.remote-surface tbody tr[data-row-key]');await b.until('Boolean(document.querySelector(".ant-drawer-open"))','OU details did not open');await b.click('.ant-drawer-close');}
    return {rows,readOnly:true,detailsOpened:rows>0};}
  if(route.id==='ou-access-applications'){
    await b.text('Grant VIEWER');await b.until('Boolean(document.querySelector(".remote-surface .ant-form-item-explain-error"))','Missing selections were not validated');return {requiredSelectionsValidated:true};}
  if(route.id==='ou-access-explain'){
    const start=b.mark();await b.select('.remote-surface .ant-select-selector','administrator');
    await b.until('Boolean(document.querySelector(".remote-surface .ant-alert-description"))','Explain result missing');
    const explain=b.responses(start).find(r=>r.path.endsWith('/explain'));assert(explain);assert.equal(explain.status,200);return {explainLoaded:true};}
  if(route.id==='access-studio'){
    await b.fill('.remote-surface input[placeholder*="جست"]','no_resource_'+runId);
    await b.until('document.querySelector(".remote-surface")?.innerText.includes("منبعی مطابق فیلتر پیدا نشد")','Resource filter did not apply');
    await b.fill('.remote-surface input[placeholder*="جست"]','');await b.text('+ منبع جدید');
    await b.until('Boolean(document.querySelector(".ant-drawer-open"))','Resource drawer missing');await b.delay(350);
    await b.text('ذخیره','.ant-drawer');await b.until('Boolean(document.querySelector(".ant-drawer .ant-form-item-explain-error"))','Resource required fields missing');
    await b.click('.ant-drawer-close');return {searchEmptyState:true,resourceValidation:true};}
  if(route.id==='proxy-operations'){
    const start=b.mark();await b.select('.remote-surface .ant-select-selector','e2e-sso');
    await b.until('document.querySelector(".remote-surface .ant-card-head-title")?.textContent.includes("e2e-sso")','Route operations missing');
    const route=(await adminApi('/proxy-routes')).find(r=>r.code==='e2e-sso');assert(route);
    await b.response(start,'/api/v1/admin/proxy-routes/'+route.id+'/operations');
    return {...await validateModal(b,'Operation جدید'),operationsLoaded:true};}
  if(route.id==='integration-test'){
    const start=b.mark();await b.text('اجرای Legacy');await b.response(start,'/api/proxy/legacy-demo/ping');
    await b.text('اجرای OAuth2 / Keycloak');await b.response(start,'/api/proxy/oauth2-demo/ping');
    return {legacy:200,oauth2:200};}
  if(route.id==='logs-api'||route.id==='logs-audit'){
    const start=b.mark();await b.fill('.remote-surface input[placeholder="Correlation ID"]',runId);await b.text('اعمال فیلتر');
    await b.response(start,'/api/v1/admin/logs/'+(route.id==='logs-api'?'api':'audit'));
    await b.until('document.querySelectorAll(".remote-surface tbody tr[data-row-key]").length===0','Log filter did not produce an empty result');
    return {correlationFilter:true,emptyState:true};}
  if(route.id==='superset'){
    await b.until('document.querySelectorAll(".remote-surface tbody tr[data-row-key]").length>=3','Superset live catalog did not load');
    const rows=await b.read('[...document.querySelectorAll(".remote-surface tbody tr[data-row-key]")].map(e=>({key:e.getAttribute("data-row-key"),text:e.innerText}))');
    assert(rows.some(r=>r.text.includes('Aurevia Native BI Demo')));
    const grantTargets=[];
    for(const asset of nativeRegistration.assets){
      const start=b.mark(),key=asset.type.toLowerCase()+':'+asset.externalId;
      await b.text('سطوح دسترسی','.remote-surface tr[data-row-key="'+key+'"]');
      await b.response(start,'/api/v1/admin/superset-assets/'+asset.id+'/grants');
      await b.until('Boolean([...document.querySelectorAll(".ant-modal")].find(e=>e.getClientRects().length))','Asset permission modal missing');
      await b.text('اعطا','.ant-modal');await b.until('Boolean(document.querySelector(".ant-modal .ant-form-item-explain-error"))','Asset holder validation missing');
      await b.click('.ant-modal-close');await b.delay(350);grantTargets.push({type:asset.type,externalId:asset.externalId,assetId:asset.id});
    }
    return {liveAssets:rows.length,dashboardListed:true,grantTargets};}
  throw new Error('No page interaction defined: '+path);
}
try{
  await admin.login('administrator',adminPassword);
  const context=await admin.json('/api/me/context');assert.equal(context.status,200);
  for(const module of context.body.uiCatalog.modules)for(const route of module.routes)
    inventory.push({module:module.moduleKey,path:('/'+module.routePrefix+'/'+route.path).replace(/\/$/,''),routeId:route.id});
  const adminModule=context.body.uiCatalog.modules.find(m=>m.moduleKey==='admin');assert.equal(adminModule.routes.length,18);
  await pagesBrowser({origin,username:'administrator',password:adminPassword},async b=>{
    for(const route of adminModule.routes)await test('PAGE-ADMIN-'+route.id,'/admin/'+route.path,'Real page, API data and user interaction',async()=>{
      const start=await b.navigate('/admin/'+route.path,(deps[route.id]??[]).map(p=>'/api/v1/admin'+p));
      const selected=await b.read('document.querySelector(".ant-menu-item-selected .nav-menu-label")?.textContent.trim()??document.querySelector(".ant-menu-item-selected")?.textContent.trim()');
      assert.equal(selected,route.title,'Selected navigation does not match the current page');
      const interaction=await actionForAdmin(b,route);const browser=await b.clean(start);await b.screenshot('admin-'+route.id);return {interaction,...browser};
    });
    const employeeList=await admin.json('/api/proxy/hr/employees');
    const employee=employeeList.body?.items?.[0];
    for(const [id,path,api] of [['employees','/hr/personal','/api/proxy/hr/employees'],['departments','/hr/departments','/api/proxy/hr/departments'],['positions','/hr/positions','/api/proxy/hr/positions']])
      await test('PAGE-HR-'+id,path,'Operational HR data is rendered',async()=>{
        const start=await b.navigate(path,[api]);const rows=await b.read('document.querySelectorAll(".remote-surface tbody tr[data-row-key]").length');assert(rows>0,'HR fixture rows missing');
        if(id==='employees'){const disabled=await b.read('[...document.querySelectorAll(".remote-surface button")].find(e=>e.textContent.includes("افزودن کارمند"))?.disabled');assert.equal(disabled,true,'HR read-only user can create an employee');}
        return {rows,...await b.clean(start)};
      });
    await test('PAGE-HR-details','/hr/personal/:id','Open employee from list, load detail and return',async()=>{
      assert(employee,'Employee fixture missing');await b.navigate('/hr/personal',['/api/proxy/hr/employees']);const start=b.mark();await b.text('جزئیات');
      await b.response(start,'/api/proxy/hr/employees/'+employee.id);
      await b.until('Boolean(document.querySelector(".remote-surface .ant-descriptions"))','Employee detail did not render');
      assert((await b.read('document.querySelector(".remote-surface").innerText')).includes(employee.name));
      await b.text('بازگشت به فهرست','.remote-surface','a');await b.until('location.pathname==="/hr/personal"','Employee back link failed');return {detailLoaded:true,backLink:true,...await b.clean(start)};
    });
    for(const [kind,label,title] of [['payments','پرداخت‌ها','مدیریت پرداخت‌ها'],['invoices','صورتحساب‌ها','صورتحساب‌ها'],['budgets','بودجه‌ها','بودجه‌ها']])
      await test('PAGE-FINANCE-'+kind,'/finance#'+kind,'Select finance page and load operational data',async()=>{
        const start=await b.navigate('/finance',['/api/proxy/finance/payments']);
        if(kind!=='payments'){const next=b.mark();await b.text(label,'.remote-surface','.ant-segmented-item-label');await b.response(next,'/api/proxy/finance/'+kind);}
        await b.until('document.querySelector(".remote-surface h3")?.textContent==='+JSON.stringify(title),'Finance page did not render');
        const rows=await b.read('document.querySelectorAll(".remote-surface tbody tr[data-row-key]").length');assert(rows>0,'Finance fixture rows missing');
        let validation=false;if(kind==='payments'){await validateModal(b,'پرداخت جدید');validation=true;}
        return {rows,validation,...await b.clean(start)};
      });
    await test('PAGE-REPORTS','/reports','Load catalog and filter visible reports',async()=>{
      const start=await b.navigate('/reports',['/api/v1/reports']);await b.until('Boolean(document.querySelector(".remote-surface a[href*=superset]"))','Report links missing');
      const count=await b.read('document.querySelectorAll(".remote-surface a[href*=superset],.remote-surface a[href*=explore]").length');assert(count>=3);
      await b.fill('.remote-surface input[aria-label="جستجوی گزارش"]','no_report_'+runId);
      await b.until('document.querySelector(".remote-surface")?.innerText.includes("گزارش تخصیص‌یافته‌ای پیدا نشد")','Report filter did not apply');
      await b.fill('.remote-surface input[aria-label="جستجوی گزارش"]','');return {catalogReports:count,filterEmptyState:true,...await b.clean(start)};
    });
    await test('WORKFLOW-PANELS','/admin/panels','Inspect active artifact, resource manifests and navigation',async()=>{
      const start=await b.navigate('/admin/panels',['/api/v1/admin/panels']);
      const panel=(await adminApi('/panels')).find(p=>p.code==='ADMIN');assert(panel);
      await b.text('Artifact و Catalog','.remote-surface tr[data-row-key="'+panel.id+'"]');
      for(const suffix of ['artifacts','resource-manifests','navigation-definitions'])await b.response(start,'/api/v1/admin/panels/'+panel.id+'/'+suffix);
      await b.until('Boolean(document.querySelector(".ant-modal"))','Artifact details missing');await b.click('.ant-modal-close');return {artifact:true,manifests:true,navigation:true,...await b.clean(start)};
    });
    await test('WORKFLOW-SUPERSET-HEALTH','/admin/superset-instances','Run native operation health through the public mapping',async()=>{
      const start=await b.navigate('/admin/superset-instances',['/api/v1/admin/superset-instances']);
      await b.text('سلامت','.remote-surface tr[data-row-key="'+nativeRegistration.instances.public.id+'"]');
      await b.response(start,'/api/integrations/superset/'+nativeRegistration.instances.public.code+'/health');
      return {operationHealthViaBff:200,...await b.clean(start)};
    });
    await test('WORKFLOW-AUTH-PROFILE','/admin/outbound-auth','Validate legacy profile, obtain a token on the server and inspect cache',async()=>{
      const start=await b.navigate('/admin/outbound-auth',['/api/v1/admin/outbound-auth-profiles']);
      const profile=(await adminApi('/outbound-auth-profiles')).find(p=>p.code==='e2e-legacy'&&p.auth_mode==='LEGACY_SERVICE_TOKEN'&&p.active);assert(profile);
      const scope='.remote-surface tr[data-row-key="'+profile.id+'"]';
      for(const [label,suffix] of [['اعتبارسنجی اتصال','connection-test'],['تست توکن','token-test'],['Cache','cache-status']]){
        const next=b.mark();await b.text(label,scope);await b.response(next,'/api/v1/admin/outbound-auth-profiles/'+profile.id+'/'+suffix);
      }
      return {connectionValidated:true,tokenTest:true,cacheChecked:true,...await b.clean(start)};
    });
    await test('WORKFLOW-IDENTITY','/admin/identity','Create a role, assign it, revoke it and deactivate the fixture',async()=>{
      let role;const users=await adminApi('/users'),subject=users.find(u=>u.username==='e2e.none');assert(subject);
      const start=await b.navigate('/admin/identity',['/api/v1/admin/roles']);
      try{
        await b.text('نقش جدید');await b.until('Boolean(document.querySelector(".ant-modal #roleKey"))','Role form missing');await b.delay(350);
        await b.fill('.ant-modal #roleKey',runId);await b.fill('.ant-modal #nameFa','نقش آزمون '+runId);await b.fill('.ant-modal #nameEn','E2E '+runId);
        let next=b.mark();await b.click('.ant-modal-footer .ant-btn-primary');await b.response(next,'/api/v1/admin/roles',201);
        await b.until('![...document.querySelectorAll(".ant-modal #roleKey")].some(e=>e.getClientRects().length)','Role modal did not close');
        role=(await adminApi('/roles')).find(r=>r.role_key===runId);assert(role);assert.equal(role.status,'ACTIVE');
        await b.select('.remote-surface #subjectId','e2e.none');await b.select('.remote-surface #roleId',runId);
        next=b.mark();await b.text('تخصیص');await b.response(next,'/api/v1/admin/role-assignments',201);
        const assignments=await adminApi('/role-assignments');assert(assignments.some(a=>a.subject_id===subject.id&&a.role_id===role.id));
        const key='USER:'+subject.id+':'+role.id;
        await b.until('Boolean(document.querySelector('+JSON.stringify('tr[data-row-key="'+key+'"]')+'))','Role assignment row missing');
        next=b.mark();await b.text('لغو','.remote-surface tr[data-row-key="'+key+'"]');await b.until('Boolean(document.querySelector(".ant-popconfirm"))','Revoke confirmation missing');await b.click('.ant-popconfirm .ant-btn-primary');
        await b.response(next,'/api/v1/admin/role-assignments/USER/'+subject.id+'/'+role.id,204);
        assert(!(await adminApi('/role-assignments')).some(a=>a.subject_id===subject.id&&a.role_id===role.id));
        return {roleCreated:true,persisted:true,assigned:true,revoked:true,...await b.clean(start)};
      }finally{
        role??=(await adminApi('/roles')).find(r=>r.role_key===runId);
        if(role){const assignments=await adminApi('/role-assignments');if(assignments.some(a=>a.subject_id===subject.id&&a.role_id===role.id))await adminApi('/role-assignments/USER/'+subject.id+'/'+role.id,'DELETE');
          const current=(await adminApi('/roles')).find(r=>r.id===role.id);if(current.status==='ACTIVE')await adminApi('/roles/'+role.id+'/status?version='+current.version,'PATCH',{active:false});
          assert.equal((await adminApi('/roles')).find(r=>r.id===role.id).status,'INACTIVE','Role fixture was not deactivated');}
      }
    });
    await test('WORKFLOW-CONNECTION','/admin/outbound-connections','Create, edit and deactivate an approved connection',async()=>{
      let connection;const template=(await adminApi('/outbound-connections')).find(c=>c.active);assert(template);
      const ref='connection://e2e/'+runId,start=await b.navigate('/admin/outbound-connections',['/api/v1/admin/outbound-connections']);
      try{
        await b.text('اتصال جدید');await b.until('Boolean(document.querySelector(".ant-modal #connectionRef"))','Connection form missing');await b.delay(350);
        await b.fill('.ant-modal #name',runId);await b.fill('.ant-modal #connectionRef',ref);await b.fill('.ant-modal #baseUrl',template.base_url);
        if(!template.tls_required)await b.click('.ant-modal #tlsRequired');
        let next=b.mark();await b.click('.ant-modal-footer .ant-btn-primary');await b.response(next,'/api/v1/admin/outbound-connections',201);
        await b.until('![...document.querySelectorAll(".ant-modal #connectionRef")].some(e=>e.getClientRects().length)','Connection form did not close');
        connection=(await adminApi('/outbound-connections')).find(c=>c.connection_ref===ref);assert(connection);
        await b.until('Boolean(document.querySelector('+JSON.stringify('tr[data-row-key="'+connection.id+'"]')+'))','Created connection row missing');
        await b.text('ویرایش','.remote-surface tr[data-row-key="'+connection.id+'"]');await b.until('Boolean(document.querySelector(".ant-modal #name"))','Connection edit form missing');await b.delay(350);
        await b.fill('.ant-modal #name',runId+'_edited');await b.click('.ant-modal #active');next=b.mark();await b.click('.ant-modal-footer .ant-btn-primary');
        await b.response(next,'/api/v1/admin/outbound-connections/'+connection.id);
        const updated=(await adminApi('/outbound-connections')).find(c=>c.id===connection.id);assert.equal(updated.name,runId+'_edited');assert.equal(updated.active,false);
        return {created:true,persisted:true,edited:true,deactivated:true,...await b.clean(start)};
      }finally{
        connection??=(await adminApi('/outbound-connections')).find(c=>c.connection_ref===ref);
        if(connection){const c=(await adminApi('/outbound-connections')).find(c=>c.id===connection.id);if(c.active)await adminApi('/outbound-connections/'+c.id,'PUT',{connectionRef:c.connection_ref,name:c.name,baseUrl:c.base_url,tlsRequired:c.tls_required,active:false,version:c.version});
          assert.equal((await adminApi('/outbound-connections')).find(c=>c.id===connection.id).active,false);}
      }
    });
    await test('SHELL-UNKNOWN','/does-not-exist','Unknown route shows a controlled state',async()=>{const start=await b.navigate('/does-not-exist');
      assert((await b.read('document.querySelector(".remote-surface").innerText')).includes('مسیر یا میکروفرانت یافت نشد'));return {controlledNotFound:true,...await b.clean(start)};});
    await test('SHELL-LOCALE','/admin/operator-guide','Switch locale and direction, then restore',async()=>{
      const start=await b.navigate('/admin/operator-guide');await b.click('.app-header button');
      await b.until('Boolean(document.querySelector(".ant-layout-ltr"))||!document.querySelector(".ant-layout-rtl")','English direction did not apply');
      await b.click('.app-header button');await b.until('Boolean(document.querySelector(".ant-layout-rtl"))','Persian direction did not restore');return {english:true,persianRestored:true,...await b.clean(start)};
    });
    await test('WORKFLOW-MENU','/admin/*','Navigate through every administrative side-menu item',async()=>{
      await b.navigate('/admin/operator-guide');const start=b.mark(),visited=[];
      for(const route of adminModule.routes){const path='/admin/'+route.path;
        await b.click('.ant-menu-item[data-menu-id$="'+path+'"]');await b.until('location.pathname==='+JSON.stringify(path)+'&&!document.querySelector(".remote-surface .ant-spin-spinning")','Menu route did not settle');
        assert.equal(await b.read('document.querySelector(".ant-menu-item-selected .nav-menu-label")?.textContent.trim()??document.querySelector(".ant-menu-item-selected")?.textContent.trim()'),route.title);
        visited.push(path);
      }
      return {visited,...await b.clean(start)};
    });
  });
  await pagesBrowser({origin,username:'e2e.none',password:passwords['e2e.none']},async b=>{
    for(const item of inventory.filter(i=>i.module==='admin'))await test('DENIED-'+item.routeId,item.path,'Ungranted user cannot render the admin page',async()=>{
      const start=await b.navigate(item.path);assert.equal(await b.read('document.querySelectorAll(".remote-surface form,.remote-surface table").length'),0);
      assert((await b.read('document.querySelector(".remote-surface").innerText')).includes('یافت نشد'));
      return {adminRemoteRendered:false,...await b.clean(start)};
    });
    await test('DENIED-ADMIN-API','/api/v1/admin/resources','Ungranted user receives HTTP 403',async()=>{
      const status=await b.read('fetch("/api/v1/admin/resources").then(r=>r.status)');assert.equal(status,403);return {status};
    });
    for(const path of ['/hr/personal','/hr/departments','/hr/positions','/finance','/reports'])await test('DENIED-PAGE-'+path.replaceAll('/','-'),path,'Ungranted user cannot render a business page',async()=>{
      await b.navigate(path);assert.equal(await b.read('document.querySelectorAll(".remote-surface table,.remote-surface form,.remote-surface a").length'),0);
      assert((await b.read('document.querySelector(".remote-surface").innerText')).includes('یافت نشد'));return {businessRemoteRendered:false};
    });
    for(const [id,path] of [['HR','/api/proxy/hr/employees'],['FINANCE','/api/proxy/finance/payments'],['CHART','/explore/?slice_id=1']])await test('DENIED-API-'+id,path,'Knowing the backend URL cannot bypass permissions',async()=>{
      assert.equal(await b.read('fetch('+JSON.stringify(path)+').then(r=>r.status)'),403);return {status:403};
    });
  });
  for(const [suffix,allowedModes] of [['dual-access',['sso','legacy']],['sso-only',['sso']],['legacy-only',['legacy']],['none',[]]])
    await test('AUTH-BROWSER-'+suffix,'/test-sso/home,/test-legacy/home','Login, menu, downstream call and denied mode for the selected persona',()=>
      verifyAuthMicrofrontendsInChrome({origin,username:'e2e.'+suffix,password:passwords['e2e.'+suffix],allowedModes}));
  await test('PAGE-SUPERSET-DASHBOARD',nativeRegistration.assets.find(a=>a.type==='DASHBOARD').path,'Real report table and total use BFF-only chart data',()=>
    verifyNativeSupersetInChrome({origin,username:'e2e.dual-access',password:passwords['e2e.dual-access'],path:nativeRegistration.assets.find(a=>a.type==='DASHBOARD').path}));
  await pagesBrowser({origin,username:'e2e.dual-access',password:passwords['e2e.dual-access']},async b=>{
    for(const chart of nativeRegistration.assets.filter(a=>a.type==='CHART'))await test('PAGE-SUPERSET-CHART-'+chart.externalId,chart.path,'Open individual chart from the reports catalog and fetch real data',async()=>{
      await b.navigate('/reports',['/api/v1/reports']);const href=await b.read('[...document.querySelectorAll(".remote-surface a")].find(a=>new URL(a.href).searchParams.get("slice_id")==='+JSON.stringify(chart.externalId)+')?.href');
      assert(href,'Chart catalog link missing');assert.equal(new URL(href).origin,origin);
      const start=b.mark();await b.cdp.call('Page.navigate',{url:href},b.sessionId);await b.response(start,'/api/v1/chart/data');
      await b.until('Boolean(document.querySelector(".chart-container"))','Individual chart did not render');
      const body=await b.read('document.body.innerText');assert(!body.includes('Missing dataset'),'Chart dataset missing');
      const denied=b.responses(start).filter(r=>r.status>=400);
      // Superset Explore attempts to persist generated query context for chart owners.
      // The Aurevia viewer boundary must reject this write while data stays readable.
      assert.deepEqual(denied,[{path:'/api/v1/chart/'+chart.externalId,status:403}]);
      await b.screenshot('superset-chart-'+chart.externalId);return {chartId:chart.externalId,rendered:true,chartData:200,automaticChartWriteDenied:403,...await b.clean(start,{allowedStatuses:[403],allowedRuntimeExceptions:1})};
    });
  });
  for(const path of ['/api/v1/me',...context.body.uiCatalog.modules.map(m=>m.remote.remoteEntryUrl)])await test('ANONYMOUS-'+path.replaceAll('/','-'),path,'Session is required for identity and executable remote entries',async()=>{
    const r=await fetch(origin+path,{redirect:'manual',signal:AbortSignal.timeout(15000)});await r.arrayBuffer();assert.equal(r.status,401);return {status:401};
  });
}catch(error){results.push({id:'RUNTIME-PREREQUISITE',path:'/',expected:'Complete browser sweep prerequisites',actual:diagnostic(error),status:'BLOCKED'});}
if(selectedCases){for(const id of selectedCases)if(!results.some(r=>r.id===id))results.push({id,path:'',expected:'Execute selected case',actual:'Selected case was not executed',status:'BLOCKED'});}
else{
  const required=['PAGE-HR-employees','PAGE-HR-departments','PAGE-HR-positions','PAGE-HR-details',
    'PAGE-FINANCE-payments','PAGE-FINANCE-invoices','PAGE-FINANCE-budgets','PAGE-REPORTS',
    'PAGE-SUPERSET-DASHBOARD',...nativeRegistration.assets.filter(a=>a.type==='CHART').map(a=>'PAGE-SUPERSET-CHART-'+a.externalId),
    ...['dual-access','sso-only','legacy-only','none'].map(s=>'AUTH-BROWSER-'+s),
    ...inventory.filter(i=>i.module==='admin').map(i=>'PAGE-ADMIN-'+i.routeId)];
  for(const id of required)if(!results.some(r=>r.id===id))results.push({id,path:'',expected:'Execute full page coverage',actual:'Runtime prerequisite prevented this case',status:'BLOCKED'});
  const unknownModules=inventory.filter(i=>!['admin','human-resources','finance','reports'].includes(i.module));
  if(unknownModules.length)results.push({id:'CATALOG-COVERAGE',path:'',expected:'Every catalog module has defined browser coverage',actual:unknownModules,status:'FAIL'});
}
mkdirSync('target/pages-e2e',{recursive:true});
const counts=Object.fromEntries(['PASS','FAIL','BLOCKED'].map(status=>[status,results.filter(r=>r.status===status).length]));
const report=safeResponse({runAt:new Date().toISOString(),runId,origin,scope:selectedCases?'SUBSET':'ALL',completed:true,inventory,counts,results},secrets);
writeFileSync('target/pages-e2e/results.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(counts));if(counts.FAIL||counts.BLOCKED)process.exitCode=1;
