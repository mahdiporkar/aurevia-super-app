import type { MicroFrontendManifest, PluginMenu, PluginRoute } from '@aurevia/contracts';
export type AdminSectionKey = 'operator-guide' | 'ou-access' | 'access-studio' | 'panels' | 'proxy-routes' | 'outbound-connections' | 'outbound-auth' | 'integration-test' | 'superset-instances' | 'identity' | 'logs' | 'superset';
export interface AdminPageDefinition extends PluginRoute {
    section: AdminSectionKey;
    sectionTitle: string;
    description: string;
    icon: string;
    order: number;
}
export declare const ADMIN_PAGE_ROUTES: readonly AdminPageDefinition[];
export declare const ADMIN_MENUS: readonly PluginMenu[];
/** Authorization references only; resource definitions live in resource-manifest.json. */
export declare const ADMIN_PUBLISHED_MANIFEST: MicroFrontendManifest;
export declare function authorizedAdminPages(routeIds: readonly string[] | undefined, legacyPermissions?: Record<string, readonly string[]>): AdminPageDefinition[];
export declare function defaultAdminPage(pages: readonly AdminPageDefinition[], preferredId?: string): AdminPageDefinition | undefined;
export declare function internalPathname(pathname: string, moduleBasePath: string): string;
