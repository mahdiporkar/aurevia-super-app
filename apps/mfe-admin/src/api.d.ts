export declare function adminApi<T>(path: string, init?: RequestInit): Promise<T>;
/** For same-origin BFF and tunnel endpoints outside the admin proxy namespace. */
export declare function sameOriginApi<T>(path: string, init?: RequestInit): Promise<T>;
