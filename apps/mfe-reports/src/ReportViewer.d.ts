import { type ReportAsset } from './report-security';
type ReportViewerProps = {
    report: ReportAsset;
    onBack: () => void;
};
export declare function ReportViewer({ report, onBack }: ReportViewerProps): import("react/jsx-runtime").JSX.Element;
export {};
