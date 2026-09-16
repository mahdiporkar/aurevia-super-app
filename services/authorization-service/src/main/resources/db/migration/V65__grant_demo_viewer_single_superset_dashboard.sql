-- Local/demo acceptance identity: the non-administrator `viewer` may see the
-- Reports MFE and exactly one registered Superset dashboard.  Keeping this as
-- an asset grant (not a logical Superset-root grant) proves per-report access.
WITH inserted AS (
  INSERT INTO authorization_grant(
    subject_type,subject_id,resource_id,action_id,relation,status)
  SELECT 'USER',u.id,r.id,a.id,'viewer','ACTIVE'
  FROM app_user u
  JOIN resource r ON r.resource_key=
    'external_resource:superset-public:dashboard:welcome-dashboard'
  JOIN action a ON a.action_key='view'
  WHERE u.issuer='http://localhost:8180/realms/aurevia'
    AND u.external_id='viewer'
  ON CONFLICT DO NOTHING
  RETURNING id,subject_id,resource_id,action_id
), demo_grant AS (
  SELECT * FROM inserted
  UNION ALL
  SELECT g.id,g.subject_id,g.resource_id,g.action_id
  FROM authorization_grant g
  JOIN app_user u ON u.id=g.subject_id
  JOIN resource r ON r.id=g.resource_id
  JOIN action a ON a.id=g.action_id
  WHERE g.subject_type='USER' AND g.status='ACTIVE'
    AND u.issuer='http://localhost:8180/realms/aurevia'
    AND u.external_id='viewer'
    AND r.resource_key=
      'external_resource:superset-public:dashboard:welcome-dashboard'
    AND a.action_key='view'
)
INSERT INTO outbox_event(
  aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.external_id,
  'relation','viewer',
  'object','external_resource:superset-public/dashboard/welcome-dashboard'),
  'demo-viewer-sales-dashboard-v65:'||g.id
FROM demo_grant g JOIN app_user u ON u.id=g.subject_id
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','65'),('superset-resource-contract','5')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
