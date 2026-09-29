import type {MicroFrontendManifest,PluginMenu,PluginRoute} from '@aurevia/contracts';

/** A sidebar entry: one administrative domain, not one operation. */
export type AdminDomainKey=
  'operator-guide'|'access'|'users'|'panels'|'routes'|'reports'|'logs';

export interface AdminDomainDefinition {
  key:AdminDomainKey;
  /** Route path of the domain landing page; equals the single page for standalone domains. */
  path:string;
  title:string;
  description:string;
  icon:string;
  order:number;
  /**
   * The permission the Shell side menu entry is filtered by. A domain groups pages
   * that may require different resources, and the effective catalog filters one
   * route at a time, so this is the domain's entry permission. Landing always
   * redirects to the first page the subject is actually authorized for.
   */
  resource:string;
  action:string;
  /** A single-page domain needs no tab strip and no separate landing route. */
  standalone?:true;
}

export const ADMIN_DOMAINS:readonly AdminDomainDefinition[]=[
  {key:'operator-guide',path:'operator-guide',title:'راهنما',
    description:'آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت',
    icon:'book',order:10,resource:'application:aurevia',action:'admin',standalone:true},
  {key:'access',path:'access',title:'دسترسی سازمانی و مجوزها',
    description:'واحدهای سازمانی، گروه‌ها، تخصیص برنامه، درخت منابع و تحلیل تصمیم دسترسی',
    icon:'safety',order:20,resource:'application:aurevia',action:'admin'},
  {key:'users',path:'users',title:'کاربران و نقش‌ها',
    description:'ایجاد کاربر در Keycloak و مدیریت نقش‌ها و گروه‌های همگام',
    icon:'idcard',order:30,resource:'application:aurevia',action:'admin'},
  {key:'panels',path:'panels',title:'میکروفرانت‌ها',
    description:'ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation',
    icon:'appstore',order:40,resource:'application:aurevia',action:'admin',standalone:true},
  {key:'routes',path:'routes',title:'مسیرها و اتصال‌ها',
    description:'مقصد سرویس، مسیر Proxy، عملیات API، اتصال خروجی، احراز هویت و تست اتصال',
    icon:'api',order:50,resource:'proxy.route',action:'admin'},
  {key:'reports',path:'reports',title:'گزارش‌ها',
    description:'محیط‌های Superset و تخصیص سطح دسترسی داشبوردها و گزارش‌ها',
    icon:'dashboard',order:60,resource:'module:admin.superset-catalog',action:'view'},
  {key:'logs',path:'logs',title:'پایش و حسابرسی',
    description:'جست‌وجوی لاگ API و مشاهده لاگ تغییرات راهبری',
    icon:'file-search',order:70,resource:'business_resource:public-zone-logs',action:'view_api'},
] as const;

export interface AdminPageDefinition extends PluginRoute {
  domain:AdminDomainKey;
  /** Label of this page's tab inside its domain. */
  tabTitle:string;
  description:string;
  icon:string;
  order:number;
}

/**
 * Tab order follows the administrator's real configuration workflow, not the
 * alphabet: a service target exists before a route points at it, a route exists
 * before its operations are declared, and an outbound connection exists before an
 * authentication profile uses it or the test lab exercises it.
 */
export const ADMIN_PAGE_ROUTES:readonly AdminPageDefinition[]=[
  {id:'operator-guide',path:'operator-guide',title:'راهنما',tabTitle:'راهنما',description:'آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت',domain:'operator-guide',icon:'book',order:10,resource:'application:aurevia',action:'admin'},

  {id:'ou-access-ous',path:'access/ous',title:'واحدهای سازمانی',tabTitle:'واحدهای سازمانی',description:'مشاهده OUهای همگام‌شده از Directory و اعضای سازمان',domain:'access',icon:'apartment',order:20,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-groups',path:'access/groups',title:'گروه‌ها',tabTitle:'گروه‌ها',description:'ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها',domain:'access',icon:'team',order:21,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-applications',path:'access/applications',title:'برنامه‌ها',tabTitle:'برنامه‌ها',description:'اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی',domain:'access',icon:'appstore',order:22,resource:'application:aurevia',action:'admin'},
  {id:'access-studio',path:'access/resources',title:'منابع و مجوزها',tabTitle:'منابع و مجوزها',description:'مدیریت درخت منابع، عملیات و Grantهای OpenFGA',domain:'access',icon:'safety',order:23,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-explain',path:'access/explain',title:'تحلیل دسترسی',tabTitle:'تحلیل دسترسی',description:'ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی',domain:'access',icon:'audit',order:24,resource:'application:aurevia',action:'admin'},

  {id:'users',path:'users/accounts',title:'مدیریت کاربران',tabTitle:'کاربران',description:'ایجاد کاربر جدید در Keycloak از پنل مدیریت بدون دسترسی مستقیم مرورگر به Keycloak',domain:'users',icon:'user-add',order:30,resource:'application:aurevia',action:'admin'},
  {id:'identity',path:'users/roles',title:'هویت و نقش',tabTitle:'نقش‌ها و گروه‌ها',description:'مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران',domain:'users',icon:'idcard',order:31,resource:'application:aurevia',action:'admin'},

  {id:'panels',path:'panels',title:'میکروفرانت‌ها',tabTitle:'میکروفرانت‌ها',description:'ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation',domain:'panels',icon:'appstore',order:40,resource:'application:aurevia',action:'admin'},

  {id:'proxy-targets',path:'routes/targets',title:'مقصدها',tabTitle:'مقصدها',description:'تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check',domain:'routes',icon:'api',order:50,resource:'proxy.target',action:'admin'},
  {id:'proxy-routes',path:'routes/routes',title:'مسیرها',tabTitle:'مسیرها',description:'اتصال namespace ورودی Microfrontend به مقصد سرویس',domain:'routes',icon:'branches',order:51,resource:'proxy.route',action:'admin'},
  {id:'proxy-operations',path:'routes/operations',title:'عملیات API',tabTitle:'عملیات API',description:'تعریف Method، Path و Resource/Action موردنیاز هر API',domain:'routes',icon:'control',order:52,resource:'proxy.operation',action:'admin'},
  {id:'outbound-connections',path:'routes/connections',title:'اتصال‌ها',tabTitle:'اتصال‌ها',description:'ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy',domain:'routes',icon:'link',order:53,resource:'integration.auth-profile',action:'admin'},
  {id:'outbound-auth',path:'routes/auth',title:'احراز هویت',tabTitle:'احراز هویت',description:'تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret',domain:'routes',icon:'key',order:54,resource:'integration.auth-profile',action:'admin'},
  {id:'integration-test',path:'routes/test',title:'تست اتصال',tabTitle:'تست اتصال',description:'اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد',domain:'routes',icon:'experiment',order:55,resource:'integration.auth-profile',action:'test'},

  {id:'superset-instances',path:'reports/instances',title:'محیط‌های گزارش',tabTitle:'محیط‌ها',description:'مدیریت Instanceهای Public و Operation در Apache Superset',domain:'reports',icon:'cloud-server',order:60,resource:'application:aurevia',action:'admin'},
  {id:'superset',path:'reports/assets',title:'گزارش‌ها',tabTitle:'داشبوردها و دسترسی',description:'تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset',domain:'reports',icon:'dashboard',order:61,resource:'module:admin.superset-catalog',action:'view'},

  {id:'logs-api',path:'logs/api',title:'لاگ API',tabTitle:'لاگ API',description:'جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID',domain:'logs',icon:'file-search',order:70,resource:'business_resource:public-zone-logs',action:'view_api'},
  {id:'logs-audit',path:'logs/audit',title:'لاگ راهبری',tabTitle:'لاگ راهبری',description:'مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات',domain:'logs',icon:'audit',order:71,resource:'business_resource:public-zone-logs',action:'view_audit'},
] as const;

/**
 * Routes that exist only so a domain has one addressable landing URL the Shell
 * side menu can point at. Standalone domains reuse their single page instead.
 */
export const ADMIN_DOMAIN_ROUTES:readonly PluginRoute[]=ADMIN_DOMAINS
  .filter(domain=>!domain.standalone)
  .map(domain=>({id:`${domain.key}-home`,path:domain.path,title:domain.title,
    resource:domain.resource,action:domain.action}));

/** One menu entry per administrative domain; individual operations become tabs. */
export const ADMIN_MENUS:readonly PluginMenu[]=ADMIN_DOMAINS.map(domain=>({
  id:`${domain.key}-menu`,
  routeId:domain.standalone?domain.key:`${domain.key}-home`,
  title:domain.title,description:domain.description,icon:domain.icon,order:domain.order,
}));

/**
 * Admin URLs published before the topic-based restructure. They are still served
 * and redirect to the page's new location inside its domain, so bookmarks and
 * runbook links keep working instead of returning "route not found".
 */
export const ADMIN_LEGACY_ROUTE_REDIRECTS:ReadonlyArray<{from:string;to:string}>=[
  {from:'ou-access',to:'access'},
  {from:'ou-access/ous',to:'access/ous'},
  {from:'ou-access/groups',to:'access/groups'},
  {from:'ou-access/applications',to:'access/applications'},
  {from:'ou-access/explain',to:'access/explain'},
  {from:'access-studio',to:'access/resources'},
  {from:'identity',to:'users/roles'},
  {from:'proxy-routes',to:'routes'},
  {from:'proxy-routes/targets',to:'routes/targets'},
  {from:'proxy-routes/routes',to:'routes/routes'},
  {from:'proxy-routes/operations',to:'routes/operations'},
  {from:'outbound-connections',to:'routes/connections'},
  {from:'outbound-auth',to:'routes/auth'},
  {from:'integration-test',to:'routes/test'},
  {from:'superset-instances',to:'reports/instances'},
  {from:'superset',to:'reports/assets'},
] as const;

/**
 * The Shell mounts this micro frontend only on a route the effective catalog
 * published, so a legacy URL has to stay a published route for its in-app redirect
 * to ever run. Each one declares exactly the resource and action of the page it
 * redirects to, so a legacy URL can never reach a page its subject could not open
 * at its new location either.
 */
export const ADMIN_LEGACY_ROUTES:readonly PluginRoute[]=ADMIN_LEGACY_ROUTE_REDIRECTS
  .map(redirect=>{
    const target=ADMIN_PAGE_ROUTES.find(page=>page.path===redirect.to)
      ??ADMIN_DOMAINS.find(domain=>domain.path===redirect.to);
    if(!target) throw new Error(`Legacy admin redirect has no target: ${redirect.to}`);
    return {
      id:`legacy-${redirect.from.replaceAll('/','-')}`,
      path:redirect.from,
      title:target.title,
      resource:target.resource,
      action:target.action,
    };
  });

/** Authorization references only; resource definitions live in resource-manifest.json. */
export const ADMIN_PUBLISHED_MANIFEST:MicroFrontendManifest={
  schemaVersion:'1.0',
  microfrontend:{key:'admin',name:'Administration',version:'0.7.0'},
  runtime:{remoteEntry:'http://localhost:3001/remoteEntry.js',remoteName:'aurevia_admin',
    exposedModule:'./bootstrap',contractVersion:'1.0',apiBasePath:'/api/v1/admin'},
  defaultRouteKey:'operator-guide',
  routes:[...ADMIN_PAGE_ROUTES,...ADMIN_DOMAIN_ROUTES,...ADMIN_LEGACY_ROUTES]
    .map(({id,path,title,resource,action})=>({
      key:id,path,title,requiredResource:resource!,requiredAction:action,
    })),
  navigation:ADMIN_MENUS.map(menu=>({key:menu.id,type:'PAGE' as const,
    routeKey:menu.routeId,title:menu.title,description:menu.description,
    icon:menu.icon,order:menu.order})),
};

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

export interface AuthorizedAdminDomain {
  domain:AdminDomainDefinition;
  pages:AdminPageDefinition[];
}

/**
 * The domains this subject can actually reach, each carrying only the pages the
 * effective catalog authorized. A domain with no authorized page is not returned,
 * so tab consolidation never exposes an operation the subject cannot perform.
 */
export function authorizedAdminDomains(
    pages:readonly AdminPageDefinition[]):AuthorizedAdminDomain[] {
  return ADMIN_DOMAINS
    .map(domain=>({domain,pages:pages.filter(page=>page.domain===domain.key)}))
    .filter(entry=>entry.pages.length>0);
}

export function adminDomainOf(page:AdminPageDefinition):AdminDomainDefinition {
  const domain=ADMIN_DOMAINS.find(item=>item.key===page.domain);
  if(!domain) throw new Error(`Unknown admin domain: ${page.domain}`);
  return domain;
}

/** The tab segment of a page path, used as the URL-addressable tab key. */
export function adminTabKey(page:AdminPageDefinition):string {
  const domain=adminDomainOf(page);
  return domain.standalone?page.path:page.path.slice(domain.path.length+1);
}

export function internalPathname(pathname:string,moduleBasePath:string):string {
  const normalizedBase=moduleBasePath==='/'?'':moduleBasePath.replace(/\/$/,'');
  const withoutBase=normalizedBase&&(pathname===normalizedBase||pathname.startsWith(`${normalizedBase}/`))
    ?pathname.slice(normalizedBase.length):pathname;
  return withoutBase.replace(/^\/+|\/+$/g,'');
}
