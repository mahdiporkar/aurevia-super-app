export interface RequestOptions {
    headers?: HeadersInit;
    signal?: AbortSignal;
}
export interface JsonHttpClient {
    request<T>(path: string, init?: RequestInit): Promise<T>;
    get<T>(path: string, options?: RequestOptions): Promise<T>;
    post<TResponse, TBody>(path: string, body: TBody, options?: RequestOptions): Promise<TResponse>;
    put<TResponse, TBody>(path: string, body: TBody, options?: RequestOptions): Promise<TResponse>;
    delete<T>(path: string, options?: RequestOptions): Promise<T>;
    clearCsrfToken(): void;
}
export interface JsonHttpClientOptions {
    basePath: string;
    csrfPath?: string;
    credentials?: RequestCredentials;
    fetchImplementation?: typeof fetch;
    correlationId?: () => string;
}
export declare class ApiError extends Error {
    readonly status: number;
    readonly correlationId: string;
    readonly responseBody?: unknown | undefined;
    constructor(message: string, status: number, correlationId: string, responseBody?: unknown | undefined);
}
export declare function createJsonHttpClient(options: JsonHttpClientOptions): JsonHttpClient;
