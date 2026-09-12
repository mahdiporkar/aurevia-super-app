import React from 'react';
import { createRoot } from 'react-dom/client';
import type { HostRuntime, EffectiveManifest, CurrentUser } from '@aurevia/contracts';
import { createJsonHttpClient } from '@aurevia/http-client';
import { App } from './bootstrap';

async function start() {
  const response = await fetch('/api/me/context', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
  if (response.status === 401) { location.assign('/auth/login'); return; }
  if (!response.ok) throw new Error('Context HTTP ' + response.status);
  const context = await response.json() as EffectiveManifest & { identity: CurrentUser };
  const client = createJsonHttpClient({ basePath: '/api/proxy/test-legacy' });
  const runtime: HostRuntime = {
    mode: 'standalone', moduleKey: 'test-legacy', routePrefix: 'test-legacy',
    http: client, session: { getCurrentUser: () => context.identity, subscribe: () => () => {} },
    navigation: { navigate: path => location.assign(path), getModuleBasePath: () => '/' },
    notifications: { success: () => {}, error: () => {} },
    events: { emit: () => {}, subscribe: () => () => {} },
    sharedState: { get: () => undefined, subscribe: () => () => {} },
    theme: { locale: 'en-US', direction: 'ltr' }
  };
  const element = document.createElement('div'); document.body.append(element);
  createRoot(element).render(<App runtime={runtime} manifest={context} />);
}
void start().catch(() => { document.body.textContent = 'Unable to load authenticated context'; });
