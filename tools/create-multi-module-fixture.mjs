// Regenerate the reviewable demo contracts; never writes to a running registry.
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
const dir = new URL('../apps/mf-test-multi-module/', import.meta.url);
mkdirSync(new URL('src/', dir), { recursive: true });
const write = (name, value) => writeFileSync(new URL(name, dir), typeof value === 'string' ? value : JSON.stringify(value, null, 2) + '\n');
const slug = 'test-multi-module';
const groups = [
  ['employee', 'Employee', ['employee-list', 'employee-detail']],
  ['payroll', 'Payroll', ['salary', 'payroll-report']],
  ['attendance', 'Attendance', ['daily-attendance', 'attendance-report']],
];
const resources = groups.flatMap(([key, name, pages]) => {
  // Explicitly customize the synthetic MODULE using the existing schema.
  const moduleKey = `module:${slug}${key === 'employee' ? '' : '.' + key}`;
  return [{ key: moduleKey, type: 'MODULE', name, actions: ['view', 'admin'] },
    ...pages.map(page => ({ key: `page:${slug}.${page}`, type: 'PAGE', parentKey: moduleKey, name: page, actions: ['view'] }))];
});
const resourceManifest = { schemaVersion: '1.0', module: { key: slug, name: 'TEST_MULTI_MODULE', version: '1.0.0' }, resources };
write('resource-manifest.json', resourceManifest);
write('resource-manifest.v2.json', { ...resourceManifest, module: { ...resourceManifest.module, version: '2.0.0' },
  resources: [...resources, { key: `module:${slug}.benefits`, type: 'MODULE', name: 'Benefits', actions: ['view', 'admin'] }] });
const mf = { schemaVersion: '1.0', microfrontend: { key: slug, name: 'TEST_MULTI_MODULE', version: '1.0.0' },
  runtime: { remoteEntry: 'http://localhost:3013/remoteEntry.js', remoteName: 'aurevia_test_multi_module', exposedModule: './plugin', contractVersion: '1.0' },
  defaultRouteKey: 'employee-list',
  routes: groups.flatMap(([key, , pages]) => pages.map(page => ({ key: page, path: `${key}/${page}`, requiredResource: `page:${slug}.${page}`, requiredAction: 'view', title: page }))),
  navigation: groups.flatMap(([key, title, pages], index) => [{ key, type: 'GROUP', title, order: index },
    ...pages.map((page, order) => ({ key: page, type: 'PAGE', parentKey: key, routeKey: page, title: page, order }))]) };
write('mf-manifest.json', mf);
write('mf-manifest.v2.json', { ...mf, microfrontend: { ...mf.microfrontend, version: '2.0.0' } });
write('package.json', { name: '@aurevia/mf-test-multi-module', version: '1.0.0', private: true,
  dependencies: { '@aurevia/contracts': '*', '@aurevia/http-client': '*', '@aurevia/sh-core-ui': '*', react: '19.1.1', 'react-dom': '19.1.1', 'react-router-dom': '7.18.3' },
  scripts: { build: 'webpack --mode production', dev: 'webpack serve --mode development', typecheck: 'tsc --noEmit' } });
write('tsconfig.json', { extends: '../../tsconfig.base.json', compilerOptions: { noEmit: false, outDir: 'dist-types' }, include: ['src'] });
const template = new URL('../apps/mf-test-sso/', import.meta.url);
write('webpack.config.cjs', readFileSync(new URL('webpack.config.cjs', template), 'utf8')
  .replaceAll('aurevia_test_sso', 'aurevia_test_multi_module').replace('SSO Test Microfrontend', 'TEST_MULTI_MODULE').replace('3011', '3013')
  .replace("historyApiFallback: true,", "historyApiFallback: true, headers: { 'Access-Control-Allow-Origin': '*' },"));
write('src/index.ts', "import('./standalone');\n");
write('src/standalone.tsx', readFileSync(new URL('src/standalone.tsx', template), 'utf8')
  .replaceAll('test-sso', slug)
  .replace('location.assign(path)', "location.assign('/test-multi-module/' + path)")
  .replace("getModuleBasePath: () => '/'", "getModuleBasePath: () => '/test-multi-module'"));
