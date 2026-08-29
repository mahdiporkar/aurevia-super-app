import {mount} from './bootstrap';

const root=document.getElementById('root');
if(root)mount(root,{locale:'fa-IR',manifest:{version:'standalone',expiresAt:new Date(Date.now()+3600000).toISOString(),panels:[],permissions:{}},correlationId:()=>crypto.randomUUID()});
