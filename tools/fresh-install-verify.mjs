import { spawnSync } from 'node:child_process';
import { readEnv } from './env-file.mjs';

const { values } = readEnv('.env');
const storeId = values.get('OPENFGA_STORE_ID');
const modelId = values.get('OPENFGA_MODEL_ID');
const apiUrl = process.env.FGA_API_URL ?? 'http://127.0.0.1:8080';
const failures = [];

function command(args) {
  const result = spawnSync('docker', ['compose', '--env-file', '.env', '--profile', 'superset', '-f', 'infra/docker-compose/compose.yml', ...args], {
    encoding: 'utf8', shell: process.platform === 'win32',
  });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout.trim();
}
async function check(relation, object) {
  const response = await fetch(`${apiUrl}/stores/${storeId}/check`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ authorization_model_id: modelId, tuple_key: { user: 'user:administrator', relation, object } }),
  });
  const body = await response.json();
  if (!response.ok || body.allowed !== true) failures.push(`administrator ${relation} ${object}: ${response.status} ${JSON.stringify(body)}`);
}

try {
  const psOutput = command(['ps', '--format', 'json']);
  const containers = psOutput.startsWith('[') ? JSON.parse(psOutput) : psOutput.split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
  const unhealthy = containers.filter(item => item.State === 'running' && item.Health && item.Health !== 'healthy').map(item => `${item.Service}=${item.Health}`);
  if (unhealthy.length) failures.push(`unhealthy containers: ${unhealthy.join(', ')}`);
  const sql = "select (select max(version::integer) from flyway_schema_history where success) || '|' || (select count(*) from outbox_event where dead_lettered_at is not null) || '|' || (select count(*) from outbox_event where processed_at is null and dead_lettered_at is null);";
  const state = command(['exec', '-T', 'auth-db', 'psql', '-U', 'aurevia', '-d', 'aurevia_auth', '-Atc', sql]).split(/\r?\n/).at(-1).split('|');
  if (Number(state[0]) < 31) failures.push(`Flyway is only at V${state[0]}`);
  if (state[1] !== '0') failures.push(`${state[1]} OpenFGA outbox events are dead-lettered`);
  if (state[2] !== '0') failures.push(`${state[2]} OpenFGA outbox events are still pending; retry after a few seconds`);
} catch (error) { failures.push(`Compose/database verification failed: ${error.message}`); }

await check('can_view', 'application:aurevia/admin');
await check('can_manage', 'application:aurevia');
await check('can_view', 'resource:business_resource/public-zone-logs');
await check('can_manage', 'resource:business_resource/public-zone-logs');

if (failures.length) {
  console.error('\nFresh-install verification FAILED:'); failures.forEach(item => console.error(` - ${item}`)); process.exit(1);
}
console.log('Fresh-install verification passed: migrations/outbox and administrator access for Admin, grants and audit logs are effective.');
