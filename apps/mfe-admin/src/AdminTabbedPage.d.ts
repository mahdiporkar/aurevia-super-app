import React from 'react';
export interface AdminTab {
    key: string;
    label: string;
    children: React.ReactNode;
}
export interface AdminTabbedPageProps {
    title: string;
    description: string;
    /** Already filtered to what this subject may see; the component adds no policy. */
    tabs: readonly AdminTab[];
    activeKey: string;
    onChange: (key: string) => void;
}
/**
 * One header and one tab strip for an administrative domain. Deliberately free of
 * domain logic: the caller decides which tabs exist, which is active and what a
 * tab change means for the URL.
 */
export declare function AdminTabbedPage({ title, description, tabs, activeKey, onChange }: AdminTabbedPageProps): import("react/jsx-runtime").JSX.Element;
