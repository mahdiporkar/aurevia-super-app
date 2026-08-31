import { describe, expect, it } from 'vitest';
import { validateRemoteDescriptor } from './remote-loader';

describe('validateRemoteDescriptor', () => {
  const url = 'https://static.example.test/hr/remoteEntry.js';

  it('accepts only an exact allowlisted HTTP(S) URL', () => {
    expect(validateRemoteDescriptor('aurevia_hr', url, [url]).href).toBe(url);
    expect(() => validateRemoteDescriptor('aurevia_hr', 'javascript:alert(1)', ['javascript:alert(1)']))
      .toThrow('allowlisted');
    expect(() => validateRemoteDescriptor('aurevia_hr', url, ['https://static.example.test/finance/remoteEntry.js']))
      .toThrow('allowlisted');
    expect(() => validateRemoteDescriptor('bad\"]scope', url, [url])).toThrow('scope');
  });

  it('validates supported SRI digests when integrity is configured', () => {
    expect(() => validateRemoteDescriptor('aurevia_hr', url, [url], 'sha384-YWJjZA==')).not.toThrow();
    expect(() => validateRemoteDescriptor('aurevia_hr', url, [url], 'md5-YWJjZA==')).toThrow('integrity');
    expect(() => validateRemoteDescriptor('aurevia_hr', url, [url], 'sha384-not a digest')).toThrow('integrity');
  });
});
