import React, { type PropsWithChildren, type ReactNode } from 'react';
import type { EffectiveManifest, PresentationMode } from '@aurevia/contracts';
type ManifestState = {
    manifest?: EffectiveManifest;
    loading: boolean;
    replace: (next: EffectiveManifest) => void;
};
export declare function SHCoreProvider(props: PropsWithChildren): import("react/jsx-runtime").JSX.Element;
export declare function SHManifestProvider({ initial, children }: {
    initial?: EffectiveManifest;
    children: ReactNode;
}): import("react/jsx-runtime").JSX.Element;
export declare function useSHManifest(): ManifestState;
export declare function useSHPolicy(resource: string, action: string): {
    allowed: boolean;
    state: "loading";
    mode?: undefined;
} | {
    allowed: boolean;
    state: "missing";
    mode?: undefined;
} | {
    allowed: boolean;
    state: "expired";
    mode?: undefined;
} | {
    allowed: boolean;
    state: "unknown";
    mode?: undefined;
} | {
    allowed: boolean;
    state: "allowed" | "denied";
    mode: PresentationMode | undefined;
};
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
