import type { UiModuleDefinition } from '@aurevia/contracts';
export interface CatalogMenuItem {
    key: string;
    title: string;
    icon?: string;
    module: UiModuleDefinition;
}
export declare function moduleBasePath(module: Pick<UiModuleDefinition, 'routePrefix'>): string;
export declare function composeModulePath(module: Pick<UiModuleDefinition, 'routePrefix'>, relativePath: string): string;
export declare function catalogMenuItems(modules: readonly UiModuleDefinition[]): CatalogMenuItem[];
export declare function activeCatalogModule(modules: readonly UiModuleDefinition[], pathname: string): UiModuleDefinition | undefined;
