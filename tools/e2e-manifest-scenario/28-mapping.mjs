import {admin,state} from './lib.mjs';
const s=await admin(); const st=state();
const m=await s.json('/api/v1/admin/superset-instances/mappings','POST',
 {publicInstanceId:st.supersetPublicId,operationInstanceId:st.supersetOperationId,
  publicPath:'/e2e-reports-runtime',isDefault:false,active:true});
console.log('CREATE mapping ->',m.status,JSON.stringify(m.body).slice(0,160));
const all=await s.json('/api/v1/admin/superset-instances/mappings');
console.log('mappings:',JSON.stringify(all.body).slice(0,400));
