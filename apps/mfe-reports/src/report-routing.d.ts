export type ReportLocation = {
    basePath: string;
    reportId?: string;
};
export declare function splitReportLocation(pathname: string): ReportLocation;
export declare function reportViewerPath(basePath: string, reportId: string): string;
export declare function reportCatalogPath(basePath: string): string;
