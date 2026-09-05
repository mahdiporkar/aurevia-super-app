import type {PluginMenu,PluginRoute} from '@aurevia/contracts';

export type AdminSectionKey=
  'operator-guide'|'ou-access'|'access-studio'|'panels'|'proxy-routes'|
  'outbound-connections'|'outbound-auth'|'integration-test'|'superset-instances'|
  'identity'|'logs'|'superset';

export interface AdminPageDefinition extends PluginRoute {
  section:AdminSectionKey;
  sectionTitle:string;
  icon:string;
  order:number;
}

export const ADMIN_PAGE_ROUTES:readonly AdminPageDefinition[]=[
  {id:'operator-guide',path:'operator-guide',title:'راهنمای فرم‌ها',section:'operator-guide',sectionTitle:'راهنمای فرم‌ها',icon:'book',order:10,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-ous',path:'ou-access/ous',title:'OUهای سازمانی',section:'ou-access',sectionTitle:'دسترسی مبتنی بر OU',icon:'apartment',order:20,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-groups',path:'ou-access/groups',title:'Access Groupها',section:'ou-access',sectionTitle:'دسترسی مبتنی بر OU',icon:'team',order:21,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-applications',path:'ou-access/applications',title:'دسترسی Microfrontend',section:'ou-access',sectionTitle:'دسترسی مبتنی بر OU',icon:'appstore',order:22,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-explain',path:'ou-access/explain',title:'بررسی دسترسی User',section:'ou-access',sectionTitle:'دسترسی مبتنی بر OU',icon:'audit',order:23,resource:'application:aurevia',action:'admin'},
  {id:'access-studio',path:'access-studio',title:'استودیوی دسترسی',section:'access-studio',sectionTitle:'استودیوی دسترسی',icon:'safety',order:30,resource:'application:aurevia',action:'admin'},
  {id:'panels',path:'panels',title:'میکروفرانت‌ها',section:'panels',sectionTitle:'میکروفرانت‌ها',icon:'appstore',order:40,resource:'application:aurevia',action:'admin'},
  {id:'proxy-targets',path:'proxy-routes/targets',title:'Service Targets',section:'proxy-routes',sectionTitle:'راهبری Proxy',icon:'api',order:50,resource:'proxy.target',action:'admin'},
  {id:'proxy-routes',path:'proxy-routes/routes',title:'Proxy Routes',section:'proxy-routes',sectionTitle:'راهبری Proxy',icon:'branches',order:51,resource:'proxy.route',action:'admin'},
  {id:'proxy-operations',path:'proxy-routes/operations',title:'Route Operations',section:'proxy-routes',sectionTitle:'راهبری Proxy',icon:'control',order:52,resource:'proxy.operation',action:'admin'},
  {id:'outbound-connections',path:'outbound-connections',title:'اتصال‌های Legacy',section:'outbound-connections',sectionTitle:'اتصال‌های Legacy',icon:'link',order:60,resource:'integration.auth-profile',action:'admin'},
  {id:'outbound-auth',path:'outbound-auth',title:'پروفایل‌های احراز هویت سرویس‌ها',section:'outbound-auth',sectionTitle:'پروفایل‌های احراز هویت سرویس‌ها',icon:'key',order:70,resource:'integration.auth-profile',action:'admin'},
  {id:'integration-test',path:'integration-test',title:'آزمایشگاه اتصال',section:'integration-test',sectionTitle:'آزمایشگاه اتصال',icon:'experiment',order:80,resource:'integration.auth-profile',action:'test'},
  {id:'superset-instances',path:'superset-instances',title:'محیط‌های Superset',section:'superset-instances',sectionTitle:'محیط‌های Superset',icon:'cloud-server',order:90,resource:'application:aurevia',action:'admin'},
  {id:'identity',path:'identity',title:'گروه‌ها و نقش‌ها',section:'identity',sectionTitle:'گروه‌ها و نقش‌ها',icon:'idcard',order:100,resource:'application:aurevia',action:'admin'},
  {id:'logs-api',path:'logs/api',title:'API Logs',section:'logs',sectionTitle:'لاگ‌ها',icon:'file-search',order:110,resource:'business_resource:public-zone-logs',action:'view_api'},
  {id:'logs-audit',path:'logs/audit',title:'Audit Logs',section:'logs',sectionTitle:'لاگ‌ها',icon:'audit',order:111,resource:'business_resource:public-zone-logs',action:'view_audit'},
  {id:'superset',path:'superset',title:'گزارش‌ها و داشبوردها',section:'superset',sectionTitle:'گزارش‌ها و داشبوردها',icon:'dashboard',order:120,resource:'module:admin.superset-catalog',action:'view'},
] as const;

export const ADMIN_MENUS:readonly PluginMenu[]=ADMIN_PAGE_ROUTES.map(route=>({
  id:`${route.id}-menu`,routeId:route.id,title:route.title,icon:route.icon,order:route.order,
}));

/** Metadata published to the existing UI artifact registry; routePrefix is intentionally absent. */
export const ADMIN_PUBLISHED_MANIFEST={
  schemaVersion:'1.0',moduleKey:'admin',defaultRouteId:'operator-guide',
  runtime:{apiBasePath:'/api/v1/admin'},
  routes:ADMIN_PAGE_ROUTES.map(({id,path,title,resource,action})=>({id,path,title,resource,action})),
  menus:ADMIN_MENUS,
} as const;

export function authorizedAdminPages(routeIds:readonly string[]|undefined,
    legacyPermissions?:Record<string,readonly string[]>):AdminPageDefinition[] {
  if(routeIds) {
    const allowed=new Set(routeIds);
    return ADMIN_PAGE_ROUTES.filter(route=>allowed.has(route.id));
  }
  if(!legacyPermissions) return [...ADMIN_PAGE_ROUTES];
  const platformAdmin=(legacyPermissions['application:aurevia']??[]).includes('admin');
  if(platformAdmin) return [...ADMIN_PAGE_ROUTES];
  const reportDesigner=(legacyPermissions['module:admin.superset-catalog']??[])
    .some(action=>['view','admin','assign'].includes(action));
  return reportDesigner?ADMIN_PAGE_ROUTES.filter(route=>route.id==='superset'):[];
}

export function defaultAdminPage(pages:readonly AdminPageDefinition[],preferredId?:string) {
  return pages.find(page=>page.id===preferredId)??pages[0];
}

export function internalPathname(pathname:string,moduleBasePath:string):string {
  const normalizedBase=moduleBasePath==='/'?'':moduleBasePath.replace(/\/$/,'');
  const withoutBase=normalizedBase&&(pathname===normalizedBase||pathname.startsWith(`${normalizedBase}/`))
    ?pathname.slice(normalizedBase.length):pathname;
  return withoutBase.replace(/^\/+|\/+$/g,'');
}
