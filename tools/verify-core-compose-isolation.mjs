import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const core = readFileSync('infra/docker-compose/compose.yml', 'utf8');
const demo = readFileSync('infra/docker-compose/compose.identity-demo.yml', 'utf8');

assert(!/^\s{2}keycloak(?:-db)?:\s*$/m.test(core),
  'Core Compose must not define a Keycloak service');
assert(!/depends_on:[\s\S]{0,300}?\bkeycloak\s*:/m.test(core),
  'Core services must not depend on Keycloak');
assert(!/http:\/\/keycloak(?::|\/)/.test(core),
  'Core Compose must not use the Keycloak container hostname');
assert(/^\s{2}keycloak:\s*$/m.test(demo),
  'Identity demo Compose must define Keycloak');
console.log('Core Compose is independent from the optional Identity Provider demo.');

// Production shape: the Core file must not start demo/test services or load fixtures; those live
// only in compose.development.yml (demo) and compose.e2e-auth*.yml (tests).
for (const service of ['demo-fixture-init', 'demo-catalog-init', 'mock-hr', 'mock-finance', 'mock-legacy', 'mock-oauth',
  'operation-gateway', 'test-sso-service', 'test-legacy-service', 'mf-test-sso', 'mf-test-legacy', 'mfe-hr', 'mfe-finance']) {
  assert(!new RegExp(`^\s{2}${service}:\s*$`, 'm').test(core), `Core Compose must not define ${service}`);
}
assert(!/fixtures\/development|integration-catalog/.test(core), 'Core Compose must not load development fixtures');
assert(!/SPRING_PROFILES_ACTIVE:\s*dev/.test(core), 'Core Compose must not force the dev Spring profile');
const development = readFileSync('infra/docker-compose/compose.development.yml', 'utf8');
assert(/^\s{2}demo-fixture-init:\s*$/m.test(development), 'Development overlay must load the demo fixtures');
console.log('Core Compose starts no demo/test service and loads no fixture; compose.development.yml carries them.');
