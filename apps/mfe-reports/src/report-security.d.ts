export type ReportAsset = {
    id: string;
    external_id: string;
    asset_type: 'DASHBOARD' | 'CHART';
    title: string;
    url_path: string;
    owner_external_id?: string;
    tags_json?: string;
};
export declare function parseReportTags(value?: string): string[];
export declare function safeReportPath(value: string): string | undefined;
