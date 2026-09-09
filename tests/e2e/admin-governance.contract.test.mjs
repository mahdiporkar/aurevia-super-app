import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {join} from 'node:path';
import test from 'node:test';

const root=join(import.meta.dirname,'..','..');
const read=path=>readFile(join(root,path),'utf8');

const pages=[
  'operator-guide','ou-access/ous','ou-access/groups','ou-access/applications','ou-access/explain',
  'access-studio','panels','proxy-routes/targets','proxy-routes/routes','proxy-routes/operations',
  'outbound-connections','outbound-auth','integration-test','superset-instances','identity',
  'logs/api','logs/audit','superset',
];

const formFields={
  'AccessStudio.tsx':['type','parentId','resourceKey','nameFa','nameEn','ownerDomain','classification','panelId','source','visibilityEnabled','externalSystem','externalType','externalId'],
  'OuAccessManagement.tsx':['code','name','description','ruleCombiner','ouId','matchMode','applicationId','accessGroupId'],
  'Panels.tsx':['artifactVersion','remoteEntryUrl','remoteName','exposedModule','contractVersion','integrity','manifest','key','source','nodeType','title','parentKey','pageKey','externalUrl','order','hidden','code','slug','name_fa','name_en','description','service_slug','remote_name','remote_entry_path','exposed_module','route_base_path','default_route_id','semantic_version','contract_version','resource_definition_mode','classification','resource_manifest_url','sort_order','active'],
  'ProxyRoutes.tsx':['code','name','environment','outboundAuthProfileId','gatewayBaseUrl','upstreamBasePath','healthCheckPath','tlsProfileRef','secretRef','connectTimeoutMs','responseTimeoutMs','maxResponseSize','active','description','panelId','serviceTargetId','serviceSlug','pathPrefix','stripPrefix','priority','allowedMethods','rewritePattern','rewriteReplacement','retryEnabled','maxRetries','preserveHost','httpMethod','pathPattern','resourceKey','actionKey','dataPolicyKey','maxBodyBytes','authorizationRequired','path','method'],
  'OutboundConnections.tsx':['name','connectionRef','baseUrl','tlsRequired','active','version'],
  'OutboundAuthProfiles.tsx':['code','name','authMode','tokenConnectionRef','tokenEndpointPath','requestFormat','credentialTransport','credentialSecretRef','authorizationScheme','tokenResponsePointer','tokenTypeResponsePointer','expiresInResponsePointer','expirySkewSeconds','connectTimeoutMs','responseTimeoutMs','maxTokenResponseSize','active','description'],
  'SupersetInstances.tsx':['publicInstanceId','operationInstanceId','publicPath','isDefault','active','code','name','zone','baseUrl','connectionRef','authMode','tlsRequired','version'],
  'SupersetAssets.tsx':['subjectType','subjectId','level'],
  'IdentityAndRoles.tsx':['subjectType','subjectId','roleId','roleKey','nameFa','nameEn'],
  'Logs.tsx':['serviceName','route','userId','statusCode','correlationId','actorId','eventType','targetType','targetId','result'],
};

test('all 18 governance pages remain published with explicit authorization metadata',async()=>{
  const source=await read('apps/mfe-admin/src/admin-route-catalog.ts');
  for(const path of pages){
    assert.match(source,new RegExp(`path:'${path.replaceAll('/','\\/')}'[^}]+resource:'[^']+'[^}]+action:'[^']+'`),`${path} must declare resource/action`);
  }
  assert.equal((source.match(/\{id:'[^']+',path:'[^']+'/g)??[]).length,18,'route inventory changed; update the E2E matrix and guide');
});

test('admin navigation uses the Shell side menu and does not render tab navigation',async()=>{
  const bootstrap=await read('apps/mfe-admin/src/bootstrap.tsx');
  const catalog=await read('apps/mfe-admin/src/admin-route-catalog.ts');
  const shellWebpack=await read('apps/shell/webpack.config.cjs');
  assert.doesNotMatch(bootstrap,/<Tabs\b|\bTabs[,}]/,'Admin MFE must not render top-level or nested tabs');
  assert.match(catalog,/ADMIN_MENUS[^=]*=ADMIN_PAGE_ROUTES\.map/,'every page must publish a Shell menu entry');
  assert.match(shellWebpack,/publicPath:'\/'/,
    'Shell assets must remain root-absolute so direct navigation to a deep link can bootstrap');
});

test('the deploy migration activates the exact canonical ADMIN 0.5.0 MF manifest',async()=>{
  const source=JSON.parse(await read('apps/mfe-admin/mf-manifest.json'));
  const migration=await read('services/authorization-service/src/main/resources/db/migration/V56__activate_admin_mf_manifest_0_5_0.sql');
  const checksum=createHash('sha256').update(JSON.stringify(source)).digest('hex');
  assert.equal(source.microfrontend.version,'0.5.0');
  assert.equal(source.navigation.length,18);
  assert.equal(source.menus,undefined,'canonical MF manifests must use navigation, not legacy menus');
  assert(source.navigation.every(item=>item.description?.trim()),'every ADMIN navigation item needs a tooltip description');
  assert(migration.includes(checksum),'V56 checksum drifted from apps/mfe-admin/mf-manifest.json');
  assert.match(migration,/active_artifact_id=a\.id[^;]+artifact_version='0\.5\.0'/s,
    'V56 must activate the canonical ADMIN artifact');
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
