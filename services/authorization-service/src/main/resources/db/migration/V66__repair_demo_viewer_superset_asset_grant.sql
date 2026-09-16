-- Resource keys were canonicalized from colon-separated to slash-separated in
-- an earlier migration. Resolve through superset_asset.resource_id so the demo
-- grant remains independent of either historical key spelling.
UPDATE resource r
SET name_fa='داشبورد فروش',name_en='Sales Dashboard',external_id='7',
    metadata=r.metadata||jsonb_build_object(
      'supersetAssetType','DASHBOARD','supersetExternalId','7',
      'supersetUrlPath','/superset/dashboard/7/'),
    version=r.version+1,updated_at=now()
FROM superset_asset asset
WHERE asset.resource_id=r.id AND asset.asset_type='DASHBOARD'
  AND asset.external_id='7';

WITH inserted AS (
  INSERT INTO authorization_grant(
    subject_type,subject_id,resource_id,action_id,relation,status)
  SELECT 'USER',u.id,asset.resource_id,a.id,'viewer','ACTIVE'
  FROM app_user u CROSS JOIN superset_asset asset CROSS JOIN action a
  WHERE u.issuer='http://localhost:8180/realms/aurevia'
    AND u.external_id='viewer'
    AND asset.asset_type='DASHBOARD' AND asset.external_id='7'
    AND a.action_key='view'
  ON CONFLICT DO NOTHING
  RETURNING id,subject_id,resource_id
), demo_grant AS (
  SELECT * FROM inserted
  UNION ALL
  SELECT g.id,g.subject_id,g.resource_id
  FROM authorization_grant g
  JOIN app_user u ON u.id=g.subject_id
  JOIN superset_asset asset ON asset.resource_id=g.resource_id
  JOIN action a ON a.id=g.action_id
  WHERE g.subject_type='USER' AND g.status='ACTIVE'
    AND u.issuer='http://localhost:8180/realms/aurevia'
    AND u.external_id='viewer'
    AND asset.asset_type='DASHBOARD' AND asset.external_id='7'
    AND a.action_key='view'
)
INSERT INTO outbox_event(
  aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.external_id,'relation','viewer',
  'object','external_resource:'||replace(
    regexp_replace(r.resource_key,'^external_resource:',''),':','/')),
  'demo-viewer-sales-dashboard-v66:'||g.id
FROM demo_grant g
JOIN app_user u ON u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','66'),('superset-resource-contract','6')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
