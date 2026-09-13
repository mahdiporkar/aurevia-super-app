import {describe,it,expect} from 'vitest';
import {isServiceUrl,serviceUrlRule} from './service-url-validation';
describe('approved service URL syntax',()=>{
  it.each(['http://mock-legacy:8080','http://operation-gateway:80','https://192.168.1.6:8088',
    'https://identity.example.org','http://[::1]:8080'])('accepts an internal or public origin: %s',url=>{
    expect(isServiceUrl(url,true)).toBe(true);
  });
  it.each(['https://user:password@example.org','https://example.org?token=x','https://example.org#fragment',
    'file:///etc/passwd','javascript:alert(1)','//example.org',' https://example.org','https://',0])(
    'rejects unsafe or malformed service URLs: %s',url=>expect(isServiceUrl(url,true)).toBe(false));
  it('permits a gateway base path but restricts the token connection to an origin',()=>{
    expect(isServiceUrl('https://gateway.example.org/services')).toBe(true);
    expect(isServiceUrl('https://gateway.example.org/services',true)).toBe(false);
  });
  it('leaves empty values to the required rule and rejects an invalid nonempty value',async()=>{
    await expect(serviceUrlRule(true).validator({},undefined)).resolves.toBeUndefined();
    await expect(serviceUrlRule(true).validator({},'ftp://mock-legacy')).rejects.toThrow();
  });
});
