#!/usr/bin/env node
// Fails the build when a permission integration test class did not actually execute.
// These classes are gated on environment variables; a misconfigured pipeline would
// otherwise let them skip silently and report a green build with no real-stack coverage.
import { readFileSync, existsSync } from 'node:fs';

const reports = 'services/authorization-service/target/surefire-reports';
const required = [
  'com.aurevia.authz.bootstrap.CoreBaselineInstallationIntegrationTest',
  'com.aurevia.authz.bootstrap.FirstAdministratorBootstrapIntegrationTest',
  'com.aurevia.authz.sync.NormalPermissionMigrationIntegrationTest',
  'com.aurevia.authz.sync.PermissionLifecycleIntegrationTest',
  'com.aurevia.authz.sync.OpenFgaReconciliationRepositoryIntegrationTest',
  'com.aurevia.authz.diagnostics.AuthorizationDiagnosticsRepositoryIntegrationTest',
];

let failed = false;
for (const name of required) {
  const file = `${reports}/TEST-${name}.xml`;
  if (!existsSync(file)) {
    console.error(`MISSING  ${name} (no surefire report)`);
    failed = true;
    continue;
  }
  const suite = readFileSync(file, 'utf8').match(/<testsuite\b[^>]*>/)?.[0] ?? '';
  const attr = (key) => Number(suite.match(new RegExp(`\\s${key}="(\\d+)"`))?.[1] ?? '0');
  const tests = attr('tests');
  const skipped = attr('skipped');
  const failures = attr('failures');
  const errors = attr('errors');
  const executed = tests - skipped;
  const ok = executed > 0 && skipped === 0 && failures === 0 && errors === 0;
  console.log(`${ok ? 'OK      ' : 'FAILED  '} ${name}: tests=${tests} executed=${executed} skipped=${skipped} failures=${failures} errors=${errors}`);
  if (!ok) failed = true;
}
if (failed) {
  console.error('Permission integration tests did not execute cleanly.');
  process.exit(1);
}
console.log('All permission integration tests executed with zero skips.');
