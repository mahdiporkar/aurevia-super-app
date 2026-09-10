import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const core = readFileSync(new URL('../infra/docker-compose/compose.yml', import.meta.url), 'utf8');
const demo = readFileSync(
  new URL('../infra/docker-compose/compose.superset-demo.yml', import.meta.url), 'utf8');
const packageJson = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

for (const service of [
  'public-superset:',
  'operation-superset:',
  'operation-superset-db:',
  'operation-superset-init:',
]) {
  assert.equal(core.includes(`  ${service}`), false,
    `Core Compose must not define ${service.slice(0, -1)}`);
}
assert.doesNotMatch(packageJson.scripts['infra:up'], /profile\s+superset/);
assert.match(packageJson.scripts['superset:up'], /compose\.superset-demo\.yml/);
assert.match(packageJson.scripts['superset:down'], /compose\.superset-demo\.yml/);
assert.match(demo, /^  superset-public:/m);
assert.match(demo, /^  superset-operation:/m);

console.log('Superset lifecycle is isolated from Aurevia Core Compose.');
