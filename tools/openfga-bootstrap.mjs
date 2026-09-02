import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { readEnv, writeEnvValue } from './env-file.mjs';

const envFile = '.env';
const composeFile = 'infra/docker-compose/compose.yml';
const apiUrl = process.env.FGA_API_URL ?? 'http://127.0.0.1:8080';
const storeName = process.env.FGA_STORE_NAME ?? 'aurevia-local';

function fail(message) { console.error(`\nOpenFGA bootstrap failed: ${message}`); process.exit(1); }
function run(command, args, options = {}) {
  const result = spawnSync(command, args, { stdio: 'inherit', shell: process.platform === 'win32', ...options });
  if (result.error || result.status !== 0) fail(`${command} ${args.join(' ')} returned ${result.status ?? result.error?.message}`);
}
async function json(url, init) {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
  return response.json();
}
async function waitForOpenFga() {
  for (let attempt = 0; attempt < 40; attempt++) {
    try { const response = await fetch(`${apiUrl}/healthz`); if (response.ok) return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  fail(`health endpoint ${apiUrl}/healthz did not become ready`);
}

if (!existsSync(envFile)) fail('create .env from .env.example first');
if (!existsSync('infra/openfga/model.fga')) fail('infra/openfga/model.fga is missing');
run('docker', ['compose', '--env-file', envFile, '-f', composeFile, 'up', '-d', 'openfga-db', 'openfga-migrate', 'openfga']);
await waitForOpenFga();

let stores = await json(`${apiUrl}/stores?page_size=100`);
let store = stores.stores?.find(item => item.name === storeName);
if (!store) store = await json(`${apiUrl}/stores`, {
  method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ name: storeName }),
});

const probe = spawnSync('fga', ['version'], { encoding: 'utf8', shell: process.platform === 'win32' });
if (probe.error || probe.status !== 0) fail('OpenFGA CLI (fga) is required to compile model.fga; install the pinned CLI described in docs/fresh-install-validation-fa.md');
run('fga', ['model', 'write', '--store-id', store.id, '--file', 'infra/openfga/model.fga'], {
  env: { ...process.env, FGA_API_URL: apiUrl },
});
const models = await json(`${apiUrl}/stores/${store.id}/authorization-models?page_size=1`);
const modelId = models.authorization_models?.[0]?.id;
if (!modelId) fail('model was written but its ID could not be read back');

writeEnvValue(envFile, 'OPENFGA_STORE_ID', store.id);
writeEnvValue(envFile, 'OPENFGA_MODEL_ID', modelId);
console.log(`\nOpenFGA is ready. Store ${store.id}, model ${modelId} were written to .env.`);
console.log('Next: npm run infra:up');
