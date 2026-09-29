import{describe,expect,it}from'vitest';
import{matchRoutes}from'react-router-dom';
import{
  ADMIN_DOMAIN_ROUTES,ADMIN_DOMAINS,ADMIN_LEGACY_ROUTE_REDIRECTS,ADMIN_PAGE_ROUTES,
  ADMIN_LEGACY_ROUTES,ADMIN_PUBLISHED_MANIFEST,adminTabKey,authorizedAdminDomains,authorizedAdminPages,
  defaultAdminPage,internalPathname,
}from'./admin-route-catalog';
import publishedManifestFile from'../mf-manifest.json';

/** Every administrative operation that existed before the topic-based restructure. */
const everyAdminPage=['operator-guide','ou-access-ous','ou-access-groups',
  'ou-access-applications','access-studio','ou-access-explain','users','identity','panels',
  'proxy-targets','proxy-routes','proxy-operations','outbound-connections','outbound-auth',
  'integration-test','superset-instances','superset','logs-api','logs-audit'];

describe('Admin route catalog',()=>{
  it('keeps every former page addressable, each inside exactly one domain',()=>{
    expect(ADMIN_PAGE_ROUTES.map(page=>page.id).sort()).toEqual([...everyAdminPage].sort());
    expect(ADMIN_PAGE_ROUTES).toHaveLength(19);
    const domainKeys=new Set(ADMIN_DOMAINS.map(domain=>domain.key));
    expect(ADMIN_PAGE_ROUTES.every(page=>domainKeys.has(page.domain))).toBe(true);
    expect(new Set(ADMIN_PAGE_ROUTES.map(page=>page.path)).size).toBe(ADMIN_PAGE_ROUTES.length);
  });

  it('publishes one side-menu entry per domain instead of one per operation',()=>{
    expect(ADMIN_PUBLISHED_MANIFEST.navigation).toHaveLength(ADMIN_DOMAINS.length);
    expect(ADMIN_PUBLISHED_MANIFEST.navigation.length).toBeLessThan(ADMIN_PAGE_ROUTES.length);
    expect(ADMIN_PUBLISHED_MANIFEST.navigation.every(menu=>menu.type==='PAGE')).toBe(true);
    // Every menu entry must resolve to a published route, or the Shell drops it.
    const routeKeys=new Set(ADMIN_PUBLISHED_MANIFEST.routes.map(route=>route.key));
    expect(ADMIN_PUBLISHED_MANIFEST.navigation.every(menu=>routeKeys.has(menu.routeKey!))).toBe(true);
    expect(ADMIN_PUBLISHED_MANIFEST.navigation.every(menu=>(menu.description?.length??0)>=20)).toBe(true);
  });

  it('publishes a landing route for every multi-tab domain and none for standalone ones',()=>{
    const multiTab=ADMIN_DOMAINS.filter(domain=>!domain.standalone);
    expect(ADMIN_DOMAIN_ROUTES).toHaveLength(multiTab.length);
    expect(ADMIN_PUBLISHED_MANIFEST.routes).toHaveLength(
      ADMIN_PAGE_ROUTES.length+multiTab.length+ADMIN_LEGACY_ROUTES.length);
    for(const domain of ADMIN_DOMAINS.filter(item=>item.standalone)) {
      const pages=ADMIN_PAGE_ROUTES.filter(page=>page.domain===domain.key);
      expect(pages).toHaveLength(1);
      expect(pages[0]!.path).toBe(domain.path);
    }
  });

  it('nests every multi-tab page URL beneath its domain path',()=>{
    for(const page of ADMIN_PAGE_ROUTES) {
      const domain=ADMIN_DOMAINS.find(item=>item.key===page.domain)!;
      if(domain.standalone) expect(page.path).toBe(domain.path);
      else expect(page.path.startsWith(`${domain.path}/`)).toBe(true);
      expect(adminTabKey(page).includes('/')).toBe(false);
    }
  });

  it.each([
    ['access/groups','ou-access-groups'],
    ['access/resources','access-studio'],
    ['routes/operations','proxy-operations'],
    ['routes/auth','outbound-auth'],
    ['reports/assets','superset'],
    ['users/roles','identity'],
    ['logs/audit','logs-audit'],
    ['panels','panels'],
  ])('deep link %s resolves page %s',(path,id)=>{
    const matches=matchRoutes(ADMIN_PAGE_ROUTES.map(route=>({path:route.path,id:route.id})),`/${path}`);
    expect(matches?.at(-1)?.route.id).toBe(id);
  });

  it('redirects every renamed legacy URL to a published route',()=>{
    const paths=new Set([...ADMIN_PAGE_ROUTES.map(page=>page.path),
      ...ADMIN_DOMAINS.map(domain=>domain.path)]);
    const published=new Map(ADMIN_PUBLISHED_MANIFEST.routes.map(route=>[route.path,route]));
    for(const redirect of ADMIN_LEGACY_ROUTE_REDIRECTS) {
      expect(paths.has(redirect.to)).toBe(true);
      // A legacy URL must no longer be a live page, or it would shadow the redirect.
      expect(ADMIN_PAGE_ROUTES.some(page=>page.path===redirect.from)).toBe(false);
      // It must still be published, or the Shell refuses to mount this micro
      // frontend on it and the redirect never runs.
      const legacy=published.get(redirect.from);
      const target=published.get(redirect.to);
      expect(legacy).toBeDefined();
      // A legacy URL grants nothing its destination would not grant.
      expect(legacy!.requiredResource).toBe(target!.requiredResource);
      expect(legacy!.requiredAction).toBe(target!.requiredAction);
    }
    // Every page whose URL moved has to be reachable from its previous URL.
    const previous=new Map<string,string>([
      ['ou-access-ous','ou-access/ous'],['ou-access-groups','ou-access/groups'],
      ['ou-access-applications','ou-access/applications'],['ou-access-explain','ou-access/explain'],
      ['access-studio','access-studio'],['identity','identity'],
      ['proxy-targets','proxy-routes/targets'],['proxy-routes','proxy-routes/routes'],
      ['proxy-operations','proxy-routes/operations'],
      ['outbound-connections','outbound-connections'],['outbound-auth','outbound-auth'],
      ['integration-test','integration-test'],['superset-instances','superset-instances'],
      ['superset','superset'],
    ]);
    for(const [id,legacy] of previous) {
      const page=ADMIN_PAGE_ROUTES.find(item=>item.id===id)!;
      expect(ADMIN_LEGACY_ROUTE_REDIRECTS.find(redirect=>redirect.from===legacy)?.to)
        .toBe(page.path);
    }
  });

  it('keeps the shipped mf-manifest.json identical to the catalog it is built from',()=>{
    // The deploy migration hashes the JSON file, so any drift between the file and
    // the code that renders these routes would ship a catalog nobody can reach.
    expect(publishedManifestFile).toEqual(JSON.parse(JSON.stringify(ADMIN_PUBLISHED_MANIFEST)));
  });

  it('has a safe authorized default and rejects an unknown route',()=>{
    const onlyAudit=authorizedAdminPages(['logs-audit']);
    expect(defaultAdminPage(onlyAudit,'operator-guide')?.id).toBe('logs-audit');
    expect(matchRoutes(onlyAudit.map(route=>({path:route.path})), '/not-existing')).toBeNull();
  });

  it('derives visible domains and tabs from the authorized pages only',()=>{
    const restricted=authorizedAdminDomains(authorizedAdminPages(['proxy-routes','outbound-auth']));
    expect(restricted.map(entry=>entry.domain.key)).toEqual(['routes']);
    expect(restricted[0]!.pages.map(page=>page.id)).toEqual(['proxy-routes','outbound-auth']);
    // Tab order follows the configuration workflow, not the granted order.
    expect(restricted[0]!.pages.map(page=>adminTabKey(page))).toEqual(['routes','auth']);
    expect(authorizedAdminDomains(authorizedAdminPages([]))).toEqual([]);
  });

  it('never selects a default tab the subject cannot open',()=>{
    // A report designer holds only the Superset catalog page.
    const designer=authorizedAdminDomains(
      authorizedAdminPages(undefined,{'module:admin.superset-catalog':['view']}));
    expect(designer.map(entry=>entry.domain.key)).toEqual(['reports']);
    expect(designer[0]!.pages.map(page=>page.id)).toEqual(['superset']);
    expect(designer[0]!.pages[0]!.path).toBe('reports/assets');
    // The domain's own menu permission has to be one such a subject actually holds,
    // otherwise the side-menu entry disappears and the page becomes unreachable.
    expect(designer[0]!.domain.resource).toBe('module:admin.superset-catalog');
    expect(designer[0]!.domain.action).toBe('view');
  });

  it('filters unauthorized page metadata and keeps authorized pages',()=>{
    const pages=authorizedAdminPages(['proxy-routes']);
    expect(pages.map(page=>page.id)).toEqual(['proxy-routes']);
    expect(pages.some(page=>page.id==='proxy-targets')).toBe(false);
    expect(authorizedAdminPages([])).toEqual([]);
  });

  it('shares relative definitions in standalone and under any Shell prefix',()=>{
    expect(authorizedAdminPages(undefined)).toHaveLength(19);
    expect(internalPathname('/management/routes/routes','/management')).toBe('routes/routes');
    expect(internalPathname('/governance/routes/routes','/governance')).toBe('routes/routes');
    expect(ADMIN_PUBLISHED_MANIFEST.routes.every(route=>!route.path.startsWith('/'))).toBe(true);
    expect('routePrefix'in ADMIN_PUBLISHED_MANIFEST).toBe(false);
  });
});
