import type{UiModuleDefinition}from'@aurevia/contracts';

export interface CatalogMenuItem {
  key:string;
  title:string;
  icon?:string;
  module:UiModuleDefinition;
}

export function moduleBasePath(module:Pick<UiModuleDefinition,'routePrefix'>):string {
  return `/${module.routePrefix}`.replace(/\/+$/,'');
}

export function composeModulePath(module:Pick<UiModuleDefinition,'routePrefix'>,relativePath:string):string {
  return `${moduleBasePath(module)}/${relativePath}`.replace(/\/+/g,'/').replace(/\/$/,'');
}

export function catalogMenuItems(modules:readonly UiModuleDefinition[]):CatalogMenuItem[] {
  return modules.flatMap(module=>module.menus.flatMap(item=>{
    const route=module.routes.find(candidate=>candidate.id===item.routeId);
    return route?[{key:composeModulePath(module,route.path),title:item.title,
      icon:item.icon??module.icon,module}]:[];
  }));
}

export function activeCatalogModule(modules:readonly UiModuleDefinition[],pathname:string) {
  return modules.find(module=>pathname===moduleBasePath(module)||
    pathname.startsWith(`${moduleBasePath(module)}/`));
}

export function activeCatalogMenuKey(items:readonly Pick<CatalogMenuItem,'key'>[],pathname:string) {
  return items.filter(item=>pathname===item.key||pathname.startsWith(`${item.key}/`))
    .sort((left,right)=>right.key.length-left.key.length)[0]?.key;
}
