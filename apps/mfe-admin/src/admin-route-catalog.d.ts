import type { MicroFrontendManifest, PluginMenu, PluginRoute } from '@aurevia/contracts';
/** A sidebar entry: one administrative domain, not one operation. */
export type AdminDomainKey = 'operator-guide' | 'access' | 'users' | 'panels' | 'routes' | 'reports' | 'logs';
export interface AdminDomainDefinition {
    key: AdminDomainKey;
    /** Route path of the domain landing page; equals the single page for standalone domains. */
    path: string;
    title: string;
    description: string;
    icon: string;
    order: number;
    /**
     * The permission the Shell side menu entry is filtered by. A domain groups pages
     * that may require different resources, and the effective catalog filters one
     * route at a time, so this is the domain's entry permission. Landing always
     * redirects to the first page the subject is actually authorized for.
     */
    resource: string;
    action: string;
    /** A single-page domain needs no tab strip and no separate landing route. */
    standalone?: true;
}
export declare const ADMIN_DOMAINS: readonly AdminDomainDefinition[];
export interface AdminPageDefinition extends PluginRoute {
    domain: AdminDomainKey;
    /** Label of this page's tab inside its domain. */
    tabTitle: string;
    description: string;
    icon: string;
    order: number;
}
/**
 * Tab order follows the administrator's real configuration workflow, not the
 * alphabet: a service target exists before a route points at it, a route exists
 * before its operations are declared, and an outbound connection exists before an
 * authentication profile uses it or the test lab exercises it.
 */
export declare const ADMIN_PAGE_ROUTES: readonly AdminPageDefinition[];
/**
 * Routes that exist only so a domain has one addressable landing URL the Shell
 * side menu can point at. Standalone domains reuse their single page instead.
 */
export declare const ADMIN_DOMAIN_ROUTES: readonly PluginRoute[];
/** One menu entry per administrative domain; individual operations become tabs. */
export declare const ADMIN_MENUS: readonly PluginMenu[];
/**
 * Admin URLs published before the topic-based restructure. They are still served
 * and redirect to the page's new location inside its domain, so bookmarks and
 * runbook links keep working instead of returning "route not found".
 */
export declare const ADMIN_LEGACY_ROUTE_REDIRECTS: ReadonlyArray<{
    from: string;
    to: string;
}>;
/**
 * The Shell mounts this micro frontend only on a route the effective catalog
 * published, so a legacy URL has to stay a published route for its in-app redirect
 * to ever run. Each one declares exactly the resource and action of the page it
 * redirects to, so a legacy URL can never reach a page its subject could not open
 * at its new location either.
 */
export declare const ADMIN_LEGACY_ROUTES: readonly PluginRoute[];
/** Authorization references only; resource definitions live in resource-manifest.json. */
export declare const ADMIN_PUBLISHED_MANIFEST: MicroFrontendManifest;
export declare function authorizedAdminPages(routeIds: readonly string[] | undefined, legacyPermissions?: Record<string, readonly string[]>): AdminPageDefinition[];
export declare function defaultAdminPage(pages: readonly AdminPageDefinition[], preferredId?: string): AdminPageDefinition | undefined;
export interface AuthorizedAdminDomain {
    domain: AdminDomainDefinition;
    pages: AdminPageDefinition[];
}
/**
 * The domains this subject can actually reach, each carrying only the pages the
 * effective catalog authorized. A domain with no authorized page is not returned,
 * so tab consolidation never exposes an operation the subject cannot perform.
 */
export declare function authorizedAdminDomains(pages: readonly AdminPageDefinition[]): AuthorizedAdminDomain[];
export declare function adminDomainOf(page: AdminPageDefinition): AdminDomainDefinition;
/** The tab segment of a page path, used as the URL-addressable tab key. */
export declare function adminTabKey(page: AdminPageDefinition): string;
export declare function internalPathname(pathname: string, moduleBasePath: string): string;
