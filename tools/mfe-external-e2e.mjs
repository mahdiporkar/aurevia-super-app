#!/usr/bin/env node
// Real E2E for an MFE hosted OUTSIDE the Compose network (the host's LAN IP), registered only
// through the supported Admin APIs, then loaded through the BFF with a real browser session.
//
// Prerequisites: core compose stack up with UI_ARTIFACT_NETWORK_POLICY=UNRESTRICTED, Keycloak
// (npm run identity:up), and the HR MFE built (apps/mfe-hr/dist). Restores the panel afterwards.
//
// Usage: node tools/mfe-external-e2e.mjs [--host 192.168.1.150] [--port 3999] [--keep]
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFileSync, readdirSync, existsSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, extname, normalize } from 'node:path';
import { spawnSync } from 'node:child_process';
import { readEnv } from './env-file.mjs';
import { Session } from './e2e-auth/session.mjs';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, all) =>
  a.startsWith('--') ? [a.slice(2), all[i + 1]?.startsWith('--') || all[i + 1] === undefined ? true : all[i + 1]] : []).filter(Boolean));
const host = typeof args.host === 'string' ? args.host : detectLanIp();
const port = Number(args.port ?? 3999);
const origin = process.env.AUREVIA_BASE_URL ?? 'http://localhost:8443';
const env = readEnv('.env').values;
const realm = JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json', 'utf8'));
const adminPassword = process.env.AUREVIA_DEMO_PASSWORD ?? realm.users.find(u => u.username === 'administrator').credentials[0].value;
const dist = 'apps/mfe-hr/dist';
const results = [];
const record = (id, expected, actual, ok) => { results.push({ id, expected, actual, status: ok ? 'PASS' : 'FAIL' }); console.log(`${ok ? 'PASS' : 'FAIL'} ${id}: ${actual}`); };

assert.ok(existsSync(join(dist, 'remoteEntry.js')), 'build apps/mfe-hr first');
assert.ok(host && !/^(127\.|localhost$)/.test(host), 'an external (non-loopback) host is required; pass --host');

// 1. Static server on the host LAN address, outside Docker.
const types = { '.js': 'application/javascript', '.json': 'application/json', '.css': 'text/css', '.html': 'text/html', '.txt': 'text/plain' };
const server = createServer((req, res) => {
  const path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^([/\\])+/, '');
  const file = join(dist, path);
  if (!file.startsWith(normalize(dist)) || !existsSync(file) || statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' });
  res.end(readFileSync(file));
});
await new Promise(resolve => server.listen(port, '0.0.0.0', resolve));
const externalBase = `http://${host}:${port}`;
console.log(`External MFE served at ${externalBase} (outside Docker)`);

// The supported Admin API: the BFF admin proxy (/api/v1/admin/**) with the administrator session.
const session = new Session(origin);
session.raw = async function (path) {
  const response = await this.request(path, { headers: { Accept: '*/*' } });
  return { status: response.status, headers: response.headers, body: await response.text() };
};
await session.login('administrator', adminPassword);
async function admin(path, method = 'GET', body) {
  return session.json(path.replace('/internal/v1/registry', '/api/v1/admin'), method, body);
}

let original, panelId;
try {
  // 2. Register: re-point the HR panel to the external host through the Admin API.
  const panels = await admin('/internal/v1/registry/panels');
  assert.equal(panels.status, 200);
  const hr = panels.body.find(p => p.slug === 'hr');
  assert.ok(hr, 'HR panel not registered');
  panelId = hr.id;
  original = hr;
  const request = {
    code: hr.code, nameFa: hr.name_fa ?? hr.nameFa, nameEn: hr.name_en ?? hr.nameEn, description: hr.description ?? '',
    slug: hr.slug, serviceSlug: hr.service_slug ?? hr.serviceSlug, remoteName: hr.remote_name ?? hr.remoteName,
    defaultRouteId: hr.default_route_id ?? hr.defaultRouteId,
    remoteEntry: `${externalBase}/remoteEntry.js`, exposedModule: hr.exposed_module ?? hr.exposedModule,
    routeBasePath: hr.route_base_path ?? hr.routeBasePath, semanticVersion: hr.semantic_version ?? hr.semanticVersion,
    contractVersion: hr.contract_version ?? hr.contractVersion, integrity: null,
    resourceDefinitionMode: hr.resource_definition_mode ?? hr.resourceDefinitionMode,
    classification: hr.classification, mfManifestUrl: `${externalBase}/mf-manifest.json`,
    resourceManifestUrl: `${externalBase}/resource-manifest.json`, active: true, sortOrder: hr.sort_order ?? hr.sortOrder ?? 0,
  };
  const updated = await admin(`/internal/v1/registry/panels/${panelId}?version=${hr.version}`, 'PUT', request);
  record('REGISTER-EXTERNAL-REMOTE', 'Admin API accepts a LAN-IP remoteEntry without allowlists', `HTTP ${updated.status} ${JSON.stringify(updated.body).slice(0, 120)}`, updated.status === 200);
  assert.equal(updated.status, 200);

  // 3. Sync MF manifest from the external host (declares localhost:3002; admin setting must win).
  const sync = await admin(`/internal/v1/registry/panels/${panelId}/frontend-manifests/sync`, 'POST', {});
  record('SYNC-MF-MANIFEST', 'Fetch + validate + artifact activation from the external host', `HTTP ${sync.status} ${JSON.stringify(sync.body).slice(0, 200)}`, sync.status === 200 && sync.body?.status === 'SUCCESS');
  const artifacts = await admin(`/internal/v1/registry/panels/${panelId}/artifacts`);
  const active = artifacts.body.find(a => a.active);
  record('ARTIFACT-ACTIVE', 'Active artifact points at the external remoteEntry', `${active?.remote_entry_url ?? active?.remoteEntryUrl} valid=${active?.validation_status ?? active?.validationStatus}`, !!active && String(active.remote_entry_url ?? active.remoteEntryUrl).startsWith(externalBase));

  // 4. Resource manifest fetch → draft → publish (admin-managed authorization catalog).
  const fetched = await admin(`/internal/v1/registry/panels/${panelId}/resource-manifests/fetch`, 'POST', {});
  const draftId = fetched.body?.draftId ?? fetched.body?.draft_id ?? fetched.body?.id;
  record('FETCH-RESOURCE-MANIFEST', 'Draft created from the external resource-manifest.json', `HTTP ${fetched.status} draft=${draftId}`, fetched.status < 300 && !!draftId);
  if (draftId) {
    const preview = await admin(`/internal/v1/registry/panels/${panelId}/resource-manifests/drafts/${draftId}`);
    record('PREVIEW-RESOURCE-MANIFEST', 'Draft diff is readable', `HTTP ${preview.status} ${JSON.stringify(preview.body).slice(0, 120)}`, preview.status === 200);
    // Publishing changes the authorization catalog (it deprecates resources the manifest no
    // longer declares). That is an administrator decision, so it is opt-in here.
    if (args.publish) {
      const published = await admin(`/internal/v1/registry/panels/${panelId}/resource-manifests/drafts/${draftId}/publish`, 'POST', {});
      record('PUBLISH-RESOURCE-MANIFEST', 'Draft publishes', `HTTP ${published.status} ${JSON.stringify(published.body).slice(0, 120)}`, published.status < 300);
    }
  }

  // 5. Login through the real BFF/Keycloak flow, read /api/me/context, load remoteEntry + chunk.
  const context = await session.json('/api/me/context');
  const module = context.body?.uiCatalog?.modules?.find(m => m.moduleKey === 'hr');
  // The browser never sees the origin: the BFF publishes its own proxy path for the artifact.
  record('CONTEXT-MODULE', 'HR module present in /api/me/context, served through the BFF proxy path', `${module?.remote?.remoteEntryUrl}`, !!module && String(module.remote?.remoteEntryUrl).startsWith('/api/mfe/hr/'));

  const entry = await session.raw('/api/mfe/hr/remoteEntry.js');
  record('BFF-REMOTE-ENTRY', 'remoteEntry.js served through the BFF from the external host', `HTTP ${entry.status} ${entry.headers.get('content-type')} ${entry.body.length}B`, entry.status === 200 && /javascript/.test(entry.headers.get('content-type') ?? ''));
  // Any content-hashed federated chunk next to remoteEntry.js; it must load relative to it.
  const chunk = readdirSync(dist).find(name => /^[0-9]+\.[0-9a-f]{16,}\.js$/.test(name));
  assert.ok(chunk, 'no federated chunk found in dist');
  const chunkResponse = await session.raw(`/api/mfe/hr/${chunk}`);
  record('BFF-FEDERATED-CHUNK', 'federated chunk resolved relative to remoteEntry through the BFF', `${chunk} HTTP ${chunkResponse.status} ${chunkResponse.body.length}B`, chunkResponse.status === 200);
  const escape = await session.raw('/api/mfe/hr/../remoteEntry.js');
  record('BFF-CHUNK-TRAVERSAL', 'traversal outside the artifact directory rejected', `HTTP ${escape.status}`, escape.status >= 400);

  // 6. Real browser: the Shell must render the externally hosted remote through the BFF only.
  if (process.env.AUREVIA_BROWSER_E2E !== 'false') {
    const browser = await renderInShell({ origin, username: 'administrator', password: adminPassword, externalBase });
    record('SHELL-RENDER-EXTERNAL-MFE', 'HR remote renders in the Shell; artifacts fetched via /api/mfe/hr/ only', JSON.stringify(browser), browser.rendered && browser.viaBff > 0 && browser.direct === 0);
  }

  // 7. Diagnosable failure: unreachable external host must not read as "policy rejected".
  const broken = await admin(`/internal/v1/registry/panels/${panelId}?version=${updated.body.version}`, 'PUT', { ...request, mfManifestUrl: `http://${host}:1/mf-manifest.json` });
  const failedSync = await admin(`/internal/v1/registry/panels/${panelId}/frontend-manifests/sync`, 'POST', {});
  record('ERROR-CONNECTION-VS-POLICY', 'connection failure reported as such', `HTTP ${failedSync.status} ${JSON.stringify(failedSync.body).slice(0, 160)}`, failedSync.status >= 400 && /connection|refused|unreachable|timed out/i.test(JSON.stringify(failedSync.body)));
  original.version = broken.body?.version ?? updated.body.version;
} finally {
  // 7. Restore the demo registration.
  if (original && !args.keep) {
    const latest = (await admin('/internal/v1/registry/panels')).body.find(p => p.id === panelId);
    const restore = await admin(`/internal/v1/registry/panels/${panelId}?version=${latest.version}`, 'PUT', {
      code: original.code, nameFa: original.name_fa ?? original.nameFa, nameEn: original.name_en ?? original.nameEn,
      description: original.description ?? '', slug: original.slug, serviceSlug: original.service_slug ?? original.serviceSlug,
      remoteName: original.remote_name ?? original.remoteName, defaultRouteId: original.default_route_id ?? original.defaultRouteId,
      remoteEntry: original.remote_entry_path ?? original.remoteEntryPath ?? original.remote_entry ?? original.remoteEntry,
      exposedModule: original.exposed_module ?? original.exposedModule, routeBasePath: original.route_base_path ?? original.routeBasePath,
      semanticVersion: original.semantic_version ?? original.semanticVersion, contractVersion: original.contract_version ?? original.contractVersion,
      integrity: original.integrity ?? null, resourceDefinitionMode: original.resource_definition_mode ?? original.resourceDefinitionMode,
      classification: original.classification, mfManifestUrl: original.mf_manifest_url ?? original.mfManifestUrl ?? null,
      resourceManifestUrl: original.resource_manifest_url ?? original.resourceManifestUrl ?? null, active: original.active, sortOrder: original.sort_order ?? original.sortOrder ?? 0,
    });
    const resync = restore.status === 200 ? await admin(`/internal/v1/registry/panels/${panelId}/frontend-manifests/sync`, 'POST', {}) : { status: 'skipped' };
    console.log(`restored HR panel: HTTP ${restore.status}, resync ${resync.status}`);
  }
  server.close();
  mkdirSync('target/mfe-external-e2e', { recursive: true });
  const counts = { PASS: results.filter(r => r.status === 'PASS').length, FAIL: results.filter(r => r.status === 'FAIL').length };
  writeFileSync('target/mfe-external-e2e/results.json', JSON.stringify({ runAt: new Date().toISOString(), externalBase, counts, results }, null, 2) + '\n');
  console.log(JSON.stringify(counts));
  process.exitCode = counts.FAIL === 0 ? 0 : 1;
}

function detectLanIp() {
  const out = spawnSync('node', ['-e', "console.log(Object.values(require('os').networkInterfaces()).flat().filter(i=>i.family==='IPv4'&&!i.internal&&!/^172\\.(1[6-9]|2\\d|3[01])\\./.test(i.address)).map(i=>i.address)[0]??'')"], { encoding: 'utf8' });
  return out.stdout.trim();
}

async function renderInShell({ origin, username, password, externalBase }) {
  const { installedChrome, launchChrome, connectCdp, evaluate } = await import('./chrome-navigation-e2e.mjs');
  const { mkdtempSync } = await import('node:fs');
  const { tmpdir } = await import('node:os');
  const executable = installedChrome();
  assert.ok(executable, 'Chrome/Edge is not installed; set AUREVIA_CHROME_PATH or AUREVIA_BROWSER_E2E=false');
  const delay = ms => new Promise(r => setTimeout(r, ms));
  const profile = mkdtempSync(join(tmpdir(), 'aurevia-mfe-e2e-'));
  let chrome, cdp;
  try {
    chrome = await launchChrome(executable, profile);
    cdp = await connectCdp(chrome.websocketUrl);
    const { targetId } = await cdp.call('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await cdp.call('Target.attachToTarget', { targetId, flatten: true });
    for (const domain of ['Page', 'Runtime', 'Network']) await cdp.call(`${domain}.enable`, {}, sessionId);
    const read = expression => evaluate(cdp.call, sessionId, expression);
    await cdp.call('Page.navigate', { url: origin + '/' }, sessionId);
    let submitted = false, ready = false;
    for (let i = 0; i < 100 && !ready; i++) {
      const state = await read(`({origin:location.origin,login:Boolean(document.querySelector('input[name="password"]'))})`);
      if (state.login && !submitted) {
        assert.equal(state.origin, 'http://localhost:8180');
        await read(`(()=>{document.querySelector('input[name="username"]').value=${JSON.stringify(username)};document.querySelector('input[name="password"]').value=${JSON.stringify(password)};document.querySelector('form').requestSubmit();})()`);
        submitted = true;
      }
      if (submitted && state.origin === origin) ready = (await read(`fetch('/api/me/context').then(r=>r.status)`)) === 200;
      if (!ready) await delay(500);
    }
    assert.ok(ready, 'browser session not ready');
    const start = cdp.events.length;
    await cdp.call('Page.navigate', { url: origin + '/hr/personal' }, sessionId);
    let rendered = false;
    for (let i = 0; i < 80 && !rendered; i++) {
      // The HR remote mounts antd content inside the shell outlet once its chunks executed.
      rendered = await read(`Boolean(document.querySelector('.app-shell') && (document.querySelector('.ant-table, .ant-card, .ant-list, [data-mfe="hr"]')))`);
      if (!rendered) await delay(250);
    }
    const screenshot = await cdp.call('Page.captureScreenshot', { format: 'png' }, sessionId);
    mkdirSync('target/mfe-external-e2e', { recursive: true });
    writeFileSync('target/mfe-external-e2e/shell-hr.png', Buffer.from(screenshot.data, 'base64'));
    const requests = cdp.events.slice(start).filter(e => e.sessionId === sessionId && e.method === 'Network.requestWillBeSent').map(e => e.params.request.url);
    const viaBff = requests.filter(url => url.startsWith(origin + '/api/mfe/hr/')).length;
    const direct = requests.filter(url => url.startsWith(externalBase)).length;
    const exceptions = cdp.events.slice(start).filter(e => e.sessionId === sessionId && e.method === 'Runtime.exceptionThrown').length;
    return { rendered, viaBff, direct, exceptions };
  } finally {
    if (cdp) { try { await cdp.call('Browser.close'); } catch {} cdp.socket.close(); }
    if (chrome?.processHandle.exitCode === null) chrome.processHandle.kill();
  }
}
