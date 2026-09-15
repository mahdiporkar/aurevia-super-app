import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, resolve, sep } from 'node:path';

const root = process.cwd();
const currentPath = 'docs/current-state-fa.md';
const current = readFileSync(currentPath, 'utf8');

function names(path) {
  return readdirSync(path, { withFileTypes: true })
    .filter(entry => entry.isDirectory() && entry.name !== 'node_modules' && entry.name !== 'target')
    .map(entry => entry.name).sort();
}

function marker(name) {
  const match = current.match(new RegExp(`<!-- sync:${name}=([^\\n]+?) -->`));
  assert(match, `Missing documentation sync marker: ${name}`);
  return match[1];
}

function expectList(name, actual) {
  assert.equal(marker(name), actual.slice().sort().join(','),
    `Documentation inventory is stale: ${name}`);
}

expectList('apps', names('apps'));
expectList('packages', names('packages'));
expectList('java-services', names('services'));

const compose = readFileSync('infra/docker-compose/compose.yml', 'utf8');
const serviceSection = compose.slice(compose.indexOf('\nservices:') + 1, compose.indexOf('\nnetworks:'));
const coreServices = [...serviceSection.matchAll(/^  ([a-z0-9-]+):\s*$/gm)].map(match => match[1]);
expectList('core-services', coreServices);

const migrations = readdirSync('services/authorization-service/src/main/resources/db/migration')
  .map(name => ({ name, version: Number(/^V(\d+)__/.exec(name)?.[1] ?? -1) }))
  .filter(item => item.version >= 0).sort((left, right) => left.version - right.version);
const latestMigration = migrations.at(-1);
assert(latestMigration, 'No Flyway migrations found');
assert.equal(marker('latest-migration'), `V${latestMigration.version}`,
  'Documented latest Flyway migration is stale');

const adminManifest = JSON.parse(readFileSync('apps/mfe-admin/mf-manifest.json', 'utf8'));
assert.equal(marker('admin-version'),
  `${adminManifest.microfrontend.version};admin-routes=${adminManifest.routes.length}`,
  'Documented Admin MFE version/route inventory is stale');

const accessAdministration = readFileSync(
  'services/authorization-service/src/main/java/com/aurevia/authz/access/AccessAdministrationService.java',
  'utf8'
);
const resourceTypeBlock = /RESOURCE_TYPES\s*=\s*Set\.of\(([^;]+)\);/s.exec(accessAdministration);
assert(resourceTypeBlock, 'Could not read the Resource Catalog type inventory');
const resourceTypes = [...resourceTypeBlock[1].matchAll(/"([A-Z_]+)"/g)].map(match => match[1]);
assert.equal(marker('resource-types'), resourceTypes.join(','),
  'Documented Resource Catalog type inventory is stale');

const bffConfiguration = readFileSync('services/superapp-bff/src/main/resources/application.yml', 'utf8');
const swaggerSpecs = [...bffConfiguration.matchAll(/^\s+url: (\/[^\s]+)$/gm)]
  .map(match => match[1]).filter(path => path.includes('api-docs') || path.includes('/docs/'));
expectList('swagger-specs', swaggerSpecs);

const shell = readFileSync('apps/shell/src/index.tsx', 'utf8');
assert(shell.includes("'/api/me/context'"), 'Shell no longer uses the documented canonical context path');
assert(current.includes('`GET /api/v1/me/manifest` | alias سازگاری'),
  'Compatibility status of /api/v1/me/manifest must remain explicit');

const packageJson = JSON.parse(readFileSync('package.json', 'utf8'));
assert.equal(packageJson.scripts?.['docs:verify'], 'node tools/verify-documentation.mjs',
  'package.json must expose the documentation verification gate');

const trackedMarkdown = execFileSync('git', ['ls-files', '*.md'], { encoding: 'utf8' })
  .split(/\r?\n/).filter(Boolean)
  .filter(path => !path.includes('/') || path.startsWith('docs/'));
const broken = [];
for (const file of trackedMarkdown) {
  const content = readFileSync(file, 'utf8');
  for (const match of content.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
    let target = match[1].trim();
    if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1);
    if (/^(?:https?:|mailto:|#|\/)/i.test(target)) continue;
    target = target.split('#', 1)[0];
    if (!target) continue;
    try { target = decodeURIComponent(target); } catch {}
    const absolute = resolve(dirname(resolve(root, file)), target);
    if (!absolute.startsWith(root + sep) && absolute !== root) {
      broken.push(`${file}: link escapes repository: ${target}`);
    } else if (!existsSync(absolute)) {
      broken.push(`${file}: missing link target: ${target}`);
    }
  }
}
assert.deepEqual(broken, [], `Broken local documentation links:\n${broken.join('\n')}`);

const canonicalDocs = [
  'README.md', 'docs/architecture.md', 'docs/guide-en.md', 'docs/guide-fa.md',
  'docs/code-reference-en.md', 'docs/code-reference-fa.md', 'docs/operations-en.md',
  'docs/operations-fa.md', 'docs/shell-runtime-and-mfe-loading-fa.md'
];
for (const file of canonicalDocs) {
  const content = readFileSync(file, 'utf8');
  assert(content.includes('/api/me/context'), `${file} omits the canonical Shell contract`);
}
assert(!readFileSync('docs/architecture.md', 'utf8').includes('BFF->>G: /superset'),
  'Architecture still routes Superset through Operation Gateway');

console.log(JSON.stringify({
  status: 'PASS', markdownFilesChecked: trackedMarkdown.length,
  apps: names('apps').length, packages: names('packages').length,
  javaServices: names('services').length, coreServices: coreServices.length,
  migrations: migrations.length, latestMigration: `V${latestMigration.version}`,
  adminRoutes: adminManifest.routes.length, resourceTypes: resourceTypes.length,
  swaggerContracts: swaggerSpecs.length
}));
