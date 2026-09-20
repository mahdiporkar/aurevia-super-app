import assert from 'node:assert/strict';
import {readFileSync,mkdtempSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {installedChrome,launchChrome,connectCdp,evaluate} from './chrome-navigation-e2e.mjs';

const origin=process.env.AUREVIA_BASE_URL??'http://localhost:8443';
assert(['localhost','127.0.0.1'].includes(new URL(origin).hostname),'Local demo only');
const user=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'))
  .users.find(user=>user.username==='administrator');
const chrome=await launchChrome(installedChrome(),mkdtempSync(join(tmpdir(),'aurevia-logout-')));
const cdp=await connectCdp(chrome.websocketUrl);
try {
  const {targetId}=await cdp.call('Target.createTarget',{url:'about:blank'});
  const {sessionId}=await cdp.call('Target.attachToTarget',{targetId,flatten:true});
  const run=expression=>evaluate(cdp.call,sessionId,expression);
  const waitFor=async expression=>{
    for(let attempt=0;attempt<100;attempt++){
      if(await run(expression))return;
      await new Promise(resolve=>setTimeout(resolve,300));
    }
    throw new Error(`Timed out: ${expression}`);
  };
  await cdp.call('Page.enable',{},sessionId);
  await cdp.call('Network.enable',{},sessionId);
  await cdp.call('Page.navigate',{url:origin},sessionId);
  await waitFor(`!!document.querySelector('input[name="password"]')`);
  await run(`(()=>{document.querySelector('input[name="username"]').value=${JSON.stringify(user.username)};
    document.querySelector('input[name="password"]').value=${JSON.stringify(user.credentials.find(c=>c.type==='password').value)};
    document.querySelector('form').requestSubmit();})()`);
  await waitFor(`!!document.querySelector('.app-sidebar .ant-menu-item')`);
  await run(`[...document.querySelectorAll('.app-header button')].find(button=>button.textContent.trim()==='خروج').click()`);
  await waitFor(`location.pathname==='/signed-out' && !!document.querySelector('a[href="/auth/login"]')`);
  assert.equal(await run(`fetch('/api/me/context').then(r=>r.status)`),401);
  await cdp.call('Page.reload',{},sessionId);
  await waitFor(`location.pathname==='/signed-out' && !!document.querySelector('a[href="/auth/login"]')`);
  await new Promise(resolve=>setTimeout(resolve,1500));
  assert.equal(await run('location.pathname'),'/signed-out');
  assert.equal(await run(`fetch('/api/me/context').then(r=>r.status)`),401);
  await run(`document.querySelector('a[href="/auth/login"]').click()`);
  await waitFor(`!!document.querySelector('.app-header')`);
  assert.equal(await run(`fetch('/api/me/context').then(r=>r.status)`),200);
  console.log('PASS: logout button ends app session, reload stays signed out, explicit SSO login succeeds.');
} finally {
  await cdp.call('Browser.close').catch(()=>{});
  cdp.socket.close();
  chrome.processHandle.kill();
}
