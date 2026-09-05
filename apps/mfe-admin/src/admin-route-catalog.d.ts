import type { PluginMenu, PluginRoute } from '@aurevia/contracts';
export type AdminSectionKey = 'operator-guide' | 'ou-access' | 'access-studio' | 'panels' | 'proxy-routes' | 'outbound-connections' | 'outbound-auth' | 'integration-test' | 'superset-instances' | 'identity' | 'logs' | 'superset';
export interface AdminPageDefinition extends PluginRoute {
    section: AdminSectionKey;
    sectionTitle: string;
    icon: string;
    order: number;
}
export declare const ADMIN_PAGE_ROUTES: readonly AdminPageDefinition[];
export declare const ADMIN_MENUS: readonly PluginMenu[];
/** Metadata published to the existing UI artifact registry; routePrefix is intentionally absent. */
export declare const ADMIN_PUBLISHED_MANIFEST: {
    readonly schemaVersion: "1.0";
    readonly moduleKey: "admin";
    readonly defaultRouteId: "operator-guide";
    readonly runtime: {
        readonly apiBasePath: "/api/v1/admin";
    };
    readonly routes: {
        id: string;
        path: string;
        title: string;
        resource: string | undefined;
        action: string | undefined;
    }[];
    readonly menus: readonly PluginMenu[];
};
export declare function authorizedAdminPages(routeIds: readonly string[] | undefined, legacyPermissions?: Record<string, readonly string[]>): AdminPageDefinition[];
export declare function defaultAdminPage(pages: readonly AdminPageDefinition[], preferredId?: string): AdminPageDefinition | undefined;
export declare function internalPathname(pathname: string, moduleBasePath: string): string;
