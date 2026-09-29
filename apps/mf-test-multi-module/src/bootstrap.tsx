import React from 'react';
import type { MicroFrontendProps } from '@aurevia/contracts';
import { SHManifestProvider, SHRouteGuard } from '@aurevia/sh-core-ui';

export const contractVersion = '1.0' as const;
export function App({ runtime, manifest }: MicroFrontendProps) {
  const module = manifest.uiCatalog?.modules.find(item => item.moduleKey === 'test-multi-module');
  const routes = module?.routes ?? [];
  const currentPath = window.location.pathname.replace(/\/$/, '');
  const route = routes.find(item => currentPath === `/${module?.routePrefix}/${item.path}`);
  const nodes = (manifest.resourceTree ?? []).filter(item => item.resource_key === 'application:aurevia/test-multi-module'
    || item.resource_key.startsWith('module:test-multi-module') || item.resource_key.startsWith('page:test-multi-module.'));
  const renderChildren = (parentId: string): React.ReactNode => <ul>{nodes.filter(node => node.parent_id === parentId).map(node =>
    <li key={node.id}>{node.name_en} ({node.type}){renderChildren(node.id)}</li>)}</ul>;
  const root = nodes.find(node => node.type === 'APPLICATION');
  return <SHManifestProvider initial={manifest}><main style={{ padding: 24 }}>
    <h1>TEST_MULTI_MODULE</h1>
    <p>Employee · Payroll · Attendance</p>
    <nav aria-label="Available pages"><ul>{routes.map(item => <li key={item.id}>
      <a href={`/${module?.routePrefix}/${item.path}`} onClick={event => {
        event.preventDefault(); runtime.navigation.navigate(item.path);
      }}>{item.title}</a>
    </li>)}</ul></nav>
    {root && <section aria-label="Accessible resource tree"><h2>Your resource tree</h2>{root.name_en}{renderChildren(root.id)}</section>}
    {route?.resource ? <SHRouteGuard resource={route.resource} action={route.action ?? 'view'}>
      <section data-testid={route.id}><h2>{route.title}</h2><p>This is a demonstration page.</p></section>
    </SHRouteGuard> : <p role="status">Choose an available page. Unavailable routes are not displayed.</p>}
  </main></SHManifestProvider>;
}
export const plugin = { contractVersion, App };
