export type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
export declare function SupersetInstances({ api }: {
    api: AdminApi;
}): import("react/jsx-runtime").JSX.Element;
