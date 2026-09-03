import type { HostRuntime, MicroFrontendProps, RemoteModule } from "@aurevia/contracts";
export declare const contractVersion: "1";
export declare function App({ runtime, manifest }: {
    runtime: HostRuntime;
    manifest: MicroFrontendProps['manifest'];
}): import("react/jsx-runtime").JSX.Element;
export declare const plugin: {
    contractVersion: "1.0";
    App: typeof App;
};
export declare const mount: RemoteModule["mount"];
