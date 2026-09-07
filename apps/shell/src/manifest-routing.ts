import type{UiModuleDefinition}from'@aurevia/contracts';

export interface CatalogMenuItem {
  key:string;
  navigationKey:string;
  parentNavigationKey?:string;
  type:'GROUP'|'PAGE'|'EXTERNAL_LINK';
  title:string;
  description?:string;
  icon?:string;
  order:number;
  module:UiModuleDefinition;
  children?:CatalogMenuItem[];
}

export function moduleBasePath(module:Pick<UiModuleDefinition,'routePrefix'>):string {
  return `/${module.routePrefix}`.replace(/\/+$/,'');
}

export function composeModulePath(module:Pick<UiModuleDefinition,'routePrefix'>,relativePath:string):string {
  return `${moduleBasePath(module)}/${relativePath}`.replace(/\/+/g,'/').replace(/\/$/,'');
}

export function catalogMenuItems(modules:readonly UiModuleDefinition[]):CatalogMenuItem[] {
  const result:CatalogMenuItem[]=[];
  for(const module of modules) {
    const navigation=module.navigation?.length?module.navigation:module.menus.map(item=>({
      key:item.id,type:'PAGE' as const,parentKey:item.parentId,pageKey:item.routeId,
      title:item.title,description:item.description,icon:item.icon,order:item.order,source:'MANIFEST' as const,
    }));
    for(const item of navigation) {
      const navigationKey=`${module.registrationId}:${item.key}`;
      const parentNavigationKey=item.parentKey?`${module.registrationId}:${item.parentKey}`:undefined;
      if(item.type==='GROUP')result.push({key:`group:${navigationKey}`,navigationKey,
        parentNavigationKey,type:item.type,title:item.title,description:item.description,icon:item.icon??module.icon,
        order:item.order,module});
      else if(item.type==='EXTERNAL_LINK'&&item.externalUrl)result.push({key:item.externalUrl,
        navigationKey,parentNavigationKey,type:item.type,title:item.title,description:item.description,
        icon:item.icon??module.icon,order:item.order,module});
      else if(item.type==='PAGE') {
        const route=module.routes.find(candidate=>candidate.id===item.pageKey);
        if(route)result.push({key:composeModulePath(module,route.path),navigationKey,
          parentNavigationKey,type:'PAGE',title:item.title,description:item.description,
          icon:item.icon??module.icon,order:item.order,module});
      }
    }
  }
  return result;
}

export function catalogMenuTree(items:readonly CatalogMenuItem[]):CatalogMenuItem[] {
  const byNavigationKey=new Map<string,CatalogMenuItem>(items.map(item=>
    [item.navigationKey,{...item,children:[] as CatalogMenuItem[]}]))
  const roots:CatalogMenuItem[]=[];
  for(const item of byNavigationKey.values()) {
    const parent=item.parentNavigationKey?byNavigationKey.get(item.parentNavigationKey):undefined;
    if(parent)parent.children!.push(item);else roots.push(item);
  }
  const sort=(nodes:CatalogMenuItem[])=>{nodes.sort((left,right)=>left.order-right.order);
    nodes.forEach(node=>sort(node.children??[]));};
  sort(roots);return roots;
}

export function activeCatalogModule(modules:readonly UiModuleDefinition[],pathname:string) {
  return modules.find(module=>pathname===moduleBasePath(module)||
    pathname.startsWith(`${moduleBasePath(module)}/`));
}

export function activeCatalogMenuKey(items:readonly Pick<CatalogMenuItem,'key'>[],pathname:string) {
  return items.filter(item=>item.key.startsWith('/')
      &&(pathname===item.key||pathname.startsWith(`${item.key}/`)))
    .sort((left,right)=>right.key.length-left.key.length)[0]?.key;
}
