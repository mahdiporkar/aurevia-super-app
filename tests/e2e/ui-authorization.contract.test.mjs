import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';

const root = join(import.meta.dirname, '..', '..');
const read = (path) => readFile(join(root, path), 'utf8');

const pages = [
  ['apps/mfe-hr/src/bootstrap.tsx', 'page:hr.employee.list'],
  ['apps/mfe-hr/src/bootstrap.tsx', 'page:hr.departments'],
  ['apps/mfe-hr/src/bootstrap.tsx', 'page:hr.positions'],
  ['apps/mfe-finance/src/bootstrap.tsx', 'page:finance.payments'],
  ['apps/mfe-finance/src/bootstrap.tsx', 'page:finance.invoices'],
  ['apps/mfe-finance/src/bootstrap.tsx', 'page:finance.budgets'],
];

const mutations = [
  ['apps/mfe-hr/src/bootstrap.tsx', 'business:hr.employee', 'create'],
  ['apps/mfe-hr/src/bootstrap.tsx', 'business:hr.employee', 'update'],
  ['apps/mfe-finance/src/bootstrap.tsx', 'finance.payment', 'create'],
  ['apps/mfe-finance/src/bootstrap.tsx', 'finance.payment', 'approve'],
  ['apps/mfe-finance/src/bootstrap.tsx', 'finance.payment', 'reject'],
];

async function migrationCorpus() {
  const directory = join(root, 'services/authorization-service/src/main/resources/db/migration');
  const files = (await readdir(directory)).filter((name) => name.endsWith('.sql'));
  return (await Promise.all(files.map((name) => readFile(join(directory, name), 'utf8')))).join('\n');
}

test('every routed HR and Finance page is guarded and catalogued', async () => {
  const migrations = await migrationCorpus();
  for (const [sourcePath, resource] of pages) {
    const source = await read(sourcePath);
    assert.match(source, new RegExp(`<SHRouteGuard\\s+resource=["']${resource.replaceAll('.', '\\.')}["']\\s+action=["']view["']`));
    assert.ok(migrations.includes(`'${resource}'`), `${resource} is missing from the resource catalog`);
  }
});

test('every business mutation is wrapped in its explicit UI policy', async () => {
  const migrations = await migrationCorpus();
  for (const [sourcePath, resource, action] of mutations) {
    const source = await read(sourcePath);
    const policy = new RegExp(`<SHAction\\s+resource=["']${resource.replaceAll('.', '\\.')}["']\\s+action=["']${action}["']`);
    assert.match(source, policy, `${resource}:${action} is missing its SHAction guard`);
    assert.ok(migrations.includes(`'${resource}'`), `${resource} is missing from the resource catalog`);
    assert.ok(migrations.includes(`'${action}'`), `${action} is missing from the action catalog`);
  }
});

test('administrative mutations remain protected server-side', async () => {
  const interceptor = await read('services/authorization-service/src/main/java/com/aurevia/authz/config/AdminAuthorizationInterceptor.java');
  assert.match(interceptor, /DELETE.*can_delete/s);
  assert.match(interceptor, /PUT.*PATCH.*can_edit/s);
  assert.match(interceptor, /POST.*can_create/s);
  assert.match(interceptor, /isPrivilegedOperation.*can_manage/s);
});
