#!/usr/bin/env node
// PRODUCTION-style acceptance on a Core-only Compose stack (npm run infra:core:up + identity demo):
//   P1 the database was installed from the Core baseline and contains no demo/test artifact
//   P2 Core works: bootstrapped administrator, Admin Panel, /api/me/context, resource tree, OpenFGA
//   P3 dynamic registration: a real micro frontend (the Reports module served on :3004) is registered
//      through the Admin API, its MF manifest synced, a permission granted through Authorization
//      Service/OpenFGA, and the new panel appears in /api/me/context for that user.
// Usage: node tools/production-core-e2e.mjs   (requires mfe-reports served at http://localhost:3004)
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { Session } from './e2e-auth/session.mjs';
import { readEnv } from './env-file.mjs';

const origin = process.env.AUREVIA_BASE_URL ?? 'http://localhost:8443';
const reportsBase = process.env.AUREVIA_REPORTS_MFE_URL ?? 'http://localhost:3004';
const { values: env } = readEnv('.env');
const realm = JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json', 'utf8'));
const adminUser = realm.users.find(user => user.id === env.get('AUREVIA_BOOTSTRAP_ADMIN_SUB'));
const memberUser = realm.users.find(user => user.username === 'viewer');
const password = user => user.credentials.find(c => c.type === 'password').value;
const compose = ['compose', '--env-file', '.env', '-f', 'infra/docker-compose/compose.yml'];
const results = [];
const DEMO_KEYS = ['application:aurevia/hr', 'application:aurevia/finance', 'page:hr.employees', 'page:finance.payments',
  'external_resource:superset/hr-workforce', 'external_resource:superset/finance-executive', 'business_resource:employee',
  'business_resource:payment', 'api:integration.legacy-demo', 'api:integration.oauth2-demo', 'module:integration'];

function sql(query) {
  const result = spawnSync('docker', [...compose, 'exec', '-T', '-e', 'PGPASSWORD=' + env.get('POSTGRES_AUTH_PASSWORD'), 'auth-db',
    'psql', '-h', '127.0.0.1', '-U', 'aurevia', '-d', 'aurevia_auth', '-At', '-c', query], { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout.trim();
}
async function step(id, description, fn) {
  try { const evidence = await fn(); results.push({ id, description, status: 'PASS', evidence }); console.log(`PASS ${id} ${description}`); }
  catch (error) { results.push({ id, description, status: 'FAIL', error: String(error.message).slice(0, 1500) }); console.error(`FAIL ${id} ${description}\n     ${error.message}`); }
}
async function until(condition, message, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) { const value = await condition(); if (value) return value; await new Promise(r => setTimeout(r, 500)); }
  throw new Error(message);
}

// ------------------------------------------------------------------ P1 clean by construction
await step('P1', 'Fresh database was installed from the Core baseline, not the historical chain', async () => {
  const history = sql("select string_agg(type||':'||coalesce(version,'-'),',' order by installed_rank) from flyway_schema_history where success");
  assert.match(history, /SQL_BASELINE:79/);
  assert.doesNotMatch(history, /(^|,)SQL:/);
  return { history };
});
await step('P1b', 'No demo/test artifact exists in the database', async () => {
  const resources = sql('select string_agg(resource_key,\',\' order by resource_key) from resource').split(',');
  for (const key of DEMO_KEYS) assert(!resources.includes(key), `demo resource present: ${key}`);
  assert.equal(sql("select string_agg(code,',') from panel"), 'ADMIN');
  assert.equal(sql("select count(*) from app_user where external_id<>'" + adminUser.id + "'"), '0');
  assert.equal(sql("select string_agg(role_key,',' order by role_key) from application_role"), 'aurevia-administrator,superset-designer,superset-viewer');
  for (const table of ['service_target', 'proxy_route', 'route_operation', 'superset_instance', 'superset_asset', 'outbound_connection', 'identity_provider'])
    assert.equal(sql(`select count(*) from ${table}`), '0', `${table} is not empty`);
  assert.equal(sql("select count(*) from outbox_event where payload::text like '%/hr%' or payload::text like '%/finance%' or payload::text like '%test-%'"), '0');
  return { resources, panels: 'ADMIN' };
});
await step('P1c', 'Compose stack contains no demo/test service', async () => {
  const services = spawnSync('docker', [...compose, 'ps', '--services'], { encoding: 'utf8' }).stdout.split(/\r?\n/).filter(Boolean).sort();
  for (const forbidden of ['mock-hr', 'mock-finance', 'mock-legacy', 'mock-oauth', 'operation-gateway', 'demo-fixture-init', 'demo-catalog-init',
    'test-sso-service', 'test-legacy-service', 'mf-test-sso', 'mf-test-legacy', 'mfe-hr', 'mfe-finance']) assert(!services.includes(forbidden), forbidden);
  return { services };
});

// ------------------------------------------------------------------ P2 core works
const admin = new Session(origin);
let adminContext;
await step('P2', 'Bootstrapped administrator logs in; Admin Panel is the only registered micro frontend; resource tree is Core only', async () => {
  await admin.login(adminUser.username, password(adminUser));
  adminContext = await until(async () => { const c = await admin.json('/api/me/context'); return c.status === 200 && c.body.permissions?.['application:aurevia']?.includes('admin') ? c : null; },
    'administrator context did not report admin');
  assert.deepEqual(adminContext.body.allowedApplications, ['admin']);
  assert.deepEqual(adminContext.body.uiCatalog.modules.map(m => m.moduleKey), ['admin']);
  const tree = await admin.json('/api/v1/admin/resource-tree');
  assert.equal(tree.status, 200);
  const keys = tree.body.map(r => r.resource_key);
  for (const key of DEMO_KEYS) assert(!keys.includes(key));
  assert(keys.includes('application:aurevia') && keys.includes('application:aurevia/admin'));
  const panels = await admin.json('/api/v1/admin/panels');
  assert.deepEqual(panels.body.map(p => p.code), ['ADMIN']);
  const health = await fetch(`${origin}/actuator/health`).then(r => r.json()).catch(() => ({ status: 'UNKNOWN' }));
  return { allowedApplications: adminContext.body.allowedApplications, resourceKeys: keys, bffHealth: health.status };
});

// ------------------------------------------------------------------ P3 dynamic registration
const member = new Session(origin);
let panelId;
await step('P3', 'Register a real micro frontend (Reports) through the Admin API and sync its MF manifest', async () => {
  const created = await admin.json('/api/v1/admin/panels', 'POST', {
    code: 'REPORTS', nameFa: 'گزارش‌ها', nameEn: 'Reports', description: 'Registered on a clean Core installation',
    slug: 'reports', serviceSlug: 'reports', remoteName: 'aurevia_reports', defaultRouteId: 'index',
    remoteEntry: `${reportsBase}/remoteEntry.js`, exposedModule: './bootstrap', routeBasePath: '/reports',
    semanticVersion: '0.2.0', contractVersion: '1.0', integrity: null, resourceDefinitionMode: 'HYBRID',
    classification: 'REAL', mfManifestUrl: `${reportsBase}/mf-manifest.json`, resourceManifestUrl: `${reportsBase}/resource-manifest.json`,
    active: true, sortOrder: 40 });
  assert.equal(created.status, 201, JSON.stringify(created.body));
  panelId = created.body.id;
  const sync = await admin.json(`/api/v1/admin/panels/${panelId}/frontend-manifests/sync`, 'POST', {});
  assert.equal(sync.status, 200, JSON.stringify(sync.body));
  assert.equal(sync.body.status, 'SUCCESS');
  const artifacts = await admin.json(`/api/v1/admin/panels/${panelId}/artifacts`);
  assert(artifacts.body.some(a => (a.artifact_version ?? a.artifactVersion) === '0.2.0'));
  const context = await until(async () => { const c = await admin.json('/api/me/context'); return c.body.uiCatalog.modules.some(m => m.moduleKey === 'reports') ? c : null; },
    'registered module did not appear in the administrator context');
  return { panelId, sync: sync.body.status, modules: context.body.uiCatalog.modules.map(m => m.moduleKey) };
});
await step('P3b', 'A Keycloak user with no grants sees nothing; after an OpenFGA grant the new panel is visible in /api/me/context', async () => {
  await member.login(memberUser.username, password(memberUser));
  const before = await member.json('/api/me/context');
  assert.deepEqual(before.body.allowedApplications, []);
  const users = await admin.json('/api/v1/admin/users');
  const subject = users.body.find(u => u.external_id === memberUser.id);
  const resources = await admin.json('/api/v1/admin/resources');
  const actions = await admin.json('/api/v1/admin/actions');
  const reports = resources.body.find(r => r.resource_key === 'application:aurevia/reports');
  const view = actions.body.find(a => a.action_key === 'view');
  const grant = await admin.json('/api/v1/admin/grants', 'POST', { subjectType: 'USER', subjectId: subject.id, resourceId: reports.id, actionId: view.id });
  assert.equal(grant.status, 201, JSON.stringify(grant.body));
  const after = await until(async () => { const c = await member.json('/api/me/context'); return c.body.allowedApplications.includes('reports') ? c : null; },
    'granted reports application did not become visible');
  const module = after.body.uiCatalog.modules.find(m => m.moduleKey === 'reports');
  assert(module && module.routes.some(r => r.id === 'index'));
  assert(!after.body.allowedApplications.includes('admin'));
  return { grantId: grant.body.id, allowedApplications: after.body.allowedApplications, routes: module.routes.map(r => r.id) };
});

mkdirSync('target/production-core-e2e', { recursive: true });
writeFileSync('target/production-core-e2e/results.json', JSON.stringify({ origin, results }, null, 2));
const failed = results.filter(r => r.status === 'FAIL');
console.log(`\n${results.length - failed.length}/${results.length} steps passed; evidence in target/production-core-e2e/results.json`);
process.exit(failed.length ? 1 : 0);
