import type { UiModuleDefinition } from '@aurevia/contracts';
export interface CatalogMenuItem {
    key: string;
    navigationKey: string;
    parentNavigationKey?: string;
    type: 'GROUP' | 'PAGE' | 'EXTERNAL_LINK';
    title: string;
    description?: string;
    icon?: string;
    order: number;
    module: UiModuleDefinition;
    children?: CatalogMenuItem[];
}
export declare function moduleBasePath(module: Pick<UiModuleDefinition, 'routePrefix'>): string;
export declare function composeModulePath(module: Pick<UiModuleDefinition, 'routePrefix'>, relativePath: string): string;
export declare function catalogMenuItems(modules: readonly UiModuleDefinition[]): CatalogMenuItem[];
export declare function catalogMenuTree(items: readonly CatalogMenuItem[]): CatalogMenuItem[];
export declare function activeCatalogModule(modules: readonly UiModuleDefinition[], pathname: string): UiModuleDefinition | undefined;
export declare function localModulePath(module: Pick<UiModuleDefinition, 'routePrefix'>, pathname: string): string;
export declare function matchesLocalRoute(pattern: string, pathname: string): boolean;
/** A module is mounted only when its backend-filtered effective route matches the URL. */
export declare function authorizedCatalogRoute(module: UiModuleDefinition, pathname: string): import("@aurevia/contracts").PluginRoute | undefined;
export declare function activeCatalogMenuKey(items: readonly Pick<CatalogMenuItem, 'key'>[], pathname: string): string | undefined;
