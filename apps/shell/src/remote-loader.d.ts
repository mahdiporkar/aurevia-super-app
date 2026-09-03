import type { LoadedRemoteModule } from '@aurevia/contracts';
declare global {
    interface Window {
        [key: string]: unknown;
    }
}
export declare function validateRemoteDescriptor(scope: string, url: string, allowed: string[], integrity?: string): URL;
export declare function clearRemoteCache(scope?: string): void;
export declare function loadRemote(scope: string, url: string, module: string, allowed: string[], integrity?: string, timeoutMs?: number): Promise<LoadedRemoteModule>;
