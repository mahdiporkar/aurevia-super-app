import {describe,it,expect} from 'vitest';
import {grantProjection} from './grant-projection';

describe('grant projection presentation',()=>{
  it('does not present a saved grant without projection evidence as applied',()=>{
    expect(grantProjection({}).status).toBe('UNKNOWN');
    expect(grantProjection({projection_status:'UNRECOGNIZED'}).status).toBe('UNKNOWN');
  });
  it.each(['PENDING','RETRYING'])('refreshes %s while showing its current state',status=>{
    expect(grantProjection({projection_status:status})).toMatchObject({status,pending:true});
  });
  it.each(['APPLIED','FAILED','REVOKED'])('keeps terminal projection state %s',status=>{
    expect(grantProjection({projection_status:status,projection_error:'projection failed'}))
      .toMatchObject({status,pending:false,error:'projection failed'});
  });
  it('never labels an expired grant as currently applied or waiting',()=>{
    for(const status of ['APPLIED','PENDING','RETRYING']) {
      expect(grantProjection({projection_status:status,expires_at:'2026-01-01T00:00:00Z'},
        Date.parse('2026-01-02T00:00:00Z'))).toMatchObject({status:'EXPIRED',pending:false});
    }
  });
});
