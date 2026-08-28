import type { RemoteModule } from '@aurevia/contracts';
declare global {
    interface Window {
        [key: string]: any;
    }
}
export declare function loadRemote(scope: string, url: string, module: string, allowed: string[]): Promise<RemoteModule>;
