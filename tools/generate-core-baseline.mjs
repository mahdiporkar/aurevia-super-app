#!/usr/bin/env node
// Generates the Aurevia Core Flyway baseline and the Development demo fixture from the versioned
// migration chain, in a throwaway PostgreSQL container:
//
//   db/migration/V1..Vn  ──migrate──►  throwaway DB  ──core-classification.sql──►  public  = Core
//                                                                                  dev_fixture = Demo
//   public       ──pg_dump──►  db/baseline/B<n>__aurevia_core_baseline.sql   (fresh installs)
//   dev_fixture  ──pg_dump──►  db/fixtures/development/010-development-demo.sql (opt-in, dev only)
//
// Both files are produced from the same run, so the fixture's foreign keys match the baseline.
// Re-run this tool whenever a new versioned migration changes Core seed data, then commit both files.
import { spawnSync } from 'node:child_process';
import { mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const resources = 'services/authorization-service/src/main/resources';
const migrationDir = resolve(resources, 'db/migration');
const baselineDir = resolve(resources, 'db/baseline');
const fixtureDir = resolve(resources, 'db/fixtures/development');
const versions = readdirSync(migrationDir).map(name => Number(/^V(\d+)__/.exec(name)?.[1] ?? -1)).filter(v => v >= 0);
const version = Math.max(...versions);
const stamp = Date.now().toString(36);
const network = `aurevia-baseline-${stamp}`;
const container = `aurevia-baseline-pg-${stamp}`;
const flywayImage = process.env.AUREVIA_FLYWAY_IMAGE ?? 'flyway/flyway:11';
const postgresImage = process.env.AUREVIA_POSTGRES_IMAGE ?? 'postgres:17.6-alpine';

function docker(args, { input, allowFailure = false } = {}) {
  const result = spawnSync('docker', args, { encoding: 'utf8', input, maxBuffer: 256 * 1024 * 1024 });
  if (result.status !== 0 && !allowFailure) {
    throw new Error(`docker ${args.slice(0, 4).join(' ')} failed:\n${result.stderr || result.stdout}`);
  }
  return result.stdout;
}
const psql = (sql) => docker(['exec', '-i', container, 'psql', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql]).trim();

/**
 * Removes psql meta-commands and session settings that a JDBC-executed migration cannot run, and the
 * hard-coded "public" schema so objects land in whatever schema Flyway is configured with.
 */
function cleanDump(text) {
  return text.split(/\r?\n/).filter(line =>
    !line.startsWith('\\') && !line.startsWith('SET ') && !line.includes("pg_catalog.set_config('search_path'")
    && !line.startsWith('COMMENT ON EXTENSION') && !/^CREATE SCHEMA public;/.test(line))
    .join('\n').replace(/\bpublic\.([A-Za-z_][A-Za-z0-9_]*)/g, '$1').replace(/\n{3,}/g, '\n\n');
}

function dumpFixtureTable(table) {
  return cleanDump(docker(['exec', container, 'pg_dump', '-U', 'postgres', '--data-only', '--column-inserts',
    '--on-conflict-do-nothing', '--no-owner', '--no-privileges', `--table=dev_fixture.${table}`, 'postgres']))
    .replaceAll(`INSERT INTO dev_fixture.${table} `, `INSERT INTO ${table} `);
}

try {
  docker(['network', 'create', network]);
  docker(['run', '-d', '--name', container, '--network', network, '-e', 'POSTGRES_PASSWORD=postgres', postgresImage]);
  for (let attempt = 0; ; attempt++) {
    if (spawnSync('docker', ['exec', container, 'pg_isready', '-U', 'postgres']).status === 0) break;
    if (attempt > 60) throw new Error('PostgreSQL did not become ready');
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
  }
  // Give the server a moment after pg_isready: the init script restarts it once.
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 3000);

  console.log(`Migrating the versioned chain (V1..V${version}) into a throwaway database`);
  docker(['run', '--rm', '--network', network, '-v', `${migrationDir.replace(/\\/g, '/')}:/flyway/sql:ro`, flywayImage,
    `-url=jdbc:postgresql://${container}:5432/postgres`, '-user=postgres', '-password=postgres',
    '-locations=filesystem:/flyway/sql', '-connectRetries=30', 'migrate']);
  const applied = psql('select max(version::int) from flyway_schema_history where success');
  if (Number(applied) !== version) throw new Error(`Chain applied V${applied}, expected V${version}`);

  console.log('Classifying Core vs Development demo rows');
  docker(['cp', resolve('tools/baseline/core-classification.sql'), `${container}:/tmp/classify.sql`]);
  docker(['exec', container, 'psql', '-U', 'postgres', '-d', 'postgres', '-f', '/tmp/classify.sql']);

  console.log('Dumping the Core baseline');
  const baseline = cleanDump(docker(['exec', container, 'pg_dump', '-U', 'postgres', '--schema=public',
    '--exclude-table=public.flyway_schema_history', '--no-owner', '--no-privileges', '--inserts',
    '--no-comments', 'postgres']));
  mkdirSync(baselineDir, { recursive: true });
  writeFileSync(resolve(baselineDir, `B${version}__aurevia_core_baseline.sql`), `-- Aurevia Core baseline (generated; do not edit by hand).
-- Equivalent to the versioned chain V1..V${version} with every development/demo row removed and the
-- OpenFGA outbox regenerated for Core relationships only. Flyway applies this file INSTEAD of
-- V1..V${version} on an empty database (fresh installation); databases that already carry the chain
-- ignore it. Regenerate with: node tools/generate-core-baseline.mjs
--
-- Contains: schema, actions, the Aurevia root/Admin/Reports application resources, the Admin Panel
-- (ADMIN panel + published MF manifests), the Superset integration root, platform roles and their
-- grants, the public-iam-forward outbound profile and schema_version markers.
-- Contains NO users, demo panels, demo resources, demo grants, demo routes or demo Superset assets.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

${baseline}
`);

  console.log('Dumping the Development demo fixture');
  const tables = ['panel', 'ui_module_artifact', 'ui_menu_override', 'resource', 'resource_action', 'resource_api_binding',
    'resource_external_binding', 'directory_group', 'app_user', 'external_identity', 'user_group_membership',
    'application_role', 'user_role_assignment', 'group_role_assignment', 'authorization_grant', 'outbound_auth_profile',
    'outbound_connection', 'service_target', 'proxy_route', 'route_operation', 'superset_instance', 'superset_proxy_mapping',
    'superset_asset', 'superset_subject_mapping', 'superset_access_sync', 'resource_manifest_import', 'outbox_event'];
  // panel.active_artifact_id references ui_module_artifact, which references panel: insert panels
  // without the active pointer, then restore it once the artifacts exist.
  psql('create table dev_fixture.panel_active as select id,active_artifact_id from dev_fixture.panel');
  psql('update dev_fixture.panel set active_artifact_id=null');
  const fixture = tables.map(dumpFixtureTable).join('\n');
  const panelActive = dumpFixtureTable('panel_active').replaceAll('INSERT INTO panel_active ', 'INSERT INTO pg_temp.fixture_panel_active ');
  mkdirSync(fixtureDir, { recursive: true });
  writeFileSync(resolve(fixtureDir, '010-development-demo.sql'), `-- Aurevia DEVELOPMENT demo fixture (generated; do not edit by hand).
-- HR/Finance/Reports demo micro frontends, demo resource tree, demo users, roles, grants, proxy
-- routes, Superset demo instances/assets and their OpenFGA outbox events, extracted from the
-- versioned chain V1..V${version}. Loaded ONLY by the development Compose overlay
-- (compose.development.yml, service demo-fixture-init). Never part of a production installation.
-- Idempotent: every statement is ON CONFLICT DO NOTHING. Applies only to databases created from the
-- Core baseline; a database that ran the historical chain already contains this data.
-- Regenerate with: node tools/generate-core-baseline.mjs
\\set ON_ERROR_STOP on
SELECT EXISTS(SELECT 1 FROM flyway_schema_history WHERE type='SQL_BASELINE') AS baseline_installed \\gset
\\if :baseline_installed
BEGIN;
CREATE TEMP TABLE fixture_panel_active(id uuid PRIMARY KEY, active_artifact_id uuid) ON COMMIT DROP;
${fixture}
${panelActive}
UPDATE panel p SET active_artifact_id=x.active_artifact_id,semantic_version=a.artifact_version
FROM pg_temp.fixture_panel_active x JOIN ui_module_artifact a ON a.id=x.active_artifact_id
WHERE x.id=p.id AND p.active_artifact_id IS NULL;
INSERT INTO schema_version(component,version) VALUES('development-demo-fixture','${version}')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
COMMIT;
\\else
\\echo 'development demo fixture skipped: this database carries the historical migration chain'
\\endif
`);
  const counts = psql(`select (select count(*) from resource)||' core resources, '||(select count(*) from dev_fixture.resource)||' demo resources, '||(select count(*) from outbox_event)||' core outbox events, '||(select count(*) from dev_fixture.outbox_event)||' demo outbox events'`);
  console.log(`Generated B${version}__aurevia_core_baseline.sql and 010-development-demo.sql (${counts})`);
} finally {
  docker(['rm', '-f', container], { allowFailure: true });
  docker(['network', 'rm', network], { allowFailure: true });
}
