#!/usr/bin/env node
// End-to-end proof of the environment-based PRIMARY authentication architecture:
//   A. clean installation: env-only OIDC, no identity_provider row, one-time First Admin bootstrap
//   B. Create User through Aurevia -> Keycloak Admin REST API, then authorize it through OpenFGA
//   C. restart idempotency and revocation that survives restarts
// Requires the local Compose stack (npm run identity:up + npm run infra:up). Never prints secrets.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { Session, safeResponse } from './e2e-auth/session.mjs';
import { readEnv } from './env-file.mjs';

const origin = process.env.AUREVIA_BASE_URL ?? 'http://localhost:8443';
const keycloak = process.env.AUREVIA_KEYCLOAK_URL ?? 'http://localhost:8180';
const { values: env } = readEnv('.env');
const realm = JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json', 'utf8'));
const bootstrapSub = env.get('AUREVIA_BOOTSTRAP_ADMIN_SUB');
const issuer = env.get('OIDC_ISSUER_URI');
const adminUser = realm.users.find(user => user.id === bootstrapSub);
const viewerUser = realm.users.find(user => user.username === 'viewer');
const password = user => user.credentials.find(c => c.type === 'password').value;
const compose = ['compose', '--env-file', '.env', '-f', 'infra/docker-compose/compose.yml'];
const runId = Date.now().toString(36);
const results = [];
const secrets = [env.get('OIDC_CLIENT_SECRET'), env.get('KEYCLOAK_ADMIN_CLIENT_SECRET'), password(adminUser)];

assert(adminUser, 'AUREVIA_BOOTSTRAP_ADMIN_SUB must be the stable id of a realm fixture user');

function docker(args) {
  const result = spawnSync('docker', args, { encoding: 'utf8', shell: false, env: process.env });
  if (result.status !== 0) throw new Error(`docker ${args.slice(0, 3).join(' ')} failed: ${result.stderr || result.stdout}`);
  return result.stdout;
}
function sql(query) {
  return docker([...compose, 'exec', '-T', '-e', 'PGPASSWORD=' + env.get('POSTGRES_AUTH_PASSWORD'), 'auth-db',
    'psql', '-h', '127.0.0.1', '-U', 'aurevia', '-d', 'aurevia_auth', '-At', '-c', query]).trim();
}
function logs(service, since) { return docker([...compose, 'logs', '--since', since, service]); }
async function step(id, description, fn) {
  try {
    const evidence = await fn();
    results.push({ id, description, status: 'PASS', evidence });
    console.log(`PASS ${id} ${description}`);
  } catch (error) {
    results.push({ id, description, status: 'FAIL', error: String(error.message).slice(0, 1500) });
    console.error(`FAIL ${id} ${description}\n     ${error.message}`);
  }
}
async function until(condition, message, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await condition();
    if (value) return value;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(message);
}
async function restart(service) {
  const started = new Date().toISOString();
  docker([...compose, 'restart', service]);
  await until(() => {
    const state = docker(['inspect', '--format', '{{.State.Health.Status}}', `${process.env.COMPOSE_PROJECT_NAME ?? 'aurevia'}-${service}-1`]).trim();
    return state === 'healthy';
  }, `${service} did not become healthy after restart`, 180000);
  return started;
}
async function keycloakAdmin(path) {
  const token = await fetch(`${keycloak}/realms/master/protocol/openid-connect/token`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli',
      username: env.get('KEYCLOAK_ADMIN'), password: env.get('KEYCLOAK_ADMIN_PASSWORD') }),
  }).then(response => response.json());
  const response = await fetch(`${keycloak}/admin/realms/aurevia${path}`, { headers: { authorization: `Bearer ${token.access_token}` } });
  return { status: response.status, body: response.status === 204 ? null : await response.json().catch(() => null) };
}
const adminPermissions = context => context.body?.permissions?.['application:aurevia'] ?? [];

// ------------------------------------------------------------------ A. initial installation
const admin = new Session(origin);
await step('A1', 'PRIMARY OIDC comes from the environment; no identity_provider row exists for the primary issuer', async () => {
  const rows = sql(`select count(*) from identity_provider where issuer_url='${issuer}' or code='public-iam'`);
  assert.equal(rows, '0');
  const providers = await admin.json('/auth/providers');
  assert.equal(providers.status, 200);
  assert.deepEqual(providers.body.map(p => p.code), ['public-iam']);
  assert.equal(providers.body[0].issuer_url ?? providers.body[0].issuerUrl, issuer);
  return { identityProviderRows: rows, providers: safeResponse(providers.body, secrets) };
});
await step('A2', 'First Administrator bootstrap ran exactly once for the configured stable sub', async () => {
  assert.equal(sql("select count(*) from schema_version where component='first-administrator'"), '1');
  const user = sql(`select u.id||'|'||u.username||'|'||u.status from app_user u join external_identity e on e.user_id=u.id where e.issuer='${issuer}' and e.subject='${bootstrapSub}'`);
  assert(user, 'bootstrap subject is not linked to an app_user');
  const grants = sql(`select r.resource_key||'/'||a.action_key||'|'||g.status from authorization_grant g join resource r on r.id=g.resource_id join action a on a.id=g.action_id join external_identity e on e.user_id=g.subject_id and g.subject_type='USER' where e.subject='${bootstrapSub}'`);
  assert.equal(grants, 'application:aurevia/admin|ACTIVE');
  return { appUser: user, grants };
});
await step('A3', 'Initial administrator logs in through Keycloak; the session subject is the stable sub', async () => {
  const me = await admin.login(adminUser.username, password(adminUser));
  assert.equal(me.subject ?? me.sub, bootstrapSub);
  return safeResponse(me, secrets);
});
let adminContext;
await step('A4', '/api/me/context exposes platform administration and the Admin Panel including User Management', async () => {
  adminContext = await until(async () => {
    const context = await admin.json('/api/me/context');
    return context.status === 200 && adminPermissions(context).includes('admin') ? context : null;
  }, 'administrator context never reported application:aurevia admin');
  assert(adminContext.body.allowedApplications.includes('admin'), 'admin panel application is not allowed');
  const adminModule = adminContext.body.uiCatalog.modules.find(module => module.moduleKey === 'admin');
  assert(adminModule, 'admin module missing from effective catalog');
  assert(adminModule.routes.some(route => route.id === 'users'), 'users route missing from admin module');
  return { permissions: adminPermissions(adminContext), allowedApplications: adminContext.body.allowedApplications,
    adminRoutes: adminModule.routes.map(route => route.id) };
});
await step('A5', 'Missing OIDC configuration fails BFF startup with a clear message (container probe)', async () => {
  // Same runtime configuration as Compose, minus the client secret. Nothing else may explain the failure.
  const result = spawnSync('docker', ['run', '--rm', '--env-file', '.env', '-e', 'OIDC_CLIENT_SECRET=',
    '-e', 'OIDC_ENDPOINT_OVERRIDES_ENABLED=false', 'aurevia/superapp-bff:local'], { encoding: 'utf8', timeout: 180000 });
  const output = result.stdout + result.stderr;
  assert.notEqual(result.status, 0, 'BFF started without OIDC_CLIENT_SECRET');
  assert.match(output, /OIDC configuration is incomplete\. Missing required configuration: OIDC_CLIENT_SECRET/);
  return { exitCode: result.status, message: 'OIDC configuration is incomplete. Missing required configuration: OIDC_CLIENT_SECRET' };
});

// ------------------------------------------------------------------ B. Create User
const newUser = { username: `e2e.user.${runId}`, firstName: 'E2E', lastName: 'User', email: `e2e.user.${runId}@aurevia.test`,
  enabled: true, initialPassword: `Init-${runId}-Pass!` };
let created;
await step('B1', 'Administrator creates a Keycloak user through the Aurevia backend; response carries the stable id and no password', async () => {
  const response = await admin.json('/api/v1/admin/keycloak-users', 'POST', newUser);
  assert.equal(response.status, 201, `create returned ${response.status}: ${JSON.stringify(response.body)}`);
  created = response.body;
  assert.match(created.id, /^[0-9a-f-]{36}$/);
  assert.equal(created.username, newUser.username);
  assert(!JSON.stringify(created).includes(newUser.initialPassword));
  assert(!('initialPassword' in created));
  return safeResponse(created, [...secrets, newUser.initialPassword]);
});
await step('B2', 'The user really exists in Keycloak with that stable id (verified through the master admin API)', async () => {
  const user = await keycloakAdmin(`/users/${created.id}`);
  assert.equal(user.status, 200);
  assert.equal(user.body.username, newUser.username);
  assert.equal(user.body.email, newUser.email);
  return { id: user.body.id, username: user.body.username, enabled: user.body.enabled };
});
await step('B3', 'Duplicate username -> 409, invalid payload -> 400 (field names only)', async () => {
  const duplicate = await admin.json('/api/v1/admin/keycloak-users', 'POST', newUser);
  assert.equal(duplicate.status, 409);
  assert.equal(duplicate.body.code, 'KEYCLOAK_USER_CONFLICT');
  const invalid = await admin.json('/api/v1/admin/keycloak-users', 'POST', { ...newUser, username: 'has space', email: 'nope' });
  assert.equal(invalid.status, 400);
  assert.equal(invalid.body.code, 'INVALID_USER');
  assert(!JSON.stringify(invalid.body).includes(newUser.initialPassword));
  return { duplicate: duplicate.body, invalid: invalid.body };
});
await step('B4', 'A non-administrator Aurevia user cannot create Keycloak users (OpenFGA decision, not Keycloak roles)', async () => {
  const viewer = new Session(origin);
  await viewer.login(viewerUser.username, password(viewerUser));
  const response = await viewer.json('/api/v1/admin/keycloak-users', 'POST', { ...newUser, username: `denied.${runId}`, email: `denied.${runId}@aurevia.test` });
  assert.equal(response.status, 403);
  assert.equal(response.body.code, 'ACCESS_DENIED');
  const inKeycloak = await keycloakAdmin(`/users?username=denied.${runId}&exact=true`);
  assert.deepEqual(inKeycloak.body, []);
  return response.body;
});
const createdSession = new Session(origin);
await step('B5', 'The created user authenticates through Keycloak; Aurevia links it by sub and grants nothing implicitly', async () => {
  const me = await createdSession.login(newUser.username, newUser.initialPassword);
  assert.equal(me.subject ?? me.sub, created.id);
  const context = await createdSession.json('/api/me/context');
  assert.equal(context.status, 200);
  assert.deepEqual(context.body.allowedApplications, []);
  const linked = sql(`select u.id||'|'||u.username from app_user u join external_identity e on e.user_id=u.id where e.issuer='${issuer}' and e.subject='${created.id}'`);
  assert(linked.endsWith('|' + newUser.username));
  return { session: safeResponse(me, secrets), allowedApplications: context.body.allowedApplications, appUser: linked };
});
await step('B6', 'Administrator grants the new user a permission through Authorization Service/OpenFGA; the context reflects it', async () => {
  const users = await admin.json('/api/v1/admin/users');
  const target = users.body.find(user => user.external_id === created.id);
  assert(target, 'created user not listed in the Authorization Service');
  const resources = await admin.json('/api/v1/admin/resources');
  const actions = await admin.json('/api/v1/admin/actions');
  const hr = resources.body.find(resource => resource.resource_key === 'application:aurevia/hr');
  const view = actions.body.find(action => action.action_key === 'view');
  const grant = await admin.json('/api/v1/admin/grants', 'POST', { subjectType: 'USER', subjectId: target.id, resourceId: hr.id, actionId: view.id });
  assert.equal(grant.status, 201, JSON.stringify(grant.body));
  const context = await until(async () => {
    const value = await createdSession.json('/api/me/context');
    return value.body?.allowedApplications?.includes('hr') ? value : null;
  }, 'granted hr application did not appear in /api/me/context');
  assert(context.body.uiCatalog.modules.some(module => module.moduleKey === 'hr'), 'hr module missing after grant');
  assert(!context.body.allowedApplications.includes('admin'));
  return { grantId: grant.body.id, allowedApplications: context.body.allowedApplications };
});
await step('B7', 'No initial password or administrative secret is persisted or logged by Aurevia', async () => {
  const persisted = sql(`select count(*) from (select row_to_json(t)::text v from app_user t union all select row_to_json(t)::text from external_identity t union all select row_to_json(t)::text from audit_event t union all select row_to_json(t)::text from audit_log t union all select row_to_json(t)::text from api_log t) x where v like '%${newUser.initialPassword}%' or v like '%${env.get('KEYCLOAK_ADMIN_CLIENT_SECRET')}%'`);
  assert.equal(persisted, '0');
  const bffLogs = logs('aurevia-bff', '30m');
  for (const secret of [newUser.initialPassword, env.get('KEYCLOAK_ADMIN_CLIENT_SECRET'), env.get('OIDC_CLIENT_SECRET')]) {
    assert(!bffLogs.includes(secret), 'a secret value appeared in BFF logs');
  }
  assert(!/eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}/.test(bffLogs), 'a JWT appeared in BFF logs');
  return { persistedMatches: Number(persisted), logsChecked: true };
});

// ------------------------------------------------------------------ C. restart and revocation
const counts = () => sql("select (select count(*) from authorization_grant where status='ACTIVE')||'|'||(select count(*) from app_user)||'|'||(select count(*) from external_identity)||'|'||(select count(*) from schema_version where component='first-administrator')");
await step('C1', 'Restarting the Authorization Service does not duplicate identities, grants or bootstrap markers', async () => {
  const before = counts();
  const since = await restart('authorization-service');
  const after = counts();
  assert.equal(after, before);
  assert.match(logs('authorization-service', since), /First administrator bootstrap already completed/);
  return { before, after };
});
await step('C2', 'Revoking the bootstrap administrator grant through the normal authorization mechanism takes effect', async () => {
  const subjectId = sql(`select user_id from external_identity where issuer='${issuer}' and subject='${bootstrapSub}'`);
  const grants = await admin.json(`/api/v1/admin/users/${subjectId}/grants`);
  const adminGrant = grants.body.find(grant => grant.resource_key === 'application:aurevia' && grant.action_key === 'admin');
  assert(adminGrant, 'bootstrap admin grant missing');
  const revoke = await admin.json(`/api/v1/admin/grants/${adminGrant.id}`, 'DELETE');
  assert.equal(revoke.status, 204);
  await until(async () => !adminPermissions(await admin.json('/api/me/context')).includes('admin'),
    'admin permission remained after revocation');
  assert.equal(sql(`select count(*) from authorization_grant g join external_identity e on e.user_id=g.subject_id and g.subject_type='USER' where e.subject='${bootstrapSub}' and g.status='ACTIVE'`), '0');
  return { revokedGrant: adminGrant.id };
});
await step('C3', 'Restart with AUREVIA_BOOTSTRAP_ADMIN_SUB still configured does NOT restore the revoked admin grant', async () => {
  const since = await restart('authorization-service');
  assert.match(logs('authorization-service', since), /First administrator bootstrap already completed/);
  assert.equal(sql(`select count(*) from authorization_grant g join external_identity e on e.user_id=g.subject_id and g.subject_type='USER' where e.subject='${bootstrapSub}' and g.status='ACTIVE'`), '0');
  await new Promise(resolve => setTimeout(resolve, 3000));
  const context = await admin.json('/api/me/context');
  assert(!adminPermissions(context).includes('admin'), 'admin permission was restored by restart');
  assert(!context.body.allowedApplications.includes('admin'));
  return { permissions: adminPermissions(context), allowedApplications: context.body.allowedApplications,
    bootstrapEnv: docker([...compose, 'exec', '-T', 'authorization-service', 'sh', '-c', 'echo $AUREVIA_BOOTSTRAP_ADMIN_SUB']).trim() };
});

mkdirSync('target/primary-auth-e2e', { recursive: true });
writeFileSync('target/primary-auth-e2e/results.json', JSON.stringify({ runId, origin, results }, null, 2));
const failed = results.filter(result => result.status === 'FAIL');
console.log(`\n${results.length - failed.length}/${results.length} steps passed; evidence in target/primary-auth-e2e/results.json`);
process.exit(failed.length ? 1 : 0);
