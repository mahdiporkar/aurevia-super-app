import{describe,expect,it}from'vitest';
import{matchRoutes}from'react-router-dom';
import{
  ADMIN_PAGE_ROUTES,ADMIN_PUBLISHED_MANIFEST,authorizedAdminPages,defaultAdminPage,
  internalPathname,
}from'./admin-route-catalog';

const formerTopTabs=['operator-guide','ou-access','access-studio','panels','proxy-routes',
  'outbound-connections','outbound-auth','integration-test','superset-instances','identity',
  'logs','superset'];
const formerNestedTabs=['ou-access-ous','ou-access-groups','ou-access-applications',
  'ou-access-explain','proxy-targets','proxy-routes','proxy-operations','logs-api','logs-audit'];

describe('Admin route catalog',()=>{
  it('represents every former top-level and nested tab as an addressable page',()=>{
    const sections=new Set(ADMIN_PAGE_ROUTES.map(route=>route.section));
    expect([...sections]).toEqual(formerTopTabs);
    expect(ADMIN_PAGE_ROUTES.map(route=>route.id)).toEqual(expect.arrayContaining(formerNestedTabs));
    expect(ADMIN_PAGE_ROUTES).toHaveLength(18);
  });

  it.each([
    ['ou-access/groups','ou-access-groups'],
    ['proxy-routes/operations','proxy-operations'],
    ['logs/audit','logs-audit'],
    ['superset','superset'],
  ])('deep link %s resolves page %s',(path,id)=>{
    const matches=matchRoutes(ADMIN_PAGE_ROUTES.map(route=>({path:route.path,id:route.id})),`/${path}`);
    expect(matches?.at(-1)?.route.id).toBe(id);
  });

  it('has a safe authorized default and rejects an unknown route',()=>{
    const onlyAudit=authorizedAdminPages(['logs-audit']);
    expect(defaultAdminPage(onlyAudit,'operator-guide')?.id).toBe('logs-audit');
    expect(matchRoutes(onlyAudit.map(route=>({path:route.path})), '/not-existing')).toBeNull();
  });

  it('filters unauthorized page metadata and keeps authorized pages',()=>{
    const pages=authorizedAdminPages(['proxy-routes']);
    expect(pages.map(page=>page.id)).toEqual(['proxy-routes']);
    expect(pages.some(page=>page.id==='proxy-targets')).toBe(false);
    expect(authorizedAdminPages([])).toEqual([]);
  });

  it('shares relative definitions in standalone and under any Shell prefix',()=>{
    expect(authorizedAdminPages(undefined)).toHaveLength(18);
    expect(internalPathname('/management/proxy-routes/routes','/management'))
      .toBe('proxy-routes/routes');
    expect(internalPathname('/governance/proxy-routes/routes','/governance'))
      .toBe('proxy-routes/routes');
    expect(ADMIN_PUBLISHED_MANIFEST.routes.every(route=>!route.path.startsWith('/'))).toBe(true);
    expect('routePrefix'in ADMIN_PUBLISHED_MANIFEST).toBe(false);
  });
});
