import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { readEnv } from './env-file.mjs';
import { verifyAdminNavigationInChrome } from './chrome-navigation-e2e.mjs';

const baseUrl = new URL(process.env.AUREVIA_BASE_URL ?? 'http://localhost:8443');
const username = process.env.AUREVIA_DEMO_USERNAME ?? 'administrator';
const localHosts = new Set(['localhost', '127.0.0.1', '::1']);
let password = process.env.AUREVIA_DEMO_PASSWORD;
const { values: localEnvironment } = readEnv('.env');
const redisPassword = process.env.REDIS_PASSWORD ?? localEnvironment.get('REDIS_PASSWORD') ?? 'change-me';
const composePrefix = ['compose', '--env-file', '.env', '-f', 'infra/docker-compose/compose.yml'];

if (!password && localHosts.has(baseUrl.hostname)) {
  const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
  const demoUser=realm.users?.find(user=>user.username===username);
  password=demoUser?.credentials?.find(credential=>credential.type==='password')?.value;
}
if (!password) {
  throw new Error('AUREVIA_DEMO_PASSWORD is required; do not put passwords in this script or command history.');
}

function decodeHtml(value) {
  return value.replaceAll('&amp;', '&').replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'").replaceAll('&lt;', '<').replaceAll('&gt;', '>');
}

function attribute(tag, name) {
  const match = tag.match(new RegExp(`\\b${name}\\s*=\\s*(["'])(.*?)\\1`, 'i'));
  return match ? decodeHtml(match[2]) : undefined;
}

class CookieJar {
  #cookies = new Map();

  accept(url, headers) {
    const values = typeof headers.getSetCookie === 'function'
      ? headers.getSetCookie() : [headers.get('set-cookie')].filter(Boolean);
    for (const value of values) {
      const parts = value.split(';').map(item => item.trim());
      const separator = parts[0].indexOf('=');
      if (separator < 1) continue;
      const name = parts[0].slice(0, separator);
      const cookieValue = parts[0].slice(separator + 1);
      const attributes = new Map(parts.slice(1).map(item => {
        const index = item.indexOf('=');
        return index < 0
          ? [item.toLowerCase(), true]
          : [item.slice(0, index).toLowerCase(), item.slice(index + 1)];
      }));
      const domain = String(attributes.get('domain') ?? url.hostname).replace(/^\./, '').toLowerCase();
      const defaultPath = url.pathname.includes('/')
        ? url.pathname.slice(0, url.pathname.lastIndexOf('/') + 1) : '/';
      const path = String(attributes.get('path') ?? defaultPath ?? '/');
      const key = `${domain}|${path}|${name}`;
      if (cookieValue === '' || attributes.get('max-age') === '0') this.#cookies.delete(key);
      else this.#cookies.set(key, { name, value: cookieValue, domain, path,
        secure: attributes.has('secure') });
    }
  }

  header(url) {
    const secureContext = url.protocol === 'https:' || localHosts.has(url.hostname);
    return [...this.#cookies.values()]
      .filter(cookie => (url.hostname === cookie.domain || url.hostname.endsWith(`.${cookie.domain}`))
        && url.pathname.startsWith(cookie.path) && (!cookie.secure || secureContext))
      .map(cookie => `${cookie.name}=${cookie.value}`).join('; ');
  }

  named(name) { return [...this.#cookies.values()].find(cookie => cookie.name === name); }
}

const cookies = new CookieJar();

function compose(args, { redisAuth = false } = {}) {
  return spawnSync('docker', [...composePrefix, ...args], {
    encoding: 'utf8', shell: false,
    env: redisAuth ? { ...process.env, REDISCLI_AUTH: redisPassword } : process.env,
  });
}

function redis(...args) {
  const result = compose([
    'exec', '-T', '-e', 'REDISCLI_AUTH', 'redis', 'redis-cli', '--raw', ...args,
  ], { redisAuth: true });
  if (result.status !== 0) {
    throw new Error(`Redis security assertion failed: ${result.stderr || result.stdout}`);
  }
  return result.stdout;
}

function sessionIdCandidates(cookieValue) {
  const candidates = new Set([cookieValue, decodeURIComponent(cookieValue)]);
  for (const value of [...candidates]) {
    try {
      const decoded = Buffer.from(value, 'base64url').toString('utf8');
      if (/^[0-9a-f-]{36}$/i.test(decoded)) candidates.add(decoded);
    } catch { /* not a base64-encoded Spring Session id */ }
  }
  return [...candidates];
}

async function request(input, options = {}) {
  let url = new URL(input, baseUrl);
  let method = options.method ?? 'GET';
  let body = options.body;
  let headers = new Headers(options.headers);
  for (let redirect = 0; redirect <= 12; redirect += 1) {
    const cookie = cookies.header(url);
    if (cookie) headers.set('cookie', cookie);
    else headers.delete('cookie');
    const response = await fetch(url, { method, body, headers, redirect: 'manual' });
    cookies.accept(url, response.headers);
    if (![301, 302, 303, 307, 308].includes(response.status)) return response;
    const location = response.headers.get('location');
    if (!location) throw new Error(`Redirect ${response.status} did not include Location`);
    url = new URL(location, url);
    if (response.status === 303 || ((response.status === 301 || response.status === 302)
        && method.toUpperCase() === 'POST')) {
      method = 'GET'; body = undefined;
      headers = new Headers([...headers].filter(([name]) => name.toLowerCase() !== 'content-type'));
    }
  }
  throw new Error('OIDC redirect limit exceeded');
}

async function json(path, correlationId) {
  const response = await request(path, { headers: {
    accept: 'application/json', ...(correlationId ? { 'x-correlation-id': correlationId } : {}),
  } });
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : null; }
  catch { body = text; }
  return { status: response.status, body };
}

function assertOpenApi(document,label,minimumOperations) {
  assert.match(String(document?.openapi),/^3\./,`${label} is not an OpenAPI 3 document`);
  const operations=Object.values(document.paths??{}).flatMap(path=>
    Object.entries(path).filter(([method])=>
      ['get','post','put','patch','delete','head','options'].includes(method))
      .map(([,operation])=>operation));
  assert(operations.length>=minimumOperations,
    `${label} exposes only ${operations.length} documented operations`);
  assert(operations.every(operation=>typeof operation.summary==='string'&&operation.summary.trim()),
    `${label} contains an operation without a summary`);
  assert(/[\u0600-\u06ff]/.test(JSON.stringify(document)),
    `${label} does not contain Persian documentation`);
  assert(operations.some(operation=>Object.values(operation.requestBody?.content??{})
    .some(media=>media.example!==undefined||media.examples!==undefined)),
    `${label} does not expose a professional request sample`);
}

const loginPage = await request('/oauth2/authorization/public-iam');
const loginHtml = await loginPage.text();
const formTags = loginHtml.match(/<form\b[^>]*>/gi) ?? [];
const loginForm = formTags.find(tag => /method\s*=\s*["']post["']/i.test(tag));
assert(loginForm, 'Keycloak login form was not found');
const action = attribute(loginForm, 'action');
assert(action, 'Keycloak login action was not found');

const fields = new URLSearchParams();
for (const tag of loginHtml.match(/<input\b[^>]*>/gi) ?? []) {
  const name = attribute(tag, 'name');
  const value = attribute(tag, 'value');
  if (name && value !== undefined && !fields.has(name)) fields.set(name, value);
}
fields.set('username', username);
fields.set('password', password);
fields.set('credentialId', fields.get('credentialId') ?? '');

const loginResult = await request(action, {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'text/html' },
  body: fields,
});
await loginResult.arrayBuffer();

const sessionCookie = cookies.named('AUREVIA_SESSION');
assert(sessionCookie, 'BFF did not issue AUREVIA_SESSION');
assert(!/^eyJ[^.]*\.[^.]+\.[^.]+$/.test(sessionCookie.value),
  'Browser session cookie unexpectedly resembles a JWT');

const me = await json('/api/v1/me');
assert.equal(me.status, 200, `Session identity failed with HTTP ${me.status}`);
assert.equal(me.body?.username, username, 'Session identity does not match the login user');

let redisSessionKey;
for (const namespace of ['aurevia:session:v2', 'aurevia:session']) {
  for (const candidate of sessionIdCandidates(sessionCookie.value)) {
    const key = `${namespace}:sessions:${candidate}`;
    if (redis('EXISTS', key).trim() === '1') redisSessionKey = key;
  }
}
assert(redisSessionKey, 'The opaque browser cookie did not resolve to a server-side Redis session');
const serializedSession = redis('HVALS', redisSessionKey);
assert(serializedSession.includes('SessionIdentity'),
  'Redis session is missing the minimal token-free identity');
assert(!/(?:OidcIdToken|DefaultOidcUser|OidcUserAuthority|OAuth2AuthorizedClient|BearerToken|RefreshToken|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)/.test(serializedSession),
  'Redis session contains OIDC credential-bearing state');

// A reused development Keycloak volume may predate the deterministic IDs in
// realm-aurevia.json. Refresh the development-only catalog after login so its
// direct grant follows the current validated OIDC subject. Production has no
// demo-catalog-init service and never performs username-based bootstrapping.
if (process.env.AUREVIA_SKIP_DEMO_CATALOG_REFRESH !== 'true') {
  const bootstrap = compose(['up', '--force-recreate', 'demo-catalog-init']);
  if (bootstrap.status !== 0) {
    throw new Error(`Development catalog refresh failed: ${bootstrap.stderr || bootstrap.stdout}`);
  }
  await new Promise(resolve => setTimeout(resolve, 7000));
}

const manifest=await json('/api/v1/me/manifest',`e2e-manifest-${Date.now()}`);
assert.equal(manifest.status,200,
  `Effective manifest failed with HTTP ${manifest.status}: ${JSON.stringify(manifest.body)}`);
const uiCatalog=await json('/api/ui/catalog',`e2e-ui-catalog-${Date.now()}`);
assert.equal(uiCatalog.status,200,
  `UI Catalog failed with HTTP ${uiCatalog.status}: ${JSON.stringify(uiCatalog.body)}`);
assert.equal(uiCatalog.body?.catalogVersion,manifest.body?.uiCatalog?.catalogVersion,
  'The dedicated UI Catalog and effective manifest versions differ');
assert.equal(uiCatalog.body?.contractVersion,'1.0',
  'The dedicated UI Catalog contract version is not supported by the Shell');
assert(Array.isArray(uiCatalog.body?.modules),'The dedicated UI Catalog has no modules array');
assert.equal(uiCatalog.body?.permissions,undefined,
  'The dedicated UI Catalog must not expose the permission map');
assert.equal(uiCatalog.body?.resourceTree,undefined,
  'The dedicated UI Catalog must not expose the authorization resource tree');
assert(Array.isArray(manifest.body?.panels)
  && manifest.body.panels.some(panel=>panel.code==='ADMIN'),
  'The runtime administrator cannot load the administration Micro Frontend');
const adminModule=manifest.body?.uiCatalog?.modules?.find(module=>module.moduleKey==='admin');
assert(adminModule,'The effective UI catalog does not contain the authorized ADMIN module');
assert.equal(manifest.body.uiCatalog.contractVersion,'1.0',
  'The effective UI catalog contract version is not supported by the Shell');
assert.equal(adminModule.routePrefix,'admin',
  'The ADMIN deployment registration did not provide the expected route prefix');
assert.equal(adminModule.defaultRouteId,'operator-guide',
  'The ADMIN effective default route is not deterministic');
assert.equal(adminModule.remote?.remoteName,'aurevia_admin',
  'The ADMIN Module Federation remote name is incorrect');
assert.equal(adminModule.remote?.exposedModule,'./bootstrap',
  'The ADMIN Module Federation exposed module is incorrect');
assert.equal(adminModule.remote?.contractVersion,'1.0',
  'The ADMIN artifact contract version is incorrect');
assert.equal(adminModule.remote?.artifactVersion,'0.5.0',
  'The ADMIN active artifact version is incorrect');
const expectedAdminMenuTitles=[
  'راهنما','واحدهای سازمانی','گروه‌ها','برنامه‌ها','تحلیل دسترسی','منابع و مجوزها',
  'میکروفرانت‌ها','مقصدها','مسیرها','عملیات API','اتصال‌ها','احراز هویت','تست اتصال',
  'محیط‌های گزارش','هویت و نقش','لاگ API','لاگ راهبری','گزارش‌ها',
];
assert.deepEqual(adminModule.menus.map(menu=>menu.title),expectedAdminMenuTitles,
  'The ADMIN runtime menu titles are stale');
assert(adminModule.menus.every(menu=>typeof menu.description==='string'&&menu.description.trim()),
  'The ADMIN runtime menu is missing descriptive tooltips');
assert.equal(adminModule.runtime?.apiBasePath,'/api/v1/admin',
  'The ADMIN runtime API base path is incorrect');
assert.equal(adminModule.routes?.length,18,
  'The administrator must receive all 18 authorized ADMIN routes');
assert(adminModule.routes.every(route=>typeof route.path==='string'&&!route.path.startsWith('/')),
  'The ADMIN artifact contains an absolute route path');
assert(adminModule.routes.some(route=>route.id===adminModule.defaultRouteId),
  'The ADMIN default route is not present in the effective routes');
assert(adminModule.menus?.every(menu=>adminModule.routes.some(route=>route.id===menu.routeId)),
  'The effective ADMIN menu references an unauthorized or missing route');

// Exercise the real server-side manifest fetch path. Fetch is intentionally a
// draft-only operation and must not mutate the effective resource catalog.
const panels=await json('/api/v1/admin/panels',`e2e-panels-${Date.now()}`);
assert.equal(panels.status,200,`Panel registry failed with HTTP ${panels.status}`);
const hrPanel=panels.body?.find(panel=>panel.code==='HR');
const adminPanel=panels.body?.find(panel=>panel.code==='ADMIN');
assert(hrPanel?.id,'HR panel is missing from the governance registry');
assert(adminPanel?.id,'ADMIN panel is missing from the governance registry');
assert.equal(adminPanel.active,true,'ADMIN panel must be active for frontend synchronization');
assert.equal(hrPanel.resource_definition_mode,'HYBRID','HR must exercise HYBRID governance');
assert.match(hrPanel.mf_manifest_url,/mf-manifest[.]json$/,
  'HR does not expose an independent MF Manifest URL');
assert.match(hrPanel.resource_manifest_url,/resource-manifest[.]json$/,
  'HR does not expose an independent Resource Manifest URL');
const csrf=await json('/api/v1/csrf',`e2e-csrf-${Date.now()}`);
assert.equal(csrf.status,200,'CSRF token could not be issued for manifest synchronization');
const resourcesBefore=await json('/api/v1/admin/resource-tree',`e2e-resource-before-${Date.now()}`);
assert.equal(resourcesBefore.status,200,'Resource tree could not be read before manifest fetch');
const fetchedDraftResponse=await request(
  `/api/v1/admin/panels/${hrPanel.id}/resource-manifests/fetch`,{
    method:'POST',headers:{accept:'application/json',[csrf.body.headerName]:csrf.body.token,
      'x-correlation-id':`e2e-manifest-fetch-${Date.now()}`},
  });
const fetchedDraftText=await fetchedDraftResponse.text();
let fetchedDraft;
try { fetchedDraft=JSON.parse(fetchedDraftText); }
catch { fetchedDraft=fetchedDraftText; }
assert.equal(fetchedDraftResponse.status,201,
  `Server-side manifest fetch failed: ${fetchedDraftText}`);
assert(['DRAFT','PUBLISHED'].includes(fetchedDraft?.workflowStatus),
  'Fetched resource manifest did not resolve to a valid immutable revision');
assert(Array.isArray(fetchedDraft?.changes),'Fetched resource manifest has no diff preview');
const draftPreview=await json(
  `/api/v1/admin/panels/${hrPanel.id}/resource-manifests/drafts/${fetchedDraft.id}`,
  `e2e-manifest-preview-${Date.now()}`);
assert.equal(draftPreview.status,200,'Staged resource manifest preview is unavailable');
assert.equal(draftPreview.body?.checksum,fetchedDraft.checksum,
  'Draft preview differs from the fetched immutable revision');
const resourcesAfter=await json('/api/v1/admin/resource-tree',`e2e-resource-after-${Date.now()}`);
assert.equal(resourcesAfter.status,200,'Resource tree could not be read after manifest fetch');
const resourceRevisions=items=>items.map(item=>`${item.id}:${item.version}`).sort();
assert.deepEqual(resourceRevisions(resourcesAfter.body),resourceRevisions(resourcesBefore.body),
  'Fetch/preview mutated the production Resource Catalog before approval');
let resourcePublish;
if(fetchedDraft.workflowStatus==='DRAFT') {
  const resourcePublishResponse=await request(
    `/api/v1/admin/panels/${hrPanel.id}/resource-manifests/drafts/${fetchedDraft.id}/publish`,{
      method:'POST',headers:{accept:'application/json',[csrf.body.headerName]:csrf.body.token,
        'x-correlation-id':`e2e-resource-manifest-publish-${Date.now()}`},
    });
  const resourcePublishText=await resourcePublishResponse.text();
  try { resourcePublish=JSON.parse(resourcePublishText); }
  catch { resourcePublish=resourcePublishText; }
  assert.equal(resourcePublishResponse.status,200,
    `Resource manifest publish failed: ${resourcePublishText}`);
} else {
  resourcePublish={workflowStatus:'PUBLISHED',idempotent:true};
}
assert.equal(resourcePublish?.workflowStatus,'PUBLISHED',
  'Resource manifest did not reach PUBLISHED state');
const publishedResources=await json('/api/v1/admin/resource-tree',
  `e2e-resource-published-${Date.now()}`);
assert.equal(publishedResources.status,200,'Published Resource Catalog could not be read');
assert(publishedResources.body.some(resource=>resource.resource_key==='field:hr.employee.salary-amount'),
  'The granular HR salary field resource was not published');

// Resource definitions now exist; frontend sync only validates their references and
// must not create or mutate them. Use the active ADMIN panel because a persisted local
// environment may intentionally have HR disabled. Repeating the same sync must converge.
const frontendSyncResponse=await request(
  `/api/v1/admin/panels/${adminPanel.id}/frontend-manifests/sync`,{
    method:'POST',headers:{accept:'application/json',[csrf.body.headerName]:csrf.body.token,
      'x-correlation-id':`e2e-mf-manifest-sync-${Date.now()}`},
  });
const frontendSyncText=await frontendSyncResponse.text();
let frontendSync;
try { frontendSync=JSON.parse(frontendSyncText); }
catch { frontendSync=frontendSyncText; }
assert.equal(frontendSyncResponse.status,200,
  `Frontend manifest sync failed: ${frontendSyncText}`);
assert.equal(frontendSync?.status,'SUCCESS','Frontend manifest sync did not succeed');
const repeatFrontendSyncResponse=await request(
  `/api/v1/admin/panels/${adminPanel.id}/frontend-manifests/sync`,{
    method:'POST',headers:{accept:'application/json',[csrf.body.headerName]:csrf.body.token,
      'x-correlation-id':`e2e-mf-manifest-resync-${Date.now()}`},
  });
const repeatFrontendSync=await repeatFrontendSyncResponse.json();
assert.equal(repeatFrontendSyncResponse.status,200,'Repeated frontend manifest sync failed');
assert.equal(repeatFrontendSync?.idempotent,true,
  'Repeated frontend manifest sync did not converge idempotently');

const shellDeepLink=await request('/admin/proxy-routes/routes',{headers:{accept:'text/html'}});
const shellHtml=await shellDeepLink.text();
assert.equal(shellDeepLink.status,200,'Shell deep link did not reach the SPA history fallback');
assert(/<title>Aurevia<\/title>/i.test(shellHtml),
  'Shell deep link returned an unexpected HTML application');
assert([...shellHtml.matchAll(/<script\b[^>]*\bsrc=(["']?)([^\s>"']+)\1/gi)]
  .every(match=>match[2].startsWith('/')),
  'Shell deep link contains a path-relative script and will render a blank page');
const standaloneAdmin=await fetch('http://localhost:3001/proxy-routes/routes',
  {headers:{accept:'text/html'}});
const standaloneAdminHtml=await standaloneAdmin.text();
assert.equal(standaloneAdmin.status,200,'Standalone ADMIN deep link did not reach its history fallback');
assert(/<title>Aurevia Admin<\/title>/i.test(standaloneAdminHtml),
  'Standalone ADMIN deep link returned an unexpected HTML application');
for(const module of manifest.body.uiCatalog.modules) {
  const remoteEntry=await fetch(module.remote.remoteEntryUrl,{headers:{accept:'application/javascript'}});
  const remoteSource=await remoteEntry.text();
  assert.equal(remoteEntry.status,200,`${module.moduleKey} remoteEntry is unavailable`);
  assert(remoteSource.length>1_000,`${module.moduleKey} remoteEntry is unexpectedly empty`);
}

let browserNavigation={status:'skipped'};
if(process.env.AUREVIA_BROWSER_E2E==='true') {
  browserNavigation={status:'verified',...(await verifyAdminNavigationInChrome({
    origin:baseUrl.origin,username,password,
    expectedTitles:expectedAdminMenuTitles,
    screenshotPath:process.env.AUREVIA_BROWSER_SCREENSHOT??'target/e2e/admin-navigation.png',
  }))};
}

const bffOpenApi=await json('/v3/api-docs',`e2e-bff-openapi-${Date.now()}`);
assert.equal(bffOpenApi.status,200,`BFF OpenAPI failed with HTTP ${bffOpenApi.status}`);
assertOpenApi(bffOpenApi.body,'BFF OpenAPI',13);
const authorizationOpenApi=await json('/api/v1/docs/authorization/openapi',
  `e2e-authz-openapi-${Date.now()}`);
assert.equal(authorizationOpenApi.status,200,
  `Authorization OpenAPI failed with HTTP ${authorizationOpenApi.status}`);
assertOpenApi(authorizationOpenApi.body,'Authorization OpenAPI',75);

const reports=await json('/api/v1/reports?instance=public-default',`e2e-reports-${Date.now()}`);
assert.equal(reports.status,200,
  `Superset catalog failed with HTTP ${reports.status}: ${JSON.stringify(reports.body)}`);
assert(Array.isArray(reports.body),'Superset catalog response is not an array');

let supersetRuntime='not-running';
const runningServices=compose(['ps','--status','running','--services']);
if(runningServices.status===0
    && runningServices.stdout.split(/\r?\n/).includes('operation-superset')) {
  const supersetHealth=await request('/api/v1/superset-instances/public-default/health',{
    headers:{accept:'text/plain','x-correlation-id':`e2e-superset-${Date.now()}`},
  });
  const healthBody=await supersetHealth.text();
  assert.equal(supersetHealth.status,200,
    `Dynamic Superset proxy failed with HTTP ${supersetHealth.status}: ${healthBody}`);
  supersetRuntime='verified';
}

// Make the first Legacy probe deterministic: it must acquire and encrypt a new
// token, while the second request must reuse that same server-side cache entry.
const legacyProfileId = '45000000-0000-0000-0000-000000000002';
const clearLegacyCache = compose([
  'exec', '-T', '-e', 'REDISCLI_AUTH', 'redis', 'redis-cli', 'DEL',
  `legacy-token-vault:local:${legacyProfileId}`,
  `legacy-token-lock:local:${legacyProfileId}`,
], { redisAuth: true });
if (clearLegacyCache.status !== 0) {
  throw new Error(`Legacy cache reset failed: ${clearLegacyCache.stderr || clearLegacyCache.stdout}`);
}

const stamp = Date.now();
const probes = [
  ['legacy-miss', `/api/proxy/legacy-demo/ping`, `e2e-legacy-miss-${stamp}`,
    'legacy-demo', 'LEGACY_SERVICE_TOKEN'],
  ['legacy-hit', `/api/proxy/legacy-demo/ping`, `e2e-legacy-hit-${stamp}`,
    'legacy-demo', 'LEGACY_SERVICE_TOKEN'],
  ['oauth2', `/api/proxy/oauth2-demo/ping`, `e2e-oauth2-${stamp}`,
    'oauth2-demo', 'KEYCLOAK_ACCESS_TOKEN'],
];

const results = [];
for (const [scenario, path, correlationId, expectedService, expectedCredential] of probes) {
  const result = await json(path, correlationId);
  assert.equal(result.status, 200, `${scenario} failed with HTTP ${result.status}: ${JSON.stringify(result.body)}`);
  assert.equal(result.body?.service, expectedService, `${scenario} reached the wrong service`);
  assert.equal(result.body?.credentialType, expectedCredential, `${scenario} used the wrong credential`);
  assert.equal(result.body?.authenticated, true, `${scenario} was not authenticated upstream`);
  const serialized = JSON.stringify(result.body);
  assert(!/("(?:access|refresh|id)[_-]?token"\s*:|"authorization"\s*:|legacy-demo-token|eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)/i.test(serialized),
    `${scenario} response exposed credential material`);
  results.push({ scenario, status: result.status, correlationId,
    service: result.body.service, credentialType: result.body.credentialType });
}

const logoutResponse=await request('/auth/logout',{
  method:'POST',
  headers:{accept:'application/json',[csrf.body.headerName]:csrf.body.token},
});
assert.equal(logoutResponse.status,204,
  `Logout failed with HTTP ${logoutResponse.status}: ${await logoutResponse.text()}`);
const afterLogout=await json('/api/v1/me',`e2e-after-logout-${Date.now()}`);
assert.equal(afterLogout.status,401,'Logged-out session can still access the BFF API');

console.log(JSON.stringify({
  login: { username: me.body.username, sessionCookie: 'AUREVIA_SESSION', opaqueSession: true },
  serverSessionContainsTokenMaterial: false,
  tokenMaterialReturnedToClient: false,
  effectiveManifest: { administrationPanel: true, catalogContract: '1.0',
    adminArtifact: adminModule.remote.artifactVersion, adminRouteCount: adminModule.routes.length,
    relativeRoutes: true },
  swagger: { bffOperations: Object.values(bffOpenApi.body.paths).flatMap(Object.values)
      .filter(operation=>operation?.operationId).length,
    authorizationOperations: Object.values(authorizationOpenApi.body.paths).flatMap(Object.values)
      .filter(operation=>operation?.operationId).length, persianSamples: true },
  frontend: { shellDeepLink: '/admin/proxy-routes/routes', standaloneAdminDeepLink: true,
    dedicatedUiCatalog: true, browserNavigation,
    remoteEntries: manifest.body.uiCatalog.modules.length },
  manifestGovernance: { mode: hrPanel.resource_definition_mode,
    separateFrontendSync: true, frontendSyncIdempotent: repeatFrontendSync.idempotent,
    serverSideFetch: true, workflowStatus: fetchedDraft.workflowStatus,
    preview: true, productionTreeUnchangedBeforeApproval: true,
    resourcePublishStatus: resourcePublish.workflowStatus,
    resourceRevisionIdempotent: resourcePublish.idempotent===true,
    granularSalaryFieldPublished: true },
  superset: { catalogStatus: reports.status, operationRuntime: supersetRuntime },
  probes: results,
  logout: { status: logoutResponse.status, accessRevoked: true, postLogoutApiStatus: afterLogout.status },
}, null, 2));
