export type Locale = 'fa-IR' | 'en-US';
export type PresentationMode = 'hide' | 'disable' | 'readOnly';
export interface PanelManifest { id:string; code:string; slug:string; nameFa:string; nameEn:string; remoteEntry:string; exposedModule:string; routeBasePath:string; semanticVersion:string; contractVersion:string; integrity?:string; }
export type ResourceType='APPLICATION'|'MODULE'|'PAGE'|'UI_COMPONENT'|'FIELD'|'BUSINESS_RESOURCE'|'EXTERNAL_RESOURCE';
export interface ManifestResource { id:string; parent_id?:string|null; resource_key:string; type:ResourceType; name_fa:string; name_en:string; owner_domain?:string|null; classification?:string|null; actions:readonly string[]; }
export interface EffectiveManifest { manifestType?:'EFFECTIVE_USER_MANIFEST'; subject?:{type:'user';id:string}; version:string; expiresAt:string; staleAt?:string; panels:PanelManifest[]; permissions:Record<string,readonly string[]>; resourceTree?:ManifestResource[]; presentation?:Record<string,PresentationMode>; }
export interface ResourceDefinition { key:string; type:ResourceType; parent?:string|null; nameFa:string; nameEn:string; ownerDomain?:string; classification?:string; actions:readonly string[]; metadata?:Record<string,unknown>; provider?:string; externalType?:string; externalId?:string; }
export interface ResourceDefinitionManifest { application:string; manifestVersion:string; resources:readonly ResourceDefinition[]; }
export interface CurrentUser { subject:string; displayName?:string; groups:readonly {id:string;displayName:string}[]; }
export interface RemoteModule { contractVersion:'1'; mount(element:HTMLElement, context:RemoteContext):()=>void; }
export interface RemoteContext { locale:Locale; manifest:EffectiveManifest; correlationId:()=>string; }
