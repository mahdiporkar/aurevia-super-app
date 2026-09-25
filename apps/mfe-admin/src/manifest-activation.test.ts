import { describe, expect, it } from 'vitest';
import { artifactActivationPath, resourceActivationPath } from './manifest-activation';

describe('coordinated manifest activation API', () => {
  it('activates an associated artifact through one transactional backend operation', () => {
    expect(artifactActivationPath('panel', 'artifact-v1', 7))
      .toBe('/panels/panel/artifacts/artifact-v1/activate?version=7');
  });
  it('passes the explicit historical association for legacy recovery', () => {
    expect(artifactActivationPath('panel', 'artifact-v1', 7, 'resource-v1'))
      .toBe('/panels/panel/artifacts/artifact-v1/activate?version=7&resourceManifestId=resource-v1');
  });
  it('reactivates a published resource revision rather than republishing its draft', () => {
    expect(resourceActivationPath('panel', 'resource-v1'))
      .toBe('/panels/panel/resource-manifests/resource-v1/activate');
  });
});
