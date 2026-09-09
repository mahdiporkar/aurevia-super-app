import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {join} from 'node:path';
import test from 'node:test';

const root=join(import.meta.dirname,'..','..');
const read=path=>readFile(join(root,path),'utf8');

test('Core compose has no demo MFE lifecycle dependency',async()=>{
  const [core,demo,preflight,pkg]=await Promise.all([
    read('infra/docker-compose/compose.yml'),
    read('infra/docker-compose/compose.mfe-demo.yml'),
    read('tools/infra-preflight.mjs'),
    read('package.json').then(JSON.parse),
  ]);
  for(const name of ['mfe-admin','mfe-hr','mfe-finance','mfe-reports']){
    assert.doesNotMatch(core,new RegExp(`^  ${name}:`,'m'),`${name} must not be a Core service`);
    assert.match(demo,new RegExp(`^  ${name}:`,'m'),`${name} must have an independent demo service`);
    assert.doesNotMatch(preflight,new RegExp(`apps/${name}/dist`),`${name} must not gate Core startup`);
  }
  assert.match(pkg.scripts['infra:up'],/compose[.]yml/);
  assert.doesNotMatch(pkg.scripts['infra:up'],/compose[.]mfe-demo[.]yml/);
  assert.match(pkg.scripts['mfe:up'],/compose[.]mfe-demo[.]yml/);
  assert.match(pkg.scripts['mfe:down'],/compose[.]mfe-demo[.]yml/);
  assert.doesNotMatch(pkg.scripts['mfe:up'],/--env-file|[.]env/);
  assert.doesNotMatch(pkg.scripts['mfe:down'],/--env-file|[.]env/);
});

test('Core uses a policy profile and generic development bridge, not per-MFE lists',async()=>{
  const [core,auth,bff]=await Promise.all([
    read('infra/docker-compose/compose.yml'),
    read('services/authorization-service/src/main/resources/application.yml'),
    read('services/superapp-bff/src/main/resources/application.yml'),
  ]);
  const source=[core,auth,bff].join('\n');
  assert.doesNotMatch(source,/UI_ARTIFACT_ALLOWED_ORIGINS|MFE_PROXY_LOOPBACK_TARGETS/);
  assert.match(core,/UI_ARTIFACT_NETWORK_POLICY: "DEVELOPMENT"/);
  assert.match(core,/UI_ARTIFACT_DEVELOPMENT_HOST: "host[.]docker[.]internal"/);
});
