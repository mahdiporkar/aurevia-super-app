import { type PropsWithChildren, type ReactNode } from 'react';
import type { EffectiveUserContext } from '@aurevia/contracts';
export type AuthorizationDecision = 'allowed' | 'denied' | 'missing' | 'expired';
/** Fail-closed, in-memory evaluator. It has no knowledge of IAM or authorization-service URLs. */
export declare class AuthorizationClient {
    private context?;
    setContext(context: EffectiveUserContext): void;
    clear(): void;
    getContext(): EffectiveUserContext | undefined;
    decision(resource: string, action: string, now?: number): AuthorizationDecision;
    can(resource: string, action: string): boolean;
}
export declare const authorization: AuthorizationClient;
export declare function AuthorizationProvider({ context, client, children }: PropsWithChildren<{
    context: EffectiveUserContext;
    client?: AuthorizationClient;
}>): import("react/jsx-runtime").JSX.Element;
export declare function useAuthorization(): AuthorizationClient;
export declare function Can({ resource, action, fallback, children }: {
    resource: string;
    action: string;
    fallback?: ReactNode;
    children: ReactNode;
}): import("react/jsx-runtime").JSX.Element;
export declare function evaluateDynamicPolicy(resource: string, action: string, attributes: Record<string, unknown>, signal?: AbortSignal): Promise<boolean>;
