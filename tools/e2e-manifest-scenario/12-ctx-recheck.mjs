import {ctxB} from './grant.mjs';
const c=await ctxB();
console.log('FRESH CONTEXT permDetail:',JSON.stringify(c.permDetail,null,1));
console.log('routes:',JSON.stringify(c.uiModules));
