type AdminApi = (path: string, init?: RequestInit) => Promise<any>;
/** Runs both credential paths through browser -> BFF -> Gateway -> protected fixture. */
export declare function IntegrationTestLab({ api }: {
    api: AdminApi;
}): import("react/jsx-runtime").JSX.Element;
export {};
