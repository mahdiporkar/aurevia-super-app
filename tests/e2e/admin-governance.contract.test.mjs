import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {join} from 'node:path';
import test from 'node:test';

const root=join(import.meta.dirname,'..','..');
const read=path=>readFile(join(root,path),'utf8');

// Every administrative page, at its topic-based location. Each is still its own
// route with its own resource/action; consolidation moved URLs, not permissions.
const pages=[
  'operator-guide','access/ous','access/groups','access/applications','access/resources',
  'access/explain','users/accounts','users/roles','panels','routes/targets','routes/routes',
  'routes/operations','routes/connections','routes/auth','routes/test','reports/instances',
  'reports/assets','logs/api','logs/audit',
];

/** Sidebar entries after the restructure: administrative domains, not operations. */
const domains=['operator-guide','access','users','panels','routes','reports','logs'];

const formFields={
  'AccessStudio.tsx':['type','parentId','resourceKey','nameFa','nameEn','ownerDomain','classification','panelId','source','visibilityEnabled','externalSystem','externalType','externalId'],
  'OuAccessManagement.tsx':['code','name','description','ruleCombiner','ouId','matchMode','applicationId','accessGroupId'],
  'Panels.tsx':['artifactVersion','remoteEntryUrl','remoteName','exposedModule','contractVersion','integrity','manifest','key','source','nodeType','title','parentKey','pageKey','externalUrl','order','hidden','code','slug','name_fa','name_en','description','service_slug','remote_name','remote_entry_path','exposed_module','route_base_path','default_route_id','semantic_version','contract_version','resource_definition_mode','classification','mf_manifest_url','resource_manifest_url','sort_order','active'],
  'ProxyRoutes.tsx':['code','name','environment','outboundAuthProfileId','gatewayBaseUrl','upstreamBasePath','healthCheckPath','tlsProfileRef','secretRef','connectTimeoutMs','responseTimeoutMs','maxResponseSize','active','description','panelId','serviceTargetId','serviceSlug','pathPrefix','stripPrefix','priority','allowedMethods','rewritePattern','rewriteReplacement','retryEnabled','maxRetries','preserveHost','httpMethod','pathPattern','resourceKey','actionKey','dataPolicyKey','maxBodyBytes','authorizationRequired','path','method'],
  'OutboundConnections.tsx':['name','connectionRef','baseUrl','tlsRequired','active','version'],
  'OutboundAuthProfiles.tsx':['code','name','authMode','tokenConnectionRef','tokenEndpointPath','requestFormat','credentialTransport','credentialSecretRef','authorizationScheme','tokenResponsePointer','tokenTypeResponsePointer','expiresInResponsePointer','expirySkewSeconds','connectTimeoutMs','responseTimeoutMs','maxTokenResponseSize','active','description'],
  'SupersetInstances.tsx':['publicInstanceId','operationInstanceId','publicPath','isDefault','active','code','name','zone','baseUrl','connectionRef','authMode','tlsRequired','version'],
  'SupersetAssets.tsx':['subjectType','subjectId','level'],
  'IdentityAndRoles.tsx':['subjectType','subjectId','roleId','roleKey','nameFa','nameEn'],
  'Logs.tsx':['serviceName','route','userId','statusCode','correlationId','actorId','eventType','targetType','targetId','result'],
};

test('all 19 governance pages remain published with explicit authorization metadata',async()=>{
  const source=await read('apps/mfe-admin/src/admin-route-catalog.ts');
  for(const path of pages){
    assert.match(source,new RegExp(`path:'${path.replaceAll('/','\\/')}'[^}]+resource:'[^']+'[^}]+action:'[^']+'`),`${path} must declare resource/action`);
  }
  assert.equal((source.match(/\{id:'[^']+',path:'[^']+'/g)??[]).length,19,'route inventory changed; update the E2E matrix and guide');
});

test('the Shell side menu lists administrative domains and each domain owns its tabs',async()=>{
  const bootstrap=await read('apps/mfe-admin/src/bootstrap.tsx');
  const catalog=await read('apps/mfe-admin/src/admin-route-catalog.ts');
  const tabbedPage=await read('apps/mfe-admin/src/AdminTabbedPage.tsx');
  const shellWebpack=await read('apps/shell/webpack.config.cjs');
  const manifest=JSON.parse(await read('apps/mfe-admin/mf-manifest.json'));

  // One sidebar entry per domain, not one per operation.
  assert.equal(manifest.navigation.length,domains.length);
  assert.deepEqual(manifest.navigation.map(item=>item.routeKey),
    domains.map(key=>['operator-guide','panels'].includes(key)?key:key+'-home'));
  assert(manifest.navigation.length<pages.length,
    'the sidebar must be shorter than the page inventory it groups');

  // Tabs live in one reusable, domain-agnostic layout.
  assert.match(tabbedPage,/<Tabs\b/,'AdminTabbedPage must render the Ant Design tab strip');
  assert.doesNotMatch(tabbedPage,/adminApi|ADMIN_PAGE_ROUTES|proxy|superset/i,
    'the shared tab layout must hold no domain-specific logic');
  assert.match(bootstrap,/AdminTabbedPage/,'domain pages must use the shared tab layout');

  // Tabs are navigation: selecting one changes the URL rather than local state only.
  assert.doesNotMatch(bootstrap,/useState/,'tab state must live in the URL, not component state');
  assert.match(bootstrap,/navigate\(/,'changing tab must navigate to the tab route');
  assert.match(catalog,/ADMIN_MENUS[^=]*=ADMIN_DOMAINS\.map/,
    'every domain must publish exactly one Shell menu entry');
  assert.match(shellWebpack,/publicPath:'\/'/,
    'Shell assets must remain root-absolute so direct navigation to a deep link can bootstrap');
});

test('consolidation keeps per-page authorization and redirects every renamed URL',async()=>{
  const manifest=JSON.parse(await read('apps/mfe-admin/mf-manifest.json'));
  const catalog=await read('apps/mfe-admin/src/admin-route-catalog.ts');
  // A tab renders only when its own route survived the effective catalog, so every
  // route keeps declaring its own resource and action.
  assert(manifest.routes.every(route=>route.requiredResource&&route.requiredAction),
    'every admin route must carry explicit authorization metadata');
  assert.equal(new Set(manifest.routes.map(route=>route.path)).size,manifest.routes.length);
  assert.equal(manifest.routes.filter(route=>route.key.startsWith('legacy-')).length,16);
  for(const path of pages)assert(manifest.routes.some(route=>route.path===path),
    path+' disappeared from the published catalog');

  // Differing tab permissions must not be flattened onto the parent page.
  const routes=new Map(manifest.routes.map(route=>[route.path,route]));
  assert.equal(routes.get('routes/test').requiredAction,'test');
  assert.equal(routes.get('routes/targets').requiredResource,'proxy.target');
  assert.equal(routes.get('routes/routes').requiredResource,'proxy.route');
  assert.equal(routes.get('routes/operations').requiredResource,'proxy.operation');
  assert.equal(routes.get('reports/assets').requiredResource,'module:admin.superset-catalog');
  assert.equal(routes.get('logs/audit').requiredAction,'view_audit');

  for(const legacy of ['ou-access/ous','access-studio','identity','proxy-routes/targets',
      'proxy-routes/routes','proxy-routes/operations','outbound-connections','outbound-auth',
      'integration-test','superset-instances','superset']) {
    assert(catalog.includes("from:'"+legacy+"',to:'"),
      legacy+' must redirect instead of returning "route not found"');
    // The legacy URL stays published so the Shell mounts the micro frontend on it
    // and the redirect runs, but it is no longer a page of its own.
    const redirectRoute=manifest.routes.find(route=>route.path===legacy);
    assert(redirectRoute,legacy+' must stay published so its redirect can run');
    assert(!pages.includes(legacy),legacy+' must no longer be a page location');
  }
});

test('the deploy migration activates the exact canonical ADMIN 0.7.0 MF manifest',async()=>{
  const source=JSON.parse(await read('apps/mfe-admin/mf-manifest.json'));
  const migration=await read('services/authorization-service/src/main/resources/db/migration/V83__activate_admin_mf_manifest_0_7_0.sql');
  const checksum=createHash('sha256').update(JSON.stringify(source)).digest('hex');
  assert.equal(source.microfrontend.version,'0.7.0');
  assert.equal(source.navigation.length,7);
  assert.equal(source.routes.length,40,
    '19 pages, one landing route per multi-tab domain, and 16 legacy redirect routes');
  assert.equal(source.menus,undefined,'canonical MF manifests must use navigation, not legacy menus');
  assert(source.navigation.every(item=>item.description?.trim()),'every ADMIN navigation item needs a tooltip description');
  assert(migration.includes(checksum),'V83 checksum drifted from apps/mfe-admin/mf-manifest.json');
  assert.deepEqual(JSON.parse(migration.split('$manifest$')[1]),source,
    'V83 must embed the repository manifest verbatim');
  assert.match(migration,/active_artifact_id=a\.id[^;]+artifact_version='0\.7\.0'/s,
    'V83 must activate the canonical ADMIN artifact');
});

test('every governance form exposes the documented field inventory',async()=>{
  const guide=await read('docs/operator-admin-form-field-guide-fa.md');
  const observed=new Set();
  for(const [file,fields] of Object.entries(formFields)){
    const source=await read(`apps/mfe-admin/src/${file}`);
    for(const field of fields){
      assert.match(source,new RegExp(`name=["']${field}["']`),`${file}:${field} is missing from UI`);
      assert.ok(guide.includes(`\`${field}\``),`${file}:${field} is missing from the operator guide`);
      observed.add(`${file}:${field}`);
    }
  }
  assert.ok(observed.size>=118,`expected at least 118 file-scoped fields, observed ${observed.size}`);
});

test('conditional, immutable, destructive and async governance states remain visible',async()=>{
  const sources=await Promise.all(['AccessStudio.tsx','OuAccessManagement.tsx','Panels.tsx','ProxyRoutes.tsx','OutboundAuthProfiles.tsx','IdentityAndRoles.tsx'].map(name=>read(`apps/mfe-admin/src/${name}`)));
  const corpus=sources.join('\n');
  for(const state of ['MANIFEST','ADMIN','HYBRID','DEPRECATED','ANY_OF','ALL_OF','EXACT','SUBTREE','FORWARD_USER_TOKEN','LEGACY_SERVICE_TOKEN','Popconfirm','disabled=','loading=']){
    assert.ok(corpus.includes(state),`governance state ${state} lost its UI representation`);
  }
});

test('test guide covers positive, negative, rollback, security and evidence workflows',async()=>{
  const guide=await read('docs/admin-governance-e2e-test-guide-fa.md');
  for(const marker of ['تست مثبت','تست منفی','Rollback','Correlation ID','OpenFGA','Remote Entry','۱۱۸','۱۸ صفحه']) assert.ok(guide.includes(marker),`test guide is missing ${marker}`);
});
