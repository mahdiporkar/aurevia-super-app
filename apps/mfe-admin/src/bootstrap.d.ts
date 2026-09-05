import type { MicroFrontendProps, RemoteContext } from '@aurevia/contracts';
import { ADMIN_PUBLISHED_MANIFEST } from './admin-route-catalog';
export declare const contractVersion: "1.0";
export { ADMIN_PUBLISHED_MANIFEST as publishedManifest };
export declare function App({ runtime, manifest }: MicroFrontendProps): import("react/jsx-runtime").JSX.Element;
/** Compatibility export for consumers that still call mount directly. */
export declare function mount(element: HTMLElement, context: RemoteContext): () => void;
