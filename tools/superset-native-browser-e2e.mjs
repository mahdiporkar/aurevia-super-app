import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync,rmSync,mkdtempSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {installedChrome,launchChrome,connectCdp,evaluate} from './chrome-navigation-e2e.mjs';
import {safeResponse} from './e2e-auth/session.mjs';

const delay=ms=>new Promise(resolvePromise=>setTimeout(resolvePromise,ms));
export async function verifyNativeSupersetInChrome({origin,username,password,path}){
  const executable=installedChrome();if(!executable){const error=new Error('Chrome/Edge is unavailable');error.code='E2E_DEPENDENCY';throw error;}
  const profile=mkdtempSync(join(tmpdir(),'aurevia-native-bi-'));let browser,cdp,sessionId,read,start=0;
  try{
    browser=await launchChrome(executable,profile);cdp=await connectCdp(browser.websocketUrl);
    const {targetId}=await cdp.call('Target.createTarget',{url:'about:blank'});
    ({sessionId}=await cdp.call('Target.attachToTarget',{targetId,flatten:true}));
    await cdp.call('Page.enable',{},sessionId);await cdp.call('Runtime.enable',{},sessionId);await cdp.call('Network.enable',{},sessionId);
    read=expression=>evaluate(cdp.call,sessionId,expression);
    async function until(expression,message){for(let i=0;i<240;i++){
      if(await read(expression))return;
      const failure=cdp.events.slice(start).find(event=>event.sessionId===sessionId
        &&event.method==='Network.responseReceived'&&event.params.response.status>=400
        &&['/api/v1/dashboard/','/api/v1/chart/data'].some(prefix=>new URL(event.params.response.url).pathname.startsWith(prefix)));
      if(failure)throw new Error('Report API HTTP '+failure.params.response.status+' '+new URL(failure.params.response.url).pathname);
      await delay(500);
    }throw new Error(message);}
    await cdp.call('Page.navigate',{url:origin+'/auth/login'},sessionId);
    await until('Boolean(document.querySelector(\'input[name="password"]\'))','Keycloak login form did not load');
    assert.equal(await read('location.origin'),'http://localhost:8180');
    await read('(function(){document.querySelector(\'input[name="username"]\').value='+JSON.stringify(username)
      +';document.querySelector(\'input[name="password"]\').value='+JSON.stringify(password)+';document.querySelector("form").requestSubmit();})()');
    await until('location.origin==='+JSON.stringify(origin),'Login did not return to Aurevia');
    await cdp.call('Page.navigate',{url:origin+'/reports'},sessionId);
    const actionButtons='[...document.querySelectorAll(".ant-card-actions button")]';
    await until(actionButtons+'.length>=3','Reports MFE did not display the granted reports');
    const reportCards=await read(actionButtons+'.length');
    assert.equal(reportCards,3,'Reports MFE did not display the three granted reports');
    // Reports open inside Aurevia: the catalog must offer no external navigation.
    assert.equal(await read('document.querySelectorAll(\'[target="_blank"]\').length'),0,
      'Reports catalog still opens a report in a new browser tab');
    assert.equal(await read('[...document.querySelectorAll("a")].filter(a=>{const p=new URL(a.href).pathname;'
      +'return p.startsWith("/superset/")||p.startsWith("/explore/")||p.startsWith("/api/v1/superset-instances/")}).length'),0,
      'Reports catalog still links the browser straight at Superset');
    const catalogText=await read('document.body.innerText');
    const pagesBefore=(await cdp.call('Target.getTargets')).targetInfos.filter(target=>target.type==='page').length;

    // Resolve the granted dashboard from the authorized catalog, then open it the
    // way a user does: by clicking its card, not by navigating to a report URL.
    const asset=await read('fetch("/api/v1/reports",{headers:{Accept:"application/json"}})'
      +'.then(response=>response.json()).then(list=>{const found=list.find(item=>'+JSON.stringify(paths)
      +'.some(candidate=>new URL(item.url_path,location.origin).pathname.endsWith(candidate)));'
      +'return found?{id:found.id,title:found.title,path:new URL(found.url_path,location.origin).pathname}:null})');
    assert(asset,'Granted dashboard is missing from the authorized report catalog');
    assert(catalogText.includes(asset.title),'Reports catalog did not render the granted dashboard');
    start=cdp.events.length;
    await read('[...document.querySelectorAll(".ant-card")].find(card=>card.innerText.includes('
      +JSON.stringify(asset.title)+')).querySelector(".ant-card-actions button").click()');
    await until('location.pathname==="/reports/view/"+encodeURIComponent('+JSON.stringify(asset.id)+')',
      'Clicking a report did not move the Shell to the report viewer route');
    // No new browser tab, and the Aurevia Shell is still the surrounding page.
    assert.equal((await cdp.call('Target.getTargets')).targetInfos.filter(target=>target.type==='page').length,
      pagesBefore,'Opening a report created a second browser tab');
    assert(await read('Boolean(document.querySelector(".app-header")&&document.querySelector(".app-sidebar"))'),
      'Aurevia Shell header/sidebar is not visible while the report is open');
    await until('Boolean(document.querySelector("iframe"))','Report viewer did not embed an iframe');
    const frameSource=await read('new URL(document.querySelector("iframe").src).pathname');
    assert.equal(frameSource,asset.path,'Report viewer embedded an unexpected path');
    assert.equal(await read('new URL(document.querySelector("iframe").src).origin'),origin,
      'Report viewer embedded a cross-origin Superset URL');

    // The embedded document is same-origin, but it is only readable once the
    // frame has a body, so every in-frame probe degrades to false until then.
    const inFrame=expression=>'(function(){try{const d=document.querySelector("iframe").contentDocument;'
      +'if(!d||!d.body)return false;return ('+expression+');}catch(ignored){return false}})()';
    await until(inFrame('d.body.innerText.includes("Aurevia Native BI Demo")'),
      'Superset dashboard did not render inside the Aurevia viewer');
    await until(inFrame('d.body.innerText.includes("Finance")&&d.body.innerText.includes("IT")'),
      'Real department data did not render in the table chart');
    // A second Superset login inside the frame would replace the dashboard with a form.
    assert.equal(await read(inFrame('d.querySelectorAll(\'input[type="password"]\').length===0')),true,
      'Embedded Superset asked the user to log in a second time');
    const events=cdp.events.slice(start).filter(event=>event.sessionId===sessionId);
    const requests=events.filter(event=>event.method==='Network.requestWillBeSent');
    const network=requests.filter(event=>new URL(event.params.request.url).protocol!=='data:');
    for(const event of network){const request=event.params.request;assert.equal(new URL(request.url).origin,origin,'Report contacted an external browser origin');
      assert(!Object.keys(request.headers).some(name=>name.toLowerCase()==='authorization'),'Report sent an OAuth bearer/basic header');}
    const queryResponses=events.filter(event=>event.method==='Network.responseReceived'
      &&new URL(event.params.response.url).pathname.endsWith('/chart/data'));
    assert(queryResponses.length>=2,'Expected two real chart data requests');
    const chartData=[];
    for(const event of queryResponses){assert.equal(event.params.response.status,200,'Chart data response');
      const body=await cdp.call('Network.getResponseBody',{requestId:event.params.requestId},sessionId);
      const value=JSON.parse(body.base64Encoded?Buffer.from(body.body,'base64').toString('utf8'):body.body);
      chartData.push(value.result[0].data);}
    assert(chartData.some(rows=>rows.length===4&&rows.some(row=>row.department==='Finance')),'Browser did not receive the four real demo rows');
    assert(chartData.some(rows=>rows.length===1&&Object.values(rows[0]).includes(1150)),'Browser did not receive the real total 1150');
    const storage=await read('({local:Object.fromEntries(Object.entries(localStorage)),session:Object.fromEntries(Object.entries(sessionStorage)),cookie:document.cookie,url:location.href})');
    safeResponse(storage,[password]);assert(!storage.cookie.includes('AUREVIA_SESSION'),'BFF cookie is visible to page JavaScript');
    assert(!/access.?token|refresh.?token|id.?token/i.test(JSON.stringify(storage)),'OAuth tokens in report storage');
    assert(!events.some(event=>event.method==='Runtime.exceptionThrown'),'Report browser runtime exception');
    await read('document.querySelector("iframe").contentDocument.querySelector(".chart-container")?.scrollIntoView({block:"center"})');
    const screenshot=await cdp.call('Page.captureScreenshot',{format:'png'},sessionId);
    mkdirSync('target/superset-native',{recursive:true});writeFileSync('target/superset-native/dashboard.png',Buffer.from(screenshot.data,'base64'));

    // Browser Back must return to the catalog without leaving Aurevia.
    await read('history.back()');
    await until('location.pathname==="/reports"&&'+actionButtons+'.length>=3',
      'Browser Back did not return to the reports catalog');
    assert.equal(await read('document.querySelectorAll("iframe").length'),0,
      'Report viewer iframe survived the Back navigation');

    return {reportsCatalogRendered:true,grantedReportLinks:reportCards,dashboardRendered:true,
      embeddedInShell:true,newBrowserTab:false,secondSupersetLogin:false,backReturnsToCatalog:true,
      tableRows:4,total:1150,chartDataResponses:queryResponses.length,
      networkRequests:network.length,sameOrigin:true,oauthHeaders:false,storageContainsCredentials:false};
  }catch(error){
    if(read)try{
      const failures=cdp.events.slice(start).filter(event=>event.sessionId===sessionId&&event.method==='Network.responseReceived'
        &&event.params.response.status>=400).map(event=>({path:new URL(event.params.response.url).pathname,status:event.params.response.status}));
      const queries=cdp.events.slice(start).filter(event=>event.sessionId===sessionId&&event.method==='Network.requestWillBeSent'
        &&new URL(event.params.request.url).pathname.endsWith('/chart/data')).map(event=>{
          const request=event.params.request;let body={};try{body=JSON.parse(request.postData??'{}');}catch{}
          return {bodyHasFormData:Boolean(body.form_data),queryHasFormData:new URL(request.url).searchParams.has('form_data'),
            csrfHeaderPresent:Object.keys(request.headers).some(name=>name.toLowerCase()==='x-csrftoken')};
        });
      const favoriteQueries=cdp.events.slice(start).filter(event=>event.sessionId===sessionId&&event.method==='Network.requestWillBeSent'
        &&new URL(event.params.request.url).pathname.includes('/favorite_status/'))
        .map(event=>({q:new URL(event.params.request.url).searchParams.get('q')}));
      const text=await read('document.body.innerText.slice(0,2200)');mkdirSync('.tmp/superset-native',{recursive:true});
      writeFileSync('.tmp/superset-native/browser-diagnostics.json',JSON.stringify(safeResponse({failures,queries,favoriteQueries,text},[password]),null,2)+'\n');
    }catch{}
    throw error;
  }finally{
    if(cdp){try{await cdp.call('Browser.close');}catch{}cdp.socket.close();}
    if(browser?.processHandle.exitCode===null)browser.processHandle.kill();
    assert(resolve(profile).startsWith(resolve(join(tmpdir(),'aurevia-native-bi-'))));
    for(let i=0;i<10;i++){try{rmSync(profile,{recursive:true,force:true});break;}catch{await delay(250);}}
  }
}
