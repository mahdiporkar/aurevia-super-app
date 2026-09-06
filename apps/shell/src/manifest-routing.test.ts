import{describe,expect,it}from'vitest';
import type{UiModuleDefinition}from'@aurevia/contracts';
import{activeCatalogMenuKey,activeCatalogModule,catalogMenuItems,catalogMenuTree,composeModulePath}from'./manifest-routing';

function module(routePrefix='management'):UiModuleDefinition{return{
  registrationId:'11111111-1111-1111-1111-111111111111',moduleKey:'admin',
  displayName:'مدیریت',displayNameEn:'Administration',order:10,routePrefix,
  defaultRouteId:'resources',remote:{remoteEntryUrl:'https://static.example.test/admin/remoteEntry.js',remoteName:'aurevia_admin',exposedModule:'./bootstrap',contractVersion:'1.0',artifactVersion:'0.2.0'},
  runtime:{apiBasePath:'/api/v1/admin'},
  routes:[{id:'resources',path:'resources',title:'منابع'}],
  menus:[{id:'resources-menu',routeId:'resources',title:'منابع',order:10}],
}}

describe('effective uiCatalog routing',()=>{
  it('consumes only modules and pages returned by the effective catalog',()=>{
    const authorized=module();
    expect(catalogMenuItems([authorized]).map(item=>item.key)).toEqual(['/management/resources']);
    expect(catalogMenuItems([])).toEqual([]);
    expect(activeCatalogModule([], '/management/resources')).toBeUndefined();
  });

  it('composes registration routePrefix without changing the MFE route',()=>{
    expect(composeModulePath(module('management'),'resources')).toBe('/management/resources');
    expect(composeModulePath(module('governance'),'resources')).toBe('/governance/resources');
    expect(module('governance').routes[0]?.path).toBe('resources');
  });

  it('recognizes deep links beneath the registered module prefix',()=>{
    expect(activeCatalogModule([module()],'/management/resources')?.moduleKey).toBe('admin');
  });

  it('keeps the nearest parent menu selected on detail routes',()=>{
    const items=[{key:'/hr/personal'},{key:'/hr/personal/archive'}];
    expect(activeCatalogMenuKey(items,'/hr/personal/e-101')).toBe('/hr/personal');
    expect(activeCatalogMenuKey(items,'/hr/personal/archive/2025')).toBe('/hr/personal/archive');
    expect(activeCatalogMenuKey(items,'/finance/payments')).toBeUndefined();
  });

  it('builds manifest groups, pages and secure external links without treating groups as routes',()=>{
    const governed={...module(),navigation:[
      {key:'admin.root',type:'GROUP' as const,title:'راهبری',order:20,source:'MANIFEST' as const},
      {key:'admin.resources',type:'PAGE' as const,parentKey:'admin.root',pageKey:'resources',
        title:'منابع',order:10,source:'MANIFEST' as const},
      {key:'admin.help',type:'EXTERNAL_LINK' as const,parentKey:'admin.root',title:'راهنما',
        externalUrl:'https://docs.example.test/admin',order:30,source:'ADMIN' as const},
    ]};

    const flat=catalogMenuItems([governed]);
    const tree=catalogMenuTree(flat);

    expect(flat.map(item=>item.key)).toEqual([
      `group:${governed.registrationId}:admin.root`,
      '/management/resources',
      'https://docs.example.test/admin',
    ]);
    expect(tree).toHaveLength(1);
    expect(tree[0]?.children?.map(item=>item.title)).toEqual(['منابع','راهنما']);
  });
});
