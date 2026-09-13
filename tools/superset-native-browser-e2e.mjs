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
    const paths=[path,path.endsWith('/')?path.slice(0,-1):path+'/'];
    const link='[...document.querySelectorAll("a")].find(a=>'+JSON.stringify(paths)+'.includes(new URL(a.href).pathname))';
    await until('Boolean('+link+')',
      'Reports MFE did not display the granted dashboard');
    const reportUrl=await read(link+'.href');
    const reportLinks=await read('[...document.querySelectorAll("a")].filter(a=>{const p=new URL(a.href).pathname;return p.startsWith("/superset/dashboard/")||p.startsWith("/explore/")}).length');
    assert.equal(reportLinks,3,'Reports MFE did not display the three granted reports');
    assert.equal(new URL(reportUrl).origin,origin);
    start=cdp.events.length;await cdp.call('Page.navigate',{url:reportUrl},sessionId);
    await until('document.body.innerText.includes("Aurevia Native BI Demo")','Superset dashboard did not render');
    await until('document.body.innerText.includes("Finance")&&document.body.innerText.includes("IT")','Real department data did not render in the table chart');
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
    await read('document.querySelector(".chart-container")?.scrollIntoView({block:"center"})');
    const screenshot=await cdp.call('Page.captureScreenshot',{format:'png'},sessionId);
    mkdirSync('target/superset-native',{recursive:true});writeFileSync('target/superset-native/dashboard.png',Buffer.from(screenshot.data,'base64'));
    return {reportsCatalogRendered:true,grantedReportLinks:reportLinks,dashboardRendered:true,tableRows:4,total:1150,chartDataResponses:queryResponses.length,
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
