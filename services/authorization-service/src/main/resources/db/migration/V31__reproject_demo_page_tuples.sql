-- Re-project page tuples independently from the original catalog transaction. This is safe on a
-- fresh store (OpenFGA duplicate writes are idempotent in the adapter) and repairs installations
-- where V30 completed while OpenFGA was temporarily unavailable or misconfigured.
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
 'user','resource:'||replace(p.resource_key,':','/'),'relation','parent',
 'object','resource:'||replace(c.resource_key,':','/')),
 'demo-extra-page-reproject-parent:'||c.id||':'||p.id
FROM resource c JOIN resource p ON p.id=c.parent_id
WHERE c.resource_key IN ('page:hr.departments','page:hr.positions','page:finance.invoices','page:finance.budgets')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
 'user','user:'||u.external_id,'relation',g.relation,
 'object','resource:'||replace(r.resource_key,':','/')),
 'demo-extra-page-reproject-grant:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE u.external_id IN ('demo-full-access','demo-hr-only') AND g.status='ACTIVE'
 AND r.resource_key IN ('page:hr.departments','page:hr.positions','page:finance.invoices','page:finance.budgets')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','31')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
