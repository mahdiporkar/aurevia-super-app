import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { installedChrome, launchChrome, connectCdp, evaluate } from './chrome-navigation-e2e.mjs';
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
export async function verifySwaggerInChrome({origin,username,password}) {
  const executable=installedChrome();
  if(!executable){const error=new Error('Local Chrome/Edge is unavailable');error.code='E2E_DEPENDENCY';throw error;}
  const profile=mkdtempSync(join(tmpdir(),'aurevia-swagger-e2e-'));
  let chrome,cdp,read;
  try {
    chrome=await launchChrome(executable,profile);cdp=await connectCdp(chrome.websocketUrl);
    const {targetId}=await cdp.call('Target.createTarget',{url:'about:blank'});
    const {sessionId}=await cdp.call('Target.attachToTarget',{targetId,flatten:true});
    await cdp.call('Page.enable',{},sessionId);await cdp.call('Runtime.enable',{},sessionId);await cdp.call('Network.enable',{},sessionId);
    read=expression=>evaluate(cdp.call,sessionId,expression);
    async function until(expression,message) {
      for(let i=0;i<100;i++){if(await read(expression))return;await delay(250);}
      throw new Error(message);
    }
    await cdp.call('Page.navigate',{url:origin+'/swagger-ui.html'},sessionId);
    await until('Boolean(document.querySelector(\'input[name="password"]\'))','Keycloak browser login is unavailable');
    assert.equal(await read('location.origin'),'http://localhost:8180');
    await read('(function(){document.querySelector(\'input[name="username"]\').value='+JSON.stringify(username)
      +';document.querySelector(\'input[name="password"]\').value='+JSON.stringify(password)
      +';document.querySelector("form").requestSubmit();})()');
    await until('location.origin==='+JSON.stringify(origin),'Browser login did not return to BFF');
    const start=cdp.events.length;
    await cdp.call('Page.navigate',{url:origin+'/swagger-ui.html'},sessionId);
    await until('Boolean(window.ui?.specSelectors?.specJson()?.getIn(["info","title"]))','Swagger UI did not load a contract');
    async function execute(path,method,payload) {
      const marker='swagger-e2e-'+method+'-'+path.replace(/[^a-z0-9]/gi,'-');
      await until('(function(){const block=[...document.querySelectorAll(".opblock")].find(element=>'
        +'element.querySelector(".opblock-summary-method")?.textContent.trim().toLowerCase()==='+JSON.stringify(method)
        +'&&(element.querySelector(".opblock-summary-path")?.getAttribute("data-path")'
        +'??element.querySelector(".opblock-summary-path")?.textContent.trim())==='+JSON.stringify(path)
        +');if(!block)return false;block.dataset.swaggerE2e='+JSON.stringify(marker)+';return true;})()',
        'Swagger operation is unavailable: '+path);
      const selector='.opblock[data-swagger-e2e="'+marker+'"]';
      const quoted=JSON.stringify(selector);
      await until('Boolean(document.querySelector('+quoted+'))','Swagger operation is unavailable: '+path);
      await read('(function(){const block=document.querySelector('+quoted+');if(!block.classList.contains("is-open"))(block.querySelector(".opblock-summary-control")??block.querySelector(".opblock-summary")).click();})()');
      await until('Boolean(document.querySelector('+quoted+')?.querySelector(".try-out__btn, button.execute"))','Try it out button is unavailable: '+path);
      await read('(function(){const block=document.querySelector('+quoted+');const button=block.querySelector(".try-out__btn");if(button&&/try it out/i.test(button.textContent))button.click();})()');
      if(payload) {
        await until('Boolean(document.querySelector('+quoted+')?.querySelector("textarea"))','Request body editor is unavailable');
        await read('(function(){const field=document.querySelector('+quoted+').querySelector("textarea");'
          +'Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,"value").set.call(field,'+JSON.stringify(JSON.stringify(payload))+');'
          +'field.dispatchEvent(new Event("input",{bubbles:true}));field.dispatchEvent(new Event("change",{bubbles:true}));})()');
      }
      await until('Boolean(document.querySelector('+quoted+')?.querySelector("button.execute"))','Execute button is unavailable');
      await read('document.querySelector('+quoted+').querySelector("button.execute").click()');
      await until('Boolean(document.querySelector('+quoted+')?.querySelector(".live-responses-table tbody .response-col_status"))','No Swagger response: '+path);
      const status=await read('document.querySelector('+quoted+').querySelector(".live-responses-table tbody .response-col_status").textContent.trim()');
      assert.equal(status,'200','Swagger Execute returned '+status+' for '+path);
      return {path,method,httpStatus:200};
    }
    const steps=[];
    steps.push(await execute('/api/v1/csrf','get'));
    const csrf=await read('fetch("/api/v1/csrf",{headers:{Accept:"application/json"}}).then(r=>r.json())');
    const identity=await read('fetch("/api/v1/me",{headers:{Accept:"application/json"}}).then(r=>r.json())');
    await read('(function(){const select=document.querySelector(".topbar select");select.value="/api/v1/docs/authorization/openapi";select.dispatchEvent(new Event("change",{bubbles:true}));})()');
    await until('/مجوزدهی/.test(window.ui?.specSelectors?.specJson()?.getIn(["info","title"])??"")','Authorization contract did not load');
    await read('window.ui.authActions.authorize({csrfToken:{name:"csrfToken",schema:{type:"apiKey",in:"header",name:"X-CSRF-TOKEN"},value:'+JSON.stringify(csrf.token)+'}})');
    steps.push(await execute('/internal/v1/registry/panels','get'));
    steps.push(await execute('/internal/v1/authorize/check','post',{
      subjectId:identity.subject,issuer:identity.issuer,resource:'application:aurevia/admin',action:'view',
      context:{channel:'swagger-browser'},correlationId:crypto.randomUUID()
    }));
    const storage=await read('({local:Object.fromEntries(Object.entries(localStorage)),session:Object.fromEntries(Object.entries(sessionStorage)),cookie:document.cookie,url:location.href})');
    assert(!/access.?token|refresh.?token|id.?token|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.|legacy_[A-Za-z0-9_-]{30,}/i.test(JSON.stringify(storage)));
    assert(!storage.cookie.includes('AUREVIA_SESSION'));
    const events=cdp.events.slice(start).filter(e=>e.sessionId===sessionId);
    const requests=events.filter(e=>e.method==='Network.requestWillBeSent').map(e=>e.params.request);
    const writeEvent=events.find(e=>e.method==='Network.requestWillBeSent'
      &&e.params.request.method==='POST'
      &&new URL(e.params.request.url).pathname.endsWith('/internal/v1/authorize/check'));
    assert(writeEvent,'Swagger did not send the POST request');
    const sent=JSON.parse(writeEvent.params.request.postData);
    assert.equal(sent.subjectId,identity.subject);assert.equal(sent.issuer,identity.issuer);
    assert(Object.entries(writeEvent.params.request.headers).some(([name,value])=>
      name.toLowerCase()==='x-csrf-token'&&value===csrf.token),'Swagger POST omitted its CSRF header');
    const responseBody=await cdp.call('Network.getResponseBody',{requestId:writeEvent.params.requestId},sessionId);
    const decision=JSON.parse(responseBody.base64Encoded?Buffer.from(responseBody.body,'base64').toString('utf8'):responseBody.body);
    assert.equal(decision.result,'ALLOW','Swagger POST access check did not allow the current administrator');
    const networkRequests=requests.filter(request=>new URL(request.url).protocol!=='data:');
    for(const request of networkRequests){
      assert.equal(new URL(request.url).origin,origin,'Swagger contacted another origin');
      assert(!Object.keys(request.headers).some(header=>header.toLowerCase()==='authorization'),'Swagger sent a bearer/basic header');
    }
    assert(!events.some(e=>e.method==='Runtime.exceptionThrown'),'Swagger runtime exception');
    const screenshot=await cdp.call('Page.captureScreenshot',{format:'png'},sessionId);
    mkdirSync('target/swagger',{recursive:true});writeFileSync('target/swagger/swagger-execute.png',Buffer.from(screenshot.data,'base64'));
    return {steps,writeDecision:decision.result,requests:networkRequests.length,embeddedAssets:requests.length-networkRequests.length,sameOrigin:true,storageContainsCredentials:false,bearerOrBasicHeaders:false};
  } catch(error) {
    if(read)try{
      const diagnostics=await read('({title:window.ui?.specSelectors?.specJson()?.getIn(["info","title"]),blocks:[...document.querySelectorAll("[data-swagger-e2e]")].map(block=>({marker:block.dataset.swaggerE2e,classes:block.className,buttons:[...block.querySelectorAll("button")].map(button=>({classes:button.className,text:button.textContent.trim().slice(0,80)}))}))})');
      mkdirSync('.tmp/swagger',{recursive:true});writeFileSync('.tmp/swagger/browser-diagnostics.json',JSON.stringify(diagnostics,null,2)+'\n');
    }catch{}
    throw error;
  } finally {
    if(cdp){try{await cdp.call('Browser.close');}catch{}cdp.socket.close();}
    if(chrome?.processHandle.exitCode===null)chrome.processHandle.kill();
    assert(resolve(profile).startsWith(resolve(join(tmpdir(),'aurevia-swagger-e2e-'))));
    for(let i=0;i<10;i++){try{rmSync(profile,{recursive:true,force:true});break;}catch{await delay(250);}}
  }
}
