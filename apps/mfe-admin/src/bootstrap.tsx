import React from'react';
import{createRoot}from'react-dom/client';
import{Alert}from'antd';
import{Navigate,Route,Routes,useLocation,useNavigate}from'react-router-dom';
import type{HostRuntime,MicroFrontendProps,RemoteContext}from'@aurevia/contracts';
import{AccessStudio}from'./AccessStudio';
import{AdminTabbedPage}from'./AdminTabbedPage';
import{IntegrationTestLab}from'./IntegrationTestLab';
import{LogsView}from'./Logs';
import{OperatorGuide}from'./OperatorGuide';
import{OuAccessManagement}from'./OuAccessManagement';
import{OutboundAuthProfiles}from'./OutboundAuthProfiles';
import{OutboundConnections}from'./OutboundConnections';
import{PanelsView}from'./Panels';
import{ProxyRouteManagement}from'./ProxyRoutes';
import{SupersetAssets}from'./SupersetAssets';
import{SupersetInstances}from'./SupersetInstances';
import{IdentityAndRoles}from'./IdentityAndRoles';
import{UserManagement}from'./UserManagement';
import{adminApi,sameOriginApi}from'./api';
import{
  ADMIN_LEGACY_ROUTE_REDIRECTS,ADMIN_PUBLISHED_MANIFEST,adminTabKey,authorizedAdminDomains,
  authorizedAdminPages,defaultAdminPage,
  type AdminPageDefinition,type AuthorizedAdminDomain,
}from'./admin-route-catalog';

export const contractVersion='1.0' as const;
export{ADMIN_PUBLISHED_MANIFEST as publishedManifest};

function Page({page}:{page:AdminPageDefinition}) {
  switch(page.id) {
    case'operator-guide':return <OperatorGuide/>;
    case'ou-access-ous':return <OuAccessManagement section="ous"/>;
    case'ou-access-groups':return <OuAccessManagement section="groups"/>;
    case'ou-access-applications':return <OuAccessManagement section="applications"/>;
    case'ou-access-explain':return <OuAccessManagement section="explain"/>;
    case'access-studio':return <AccessStudio/>;
    case'panels':return <PanelsView/>;
    case'proxy-targets':return <ProxyRouteManagement api={adminApi} section="targets"/>;
    case'proxy-routes':return <ProxyRouteManagement api={adminApi} section="routes"/>;
    case'proxy-operations':return <ProxyRouteManagement api={adminApi} section="operations"/>;
    case'outbound-connections':return <OutboundConnections api={adminApi}/>;
    case'outbound-auth':return <OutboundAuthProfiles api={adminApi}/>;
    case'integration-test':return <IntegrationTestLab api={adminApi}/>;
    case'superset-instances':return <SupersetInstances api={adminApi} healthApi={sameOriginApi}/>;
    case'users':return <UserManagement/>;
    case'identity':return <IdentityAndRoles/>;
    case'logs-api':return <LogsView section="api"/>;
    case'logs-audit':return <LogsView section="audit"/>;
    case'superset':return <SupersetAssets/>;
    default:return <Alert type="warning" showIcon message="صفحه مدیریت یافت نشد"/>;
  }
}

/**
 * The module's own mount path. React Router resolves a relative `to` against the
 * matched route, not the module root, so sibling navigation is built from the base
 * that remains once the current route's path is removed from the URL. This keeps the
 * micro frontend independent of the registration prefix the Shell mounted it under.
 */
function useModuleBasePath(routePath:string):string {
  const {pathname}=useLocation();
  const suffix=`/${routePath}`;
  return pathname.endsWith(suffix)
    ? pathname.slice(0,-suffix.length)
    : pathname.replace(new RegExp(`${suffix}/+$`),'');
}

function LegacyRedirect({from,to}:{from:string;to:string}) {
  const base=useModuleBasePath(from);
  return <Navigate to={`${base}/${to}`} replace/>;
}

/**
 * A page rendered inside its domain's tab strip. The tab strip is navigation, so
 * selecting a tab changes the URL and only the addressed page is mounted. That
 * matches the previous one-page-per-route behaviour, so switching tabs discards no
 * more in-progress form input than switching side-menu items already did.
 */
function DomainPage({entry,page}:{entry:AuthorizedAdminDomain;page:AdminPageDefinition}) {
  const navigate=useNavigate();
  const base=useModuleBasePath(page.path);
  const activeKey=adminTabKey(page);
  return <AdminTabbedPage
    title={entry.domain.title}
    description={entry.domain.description}
    activeKey={activeKey}
    onChange={key=>{
      const target=entry.pages.find(candidate=>adminTabKey(candidate)===key);
      if(target)navigate(`${base}/${target.path}`);
    }}
    tabs={entry.pages.map(candidate=>({
      key:adminTabKey(candidate),
      label:candidate.tabTitle,
      children:candidate.id===page.id?<Page page={page}/>:null,
    }))}
  />;
}

export function App({runtime,manifest}:MicroFrontendProps) {
  const module=manifest.uiCatalog?.modules.find(item=>item.moduleKey===runtime.moduleKey);
  const pages=runtime.mode==='standalone'
    ?authorizedAdminPages(undefined)
    :authorizedAdminPages(module?module.routes.map(route=>route.id):manifest.uiCatalog?[]:undefined,
        manifest.uiCatalog?undefined:manifest.permissions);
  const defaultPage=defaultAdminPage(pages,module?.defaultRouteId);
  const domains=authorizedAdminDomains(pages);
  // A legacy URL is only redirected when its destination is authorized; otherwise it
  // falls through to the same "not in your effective catalog" notice as before.
  const authorizedPaths=new Set(pages.map(page=>page.path));
  const legacyRedirects=ADMIN_LEGACY_ROUTE_REDIRECTS.filter(redirect=>
    authorizedPaths.has(redirect.to)||domains.some(entry=>entry.domain.path===redirect.to));

  if(!pages.length) return <Alert type="warning" showIcon
    message="هیچ صفحه مجازی برای این ماژول وجود ندارد"
    description="دسترسی صفحه‌ای از OpenFGA دریافت نشده است."/>;

  return <>
    <Routes>
      <Route index element={defaultPage?<Navigate to={defaultPage.path} replace/>:null}/>
      {/* Landing on a domain selects the first tab this subject is authorized for,
          so no default tab is hardcoded to a page a restricted admin cannot open. */}
      {domains.filter(entry=>!entry.domain.standalone).map(entry=>
        <Route key={`${entry.domain.key}-home`} path={entry.domain.path}
          element={<Navigate replace
            to={entry.pages[0]!.path.slice(entry.domain.path.length+1)}/>}/>)}
      {legacyRedirects.map(redirect=><Route key={`legacy-${redirect.from}`} path={redirect.from}
        element={<LegacyRedirect from={redirect.from} to={redirect.to}/>}/>)}
      {domains.flatMap(entry=>entry.pages.map(page=>
        <Route key={page.id} path={page.path} element={entry.domain.standalone
          ?<Page page={page}/>
          :<DomainPage entry={entry} page={page}/>}/>))}
      <Route path="*" element={<Alert type="warning" showIcon
        message="صفحه مدیریت یافت نشد"
        description="مسیر در کاتالوگ مؤثر این کاربر وجود ندارد."/>}/>
    </Routes>
  </>;
}

/** Compatibility export for consumers that still call mount directly. */
export function mount(element:HTMLElement,context:RemoteContext) {
  const root=createRoot(element);
  const runtime={mode:'embedded',moduleKey:'admin',routePrefix:'',
    http:{get:<T,>(path:string)=>adminApi(path)as Promise<T>,post:<T,B>(path:string,body:B)=>adminApi(path,{method:'POST',body:JSON.stringify(body)})as Promise<T>,put:<T,B>(path:string,body:B)=>adminApi(path,{method:'PUT',body:JSON.stringify(body)})as Promise<T>},
    navigation:{navigate:(path:string)=>window.history.pushState({},'',path),getModuleBasePath:()=>''},
    session:{getCurrentUser:()=>null,subscribe:()=>()=>{}},notifications:{success:()=>{},error:()=>{}},
    events:{emit:()=>{},subscribe:()=>()=>{}},sharedState:{get:()=>undefined,subscribe:()=>()=>{}},
    theme:{locale:context.locale,direction:context.locale==='fa-IR'?'rtl':'ltr'}} satisfies HostRuntime;
  root.render(<App runtime={runtime} manifest={context.manifest}/>);
  return()=>root.unmount();
}
