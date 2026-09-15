-- Four additional fake-data pages for the HR/Finance demo.
INSERT INTO resource(resource_key,type,parent_id,name_fa,name_en,owner_domain,classification,source,metadata)
SELECT v.key,'PAGE',p.id,v.fa,v.en,v.domain,v.classification,'APPLICATION_MANIFEST',jsonb_build_object('route',v.route)
FROM (VALUES
 ('page:hr.departments','module:hr.erp','واحدهای سازمانی','Departments','hr','INTERNAL','/hr/departments'),
 ('page:hr.positions','module:hr.erp','سمت‌های سازمانی','Positions','hr','INTERNAL','/hr/positions'),
 ('page:finance.invoices','module:finance.erp','صورتحساب‌ها','Invoices','finance','CONFIDENTIAL','/finance/invoices'),
 ('page:finance.budgets','module:finance.erp','بودجه‌ها','Budgets','finance','CONFIDENTIAL','/finance/budgets')
) v(key,parent_key,fa,en,domain,classification,route)
JOIN resource p ON p.resource_key=v.parent_key
ON CONFLICT(resource_key) DO UPDATE SET parent_id=excluded.parent_id,name_fa=excluded.name_fa,
 name_en=excluded.name_en,owner_domain=excluded.owner_domain,classification=excluded.classification,
 source=excluded.source,metadata=excluded.metadata,status='ACTIVE',updated_at=now();

INSERT INTO resource_action(resource_id,action_id)
SELECT r.id,a.id FROM resource r CROSS JOIN action a
WHERE r.resource_key IN ('page:hr.departments','page:hr.positions','page:finance.invoices','page:finance.budgets')
 AND a.action_key='view' ON CONFLICT DO NOTHING;

INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,'viewer' FROM (VALUES
 ('demo-full-access','page:hr.departments'),('demo-full-access','page:hr.positions'),
 ('demo-full-access','page:finance.invoices'),('demo-full-access','page:finance.budgets'),
 ('demo-hr-only','page:hr.departments'),('demo-hr-only','page:hr.positions')
) v(user_key,resource_key)
JOIN app_user u ON u.external_id=v.user_key JOIN resource r ON r.resource_key=v.resource_key
JOIN action a ON a.action_key='view'
WHERE NOT EXISTS (SELECT 1 FROM authorization_grant g WHERE g.subject_type='USER'
 AND g.subject_id=u.id AND g.resource_id=r.id AND g.action_id=a.id AND g.status='ACTIVE');

-- Finance reference-data APIs belong only to the full-access demo user.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,'viewer' FROM (VALUES
 ('finance.invoice','list'),('finance.invoice','view'),
 ('finance.budget','list'),('finance.budget','view')
) v(resource_key,action_key)
JOIN app_user u ON u.external_id='demo-full-access' JOIN resource r ON r.resource_key=v.resource_key
JOIN action a ON a.action_key=v.action_key
WHERE NOT EXISTS (SELECT 1 FROM authorization_grant g WHERE g.subject_type='USER'
 AND g.subject_id=u.id AND g.resource_id=r.id AND g.action_id=a.id AND g.status='ACTIVE');

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
 'user','resource:'||replace(p.resource_key,':','/'),'relation','parent',
 'object','resource:'||replace(c.resource_key,':','/')),
 'demo-extra-page-parent:'||c.id||':'||p.id
FROM resource c JOIN resource p ON p.id=c.parent_id
WHERE c.resource_key IN ('page:hr.departments','page:hr.positions','page:finance.invoices','page:finance.budgets')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
 'user','user:'||u.external_id,'relation',g.relation,
 'object','resource:'||replace(r.resource_key,':','/')),
 'demo-extra-page-grant:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE u.external_id IN ('demo-full-access','demo-hr-only') AND g.status='ACTIVE'
 AND r.resource_key IN ('page:hr.departments','page:hr.positions','page:finance.invoices','page:finance.budgets','finance.invoice','finance.budget')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','30')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
