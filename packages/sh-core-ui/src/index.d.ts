import React, { type PropsWithChildren, type ReactNode } from 'react';
import type { EffectiveManifest, PresentationMode } from '@aurevia/contracts';
export type SHPolicyState = 'loading' | 'missing' | 'expired' | 'stale' | 'unknown' | 'denied' | 'allowed';
export type SHPolicyDecision = {
    allowed: boolean;
    state: SHPolicyState;
    mode?: PresentationMode;
};
type ManifestState = {
    manifest?: EffectiveManifest;
    loading: boolean;
    replace: (next: EffectiveManifest) => void;
    clear: () => void;
    setLoading: (loading: boolean) => void;
};
export declare function evaluateSHPolicy(manifest: EffectiveManifest | undefined, loading: boolean, resource: string, action: string, now?: number): SHPolicyDecision;
export declare function SHCoreProvider({ manifest, children }: PropsWithChildren<{
    manifest?: EffectiveManifest;
}>): import("react/jsx-runtime").JSX.Element;
export declare function SHManifestProvider({ initial, children }: {
    initial?: EffectiveManifest;
    children: ReactNode;
}): import("react/jsx-runtime").JSX.Element;
export declare function useSHManifest(): ManifestState;
export declare function useSHPolicy(resource: string, action: string, _context?: Record<string, unknown>): SHPolicyDecision;
export declare function SHAccessDenied({ children }: {
    children?: ReactNode;
}): import("react/jsx-runtime").JSX.Element;
export declare function SHCan({ resource, action, fallback, children }: {
    resource: string;
    action: string;
    fallback?: ReactNode;
    children: ReactNode;
}): import("react/jsx-runtime").JSX.Element;
export declare function SHRouteGuard(props: {
    resource: string;
    action: string;
    children: ReactNode;
}): import("react/jsx-runtime").JSX.Element;
export declare function SHAction({ resource, action, mode, children }: {
    resource: string;
    action: string;
    mode?: PresentationMode;
    children: React.ReactElement;
}): React.ReactElement<unknown, string | React.JSXElementConstructor<any>> | null;
export {};
