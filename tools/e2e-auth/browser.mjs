import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { installedChrome, launchChrome, connectCdp, evaluate } from '../chrome-navigation-e2e.mjs';

const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
export async function verifyAuthMicrofrontendsInChrome({origin,username,password,allowedModes}) {
  const executable=installedChrome();
  if(!executable) {
    const error=new Error('Chrome/Edge is not installed; set AUREVIA_CHROME_PATH to a browser executable');
    error.code='E2E_DEPENDENCY';throw error;
  }
  const profile=mkdtempSync(join(tmpdir(),'aurevia-auth-e2e-'));
  let chrome,cdp;
  try {
    chrome=await launchChrome(executable,profile);
    cdp=await connectCdp(chrome.websocketUrl);
    const {targetId}=await cdp.call('Target.createTarget',{url:'about:blank'});
    const {sessionId}=await cdp.call('Target.attachToTarget',{targetId,flatten:true});
    await cdp.call('Page.enable',{},sessionId);
    await cdp.call('Runtime.enable',{},sessionId);
    await cdp.call('Network.enable',{},sessionId);
    const read=expression=>evaluate(cdp.call,sessionId,expression);
    await cdp.call('Page.navigate',{url:origin+'/'},sessionId);
    let submitted=false,ready=false;
    for(let i=0;i<100;i++) {
      const state=await read(`({origin:location.origin,login:Boolean(document.querySelector('input[name="password"]')),shell:Boolean(document.querySelector('.app-shell'))})`);
      if(state.login&&!submitted) {
        assert.equal(state.origin,'http://localhost:8180','Unexpected browser login credential destination');
        await read(`(()=>{document.querySelector('input[name="username"]').value=${JSON.stringify(username)};document.querySelector('input[name="password"]').value=${JSON.stringify(password)};document.querySelector('form').requestSubmit();})()`);
        submitted=true;
      }
      if(submitted&&state.origin===origin) {
        const status=await read(`fetch('/api/me/context',{headers:{Accept:'application/json'}}).then(r=>r.status)`);
        if(status===200){ready=true;break;}
      }
      await delay(500);
    }
    assert(ready,'Browser OIDC session did not become ready');
    const start=cdp.events.length;
    const checks=[];
    for(const mode of ['sso','legacy']) {
      const allowed=allowedModes.includes(mode);
      if(allowed) {
        await cdp.call('Page.navigate',{url:origin+'/test-'+mode+'/home'},sessionId);
        let visible=false;
        for(let i=0;i<80;i++) {
          visible=await read(`Boolean(document.querySelector('[data-testid="test-${mode}"]'))`);
          if(visible)break;
          await delay(250);
        }
        assert(visible,'Authorized microfrontend did not render');
        assert.equal(await read(`document.querySelector('[data-testid="username"]').textContent`),username);
        const menu=await read(`[...document.querySelectorAll('.ant-menu-item')].map(n=>n.textContent.trim())`);
        assert(menu.includes(mode==='sso'?'SSO Test':'Legacy Test'),'Authorized navigation is missing');
        await read(`document.querySelector('[data-testid="test-${mode}"] button').click()`);
        let status;
        for(let i=0;i<80;i++) {
          status=await read(`document.querySelector('[data-testid="request-status"]')?.textContent`);
          if(status==='SUCCESS'||status==='FAILED')break;
          await delay(250);
        }
        assert.equal(status,'SUCCESS','Microfrontend downstream call failed');
        const screenshot=await cdp.call('Page.captureScreenshot',{format:'png'},sessionId);
        mkdirSync('target/e2e-auth',{recursive:true});
        writeFileSync(resolve('target/e2e-auth/'+username+'-'+mode+'.png'),Buffer.from(screenshot.data,'base64'));
      } else {
        const menus=await read(`[...document.querySelectorAll('.ant-menu-item')].map(n=>n.textContent.trim())`);
        assert(!menus.includes(mode==='sso'?'SSO Test':'Legacy Test'),'Unauthorized menu is visible');
        await cdp.call('Page.navigate',{url:origin+'/test-'+mode+'/home'},sessionId);
        await delay(1200);
        assert(!await read(`Boolean(document.querySelector('[data-testid="test-${mode}"]'))`),'Unauthorized remote rendered');
      }
      const apiStatus=await read(`fetch('/api/proxy/test-${mode}/api/test/whoami',{headers:{Accept:'application/json'}}).then(r=>r.status)`);
      assert.equal(apiStatus,allowed?200:403,'Browser backend authorization differs');
      checks.push({mode,visible:allowed,apiStatus});
    }
    const storage=await read(`({local:Object.fromEntries(Object.entries(localStorage)),session:Object.fromEntries(Object.entries(sessionStorage)),cookie:document.cookie,url:location.href})`);
    assert(!/access.?token|refresh.?token|id.?token|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.|legacy_[A-Za-z0-9_-]{30,}/i.test(JSON.stringify(storage)),'Browser storage/URL contains a credential');
    assert(!storage.cookie.includes('AUREVIA_SESSION'),'HttpOnly session exposed to JavaScript');
    const events=cdp.events.slice(start).filter(event=>event.sessionId===sessionId);
    const requests=events.filter(event=>event.method==='Network.requestWillBeSent').map(event=>event.params.request);
    for(const request of requests) {
      assert(!/access_token=|refresh_token=|id_token=/i.test(request.url),'Token in browser URL');
      assert(!Object.keys(request.headers).some(header=>header.toLowerCase()==='authorization'),'Browser sent a bearer token');
      if(request.url.includes('/api/'))assert.equal(new URL(request.url).origin,origin,'Browser bypassed the BFF');
      assert(!/test-(sso|legacy)-service|operation-gateway|:809[12]/.test(request.url),'Browser contacted a protected downstream');
    }
    assert(!events.some(event=>event.method==='Runtime.exceptionThrown'),'Browser runtime exception');
    return {checks,storageContainsCredentials:false,bearerHeadersFromBrowser:false,directDownstreamRequests:0,apiRequestCount:requests.filter(request=>request.url.includes('/api/')).length};
  } finally {
    if(cdp){try{await cdp.call('Browser.close');}catch{}cdp.socket.close();}
    if(chrome?.processHandle.exitCode===null)chrome.processHandle.kill();
    assert(profile.startsWith(join(tmpdir(),'aurevia-auth-e2e-')));
    for(let i=0;i<10;i++){try{rmSync(profile,{recursive:true,force:true});break;}catch{await delay(250);}}
  }
}
