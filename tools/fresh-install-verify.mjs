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
let administratorSubject=null;
try {
  await waitForHealthyContainers();
  const sql = `select
    (select max(version::integer) from flyway_schema_history where success) || '|' ||
    (select count(*) from outbox_event where dead_lettered_at is not null) || '|' ||
    (select count(*) from outbox_event where processed_at is null and dead_lettered_at is null) || '|' ||
    coalesce((select subject_key from app_user where issuer='http://localhost:8180/realms/aurevia' and username='administrator' and status='ACTIVE' order by case when external_id=username then 1 else 0 end,updated_at desc limit 1),'') || '|' ||
    coalesce((select a.artifact_version from panel p join ui_module_artifact a on a.id=p.active_artifact_id where p.code='ADMIN'),'') || '|' ||
    coalesce((select a.contract_version from panel p join ui_module_artifact a on a.id=p.active_artifact_id where p.code='ADMIN'),'') || '|' ||
    coalesce((select jsonb_array_length(a.manifest_snapshot->'routes') from panel p join ui_module_artifact a on a.id=p.active_artifact_id where p.code='ADMIN'),0) || '|' ||
    coalesce((select count(*) from panel p join ui_module_artifact a on a.id=p.active_artifact_id cross join lateral jsonb_array_elements(a.manifest_snapshot->'routes') route where p.code='ADMIN' and route->>'path' like '/%'),0) || '|' ||
    coalesce((select a.manifest_snapshot#>>'{runtime,apiBasePath}' from panel p join ui_module_artifact a on a.id=p.active_artifact_id where p.code='ADMIN'),'') || '|' ||
    coalesce((select (a.manifest_snapshot ? 'routePrefix')::text from panel p join ui_module_artifact a on a.id=p.active_artifact_id where p.code='ADMIN'),'') || '|' ||
    coalesce((select external_id from app_user where issuer='http://localhost:8180/realms/aurevia' and username='administrator' and status='ACTIVE' order by case when external_id=username then 1 else 0 end,updated_at desc limit 1),'');`;
  const state = command(['exec', '-T', 'auth-db', 'psql', '-U', 'aurevia', '-d', 'aurevia_auth', '-Atc', sql]).split(/\r?\n/).at(-1).split('|');
  if (Number(state[0]) < 49) failures.push(`Flyway is only at V${state[0]}; V49 or newer is required`);
  if (state[1] !== '0') failures.push(`${state[1]} OpenFGA outbox events are dead-lettered`);
  if (state[2] !== '0') failures.push(`${state[2]} OpenFGA outbox events are still pending; retry after a few seconds`);
  if (!state[3]) failures.push('runtime development administrator canonical subject is missing');
  else administratorUser=`user:${state[3]}`;
  if (state[4] !== '0.2.0') failures.push(`ADMIN active artifact is ${state[4] || 'missing'}; expected 0.2.0`);
  if (state[5] !== '1.0') failures.push(`ADMIN manifest contract is ${state[5] || 'missing'}; expected 1.0`);
  if (state[6] !== '18') failures.push(`ADMIN manifest exposes ${state[6] || '0'} routes; expected 18`);
  if (state[7] !== '0') failures.push(`ADMIN manifest contains ${state[7]} absolute route path(s)`);
  if (state[8] !== '/api/v1/admin') failures.push(`ADMIN runtime apiBasePath is ${state[8] || 'missing'}`);
  if (state[9] !== 'false') failures.push('ADMIN artifact must not embed a deployment-specific routePrefix');
  administratorSubject=state[10]||null;
} catch (error) { failures.push(`Compose/database verification failed: ${error.message}`); }

if(administratorSubject) try {
  const driftResponse=command(['exec','-T','-e',`VERIFY_ACTOR_SUBJECT=${administratorSubject}`,
    'authorization-service','sh','-ec',
    'curl --fail --silent --show-error --user "$AUTH_INTERNAL_USER:$AUTH_INTERNAL_PASSWORD" --header "X-Actor-Issuer: http://localhost:8180/realms/aurevia" --header "X-Actor-Subject: $VERIFY_ACTOR_SUBJECT" --request POST "http://127.0.0.1:8082/internal/v1/registry/operations/openfga-reconcile?repair=false"']);
  const drift=JSON.parse(driftResponse);
  if (!drift.dryRun) failures.push('OpenFGA verification unexpectedly ran in repair mode');
  if (drift.missing?.length) failures.push(`${drift.missing.length} OpenFGA tuple(s) are missing`);
  if (drift.unexpected?.length) failures.push(`${drift.unexpected.length} unexpected OpenFGA tuple(s) exist`);
} catch (error) { failures.push(`OpenFGA drift verification failed: ${error.message}`); }

if(administratorUser) {
  await check(administratorUser,'can_view', 'application:aurevia/admin');
  await check(administratorUser,'can_manage', 'application:aurevia');
  await check(administratorUser,'can_view', 'resource:business_resource/public-zone-logs');
  await check(administratorUser,'can_manage', 'resource:business_resource/public-zone-logs');
  await check(administratorUser,'can_manage', 'resource:proxy.target');
  await check(administratorUser,'can_manage', 'resource:proxy.route');
  await check(administratorUser,'can_manage', 'resource:proxy.operation');
  await check(administratorUser,'can_manage', 'resource:integration.auth-profile');
}

if (failures.length) {
  console.error('\nFresh-install verification FAILED:'); failures.forEach(item => console.error(` - ${item}`)); process.exit(1);
}
console.log('Fresh-install verification passed: V49+, zero OpenFGA drift, outbox, ADMIN 0.2.0 manifest contract and runtime administrator access are effective.');
