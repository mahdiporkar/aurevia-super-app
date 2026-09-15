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

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly correlationId: string,
    readonly responseBody?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type CsrfToken = { headerName: string; token: string };

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);
const MAX_ERROR_LENGTH = 2_000;

function joinPath(basePath: string, path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  const base = basePath.replace(/\/+$/, '');
  const suffix = path.replace(/^\/+/, '');
  return suffix ? `${base}/${suffix}` || `/${suffix}` : base || '/';
}

function defaultCorrelationId(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

function errorMessage(status: number, body: unknown): string {
  let detail = '';
  if (typeof body === 'string') detail = body;
  else if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;
    const candidate = record.detail ?? record.message ?? record.title ?? record.error;
    if (typeof candidate === 'string') detail = candidate;
  }
  const normalized = detail.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  return (normalized || `HTTP ${status}`).slice(0, MAX_ERROR_LENGTH);
}

async function decodeBody(response: Response, correlationId: string): Promise<unknown> {
  if (response.status === 204 || response.status === 205) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  const contentType = response.headers.get('content-type')?.toLowerCase() ?? '';
  if (contentType.includes('json') || /^[\s]*[{[]/.test(text)) {
    try { return JSON.parse(text) as unknown; }
    catch {
      if (response.ok) {
        throw new ApiError('پاسخ JSON سرویس معتبر نیست', 502, correlationId,
          text.slice(0, MAX_ERROR_LENGTH));
      }
    }
  }
  return text;
}

export function createJsonHttpClient(options: JsonHttpClientOptions): JsonHttpClient {
  const fetcher = options.fetchImplementation ?? globalThis.fetch.bind(globalThis);
  const credentials = options.credentials ?? 'same-origin';
  const csrfPath = options.csrfPath ?? '/api/v1/csrf';
  const nextCorrelationId = options.correlationId ?? defaultCorrelationId;
  let csrfToken: Promise<CsrfToken> | undefined;

  const csrf = () => {
    csrfToken ??= (async () => {
      const correlationId = nextCorrelationId();
      const response = await fetcher(csrfPath, {
        credentials,
        headers: { Accept: 'application/json', 'X-Correlation-ID': correlationId },
      });
      const body = await decodeBody(response, correlationId);
      if (!response.ok) {
        throw new ApiError(errorMessage(response.status, body), response.status, correlationId, body);
      }
      if (!body || typeof body !== 'object'
          || typeof (body as Partial<CsrfToken>).headerName !== 'string'
          || typeof (body as Partial<CsrfToken>).token !== 'string') {
        throw new ApiError('پاسخ CSRF سرویس معتبر نیست', 502, correlationId, body);
      }
      return body as CsrfToken;
    })().catch((reason) => {
      csrfToken = undefined;
      throw reason;
    });
    return csrfToken;
  };

  const request = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);
    const correlationId = headers.get('X-Correlation-ID') || nextCorrelationId();
    headers.set('Accept', headers.get('Accept') || 'application/json');
    headers.set('X-Correlation-ID', correlationId);
    if (init.body != null && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    if (!SAFE_METHODS.has(method)) {
      const token = await csrf();
      headers.set(token.headerName, token.token);
    }
    const response = await fetcher(joinPath(options.basePath, path), {
      ...init,
      method,
      credentials,
      headers,
    });
    const body = await decodeBody(response, correlationId);
    if (!response.ok) {
      if (response.status === 403 && !SAFE_METHODS.has(method)) csrfToken = undefined;
      throw new ApiError(errorMessage(response.status, body), response.status, correlationId, body);
    }
    return body as T;
  };

  return {
    request,
    get: (path, requestOptions) => request(path, requestOptions),
    post: (path, body, requestOptions) => request(path, {
      method: 'POST', body: JSON.stringify(body), ...requestOptions,
    }),
    put: (path, body, requestOptions) => request(path, {
      method: 'PUT', body: JSON.stringify(body), ...requestOptions,
    }),
    delete: (path, requestOptions) => request(path, { method: 'DELETE', ...requestOptions }),
    clearCsrfToken: () => { csrfToken = undefined; },
  };
}
