import React, {
  createContext, useContext, useEffect, useMemo, useState,
  type PropsWithChildren, type ReactNode,
} from 'react';
import type { EffectiveManifest, PresentationMode } from '@aurevia/contracts';

export type SHPolicyState = 'loading' | 'missing' | 'expired' | 'stale' | 'unknown' | 'denied' | 'allowed';
export type SHPolicyDecision = { allowed: boolean; state: SHPolicyState; mode?: PresentationMode };
type ManifestState = {
  manifest?: EffectiveManifest;
  loading: boolean;
  replace: (next: EffectiveManifest) => void;
  clear: () => void;
  setLoading: (loading: boolean) => void;
};

const ManifestContext = createContext<ManifestState | undefined>(undefined);

export function evaluateSHPolicy(
  manifest: EffectiveManifest | undefined,
  loading: boolean,
  resource: string,
  action: string,
  now = Date.now(),
): SHPolicyDecision {
  if (loading) return { allowed: false, state: 'loading' };
  if (!manifest) return { allowed: false, state: 'missing' };
  if (!Number.isFinite(Date.parse(manifest.expiresAt)) || Date.parse(manifest.expiresAt) <= now) {
    return { allowed: false, state: 'expired' };
  }
  if (manifest.staleAt && Date.parse(manifest.staleAt) <= now) {
    return { allowed: false, state: 'stale' };
  }
  const actions = manifest.permissions[resource];
  if (!actions) return { allowed: false, state: 'unknown' };
  const allowed = actions.includes(action);
  return {
    allowed,
    state: allowed ? 'allowed' : 'denied',
    mode: manifest.presentation?.[`${resource}:${action}`],
  };
}

export function SHCoreProvider({ manifest, children }: PropsWithChildren<{ manifest?: EffectiveManifest }>) {
  return <SHManifestProvider initial={manifest}>{children}</SHManifestProvider>;
}

export function SHManifestProvider({ initial, children }: { initial?: EffectiveManifest; children: ReactNode }) {
  const [manifest, setManifest] = useState(initial);
  const [loading, setLoading] = useState(initial === undefined);

  useEffect(() => {
    if (initial !== undefined) {
      setManifest(initial);
      setLoading(false);
    }
  }, [initial]);

  const value = useMemo<ManifestState>(() => ({
    manifest,
    loading,
    replace: next => {
      setManifest(next);
      setLoading(false);
    },
    clear: () => {
      setManifest(undefined);
      setLoading(false);
    },
    setLoading,
  }), [manifest, loading]);
  return <ManifestContext.Provider value={value}>{children}</ManifestContext.Provider>;
}

export function useSHManifest() {
  const value = useContext(ManifestContext);
  if (!value) throw new Error('SHManifestProvider is required');
  return value;
}

export function useSHPolicy(resource: string, action: string, _context?: Record<string, unknown>) {
  const { manifest, loading } = useSHManifest();
  return evaluateSHPolicy(manifest, loading, resource, action);
}

export function SHAccessDenied({ children = 'Access denied' }: { children?: ReactNode }) {
  return <div role="alert">{children}</div>;
}

export function SHCan({ resource, action, fallback = null, children }: {
  resource: string; action: string; fallback?: ReactNode; children: ReactNode;
}) {
  return useSHPolicy(resource, action).allowed ? <>{children}</> : <>{fallback}</>;
}

export function SHRouteGuard(props: { resource: string; action: string; children: ReactNode }) {
  return <SHCan {...props} fallback={<SHAccessDenied />} />;
}

export function SHAction({ resource, action, mode = 'hide', children }: {
  resource: string; action: string; mode?: PresentationMode; children: React.ReactElement;
}) {
  const policy = useSHPolicy(resource, action);
  if (policy.allowed) return children;
  const effective = policy.mode ?? mode;
  if (effective === 'hide') return null;
  const props = effective === 'disable'
    ? { disabled: true, 'aria-disabled': true }
    : { readOnly: true, 'aria-readonly': true };
  return React.cloneElement(children as React.ReactElement<Record<string, unknown>>, props);
}
