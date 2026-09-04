type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
/** Manages approved non-secret token endpoints. Credentials stay in the secret store. */
export declare function OutboundConnections({ api }: {
    api: AdminApi;
}): import("react/jsx-runtime").JSX.Element;
export {};
