import assert from 'node:assert/strict';
import {mkdirSync,mkdtempSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {installedChrome,launchChrome,connectCdp,evaluate} from './chrome-navigation-e2e.mjs';
import {safeResponse} from './e2e-auth/session.mjs';

const delay=ms=>new Promise(done=>setTimeout(done,ms));
export async function pagesBrowser({origin,username,password},work){
  const executable=installedChrome();
  if(!executable){const error=new Error('Chrome/Edge is required');error.code='E2E_DEPENDENCY';throw error;}
  const profile=mkdtempSync(join(tmpdir(),'aurevia-pages-e2e-'));let chrome,cdp;
  try{
    chrome=await launchChrome(executable,profile);cdp=await connectCdp(chrome.websocketUrl);
    const {targetId}=await cdp.call('Target.createTarget',{url:'about:blank'});
    const {sessionId}=await cdp.call('Target.attachToTarget',{targetId,flatten:true});
    await Promise.all(['Page.enable','Network.enable','Runtime.enable','Log.enable'].map(method=>cdp.call(method,{},sessionId)));
    const read=expression=>evaluate(cdp.call,sessionId,expression);
    const visible='e=>Boolean(e.getClientRects().length)&&getComputedStyle(e).visibility!=="hidden"';
    const events=()=>cdp.events.filter(e=>e.sessionId===sessionId);
    const mark=()=>events().length;
    async function until(expression,message,timeout=45000){const deadline=Date.now()+timeout;
      while(Date.now()<deadline){if(await read(expression))return;await delay(150);}throw new Error(message);}
    async function click(selector){
      await read('(()=>{const e=[...document.querySelectorAll('+JSON.stringify(selector)+')].find('+visible+');e?.scrollIntoView({block:"nearest"})})()');
      await delay(150);
      const rect=await read('(()=>{const e=[...document.querySelectorAll('+JSON.stringify(selector)+')].find('+visible+');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})()');
      assert(rect,'Visible element missing: '+selector);
      await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',...rect},sessionId);
      await cdp.call('Input.dispatchMouseEvent',{type:'mousePressed',...rect,button:'left',clickCount:1},sessionId);
      await cdp.call('Input.dispatchMouseEvent',{type:'mouseReleased',...rect,button:'left',clickCount:1},sessionId);
    }
    async function text(label,scope='.remote-surface',selector='button',partial=false){
      const token='pages-e2e-click';
      const match=partial?'e.textContent.trim().includes('+JSON.stringify(label)+')':'e.textContent.trim()==='+JSON.stringify(label);
      const found=await read('(()=>{const e=[...document.querySelectorAll('+JSON.stringify(scope+' '+selector)+')].find(e=>('+visible+')(e)&&'+match+');if(!e)return false;e.setAttribute("data-e2e-click",'+JSON.stringify(token)+');return true})()');
      assert(found,'Visible action missing: '+label);await click('[data-e2e-click="'+token+'"]');
      await read('document.querySelector("[data-e2e-click]")?.removeAttribute("data-e2e-click")');
    }
    async function fill(selector,value){await click(selector);
      await cdp.call('Input.dispatchKeyEvent',{type:'keyDown',key:'a',code:'KeyA',windowsVirtualKeyCode:65,modifiers:2},sessionId);
      await cdp.call('Input.dispatchKeyEvent',{type:'keyUp',key:'a',code:'KeyA',windowsVirtualKeyCode:65,modifiers:2},sessionId);
      if(value)await cdp.call('Input.insertText',{text:String(value)},sessionId);
      else {await cdp.call('Input.dispatchKeyEvent',{type:'keyDown',key:'Backspace',code:'Backspace',windowsVirtualKeyCode:8},sessionId);
        await cdp.call('Input.dispatchKeyEvent',{type:'keyUp',key:'Backspace',code:'Backspace',windowsVirtualKeyCode:8},sessionId);}
    }
    async function select(selector,label){await click(selector);await until('Boolean([...document.querySelectorAll(".ant-select-item-option")].find(e=>('+visible+')(e)&&e.textContent.includes('+JSON.stringify(label)+')))','Select option missing: '+label);
      await delay(300);
      await text(label,'body','.ant-select-item-option',true);}
    function responses(start){return events().slice(start).filter(e=>e.method==='Network.responseReceived').map(e=>({path:new URL(e.params.response.url).pathname,status:e.params.response.status}));}
    async function response(start,path,status=200){const deadline=Date.now()+45000;
      while(Date.now()<deadline){const found=responses(start).find(r=>r.path===path);
        if(found){assert.equal(found.status,status,'API '+path);return found;}await delay(150);}throw new Error('API response missing: '+path);}
    async function clean(start,{allowedStatuses=[],allowedRuntimeExceptions=0}={}){await delay(400);
      const sweep=events().slice(start);
      const errors=responses(start).filter(r=>r.status>=400&&!allowedStatuses.includes(r.status));
      assert.deepEqual(errors,[],'Unexpected browser HTTP errors');
      assert.equal(sweep.filter(e=>e.method==='Runtime.exceptionThrown').length,allowedRuntimeExceptions,'Browser runtime exception');
      assert.equal(sweep.filter(e=>e.method==='Runtime.consoleAPICalled'&&e.params.type==='error').length,0,'Browser console error');
      const failures=sweep.filter(e=>e.method==='Network.loadingFailed'&&!e.params.canceled&&e.params.errorText!=='net::ERR_ABORTED');
      assert.equal(failures.length,0,'Browser network failure');
      for(const event of sweep.filter(e=>e.method==='Network.requestWillBeSent')){
        const request=event.params.request,url=new URL(request.url);
        if(url.pathname.startsWith('/api/')||url.pathname.includes('-micro/api/'))assert.equal(url.origin,origin,'API bypassed the BFF');
        assert(!Object.keys(request.headers).some(key=>key.toLowerCase()==='authorization'),'Browser sent an Authorization header');
      }
      const exposed=await read('({local:Object.fromEntries(Object.entries(localStorage)),session:Object.fromEntries(Object.entries(sessionStorage)),cookie:document.cookie})');
      safeResponse(exposed,[password]);assert(!exposed.cookie.includes('AUREVIA_SESSION'),'Session cookie exposed to JavaScript');
      assert(!/access.?token|refresh.?token|id.?token/i.test(JSON.stringify(exposed)),'OAuth token in browser storage');
      return {apiResponses:responses(start).filter(r=>r.path.includes('/api/')),runtimeExceptions:allowedRuntimeExceptions,consoleErrors:0,networkFailures:0,bffOnlyApis:true,credentialExposure:false};
    }
    async function screenshot(name){mkdirSync('target/pages-e2e/screenshots',{recursive:true});
      const result=await cdp.call('Page.captureScreenshot',{format:'png'},sessionId);
      writeFileSync('target/pages-e2e/screenshots/'+name+'.png',Buffer.from(result.data,'base64'));}
    async function navigate(path,dependencies=[]){const start=mark();await cdp.call('Page.navigate',{url:origin+path},sessionId);
      await until('location.origin==='+JSON.stringify(origin)+'&&Boolean(document.querySelector(".remote-surface"))','Shell did not load: '+path);
      for(const dependency of dependencies)await response(start,dependency);
      await until('Boolean(document.querySelector(".remote-surface")?.innerText.trim())&&!document.querySelector(".remote-surface .ant-spin-spinning,.remote-surface .ant-skeleton-active")','Page did not settle: '+path);
      const alerts=await read('[...document.querySelectorAll(".remote-surface .ant-alert-error,.ant-message-error")].map(e=>e.textContent.trim())');
      assert.deepEqual(alerts,[],'Page rendered an error');return start;}
    await cdp.call('Page.navigate',{url:origin+'/auth/login'},sessionId);
    await until('Boolean(document.querySelector("input[name=password]"))','OIDC login form missing');
    assert.equal(await read('location.origin'),'http://localhost:8180','Unexpected credential destination');
    await fill('input[name=username]',username);await fill('input[name=password]',password);await click('form input[type=submit],form button[type=submit]');
    await until('location.origin==='+JSON.stringify(origin)+'&&Boolean(document.querySelector(".app-shell"))','OIDC login failed');
    await until('!document.querySelector(".remote-surface .ant-spin-spinning")','Initial catalog did not settle');
    return await work({read,until,click,text,fill,select,response,responses,clean,navigate,screenshot,mark,cdp,sessionId,delay});
  }finally{
    if(cdp){try{await cdp.call('Browser.close');}catch{}cdp.socket.close();}
    if(chrome?.processHandle.exitCode===null)chrome.processHandle.kill();
    assert(resolve(profile).startsWith(resolve(join(tmpdir(),'aurevia-pages-e2e-'))));
    for(let i=0;i<15;i++){try{rmSync(profile,{recursive:true,force:true});break;}catch{await delay(250);}}
  }
}
