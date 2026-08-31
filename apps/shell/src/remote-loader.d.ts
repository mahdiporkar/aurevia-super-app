import type { RemoteModule } from '@aurevia/contracts';
declare global {
    interface Window {
        [key: string]: any;
    }
}
export declare function validateRemoteDescriptor(scope: string, url: string, allowed: string[], integrity?: string): URL;
export declare function loadRemote(scope: string, url: string, module: string, allowed: string[], integrity?: string): Promise<RemoteModule>;
