import { spawnSync } from 'node:child_process';
import { readEnv } from './env-file.mjs';

const { values } = readEnv('.env');
const storeId = values.get('OPENFGA_STORE_ID');
const modelId = values.get('OPENFGA_MODEL_ID');
const apiUrl = process.env.FGA_API_URL ?? 'http://127.0.0.1:8080';
const failures = [];

function command(args) {
  const result = spawnSync('docker', ['compose', '--env-file', '.env', '--profile', 'superset', '-f', 'infra/docker-compose/compose.yml', ...args], {
    encoding: 'utf8', shell: false,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout.trim();
}
async function check(user, relation, object) {
  const response = await fetch(`${apiUrl}/stores/${storeId}/check`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ authorization_model_id: modelId, tuple_key: { user, relation, object } }),
  });
  const body = await response.json();
  if (!response.ok || body.allowed !== true) failures.push(`administrator ${relation} ${object}: ${response.status} ${JSON.stringify(body)}`);
}

async function waitForHealthyContainers() {
  let unhealthy=[];
  for(let attempt=0;attempt<45;attempt++) {
    const psOutput=command(['ps','--format','json']);
    const containers=psOutput.startsWith('[')?JSON.parse(psOutput)
      :psOutput.split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line));
    unhealthy=containers.filter(item=>item.State==='running'&&item.Health&&item.Health!=='healthy')
      .map(item=>`${item.Service}=${item.Health}`);
    if(!unhealthy.length) return;
    await new Promise(resolve=>setTimeout(resolve,2000));
  }
  failures.push(`unhealthy containers: ${unhealthy.join(', ')}`);
}

let administratorUser=null;
try {
  await waitForHealthyContainers();
  const sql = "select (select max(version::integer) from flyway_schema_history where success) || '|' || (select count(*) from outbox_event where dead_lettered_at is not null) || '|' || (select count(*) from outbox_event where processed_at is null and dead_lettered_at is null) || '|' || coalesce((select subject_key from app_user where issuer='http://localhost:8180/realms/aurevia' and external_id='administrator' limit 1),'');";
  const state = command(['exec', '-T', 'auth-db', 'psql', '-U', 'aurevia', '-d', 'aurevia_auth', '-Atc', sql]).split(/\r?\n/).at(-1).split('|');
  if (Number(state[0]) < 31) failures.push(`Flyway is only at V${state[0]}`);
  if (state[1] !== '0') failures.push(`${state[1]} OpenFGA outbox events are dead-lettered`);
  if (state[2] !== '0') failures.push(`${state[2]} OpenFGA outbox events are still pending; retry after a few seconds`);
  if (!state[3]) failures.push('development administrator canonical subject is missing');
  else administratorUser=`user:${state[3]}`;
} catch (error) { failures.push(`Compose/database verification failed: ${error.message}`); }

if(administratorUser) {
  await check(administratorUser,'can_view', 'application:aurevia/admin');
  await check(administratorUser,'can_manage', 'application:aurevia');
  await check(administratorUser,'can_view', 'resource:business_resource/public-zone-logs');
  await check(administratorUser,'can_manage', 'resource:business_resource/public-zone-logs');
}

if (failures.length) {
  console.error('\nFresh-install verification FAILED:'); failures.forEach(item => console.error(` - ${item}`)); process.exit(1);
}
console.log('Fresh-install verification passed: migrations/outbox and administrator access for Admin, grants and audit logs are effective.');
