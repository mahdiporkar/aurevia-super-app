import { describe, expect, it, vi } from 'vitest';
import { ApiError, createJsonHttpClient } from './index';

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: { 'Content-Type': 'application/json' },
});

describe('createJsonHttpClient', () => {
  it('adds correlation, credentials and a cached CSRF token to mutations', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(json({ headerName: 'X-CSRF-TOKEN', token: 'safe-token' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const client = createJsonHttpClient({
      basePath: '/api/v1/admin', fetchImplementation: fetcher, correlationId: () => 'cid-1',
    });

    await client.post<void, { name: string }>('/roles', { name: 'operator' });
    await client.delete<void>('/roles/1');

    expect(fetcher).toHaveBeenCalledTimes(3);
    expect(fetcher.mock.calls[1]?.[0]).toBe('/api/v1/admin/roles');
    const mutation = fetcher.mock.calls[1]?.[1] as RequestInit;
    const headers = new Headers(mutation.headers);
    expect(mutation.credentials).toBe('same-origin');
    expect(headers.get('X-CSRF-TOKEN')).toBe('safe-token');
    expect(headers.get('X-Correlation-ID')).toBe('cid-1');
  });

  it('returns undefined for empty successful responses', async () => {
    const client = createJsonHttpClient({
      basePath: '/api', fetchImplementation: vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    });
    await expect(client.get<void>('/empty')).resolves.toBeUndefined();
  });

  it('normalizes problem details and preserves status and correlation id', async () => {
    const client = createJsonHttpClient({
      basePath: '/api', correlationId: () => 'cid-error',
      fetchImplementation: vi.fn().mockResolvedValue(json({ detail: 'ورودی نامعتبر است' }, 422)),
    });
    await expect(client.get('/broken')).rejects.toEqual(expect.objectContaining({
      name: 'ApiError', status: 422, correlationId: 'cid-error', message: 'ورودی نامعتبر است',
    } satisfies Partial<ApiError>));
  });

  it('does not send a content type header on bodyless reads', async () => {
    const fetcher = vi.fn().mockResolvedValue(json({ ok: true }));
    const client = createJsonHttpClient({ basePath: '/api/', fetchImplementation: fetcher });
    await client.get('/health');
    const headers = new Headers((fetcher.mock.calls[0]?.[1] as RequestInit).headers);
    expect(headers.has('Content-Type')).toBe(false);
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/health');
  });
});
