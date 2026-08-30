import {mount} from './bootstrap';

const root=document.getElementById('root');
if(root)mount(root,{locale:'fa-IR',manifest:{version:'standalone',expiresAt:new Date(Date.now()+3600000).toISOString(),panels:[],permissions:{'component:hr.employee.create-button':['view'],'component:hr.employee.grid':['view'],'component:hr.employee.salary-field':['view'],'hr.employee':['create','update']}},correlationId:()=>crypto.randomUUID()});
