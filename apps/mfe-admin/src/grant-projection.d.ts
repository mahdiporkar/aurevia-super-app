export type GrantProjection = {
    projection_status?: string;
    projection_error?: string | null;
    expires_at?: string | null;
};
export declare function grantProjection(grant: GrantProjection, now?: number): {
    error: string | null | undefined;
    pending: boolean;
    label: string;
    color: string;
    status: string;
};
