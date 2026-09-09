import {spawn} from 'node:child_process';
import {existsSync,mkdirSync,mkdtempSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {createServer} from 'node:net';
import {dirname,join,resolve} from 'node:path';
import {isDeepStrictEqual} from 'node:util';

const delay=milliseconds=>new Promise(resolveDelay=>setTimeout(resolveDelay,milliseconds));

function installedChrome() {
  const candidates=[process.env.AUREVIA_CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    '/usr/bin/google-chrome','/usr/bin/chromium','/usr/bin/chromium-browser'];
  return candidates.find(candidate=>candidate&&existsSync(candidate));
}

async function freePort() {
  const server=createServer();
  await new Promise((resolveListen,reject)=>{
    server.once('error',reject);
    server.listen(0,'127.0.0.1',resolveListen);
  });
  const {port}=server.address();
  await new Promise(resolveClose=>server.close(resolveClose));
  return port;
}

async function launchChrome(executable,profile) {
  const port=await freePort();
  const processHandle=spawn(executable,[
    '--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',
    '--disable-background-networking','--disable-extensions','--remote-allow-origins=*',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,'--window-size=1600,1100','about:blank',
  ],{stdio:['ignore','ignore','pipe'],windowsHide:true});
  let launchError;
  processHandle.once('error',error=>{launchError=error;});
  let websocketUrl;
  for(let attempt=0;attempt<100&&!websocketUrl;attempt++) {
    if(launchError)throw launchError;
    try {
      const response=await fetch(`http://127.0.0.1:${port}/json/version`);
      if(response.ok)websocketUrl=(await response.json()).webSocketDebuggerUrl;
    } catch { /* Browser endpoint is still starting. */ }
    if(!websocketUrl)await delay(200);
  }
  if(!websocketUrl)throw new Error(`Chrome DevTools endpoint did not start on port ${port}`);
  return {processHandle,websocketUrl};
}

async function connectCdp(websocketUrl) {
  const socket=new WebSocket(websocketUrl);
  await new Promise((resolveOpen,reject)=>{
    socket.addEventListener('open',resolveOpen,{once:true});
    socket.addEventListener('error',()=>reject(new Error('Chrome DevTools WebSocket connection failed')),{once:true});
  });
  let nextId=0;
  const pending=new Map();
  socket.addEventListener('message',event=>{
    const message=JSON.parse(String(event.data));
    if(!message.id)return;
    const callback=pending.get(message.id);
    if(!callback)return;
    pending.delete(message.id);
    if(message.error)callback.reject(new Error(`${message.error.message} (${message.error.code})`));
    else callback.resolve(message.result??{});
  });
  const call=(method,params={},sessionId)=>new Promise((resolveCall,reject)=>{
    const id=++nextId;
    pending.set(id,{resolve:resolveCall,reject});
    socket.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}));
  });
  return {socket,call};
}

async function evaluate(call,sessionId,expression) {
  const result=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true},sessionId);
  if(result.exceptionDetails)throw new Error(`Browser evaluation failed: ${result.exceptionDetails.text}`);
  return result.result?.value;
}

export async function verifyAdminNavigationInChrome({origin,username,password,expectedTitles,
    screenshotPath='target/e2e/admin-navigation.png'}) {
  const executable=installedChrome();
  if(!executable)throw new Error('Chrome or Edge is required when AUREVIA_BROWSER_E2E=true');
  const profile=mkdtempSync(join(tmpdir(),'aurevia-navigation-e2e-'));
  let chrome;
  let cdp;
  try {
    chrome=await launchChrome(executable,profile);
    cdp=await connectCdp(chrome.websocketUrl);
    const {targetId}=await cdp.call('Target.createTarget',{url:'about:blank'});
    const {sessionId}=await cdp.call('Target.attachToTarget',{targetId,flatten:true});
    await Promise.all([
      cdp.call('Network.enable',{},sessionId),
      cdp.call('Page.enable',{},sessionId),
      cdp.call('Runtime.enable',{},sessionId),
    ]);
    const shellOrigin=new URL(origin);
    const targetUrl=new URL('/admin/operator-guide',shellOrigin).href;
    await cdp.call('Page.navigate',{url:targetUrl},sessionId);

    let state;
    let loginSubmitted=false;
    const deadline=Date.now()+45_000;
    while(Date.now()<deadline) {
      state=await evaluate(cdp.call,sessionId,`(()=>({
        readyState:document.readyState,
        url:location.href,
        menuTitles:[...document.querySelectorAll('.nav-menu-label')].map(node=>node.textContent?.trim()).filter(Boolean),
        menuItems:[...document.querySelectorAll('.ant-menu-item')].map(node=>({text:node.textContent?.trim(),className:node.className,menuId:node.getAttribute('data-menu-id')})),
        iconText:[...document.querySelectorAll('.nav-icon')].map(node=>node.textContent?.trim()).filter(Boolean),
        tabCount:document.querySelectorAll('.ant-tabs-tab').length,
        selected:document.querySelector('.ant-menu-item-selected .nav-menu-label')?.textContent?.trim()
          ??document.querySelector('.ant-menu-item-selected')?.textContent?.trim(),
        spinner:Boolean(document.querySelector('.remote-surface .ant-spin')),
        error:document.querySelector('.remote-surface .ant-alert-error')?.textContent?.trim(),
        loginForm:Boolean(document.querySelector('form input[name="username"]')&&document.querySelector('form input[name="password"]')),
        title:document.title,
        bodyText:document.body?.innerText?.slice(0,1000),
        rootHtml:document.querySelector('#root')?.innerHTML?.slice(0,1000),
        scripts:[...document.scripts].map(script=>script.src)
      }))()`);
      if(state?.loginForm&&!loginSubmitted) {
        if(!username||!password)throw new Error('Browser E2E credentials are required for the OIDC login');
        const submitted=await evaluate(cdp.call,sessionId,`(()=>{
          const form=document.querySelector('form');
          const username=document.querySelector('input[name="username"]');
          const password=document.querySelector('input[name="password"]');
          if(!form||!username||!password)return false;
          username.value=${JSON.stringify(username)};
          password.value=${JSON.stringify(password)};
          form.requestSubmit();return true;
        })()`);
        if(!submitted)throw new Error('Keycloak login form could not be submitted');
        loginSubmitted=true;
      }
      if(state?.readyState==='complete'&&state.menuTitles?.length===expectedTitles.length
          &&state.selected==='راهنما'&&!state.spinner)break;
      await delay(500);
    }
    const capture=await cdp.call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false},sessionId);
    const absoluteScreenshot=resolve(screenshotPath);
    mkdirSync(dirname(absoluteScreenshot),{recursive:true});
    writeFileSync(absoluteScreenshot,Buffer.from(capture.data,'base64'));
    if(!state||!isDeepStrictEqual(state.menuTitles,expectedTitles)) {
      throw new Error(`Rendered ADMIN navigation differs: ${JSON.stringify(state)}`);
    }
    if(state.url!==targetUrl)throw new Error(`Browser was redirected away from the ADMIN page: ${state.url}`);
    if(state.tabCount!==0)throw new Error(`ADMIN rendered ${state.tabCount} obsolete tab(s)`);
    if(state.iconText.length)throw new Error(`Navigation icon keys are visible as text: ${state.iconText.join(', ')}`);
    if(state.selected!=='راهنما')throw new Error(`Unexpected selected ADMIN menu item: ${JSON.stringify(state)}`);
    if(state.error)throw new Error(`ADMIN remote rendered an error: ${state.error}`);

    const firstItem=await evaluate(cdp.call,sessionId,`(()=>{const rect=document.querySelector('.nav-menu-label')?.getBoundingClientRect();return rect?{x:rect.left+rect.width/2,y:rect.top+rect.height/2}:null})()`);
    if(!firstItem)throw new Error('Cannot locate the first ADMIN navigation label');
    await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',x:firstItem.x,y:firstItem.y},sessionId);
    let tooltip='';
    for(let attempt=0;attempt<12&&!tooltip;attempt++) {
      await delay(150);
      tooltip=await evaluate(cdp.call,sessionId,
        `document.querySelector('.ant-tooltip-inner')?.textContent?.trim()??''`);
    }
    if(!tooltip)throw new Error('ADMIN navigation tooltip did not render on hover');

    const logoutStatus=await evaluate(cdp.call,sessionId,`(async()=>{
      const csrf=await fetch('/api/v1/csrf',{headers:{accept:'application/json'}}).then(response=>response.json());
      return fetch('/auth/logout',{method:'POST',headers:{accept:'application/json',[csrf.headerName]:csrf.token}})
        .then(response=>response.status);
    })()`);
    if(logoutStatus!==204)throw new Error(`Browser E2E logout failed with HTTP ${logoutStatus}`);
    const postLogoutStatus=await evaluate(cdp.call,sessionId,
      `fetch('/api/v1/me',{headers:{accept:'application/json'}}).then(response=>response.status)`);
    if(postLogoutStatus!==401)throw new Error(
      `Browser session still has API access after logout (HTTP ${postLogoutStatus})`);

    return {browser:executable,url:state.url,menuCount:state.menuTitles.length,
      selected:state.selected,tooltip,screenshot:absoluteScreenshot,logoutStatus,postLogoutStatus};
  } finally {
    if(cdp) {
      try {await cdp.call('Browser.close');} catch { /* Chrome may already be closed. */ }
      cdp.socket.close();
    }
    if(chrome?.processHandle.exitCode===null)chrome.processHandle.kill();
    if(profile.startsWith(join(tmpdir(),'aurevia-navigation-e2e-'))) {
      for(let attempt=0;attempt<20;attempt++) {
        try {rmSync(profile,{recursive:true,force:true});break;}
        catch(error) {
          if(attempt===19)console.warn(`Could not remove temporary Chrome profile: ${error.message}`);
          else await delay(250);
        }
      }
    }
  }
}
