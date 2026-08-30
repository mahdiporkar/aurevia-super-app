export type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
export declare function ProxyRouteManagement({ api }: {
    api: AdminApi;
}): import("react/jsx-runtime").JSX.Element;
