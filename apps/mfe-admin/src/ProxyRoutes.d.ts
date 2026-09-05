export type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
export declare function ProxyRouteManagement({ api, section }: {
    api: AdminApi;
    section: 'targets' | 'routes' | 'operations';
}): import("react/jsx-runtime").JSX.Element;
