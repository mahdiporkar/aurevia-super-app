-- V66 repaired the relational grant but projected the external subject alias.
-- OpenFGA principals use the immutable canonical_user_id introduced by V59.
INSERT INTO outbox_event(
  aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.canonical_user_id,'relation','viewer',
  'object','external_resource:'||replace(
    regexp_replace(r.resource_key,'^external_resource:',''),':','/')),
  'demo-viewer-sales-dashboard-canonical-v67:'||g.id
FROM authorization_grant g
JOIN app_user u ON u.id=g.subject_id
JOIN superset_asset asset ON asset.resource_id=g.resource_id
JOIN resource r ON r.id=g.resource_id
JOIN action a ON a.id=g.action_id
WHERE g.subject_type='USER' AND g.status='ACTIVE'
  AND u.issuer='http://localhost:8180/realms/aurevia'
  AND u.external_id='viewer'
  AND asset.asset_type='DASHBOARD' AND asset.external_id='7'
  AND a.action_key='view'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','67'),('superset-resource-contract','7')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
