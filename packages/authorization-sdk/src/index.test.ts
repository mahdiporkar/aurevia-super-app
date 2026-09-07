import{describe,expect,it}from'vitest';
import type{EffectiveUserContext}from'@aurevia/contracts';
import{AuthorizationClient}from'./index';

const context:EffectiveUserContext={contractVersion:'1.0',version:'v1',
  expiresAt:new Date(Date.now()+60_000).toISOString(),panels:[],permissions:{},
  identity:{issuer:'issuer',subject:'user-1',username:'operator'},tenant:{id:'tenant-1'},
  organizations:[],allowedApplications:['finance'],allowedMicros:[],dynamicRoutes:[],navigation:[],
  resources:[],actions:{'finance.invoice':['view','approve']},policies:{}};

describe('AuthorizationClient',()=>{
  it('allows only actions in the effective context',()=>{const client=new AuthorizationClient();
    client.setContext(context);expect(client.can('finance.invoice','approve')).toBe(true);
    expect(client.can('finance.invoice','delete')).toBe(false);});
  it('fails closed without context or after expiry',()=>{const client=new AuthorizationClient();
    expect(client.can('finance.invoice','view')).toBe(false);client.setContext({...context,expiresAt:new Date(0).toISOString()});
    expect(client.decision('finance.invoice','view')).toBe('expired');});
});
