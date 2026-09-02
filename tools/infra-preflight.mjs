import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { readEnv } from './env-file.mjs';

function fail(items) {
  console.error('\nInfrastructure preflight failed:');
  items.forEach(item => console.error(` - ${item}`));
  console.error('\nFollow docs/fresh-install-validation-fa.md. For a new machine run: npm run openfga:bootstrap');
  process.exit(1);
}
const errors = [];
if (!existsSync('.env')) fail(['.env does not exist; copy .env.example to .env and set local secrets']);
const { values } = readEnv('.env');
for (const key of ['OPENFGA_STORE_ID', 'OPENFGA_MODEL_ID']) {
  const value = values.get(key) ?? '';
  if (!value || value.includes('created-by-') || value.includes('bootstrap-required')) errors.push(`${key} is not a real pinned ID`);
}
for (const path of ['apps/shell/dist', 'apps/mfe-admin/dist', 'apps/mfe-hr/dist', 'apps/mfe-finance/dist', 'apps/mfe-reports/dist']) {
  if (!existsSync(path)) errors.push(`${path} is missing; run npm ci && npm run build`);
}
const docker = spawnSync('docker', ['version', '--format', '{{.Server.Version}}'], { encoding: 'utf8', shell: process.platform === 'win32' });
if (docker.error || docker.status !== 0) errors.push('Docker Engine is not running or is inaccessible');
if (errors.length) fail(errors);

const apiUrl = process.env.FGA_API_URL ?? 'http://127.0.0.1:8080';
try {
  const storeId = values.get('OPENFGA_STORE_ID');
  const modelId = values.get('OPENFGA_MODEL_ID');
  const [health, store, model] = await Promise.all([
    fetch(`${apiUrl}/healthz`), fetch(`${apiUrl}/stores/${storeId}`),
    fetch(`${apiUrl}/stores/${storeId}/authorization-models/${modelId}`),
  ]);
  if (!health.ok) errors.push(`OpenFGA health returned HTTP ${health.status}`);
  if (!store.ok) errors.push(`OPENFGA_STORE_ID is not present at ${apiUrl} (HTTP ${store.status})`);
  if (!model.ok) errors.push(`OPENFGA_MODEL_ID is not present in configured store (HTTP ${model.status})`);
} catch (error) { errors.push(`cannot validate OpenFGA at ${apiUrl}: ${error.message}`); }
if (errors.length) fail(errors);
console.log('Infrastructure preflight passed: Docker, frontend artifacts, OpenFGA store and pinned model are valid.');
