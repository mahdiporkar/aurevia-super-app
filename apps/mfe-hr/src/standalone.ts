import {mount} from './bootstrap';

const root=document.getElementById('root');
if(root)mount(root,{locale:'fa-IR',manifest:{version:'standalone',expiresAt:new Date(Date.now()+3600000).toISOString(),panels:[],permissions:{'page:hr.employee.list':['view'],'business:hr.employee':['view','create','update'],'field:hr.employee.salary-amount':['view']}},correlationId:()=>crypto.randomUUID()});
