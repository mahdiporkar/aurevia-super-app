INSERT INTO panel(code,name_fa,name_en,slug,remote_entry_path,exposed_module,route_base_path,semantic_version,contract_version,active,sort_order) VALUES
('ADMIN','مدیریت','Administration','admin','/mfe/admin/remoteEntry.js','./bootstrap','/admin','0.1.0','1',true,10),
('HR','منابع انسانی','Human Resources','hr','/mfe/hr/remoteEntry.js','./bootstrap','/hr','0.1.0','1',true,20),
('FINANCE','مالی','Finance','finance','/mfe/finance/remoteEntry.js','./bootstrap','/finance','0.1.0','1',true,30),
('REPORTS','گزارش‌ها','Reports','reports','/mfe/reports/remoteEntry.js','./bootstrap','/reports','0.1.0','1',true,40)
ON CONFLICT(code) DO NOTHING;
INSERT INTO action(action_key,name_fa,name_en) VALUES ('view','مشاهده','View'),('create','ایجاد','Create'),('update','ویرایش','Update'),('approve','تأیید','Approve'),('admin','مدیریت','Admin') ON CONFLICT(action_key) DO NOTHING;
INSERT INTO resource(resource_key,type,name_fa,name_en,owner_domain) VALUES
('application:aurevia','APPLICATION','اوراویا','Aurevia','platform'),('business_resource:employee','BUSINESS_RESOURCE','کارمند','Employee','hr'),('business_resource:payment','BUSINESS_RESOURCE','پرداخت','Payment','finance'),('external_resource:superset-public','EXTERNAL_RESOURCE','گزارش عمومی','Public report','reports') ON CONFLICT(resource_key) DO NOTHING;
INSERT INTO schema_version(component,version) VALUES('control-plane','2') ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
