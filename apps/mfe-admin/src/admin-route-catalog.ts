import type {MicroFrontendManifest,PluginMenu,PluginRoute} from '@aurevia/contracts';

export type AdminSectionKey=
  'operator-guide'|'ou-access'|'access-studio'|'panels'|'proxy-routes'|
  'outbound-connections'|'outbound-auth'|'integration-test'|'superset-instances'|
  'identity'|'logs'|'superset';

export interface AdminPageDefinition extends PluginRoute {
  section:AdminSectionKey;
  sectionTitle:string;
  description:string;
  icon:string;
  order:number;
}

export const ADMIN_PAGE_ROUTES:readonly AdminPageDefinition[]=[
  {id:'operator-guide',path:'operator-guide',title:'راهنما',description:'آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت',section:'operator-guide',sectionTitle:'راهنمای راهبری',icon:'book',order:10,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-ous',path:'ou-access/ous',title:'واحدهای سازمانی',description:'مشاهده OUهای همگام‌شده از Directory و اعضای سازمان',section:'ou-access',sectionTitle:'دسترسی سازمانی',icon:'apartment',order:20,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-groups',path:'ou-access/groups',title:'گروه‌ها',description:'ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها',section:'ou-access',sectionTitle:'دسترسی سازمانی',icon:'team',order:21,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-applications',path:'ou-access/applications',title:'برنامه‌ها',description:'اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی',section:'ou-access',sectionTitle:'دسترسی سازمانی',icon:'appstore',order:22,resource:'application:aurevia',action:'admin'},
  {id:'ou-access-explain',path:'ou-access/explain',title:'تحلیل دسترسی',description:'ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی',section:'ou-access',sectionTitle:'دسترسی سازمانی',icon:'audit',order:23,resource:'application:aurevia',action:'admin'},
  {id:'access-studio',path:'access-studio',title:'منابع و مجوزها',description:'مدیریت درخت منابع، عملیات و Grantهای OpenFGA',section:'access-studio',sectionTitle:'استودیوی مجوزها',icon:'safety',order:30,resource:'application:aurevia',action:'admin'},
  {id:'panels',path:'panels',title:'میکروفرانت‌ها',description:'ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation',section:'panels',sectionTitle:'مدیریت میکروفرانت‌ها',icon:'appstore',order:40,resource:'application:aurevia',action:'admin'},
  {id:'proxy-targets',path:'proxy-routes/targets',title:'مقصدها',description:'تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check',section:'proxy-routes',sectionTitle:'راهبری API Proxy',icon:'api',order:50,resource:'proxy.target',action:'admin'},
  {id:'proxy-routes',path:'proxy-routes/routes',title:'مسیرها',description:'اتصال namespace ورودی Microfrontend به مقصد سرویس',section:'proxy-routes',sectionTitle:'راهبری API Proxy',icon:'branches',order:51,resource:'proxy.route',action:'admin'},
  {id:'proxy-operations',path:'proxy-routes/operations',title:'عملیات API',description:'تعریف Method، Path و Resource/Action موردنیاز هر API',section:'proxy-routes',sectionTitle:'راهبری API Proxy',icon:'control',order:52,resource:'proxy.operation',action:'admin'},
  {id:'outbound-connections',path:'outbound-connections',title:'اتصال‌ها',description:'ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy',section:'outbound-connections',sectionTitle:'اتصال‌های خروجی',icon:'link',order:60,resource:'integration.auth-profile',action:'admin'},
  {id:'outbound-auth',path:'outbound-auth',title:'احراز هویت',description:'تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret',section:'outbound-auth',sectionTitle:'احراز هویت سرویس‌ها',icon:'key',order:70,resource:'integration.auth-profile',action:'admin'},
  {id:'integration-test',path:'integration-test',title:'تست اتصال',description:'اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد',section:'integration-test',sectionTitle:'آزمایش اتصال',icon:'experiment',order:80,resource:'integration.auth-profile',action:'test'},
  {id:'superset-instances',path:'superset-instances',title:'محیط‌های گزارش',description:'مدیریت Instanceهای Public و Operation در Apache Superset',section:'superset-instances',sectionTitle:'محیط‌های گزارش‌گیری',icon:'cloud-server',order:90,resource:'application:aurevia',action:'admin'},
  {id:'identity',path:'identity',title:'هویت و نقش',description:'مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران',section:'identity',sectionTitle:'هویت‌ها و نقش‌ها',icon:'idcard',order:100,resource:'application:aurevia',action:'admin'},
  {id:'logs-api',path:'logs/api',title:'لاگ API',description:'جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID',section:'logs',sectionTitle:'پایش و حسابرسی',icon:'file-search',order:110,resource:'business_resource:public-zone-logs',action:'view_api'},
  {id:'logs-audit',path:'logs/audit',title:'لاگ راهبری',description:'مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات',section:'logs',sectionTitle:'پایش و حسابرسی',icon:'audit',order:111,resource:'business_resource:public-zone-logs',action:'view_audit'},
  {id:'superset',path:'superset',title:'گزارش‌ها',description:'تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset',section:'superset',sectionTitle:'دسترسی گزارش‌ها',icon:'dashboard',order:120,resource:'module:admin.superset-catalog',action:'view'},
] as const;

export const ADMIN_MENUS:readonly PluginMenu[]=ADMIN_PAGE_ROUTES.map(route=>({
  id:`${route.id}-menu`,routeId:route.id,title:route.title,description:route.description,
  icon:route.icon,order:route.order,
}));

/** Authorization references only; resource definitions live in resource-manifest.json. */
export const ADMIN_PUBLISHED_MANIFEST:MicroFrontendManifest={
  schemaVersion:'1.0',
  microfrontend:{key:'admin',name:'Administration',version:'0.5.0'},
  runtime:{remoteEntry:'http://localhost:3001/remoteEntry.js',remoteName:'aurevia_admin',
    exposedModule:'./bootstrap',contractVersion:'1.0',apiBasePath:'/api/v1/admin'},
  defaultRouteKey:'operator-guide',
  routes:ADMIN_PAGE_ROUTES.map(({id,path,title,resource,action})=>({
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

export function internalPathname(pathname:string,moduleBasePath:string):string {
  const normalizedBase=moduleBasePath==='/'?'':moduleBasePath.replace(/\/$/,'');
  const withoutBase=normalizedBase&&(pathname===normalizedBase||pathname.startsWith(`${normalizedBase}/`))
    ?pathname.slice(normalizedBase.length):pathname;
  return withoutBase.replace(/^\/+|\/+$/g,'');
}
