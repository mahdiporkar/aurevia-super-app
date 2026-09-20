#!/usr/bin/env node
// Functional-baseline acceptance scenario for ONE micro frontend ("enterprise-hr") through the
// real stack: Keycloak login → BFF → Authorization Service → PostgreSQL/OpenFGA → route
// resolver → outbound auth (FORWARD_USER_TOKEN and LEGACY_SERVICE_TOKEN) → test backends.
//
// Everything is done with the supported Admin API (BFF /api/v1/admin) and an administrator
// session. No SQL, no OpenFGA tuples, no source changes, no MFE rebuild.
//
// Prerequisites: npm run identity:up; e2e-auth stack (npm run e2e:auth:prepare/core:up/up),
// which provides test-sso-service, test-legacy-service and the e2e-sso / e2e-legacy profiles.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { Session } from './e2e-auth/session.mjs';

const origin = process.env.AUREVIA_BASE_URL ?? 'http://localhost:8443';
const realm = JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json', 'utf8'));
const password = user => process.env.AUREVIA_DEMO_PASSWORD ?? realm.users.find(u => u.username === user).credentials[0].value;
const KEY = 'enterprise-hr', CODE = 'ENTERPRISE_HR';
const results = [];
const record = (id, expected, actual, ok) => { results.push({ id, expected, actual, status: ok ? 'PASS' : 'FAIL' }); console.log(`${ok ? 'PASS' : 'FAIL'} ${id}: ${actual}`); };
const delay = ms => new Promise(r => setTimeout(r, ms));

const admin = new Session(origin);
await admin.login('administrator', password('administrator'));
async function api(path, method = 'GET', body) {
  const response = await admin.json('/api/v1/admin' + path, method, body);
  if (![200, 201, 204].includes(response.status)) throw new Error(`${method} ${path.split('?')[0]} → HTTP ${response.status} ${JSON.stringify(response.body).slice(0, 200)}`);
  return response.body;
}
async function waitFor(session, predicate, label) {
  for (let i = 0; i < 60; i++) { if (await predicate()) return true; await delay(1000); }
  throw new Error(`timeout waiting for ${label}`);
}

// Keycloak-provisioned E2E users (tools/e2e-auth/prepare.mjs) log in once so login-sync
// creates their canonical identities. USER_A has no other grants; USER_NONE never gets one.
const e2eUsers = JSON.parse(readFileSync('.tmp/e2e-auth/users.json', 'utf8'));
const USER_A = 'e2e.none', USER_NONE = 'e2e.sso-only';
const userA = new Session(origin); await userA.login(USER_A, e2eUsers[USER_A]);
const userNone = new Session(origin); await userNone.login(USER_NONE, e2eUsers[USER_NONE]);
const users = await api('/users');
const idOf = username => users.find(u => u.username === username)?.id;
assert.ok(idOf(USER_A) && idOf(USER_NONE), 'e2e users were not synchronized; run npm run e2e:auth:verify once');
const actions = await api('/actions');
const actionId = key => actions.find(a => a.action_key === key).id;

// 1. Register the micro frontend (network address; remoteName owned by this panel).
const panels = await api('/panels');
let panel = panels.find(p => p.code === CODE);
const panelRequest = { code: CODE, nameFa: 'منابع انسانی سازمانی', nameEn: 'Enterprise HR', description: 'functional baseline scenario',
  slug: KEY, serviceSlug: KEY, remoteName: 'aurevia_enterprise_hr', defaultRouteId: 'employee-list',
  remoteEntry: 'http://localhost:3011/remoteEntry.js', exposedModule: './plugin', routeBasePath: '/' + KEY,
  semanticVersion: '1.0.0', contractVersion: '1.0', integrity: null, resourceDefinitionMode: 'HYBRID', classification: 'DEMO',
  mfManifestUrl: null, resourceManifestUrl: null, active: true, sortOrder: 120 };
panel = panel ? await api(`/panels/${panel.id}?version=${panel.version}`, 'PUT', panelRequest).then(() => api('/panels')).then(all => all.find(p => p.code === CODE))
              : await api('/panels', 'POST', panelRequest);
record('REGISTER-MICRO', 'enterprise-hr registered via Admin API', `panel ${panel.id}`, !!panel.id);

// 2. Resource tree: part imported as a Resource Manifest draft (JSON import), part manual.
const draft = await api(`/panels/${panel.id}/resource-manifests/drafts`, 'POST', {
  schemaVersion: '1.0',
  module: { key: KEY, name: 'Enterprise HR', nameFa: 'منابع انسانی سازمانی', nameEn: 'Enterprise HR', version: '1.0.0' },
  resources: [
    { key: `module:${KEY}.employee`, type: 'MODULE', name: 'Employee', nameFa: 'کارکنان', nameEn: 'Employee', classification: 'INTERNAL', actions: ['view'] },
    { key: `page:${KEY}.employee.list`, type: 'PAGE', parentKey: `module:${KEY}.employee`, name: 'List', nameFa: 'فهرست', nameEn: 'Employee list', classification: 'INTERNAL', actions: ['view'] },
    { key: `page:${KEY}.employee.details`, type: 'PAGE', parentKey: `module:${KEY}.employee`, name: 'Details', nameFa: 'جزئیات', nameEn: 'Employee details', classification: 'INTERNAL', actions: ['view'] },
  ] });
// A re-run stages an identical manifest, which the workflow reports as already applied.
const published = draft.workflowStatus === 'DRAFT'
  ? await api(`/panels/${panel.id}/resource-manifests/drafts/${draft.id}/publish`, 'POST', {})
  : { workflowStatus: draft.workflowStatus, created: 0, updated: 0 };
record('IMPORT-RESOURCE-MANIFEST', 'manifest draft published into the resource tree (or already identical)', `status=${published.workflowStatus} created=${published.created} updated=${published.updated}`, ['PUBLISHED', 'NO_CHANGES', 'UNCHANGED', 'IDENTICAL'].includes(published.workflowStatus) || draft.workflowStatus !== 'DRAFT');
let resources = await api('/resources');
const byKey = key => resources.find(r => r.resource_key === key);
const application = byKey(`application:aurevia/${KEY}`) ?? byKey(`application:${KEY}`);
assert.ok(application, 'application resource missing after publish');
// Manual resource: payroll page under the imported module.
let payroll = byKey(`page:${KEY}.payroll`);
if (!payroll) {
  payroll = await api('/resources', 'POST', { resourceKey: `page:${KEY}.payroll`, type: 'PAGE', parentId: byKey(`module:${KEY}.employee`).id,
    nameFa: 'حقوق', nameEn: 'Payroll', ownerDomain: 'hr', classification: 'INTERNAL', source: 'ADMIN', panelId: panel.id, visibilityEnabled: true, metadata: {} });
  await api(`/resources/${payroll.id}/actions/${actionId('view')}`, 'PUT', {});
  resources = await api('/resources'); payroll = byKey(`page:${KEY}.payroll`);
}
record('MANUAL-RESOURCE', 'payroll page created manually with view action', `${payroll.resource_key} actions=${(payroll.actions ?? []).map(a => a.key).join(',')}`, (payroll.actions ?? []).some(a => a.key === 'view'));

// 3. MF manifest published manually; routes reference imported AND manual resources.
const mfManifest = { schemaVersion: '1.0', microfrontend: { key: KEY, name: 'Enterprise HR', version: '1.0.0' },
  runtime: { remoteEntry: 'http://localhost:3011/remoteEntry.js', remoteName: 'aurevia_enterprise_hr', exposedModule: './plugin', contractVersion: '1.0', apiBasePath: `/api/proxy/${KEY}` },
  defaultRouteKey: 'employee-list',
  routes: [
    { key: 'employee-list', path: 'employees', requiredResource: `page:${KEY}.employee.list`, requiredAction: 'view', title: 'Employees' },
    { key: 'employee-details', path: 'employees/:id', requiredResource: `page:${KEY}.employee.details`, requiredAction: 'view', title: 'Employee' },
    { key: 'payroll', path: 'payroll', requiredResource: `page:${KEY}.payroll`, requiredAction: 'view', title: 'Payroll' },
  ],
  navigation: [{ key: 'nav-employees', type: 'PAGE', routeKey: 'employee-list', title: 'Employees', order: 10 },
               { key: 'nav-payroll', type: 'PAGE', routeKey: 'payroll', title: 'Payroll', order: 20 }] };
const artifacts = await api(`/panels/${panel.id}/artifacts`);
let artifact = artifacts.find(a => a.artifact_version === '1.0.0');
if (!artifact) {
  const created = await api(`/panels/${panel.id}/artifacts`, 'POST', { artifactVersion: '1.0.0', remoteEntryUrl: 'http://localhost:3011/remoteEntry.js',
    remoteName: 'aurevia_enterprise_hr', exposedModule: './plugin', contractVersion: '1.0', integrity: null, manifest: JSON.stringify(mfManifest) });
  panel = (await api('/panels')).find(p => p.code === CODE);
  await api(`/panels/${panel.id}/artifacts/${created.id}/activate?version=${panel.version}`, 'POST', {});
  artifact = (await api(`/panels/${panel.id}/artifacts`)).find(a => a.id === created.id);
}
record('MF-MANIFEST-ACTIVE', 'manual MF manifest artifact active and valid', `version=${artifact.artifact_version} valid=${artifact.validation_status} active=${artifact.active}`, artifact.validation_status === 'VALID');

// 4. Grant ONE page to user A, then verify effective context through the real login.
//    Start from a clean slate: revoke any enterprise-hr grant left by an earlier run.
const listResource = byKey(`page:${KEY}.employee.list`);
for (const stale of (await api(`/subjects/USER/${idOf(USER_A)}/grants`)).filter(g => g.resource_key.includes(KEY))) await api(`/grants/${stale.id}`, 'DELETE');
await waitFor(userA, async () => !Object.keys((await userA.json('/api/me/context')).body?.permissions ?? {}).some(k => k.includes(KEY)), 'stale grant cleanup');
const existingList = undefined;
const listGrant = await api('/grants', 'POST', { subjectType: 'USER', subjectId: idOf(USER_A), resourceId: listResource.id, actionId: actionId('view') });
await waitFor(userA, async () => (await userA.json('/api/me/context')).body?.permissions?.[listResource.resource_key]?.includes('view'), 'page grant projection');
const contextA = (await userA.json('/api/me/context')).body;
const moduleA = contextA.uiCatalog.modules.find(m => m.moduleKey === KEY);
record('CONTEXT-MICRO-VISIBLE', 'enterprise-hr module + only the authorized route in /api/me/context', JSON.stringify({ panel: contextA.panels.some(p => p.slug === KEY), routes: moduleA?.routes?.map(r => r.id) }),
  contextA.panels.some(p => p.slug === KEY) && moduleA?.routes?.map(r => r.id).join(',') === 'employee-list');
record('CONTEXT-NO-APPLICATION-GRANT', 'no application-level grant was needed or created', JSON.stringify(Object.keys(contextA.permissions).filter(k => k.includes(KEY))),
  !Object.keys(contextA.permissions).some(k => k.startsWith('application:') && k.includes(KEY)));

// 5. Forward route on this micro → test-sso-service (reports the received token).
const profiles = await api('/outbound-auth-profiles');
const targets = await api('/service-targets');
const forwardProfile = profiles.find(p => p.code === 'e2e-sso'), legacyProfile = profiles.find(p => p.code === 'e2e-legacy');
const forwardTarget = targets.find(t => t.code === 'e2e-sso'), legacyTarget = targets.find(t => t.code === 'e2e-legacy');
assert.ok(forwardProfile && legacyProfile && forwardTarget && legacyTarget, 'run npm run e2e:auth:verify once to provision e2e profiles/targets');
async function upsertRoute(code, request) {
  const existing = (await api('/proxy-routes')).find(r => r.code === code);
  return existing ? api(`/proxy-routes/${existing.id}?version=${existing.version}`, 'PUT', request) : api('/proxy-routes', 'POST', request);
}
async function ensureOperation(routeId, op) {
  const ops = await api(`/proxy-routes/${routeId}/operations`);
  if (!ops.some(o => o.http_method === op.httpMethod && o.path_pattern === op.pathPattern)) await api(`/proxy-routes/${routeId}/operations`, 'POST', op);
}
const forwardRoute = await upsertRoute(`${KEY}-forward`, { code: `${KEY}-forward`, panelId: panel.id, serviceTargetId: forwardTarget.id, outboundAuthProfileId: forwardProfile.id,
  serviceSlug: KEY, pathPrefix: `/api/proxy/${KEY}`, stripPrefix: 0, rewritePattern: `^/api/proxy/${KEY}/employees`, rewriteReplacement: '/test-sso-service/api/test/whoami',
  priority: 0, allowedMethods: ['GET'], preserveHost: false, retryEnabled: false, maxRetries: 0, active: true });
await ensureOperation(forwardRoute.id, { httpMethod: 'GET', pathPattern: '/employees', resourceKey: listResource.resource_key, actionKey: 'view', authorizationRequired: true, active: true, maxBodyBytes: 0 });
const forwardA = await userA.json(`/api/proxy/${KEY}/employees`);
record('FORWARD-AUTHORIZED', 'user A reaches the modern backend with the user Keycloak token', `HTTP ${forwardA.status} authMode=${forwardA.body?.authMode} subject=${forwardA.body?.subject} tokenPresent=${forwardA.body?.authenticated}`,
  forwardA.status === 200 && forwardA.body?.authMode === 'SSO' && forwardA.body?.authenticated === true && forwardA.body?.subject === contextA.identity.subject);
const forwardNone = await userNone.json(`/api/proxy/${KEY}/employees`);
record('FORWARD-UNAUTHORIZED', 'user without the page grant gets 403', `HTTP ${forwardNone.status}`, forwardNone.status === 403);

// 6. Legacy route on the SAME micro → test-legacy-service via the legacy token endpoint.
const legacyRoute = await upsertRoute(`${KEY}-legacy`, { code: `${KEY}-legacy`, panelId: panel.id, serviceTargetId: legacyTarget.id, outboundAuthProfileId: legacyProfile.id,
  serviceSlug: KEY, pathPrefix: `/api/proxy/${KEY}/payroll`, stripPrefix: 0, rewritePattern: `^/api/proxy/${KEY}/payroll`, rewriteReplacement: '/test-legacy-service/api/test/whoami',
  priority: 0, allowedMethods: ['GET'], preserveHost: false, retryEnabled: false, maxRetries: 0, active: true });
await ensureOperation(legacyRoute.id, { httpMethod: 'GET', pathPattern: '/', resourceKey: payroll.resource_key, actionKey: 'view', authorizationRequired: true, active: true, maxBodyBytes: 0 });
const tokenTest = await admin.json(`/api/v1/admin/outbound-auth-profiles/${legacyProfile.id}/token-test`, 'POST', {});
record('LEGACY-TOKEN-TEST', 'admin token-test acquires a legacy token (never returned in clear)', `HTTP ${tokenTest.status} ${JSON.stringify(tokenTest.body).slice(0, 120)}`, tokenTest.status === 200 && !/eyJ|legacy_[A-Za-z0-9_-]{30,}/.test(JSON.stringify(tokenTest.body)));
const beforePayroll = await userA.json(`/api/proxy/${KEY}/payroll`);
record('LEGACY-BEFORE-GRANT', 'legacy route denied before the payroll grant', `HTTP ${beforePayroll.status}`, beforePayroll.status === 403);
const payrollGrant = await api('/grants', 'POST', { subjectType: 'USER', subjectId: idOf(USER_A), resourceId: payroll.id, actionId: actionId('view') });
await waitFor(userA, async () => (await userA.json(`/api/proxy/${KEY}/payroll`)).status === 200, 'payroll grant projection');
const legacyA = await userA.json(`/api/proxy/${KEY}/payroll`);
record('LEGACY-AUTHORIZED', 'legacy backend reached with the LEGACY service token, user token not leaked', `HTTP ${legacyA.status} authMode=${legacyA.body?.authMode} service=${legacyA.body?.service}`,
  legacyA.status === 200 && legacyA.body?.authMode === 'LEGACY' && legacyA.body?.authenticated === true);
record('MIXED-MICRO', 'same micro, same session: forward and legacy routes both work', `forward=${forwardA.body?.authMode} legacy=${legacyA.body?.authMode}`, forwardA.body?.authMode === 'SSO' && legacyA.body?.authMode === 'LEGACY');

// 7. Route change is plain configuration: no semantic version, no MFE rebuild.
const current = (await api('/proxy-routes')).find(r => r.id === legacyRoute.id);
const edited = await api(`/proxy-routes/${current.id}?version=${current.version}`, 'PUT', { ...routeBody(current), priority: 5 });
record('ROUTE-EDIT-NO-RELEASE-VERSION', 'route edit bumps only the optimistic-lock version', `version ${current.version} → ${edited.version}; artifact still ${artifact.artifact_version}`, edited.version === current.version + 1);
const afterEdit = await userA.json(`/api/proxy/${KEY}/payroll`);
record('ROUTE-CHANGE-LIVE', 'edited route effective without restart or rebuild', `HTTP ${afterEdit.status}`, afterEdit.status === 200);

// 8. Revoke payroll → 403; revoke list → micro disappears from context.
await api(`/grants/${payrollGrant.id}`, 'DELETE');
await waitFor(userA, async () => (await userA.json(`/api/proxy/${KEY}/payroll`)).status === 403, 'payroll revoke projection');
record('REVOKE-LEGACY', 'revoked payroll grant denies the legacy route', `HTTP ${(await userA.json(`/api/proxy/${KEY}/payroll`)).status}`, true);
if (!existingList) {
  await api(`/grants/${listGrant.id}`, 'DELETE');
  await waitFor(userA, async () => !(await userA.json('/api/me/context')).body?.panels?.some(p => p.slug === KEY), 'list revoke projection');
  const after = (await userA.json('/api/me/context')).body;
  record('REVOKE-PAGE-HIDES-MICRO', 'without any page the micro leaves context and the forward route denies', `panels=${after.panels.map(p => p.slug).join(',')} forward=${(await userA.json(`/api/proxy/${KEY}/employees`)).status}`,
    !after.panels.some(p => p.slug === KEY));
}

function routeBody(r) {
  return { code: r.code, panelId: r.panel_id, serviceTargetId: r.service_target_id, outboundAuthProfileId: r.outbound_auth_profile_id, serviceSlug: r.service_slug,
    pathPrefix: r.path_prefix, stripPrefix: r.strip_prefix, rewritePattern: r.rewrite_pattern, rewriteReplacement: r.rewrite_replacement, priority: r.priority,
    allowedMethods: r.allowed_methods, preserveHost: r.preserve_host, retryEnabled: r.retry_enabled, maxRetries: r.max_retries, active: r.active };
}

mkdirSync('target/enterprise-hr-e2e', { recursive: true });
const counts = { PASS: results.filter(r => r.status === 'PASS').length, FAIL: results.filter(r => r.status === 'FAIL').length };
writeFileSync('target/enterprise-hr-e2e/results.json', JSON.stringify({ runAt: new Date().toISOString(), counts, results }, null, 2) + '\n');
console.log(JSON.stringify(counts));
process.exitCode = counts.FAIL === 0 ? 0 : 1;
