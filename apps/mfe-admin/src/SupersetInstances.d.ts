export type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
export declare function SupersetInstances({ api, healthApi }: {
    api: AdminApi;
    healthApi?: AdminApi;
}): import("react/jsx-runtime").JSX.Element;
