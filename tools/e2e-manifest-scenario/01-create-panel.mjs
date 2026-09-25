import {admin,show,SLUG,save} from './lib.mjs';
const s=await admin();
const body={code:'E2E_HYBRID_TEST',nameFa:'میکرو آزمون هیبرید',nameEn:'E2E Hybrid Test Micro',
 description:'E2E validation micro frontend',slug:SLUG,serviceSlug:SLUG,
 remoteName:'aurevia_e2e_hybrid_test_micro',defaultRouteId:'page-a',
 remoteEntry:'http://host.docker.internal:3010/remoteEntry.js',exposedModule:'./plugin',
 routeBasePath:'/'+SLUG,semanticVersion:'1.0.0',contractVersion:'1.0',integrity:null,
 resourceDefinitionMode:'HYBRID',classification:'REAL',
 mfManifestUrl:'http://host.docker.internal:3010/mf-manifest.json',
 resourceManifestUrl:'http://host.docker.internal:3010/resource-manifest.json',
 active:true,sortOrder:90};
const created=show('CREATE panel',await s.json('/api/v1/admin/panels','POST',body));
const panels=await s.json('/api/v1/admin/panels');
const p=panels.body.find(x=>x.slug===SLUG);
console.log('FOUND IN ADMIN LIST:',!!p, p&&p.id, p&&p.resource_definition_mode, 'version',p&&p.version);
save({panelId:p.id,panelVersion:p.version});
