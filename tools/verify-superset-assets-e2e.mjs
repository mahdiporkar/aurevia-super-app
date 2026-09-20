import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {Session,safeResponse} from './e2e-auth/session.mjs';
import {pagesBrowser} from './pages-browser-e2e.mjs';

// Run sequentially after other provisioning runners: this changes the demo
// user's asset grants and restores only grants created by this execution.
const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';
assert.equal(new URL(origin).hostname,'localhost');
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const passwords=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));
const password=process.env.AUREVIA_DEMO_PASSWORD??realm.users.find(u=>u.username==='administrator').credentials[0].value;
const secrets=[password,...Object.values(passwords)],results=[];
const admin=new Session(origin),viewer=new Session(origin);
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function api(path,method='GET',body){
  const r=await admin.json('/api/v1/admin'+path,method,body);
  assert([200,201,204].includes(r.status),'Admin API '+method+' '+path+' HTTP '+r.status);
  return r.body;
}
async function test(id,fn){
  try{results.push({id,status:'PASS',actual:safeResponse(await fn(),secrets)});}
  catch(error){let message=String(error.message).slice(0,1000);for(const secret of secrets)message=message.replaceAll(secret,'[REDACTED]');
    results.push({id,status:'FAIL',actual:message.replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g,'[REDACTED]')});}
  console.log(id+' '+results.at(-1).status);
}
async function waitForCatalog(assetId,visible){
  for(let i=0;i<45;i++){
    const r=await viewer.json('/api/v1/reports');
    if(r.status===200&&r.body.some(a=>a.id===assetId)===visible)return;
    await pause(1000);
  }
  throw new Error('Asset catalog did not reach expected visibility='+visible);
}
try{
  await admin.login('administrator',password);await viewer.login('e2e.none',passwords['e2e.none']);
  const subjects=(await api('/superset-assets/access-options')).subjects;
  const subject=subjects.find(s=>s.type==='USER'&&s.key==='e2e.none')
    ??subjects.find(s=>s.type==='USER'&&s.label.includes('e2e.none'));
  assert(subject,'Demo subject e2e.none is missing');
  const assets=await api('/superset-assets');
  const selected=[assets.find(a=>a.asset_type==='DASHBOARD'&&a.title==='Sales Dashboard'),
    assets.find(a=>a.asset_type==='CHART'&&a.published)];
  for(const [index,asset] of selected.entries())await test(index===0?'DASHBOARD-GRANT-LIFECYCLE':'CHART-GRANT-LIFECYCLE',async()=>{
    assert(asset,'Published demo asset is missing');
    const path='/superset-assets/'+asset.id+'/grants';
    const before=await api(path);
    assert(!before.some(g=>g.subject_type==='USER'&&g.subject_id===subject.id),'Demo user already has direct grants; refusing to alter them');
    const reportBefore=await viewer.json('/api/v1/reports');
    assert.equal(reportBefore.status,200);assert(!reportBefore.body.some(a=>a.id===asset.id),'Demo user already has inherited access');
    const levels=[];
    try{
      for(const [level,action] of [['VIEW','view'],['EDIT','update'],['MANAGE','admin']]){
        await api(path,'POST',{subjectType:'USER',subjectId:subject.id,level});
        const grants=await api(path);
        assert(grants.some(g=>g.subject_id===subject.id&&g.action_key===action),'Grant action was not persisted for '+level);
        await waitForCatalog(asset.id,true);levels.push(level);
      }
      const response=await viewer.json(asset.url_path);
      assert.equal(response.status,200,'Authorized asset runtime HTTP status');
      return {assetType:asset.asset_type,levels,runtimeStatus:response.status};
    }finally{
      for(const grant of await api(path))if(grant.subject_type==='USER'&&grant.subject_id===subject.id&&!before.some(g=>g.id===grant.id))await api(path+'/'+grant.id,'DELETE');
      await waitForCatalog(asset.id,false);
      const response=await viewer.json(asset.url_path);
      assert.equal(response.status,403,'Revoked asset must be denied by the backend');
      assert(!(await api(path)).some(g=>g.subject_type==='USER'&&g.subject_id===subject.id),'Fixture grant cleanup failed');
    }
  });
  await test('SUPERSET-OPERATION-HEALTH',async()=>{
    const r=await admin.json('/api/integrations/superset/public-default/health');assert.equal(r.status,200);return {httpStatus:200};
  });
  await test('ASSET-GRANT-FORM',async()=>pagesBrowser({origin,username:'administrator',password},async b=>{
    const asset=selected[0];assert(asset,'Sales Dashboard is missing');
    await b.navigate('/admin/superset',['/api/v1/admin/superset-assets/access-options']);
    const key=asset.asset_type.toLowerCase()+':'+asset.external_id;
    await b.until('Boolean(document.querySelector('+JSON.stringify('tr[data-row-key="'+key+'"]')+'))','Dashboard catalog row is missing');
    const start=b.mark();await b.text('سطوح دسترسی','.remote-surface tr[data-row-key="'+key+'"]');
    await b.response(start,'/api/v1/admin/superset-assets/'+asset.id+'/grants');
    await b.until('Boolean(document.querySelector(".ant-modal #subjectId"))','Grant dialog did not open');
    await b.text('اعطا','.ant-modal');
    await b.until('document.querySelectorAll(".ant-modal .ant-form-item-explain-error").length>=2','Missing holder and level were not rejected');
    assert(!b.responses(start).some(r=>r.path.endsWith('/grants')&&r.status===201),'Invalid form created a grant');
    await b.screenshot('superset-asset-grant-validation');
    return {dialogOpened:true,requiredHolderAndLevel:true,invalidMutation:false};
  }));
  await test('DASHBOARD-BROWSER-DATA',async()=>{
    const asset=selected[0];assert(asset,'Sales Dashboard is missing');
    return pagesBrowser({origin,username:'administrator',password},async b=>{
      const start=b.mark();await b.cdp.call('Page.navigate',{url:origin+asset.url_path},b.sessionId);
      await b.until('document.querySelectorAll(".chart-container").length>0','Dashboard charts did not render',60000);
      await b.response(start,'/api/v1/chart/data');
      const chartResponses=b.responses(start).filter(r=>r.path==='/api/v1/chart/data');
      assert(chartResponses.length>0);assert(chartResponses.every(r=>r.status===200));
      await b.screenshot('superset-docker-sales-dashboard');
      return {charts:await b.read('document.querySelectorAll(".chart-container").length'),successfulChartRequests:chartResponses.length};
    });
  });
}catch(error){results.push({id:'PREREQUISITE',status:'BLOCKED',actual:String(error.message).slice(0,500)});}
const counts=Object.fromEntries(['PASS','FAIL','BLOCKED'].map(s=>[s,results.filter(r=>r.status===s).length]));
mkdirSync('target/superset-assets-e2e',{recursive:true});
writeFileSync('target/superset-assets-e2e/results.json',JSON.stringify(safeResponse({runAt:new Date().toISOString(),counts,results},secrets),null,2)+'\n');
console.log(JSON.stringify(counts));if(counts.FAIL||counts.BLOCKED)process.exitCode=1;
