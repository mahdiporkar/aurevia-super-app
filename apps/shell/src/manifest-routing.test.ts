import{describe,expect,it}from'vitest';
import type{UiModuleDefinition}from'@aurevia/contracts';
import{activeCatalogModule,catalogMenuItems,composeModulePath}from'./manifest-routing';

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
});
