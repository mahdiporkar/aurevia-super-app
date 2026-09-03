export type Locale = 'fa-IR' | 'en-US';
export type PresentationMode = 'hide' | 'disable' | 'readOnly';
export interface PluginRoute {
    id: string;
    path: string;
    title: string;
    resource?: string;
    action?: string;
}
export interface PluginMenu {
    id: string;
    parentId?: string | null;
    routeId: string;
    title: string;
    icon?: string;
    order: number;
}
export interface RemoteDescriptor {
    remoteEntryUrl: string;
    remoteName: string;
    exposedModule: string;
    contractVersion: string;
    artifactVersion: string;
    integrity?: string;
}
export interface UiModuleDefinition {
    registrationId: string;
    moduleKey: string;
    displayName: string;
    displayNameEn: string;
    description?: string;
    icon?: string;
    order: number;
    routePrefix: string;
    defaultRouteId?: string;
    remote: RemoteDescriptor;
    runtime: {
        apiBasePath: string;
    };
    routes: PluginRoute[];
    menus: PluginMenu[];
}
export interface UiCatalog {
    catalogVersion: string;
    generatedAt: string;
    contractVersion: string;
    modules: UiModuleDefinition[];
}
export interface PanelManifest {
    id: string;
    code: string;
    slug: string;
    nameFa: string;
    nameEn: string;
    remoteEntry: string;
    exposedModule: string;
    routeBasePath: string;
    semanticVersion: string;
    contractVersion: string;
    integrity?: string;
    remoteName?: string;
    serviceSlug?: string;
    routes?: PluginRoute[];
    menus?: PluginMenu[];
}
export type ResourceType = 'APPLICATION' | 'MODULE' | 'PAGE' | 'UI_COMPONENT' | 'FIELD' | 'BUSINESS_RESOURCE' | 'EXTERNAL_RESOURCE';
export interface ManifestResource {
    id: string;
    parent_id?: string | null;
    resource_key: string;
    type: ResourceType;
    name_fa: string;
    name_en: string;
    owner_domain?: string | null;
    classification?: string | null;
    actions: readonly string[];
}
export interface EffectiveManifest {
    manifestType?: 'EFFECTIVE_USER_MANIFEST';
    subject?: {
        type: 'user';
        id: string;
    };
    version: string;
    expiresAt: string;
    staleAt?: string;
    panels: PanelManifest[];
    permissions: Record<string, readonly string[]>;
    resourceTree?: ManifestResource[];
    presentation?: Record<string, PresentationMode>;
    uiCatalog?: UiCatalog;
}
export interface ResourceDefinition {
    key: string;
    type: ResourceType;
    parent?: string | null;
    nameFa: string;
    nameEn: string;
    ownerDomain?: string;
    classification?: string;
    actions: readonly string[];
    metadata?: Record<string, unknown>;
    provider?: string;
    externalType?: string;
    externalId?: string;
}
export interface ResourceDefinitionManifest {
    application: string;
    manifestVersion: string;
    resources: readonly ResourceDefinition[];
}
export interface CurrentUser {
    subject: string;
    displayName?: string;
    groups: readonly {
        id: string;
        displayName: string;
    }[];
}
export interface RequestOptions {
    headers?: Record<string, string>;
    signal?: AbortSignal;
}
export interface HttpPort {
    get<T>(path: string, options?: RequestOptions): Promise<T>;
    post<TResponse, TBody>(path: string, body: TBody, options?: RequestOptions): Promise<TResponse>;
    put<TResponse, TBody>(path: string, body: TBody, options?: RequestOptions): Promise<TResponse>;
}
export interface NavigationPort {
    navigate(relativePath: string): void;
    getModuleBasePath(): string;
}
export interface SessionPort {
    getCurrentUser(): CurrentUser | null;
    subscribe(listener: (user: CurrentUser | null) => void): () => void;
}
export interface NotificationPort {
    success(message: string): void;
    error(message: string): void;
}
export interface EventPort {
    emit<T>(eventName: string, payload: T): void;
    subscribe<T>(eventName: string, listener: (payload: T) => void): () => void;
}
export interface SharedStatePort {
    get<T>(key: string): T | undefined;
    subscribe<T>(key: string, listener: (value: T | undefined) => void): () => void;
}
export interface ThemePort {
    locale: Locale;
    direction: 'rtl' | 'ltr';
}
export interface HostRuntime {
    mode: 'standalone' | 'embedded';
    moduleKey: string;
    routePrefix: string;
    http: HttpPort;
    navigation: NavigationPort;
    session: SessionPort;
    notifications: NotificationPort;
    events: EventPort;
    sharedState: SharedStatePort;
    theme: ThemePort;
}
export interface MicroFrontendProps {
    runtime: HostRuntime;
    manifest: EffectiveManifest;
}
export interface MicroFrontendPlugin {
    contractVersion: '1.0';
    App: import('react').ComponentType<MicroFrontendProps>;
}
/** Legacy contract retained while existing remotes migrate to the component plugin. */
export interface LegacyRemoteModule {
    contractVersion: '1';
    mount(element: HTMLElement, context: RemoteContext): () => void;
}
export type RemoteModule = LegacyRemoteModule;
export type LoadedRemoteModule = MicroFrontendPlugin | LegacyRemoteModule;
export interface RemoteContext {
    locale: Locale;
    manifest: EffectiveManifest;
    correlationId: () => string;
}
