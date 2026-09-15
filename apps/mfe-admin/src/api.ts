import { createJsonHttpClient } from '@aurevia/http-client';

const adminClient = createJsonHttpClient({ basePath: '/api/v1/admin' });
const sameOriginClient = createJsonHttpClient({ basePath: '' });

export function adminApi<T>(path: string, init: RequestInit = {}): Promise<T> {
  return adminClient.request<T>(path, init);
}

/** For same-origin BFF and tunnel endpoints outside the admin proxy namespace. */
export function sameOriginApi<T>(path: string, init: RequestInit = {}): Promise<T> {
  return sameOriginClient.request<T>(path, init);
}
