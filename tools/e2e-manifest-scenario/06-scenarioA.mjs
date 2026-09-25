import {grantTo,listGrants,ctxB,drain,ids} from './grant.mjs';
console.log('SCENARIO A: grant micro root (application) view');
const g=await grantTo(ids.app);
console.log('grant ->',g.status,JSON.stringify(g.body));
console.log('outbox drained:',await drain());
console.log('CONTEXT AFTER ROOT GRANT:',JSON.stringify(await ctxB(),null,1));
