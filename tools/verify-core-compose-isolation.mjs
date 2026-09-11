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
