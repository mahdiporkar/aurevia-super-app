import React from'react';
import{createRoot}from'react-dom/client';
import{Alert,Card,Tabs,Typography}from'antd';
import{Navigate,Route,Routes,useLocation,useNavigate}from'react-router-dom';
import type{HostRuntime,MicroFrontendProps,RemoteContext}from'@aurevia/contracts';
import{AccessStudio}from'./AccessStudio';
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
import{adminApi}from'./api';
import{
  ADMIN_PUBLISHED_MANIFEST,authorizedAdminPages,defaultAdminPage,internalPathname,
  type AdminPageDefinition,type AdminSectionKey,
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
    case'superset-instances':return <SupersetInstances api={adminApi}/>;
    case'identity':return <IdentityAndRoles/>;
    case'logs-api':return <LogsView section="api"/>;
    case'logs-audit':return <LogsView section="audit"/>;
    case'superset':return <SupersetAssets/>;
    default:return <Alert type="warning" showIcon message="صفحه مدیریت یافت نشد"/>;
  }
}

function sectionPages(pages:readonly AdminPageDefinition[],section:AdminSectionKey) {
  return pages.filter(page=>page.section===section);
}

export function App({runtime,manifest}:MicroFrontendProps) {
  const location=useLocation(),navigate=useNavigate();
  const module=manifest.uiCatalog?.modules.find(item=>item.moduleKey===runtime.moduleKey);
  const pages=runtime.mode==='standalone'
    ?authorizedAdminPages(undefined)
    :authorizedAdminPages(module?module.routes.map(route=>route.id):manifest.uiCatalog?[]:undefined,
        manifest.uiCatalog?undefined:manifest.permissions);
  const defaultPage=defaultAdminPage(pages,module?.defaultRouteId);
  const base=runtime.mode==='embedded'?runtime.navigation.getModuleBasePath():'';
  const internalPath=internalPathname(location.pathname,base);
  const activePage=pages.find(page=>page.path===internalPath);
  const activeSection=activePage?.section??pages.find(page=>internalPath===page.section||
    internalPath.startsWith(`${page.section}/`))?.section;
  const sections=pages.reduce<Array<{key:AdminSectionKey;label:string;page:AdminPageDefinition}>>(
    (result,page)=>{
      if(!result.some(item=>item.key===page.section)) {
        result.push({key:page.section,label:page.sectionTitle,page});
      }
      return result;
    },[]);
  const go=(path:string)=>runtime.mode==='embedded'
    ?runtime.navigation.navigate(path):navigate(`/${path}`);
  const childPages=activeSection?sectionPages(pages,activeSection):[];
  const groupRedirects=sections.filter(item=>item.page.path!==item.key);

  if(!pages.length) return <Alert type="warning" showIcon
    message="هیچ صفحه مجازی برای این ماژول وجود ندارد"
    description="دسترسی صفحه‌ای از OpenFGA دریافت نشده است."/>;

  return <Card>
    <Typography.Title level={3}>مرکز مدیریت Aurevia</Typography.Title>
    <Alert showIcon type="info" message={pages.some(page=>page.id!=='superset')
      ?'تعریف میکروفرانت، مدل‌سازی منابع و مدیریت دسترسی مبتنی بر OU'
      :'راهبری گزارش‌ها و داشبوردهای مجاز'}/>
    <Tabs style={{marginTop:16}} activeKey={activeSection}
      onChange={key=>go(sections.find(item=>item.key===key)!.page.path)}
      items={sections.map(item=>({key:item.key,label:item.label}))}/>
    {childPages.length>1&&<Tabs size="small" activeKey={activePage?.id}
      onChange={id=>go(pages.find(page=>page.id===id)!.path)}
      items={childPages.map(page=>({key:page.id,label:page.title}))}/>}
    <Routes>
      <Route index element={defaultPage?<Navigate to={defaultPage.path} replace/>:null}/>
      {groupRedirects.map(item=><Route key={`${item.key}-index`} path={item.key}
        element={<Navigate to={item.page.path.slice(item.key.length+1)} replace/>}/>)}
      {pages.map(page=><Route key={page.id} path={page.path} element={<Page page={page}/>}/>)}
      <Route path="*" element={<Alert type="warning" showIcon
        message="صفحه مدیریت یافت نشد"
        description="مسیر در کاتالوگ مؤثر این کاربر وجود ندارد."/>}/>
    </Routes>
  </Card>;
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
