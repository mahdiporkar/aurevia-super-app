import React, { useState } from 'react';
import type { MicroFrontendProps } from '@aurevia/contracts';
import { SHManifestProvider, SHRouteGuard } from '@aurevia/sh-core-ui';

type Diagnostic = { service: string; authenticated: boolean; authMode: string; tokenType: string; subject?: string; username?: string; correlationId: string };
export const contractVersion = '1.0' as const;
export function App({ runtime, manifest }: MicroFrontendProps) {
  const [result, setResult] = useState<Diagnostic>();
  const [status, setStatus] = useState('READY');
  const [httpStatus, setHttpStatus] = useState('');
  const call = async () => {
    setStatus('PENDING'); setResult(undefined); setHttpStatus('');
    try {
      const value = await runtime.http.get<Diagnostic>('/api/test/whoami', { headers: { 'X-Correlation-ID': crypto.randomUUID() } });
      if (value.service !== 'test-legacy-service' || !value.authenticated || value.authMode !== 'LEGACY') throw new Error('Unexpected downstream response');
      setResult(value); setStatus('SUCCESS'); setHttpStatus('200');
    } catch (error) {
      setStatus('FAILED');
      setHttpStatus(error instanceof Error ? error.message : 'Request failed');
    }
  };
  return <SHManifestProvider initial={manifest}><SHRouteGuard resource="page:test-legacy.home" action="view">
    <main style={{ padding: 24 }} data-testid="test-legacy">
      <h1>Legacy Test Microfrontend</h1>
      <p>Authenticated Super App User: <strong data-testid="username">{runtime.session.getCurrentUser()?.username ?? '—'}</strong></p>
      <p>Downstream Service: test-legacy-service</p>
      <p>Authentication Mode: LEGACY</p>
      <p>Request Status: <strong data-testid="request-status" role="status">{status}</strong> {httpStatus}</p>
      <button onClick={() => void call()} disabled={status === 'PENDING'}>Call Legacy Service</button>
      {result && <dl>
        <dt>Service</dt><dd>{result.service}</dd>
        <dt>Token type</dt><dd>{result.tokenType}</dd>
        <dt>Subject</dt><dd>{result.subject ?? 'Service credential'}</dd>
        <dt>Username</dt><dd>{result.username ?? runtime.session.getCurrentUser()?.username}</dd>
        <dt>Correlation ID</dt><dd data-testid="correlation-id">{result.correlationId}</dd>
      </dl>}
    </main>
  </SHRouteGuard></SHManifestProvider>;
}
export const plugin = { contractVersion, App };
