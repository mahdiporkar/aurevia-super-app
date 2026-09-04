import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { readEnv } from './env-file.mjs';

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
assert(Array.isArray(manifest.body?.panels)
  && manifest.body.panels.some(panel=>panel.code==='ADMIN'),
  'The runtime administrator cannot load the administration Micro Frontend');

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

console.log(JSON.stringify({
  login: { username: me.body.username, sessionCookie: 'AUREVIA_SESSION', opaqueSession: true },
  serverSessionContainsTokenMaterial: false,
  tokenMaterialReturnedToClient: false,
  effectiveManifest: { administrationPanel: true },
  superset: { catalogStatus: reports.status, operationRuntime: supersetRuntime },
  probes: results,
}, null, 2));
