export type AdminApi = <T>(path: string, init?: RequestInit) => Promise<T>;
type Section = 'targets' | 'routes' | 'operations';
export declare function ProxyRouteManagement({ api, section }: {
    api: AdminApi;
    section: Section;
}): import("react/jsx-runtime").JSX.Element;
export {};
