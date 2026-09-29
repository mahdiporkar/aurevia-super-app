import { type ReportAsset } from './report-security';
type ReportsCatalogProps = {
    reports: ReportAsset[];
    loading: boolean;
    error?: string;
    onRetry: () => void;
    onOpen: (report: ReportAsset) => void;
};
export declare function ReportsCatalog({ reports, loading, error, onRetry, onOpen }: ReportsCatalogProps): import("react/jsx-runtime").JSX.Element;
export {};
