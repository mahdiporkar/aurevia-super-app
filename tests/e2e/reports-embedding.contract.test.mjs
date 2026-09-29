import assert from 'node:assert/strict';
import {readdir, readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {join} from 'node:path';
import test from 'node:test';

const root=join(import.meta.dirname,'..','..');
const read=path=>readFile(join(root,path),'utf8');

test('Superset reports are embedded in the Shell and never opened externally',async()=>{
  const dir=join(root,'apps/mfe-reports/src');
  const sources=(await readdir(dir)).filter(name=>/[.]tsx?$/.test(name)&&!name.endsWith('.d.ts')
    &&!name.includes('.test.'));
  assert.ok(sources.length>0,'the Reports micro frontend must have sources to inspect');
  for(const name of sources){
    const source=await readFile(join(dir,name),'utf8');
    assert.doesNotMatch(source,/target\s*=\s*["'{]?\s*_blank/,
      `${name} must not open a report in a new browser tab`);
    assert.doesNotMatch(source,/window[.]open\s*\(/,
      `${name} must not open a report through window.open`);
  }
  const viewer=await read('apps/mfe-reports/src/ReportViewer.tsx');
  assert.match(viewer,/<iframe/,'the viewer must embed the report in the Aurevia content area');
  // The iframe src is the validated same-origin BFF path, never a Superset host.
  assert.match(viewer,/safeReportPath\(report[.]url_path\)/);
  assert.doesNotMatch(viewer,/https?:\/\/(?!localhost)/,
    'the viewer must not contain an absolute Superset URL');
});

test('the Reports viewer route is published in the effective catalog',async()=>{
  const manifest=JSON.parse(await read('apps/mfe-reports/mf-manifest.json'));
  const index=manifest.routes.find(route=>route.key==='index');
  const viewer=manifest.routes.find(route=>route.key==='view');
  assert.ok(viewer,'the Shell mounts a micro frontend only on a manifest-declared route');
  assert.equal(viewer.path,'view/:reportId');
  // Embedding must not widen access: the viewer declares the landing route's
  // resource and action, and the BFF still authorizes every Superset request.
  assert.equal(viewer.requiredResource,index.requiredResource);
  assert.equal(viewer.requiredAction,index.requiredAction);
  assert.ok(!manifest.navigation.some(item=>item.routeKey==='view'),
    'the viewer is reached from the catalog, not from a second sidebar entry');

  const migration=await read(
    'services/authorization-service/src/main/resources/db/migration/V82__activate_reports_mf_manifest_0_3_0.sql');
  const [,contract]=migration.split('$manifest$');
  assert.deepEqual(JSON.parse(contract),manifest,
    'the migration must activate the repository manifest verbatim');
  const checksum=createHash('sha256').update(JSON.stringify(manifest),'utf8').digest('hex');
  assert.ok(migration.includes(checksum),
    'the declared manifest_checksum must match the manifest a later sync computes');
});

test('same-origin framing stays enabled for the Superset tunnel',async()=>{
  const nginx=await read('infra/nginx/nginx.conf');
  assert.doesNotMatch(nginx,/X-Frame-Options\s+DENY/i,
    'DENY would break the in-app report viewer');
  for(const policy of nginx.match(/frame-ancestors [^;"]*/g)??[]){
    assert.match(policy,/frame-ancestors 'self'/,
      'every Superset location must keep same-origin framing');
  }
  const proxy=await read(
    'services/superapp-bff/src/main/java/com/aurevia/bff/api/OperationSupersetProxyController.java');
  // The proxy forwards an allow-list of response headers, so Superset's own
  // X-Frame-Options/CSP cannot reach the browser and override the Shell's policy.
  assert.doesNotMatch(proxy,/X-Frame-Options/,
    'the BFF must not forward an upstream framing header');
});
