import { describe, expect, it } from 'vitest';
import { describeError } from './ProxyRoutes';

describe('proxy route validation messages', () => {
  it('translates a bare Authorization Service code', () => {
    expect(describeError(new Error('STRIP_PREFIX_EXCEEDS_PATH_PREFIX')))
      .toContain('Segment');
    expect(describeError(new Error('STRIP_PREFIX_EXCEEDS_PATH_PREFIX')))
      .toContain('(STRIP_PREFIX_EXCEEDS_PATH_PREFIX)');
  });
  it('translates a runtime message that starts with a code', () => {
    expect(describeError(new Error('REWRITE_PREFIX_NOT_FOUND_AFTER_STRIP: /x')))
      .toContain('Rewrite Pattern');
  });
  it('leaves unknown messages untouched', () => {
    expect(describeError(new Error('HTTP 502'))).toBe('HTTP 502');
  });
});
