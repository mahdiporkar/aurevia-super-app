-- Publish a corrected immutable HR UI artifact. The detail route has its own PAGE
-- authorization boundary and must not reuse the employee-list entitlement.
INSERT INTO ui_module_artifact(
  id,panel_id,artifact_version,remote_entry_url,remote_name,exposed_module,
  contract_version,schema_version,integrity,manifest_snapshot,validation_status,created_by)
SELECT gen_random_uuid(),p.id,'0.1.1',current.remote_entry_url,current.remote_name,
  current.exposed_module,current.contract_version,current.schema_version,current.integrity,
  jsonb_set(current.manifest_snapshot,'{routes}',(
    SELECT jsonb_agg(
      CASE WHEN route->>'id'='employee-details'
        THEN jsonb_set(route,'{resource}','"page:hr.employee.detail"'::jsonb)
        ELSE route END
      ORDER BY ordinal)
    FROM jsonb_array_elements(current.manifest_snapshot->'routes') WITH ORDINALITY AS items(route,ordinal)
  )),
  'VALID','migration-v50'
FROM panel p
JOIN ui_module_artifact current ON current.id=p.active_artifact_id
WHERE p.code='HR'
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

UPDATE panel p SET
  active_artifact_id=artifact.id,
  semantic_version=artifact.artifact_version,
  version=p.version+1,
  updated_at=now()
FROM ui_module_artifact artifact
WHERE p.code='HR' AND artifact.panel_id=p.id AND artifact.artifact_version='0.1.1';

-- Preserve the documented demo journey without granting the detail page to arbitrary users.
INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'USER',u.id,r.id,a.id,'viewer'
FROM app_user u
JOIN resource r ON r.resource_key='page:hr.employee.detail'
JOIN action a ON a.action_key='view'
WHERE u.external_id IN ('demo-full-access','demo-hr-only')
  AND NOT EXISTS (
    SELECT 1 FROM authorization_grant g
    WHERE g.subject_type='USER' AND g.subject_id=u.id AND g.resource_id=r.id
      AND g.action_id=a.id AND g.status='ACTIVE'
  );

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.external_id,
  'relation',g.relation,
  'object','resource:'||replace(r.resource_key,':','/')
),
'hr-detail-page-grant:'||g.id
FROM authorization_grant g
JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id AND r.resource_key='page:hr.employee.detail'
JOIN action a ON a.id=g.action_id AND a.action_key='view'
WHERE u.external_id IN ('demo-full-access','demo-hr-only') AND g.status='ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('control-plane','50')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
