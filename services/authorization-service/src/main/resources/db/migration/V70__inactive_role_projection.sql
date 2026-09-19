-- Repair roles disabled before status changes started generating projection events.
-- Keep the relational assignments/grants so activation can restore unexpired access.
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'role-assignment',r.id,'ROLE_ASSIGNMENT_DELETE',
  jsonb_build_object('user',s.subject,'relation','assignee','object','role:'||r.role_key),
  'V70:assignment:'||r.id||':'||md5(s.subject)
FROM application_role r JOIN (
  SELECT x.role_id,'user:'||u.canonical_user_id subject
  FROM user_role_assignment x JOIN app_user u ON u.id=x.user_id
  UNION ALL
  SELECT x.role_id,'group:directory/'||g.id||'#member'
  FROM group_role_assignment x JOIN directory_group g ON g.id=x.group_id
  UNION ALL
  SELECT x.role_id,'group:'||lower(g.code)||'#member'
  FROM access_group_role_assignment x JOIN access_group g ON g.id=x.access_group_id
) s ON s.role_id=r.id
WHERE r.status<>'ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_DELETE',
  jsonb_build_object('user','role:'||ar.role_key||'#assignee','relation',g.relation,
    'object',CASE WHEN r.type='APPLICATION' THEN r.resource_key
      WHEN r.type='EXTERNAL_RESOURCE' THEN 'external_resource:'||
        replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
      ELSE 'resource:'||replace(r.resource_key,':','/') END),
  'V70:grant:'||g.id
FROM authorization_grant g JOIN application_role ar ON ar.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE g.subject_type='ROLE' AND ar.status<>'ACTIVE'
ON CONFLICT(idempotency_key) DO NOTHING;
