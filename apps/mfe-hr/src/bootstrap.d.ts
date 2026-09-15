import type { HostRuntime, MicroFrontendProps, RemoteModule } from "@aurevia/contracts";
/** Shell-facing component contract; `mount` below is retained only for legacy hosts. */
export declare const contractVersion: "1.0";
export declare function App({ runtime, manifest }: {
    runtime: HostRuntime;
    manifest: MicroFrontendProps['manifest'];
}): import("react/jsx-runtime").JSX.Element;
export declare const plugin: {
    contractVersion: "1.0";
    App: typeof App;
};
export declare const mount: RemoteModule["mount"];
