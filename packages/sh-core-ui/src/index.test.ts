import { describe, expect, it } from 'vitest';
import type { EffectiveManifest } from '@aurevia/contracts';
import { evaluateSHPolicy } from './index';

const future = new Date(Date.now() + 60_000).toISOString();
const manifest: EffectiveManifest = {
  version: 'test', expiresAt: future, panels: [], permissions: { 'hr.employee': ['view'] },
};

describe('evaluateSHPolicy', () => {
  it('allows only an explicitly present resource action', () => {
    expect(evaluateSHPolicy(manifest, false, 'hr.employee', 'view').state).toBe('allowed');
    expect(evaluateSHPolicy(manifest, false, 'hr.employee', 'update').state).toBe('denied');
    expect(evaluateSHPolicy(manifest, false, 'unknown', 'view').state).toBe('unknown');
  });
  it('fails closed while loading or when missing, stale, or expired', () => {
    expect(evaluateSHPolicy(manifest, true, 'hr.employee', 'view').state).toBe('loading');
    expect(evaluateSHPolicy(undefined, false, 'hr.employee', 'view').state).toBe('missing');
    expect(evaluateSHPolicy({ ...manifest, staleAt: new Date(0).toISOString() }, false, 'hr.employee', 'view').state).toBe('stale');
    expect(evaluateSHPolicy({ ...manifest, expiresAt: new Date(0).toISOString() }, false, 'hr.employee', 'view').state).toBe('expired');
  });
  it('returns the server-selected presentation mode for denied actions', () => {
    const decision = evaluateSHPolicy({ ...manifest, presentation: { 'hr.employee:update': 'disable' } }, false, 'hr.employee', 'update');
    expect(decision).toMatchObject({ allowed: false, state: 'denied', mode: 'disable' });
  });
});
