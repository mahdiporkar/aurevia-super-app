import { describe, expect, it } from 'vitest';
import { validateRemoteDescriptor } from './remote-loader';

describe('validateRemoteDescriptor', () => {
  const url = 'https://static.example.test/hr/remoteEntry.js';

  it('accepts any HTTP(S) remote URL without a catalog allowlist', () => {
    expect(validateRemoteDescriptor('aurevia_hr', url).href).toBe(url);
    expect(validateRemoteDescriptor('aurevia_hr', 'https://external.example.test/remoteEntry.js').href)
      .toBe('https://external.example.test/remoteEntry.js');
    expect(() => validateRemoteDescriptor('aurevia_hr', 'javascript:alert(1)'))
      .toThrow('HTTP(S)');
    expect(() => validateRemoteDescriptor('bad\"]scope', url)).toThrow('scope');
  });

  it('validates supported SRI digests when integrity is configured', () => {
    expect(() => validateRemoteDescriptor('aurevia_hr', url, 'sha384-YWJjZA==')).not.toThrow();
    expect(() => validateRemoteDescriptor('aurevia_hr', url, 'md5-YWJjZA==')).toThrow('integrity');
    expect(() => validateRemoteDescriptor('aurevia_hr', url, 'sha384-not a digest')).toThrow('integrity');
  });

  it('accepts same-origin proxy paths', () => {
    const proxy = '/api/mfe/hr/remoteEntry.js';
    expect(validateRemoteDescriptor('aurevia_hr', proxy, undefined,
      'http://localhost:8443').href).toBe('http://localhost:8443/api/mfe/hr/remoteEntry.js');
    expect(() => validateRemoteDescriptor('aurevia_hr', '//evil.test/remoteEntry.js',
      undefined, 'http://localhost:8443')).toThrow('same-origin');
    expect(() => validateRemoteDescriptor('aurevia_hr', 'api/mfe/hr/remoteEntry.js',
      undefined, 'http://localhost:8443')).toThrow('same-origin');
  });
});
