-- Aurevia Core / Development-demo classification for the baseline generator.
--
-- This script runs ONLY inside tools/generate-core-baseline.mjs against a throwaway database
-- that contains nothing but the output of the versioned Flyway chain. It never touches a live
-- environment. Every row that is not part of Aurevia Core is moved into schema dev_fixture
-- (which becomes db/fixtures/development/010-development-demo.sql) and removed from public
-- (which becomes db/baseline/B<version>__aurevia_core_baseline.sql).
--
-- Core is defined by explicit keep-lists of stable natural keys; everything else that the chain
-- seeded is development/demo data. No name-pattern matching is used.
\set ON_ERROR_STOP on
BEGIN;

CREATE SCHEMA dev_fixture;

CREATE TEMP TABLE keep_resource(resource_key varchar) ON COMMIT DROP;
INSERT INTO keep_resource VALUES
  ('application:aurevia'),                    -- platform root; admin permission target
  ('application:aurevia/admin'),              -- Admin Panel application
  ('application:aurevia/reports'),            -- Reports application root used by the Superset role model
  ('module:admin.superset-catalog'),          -- Admin Panel "reports" page resource
  ('external_resource:superset-public'),      -- Superset integration catalog root (code depends on it)
  ('business_resource:public-zone-logs'),     -- Admin Panel log pages
  ('integration.auth-profile'),               -- Admin Panel outbound auth pages
  ('proxy.target'),('proxy.route'),('proxy.operation'); -- Admin Panel proxy pages

CREATE TEMP TABLE keep_role(role_key varchar) ON COMMIT DROP;
INSERT INTO keep_role VALUES ('aurevia-administrator'),('superset-designer'),('superset-viewer');

CREATE TEMP TABLE keep_auth_profile(code varchar) ON COMMIT DROP;
INSERT INTO keep_auth_profile VALUES ('public-iam-forward');

-- ------------------------------------------------------------------ demo identification
CREATE TEMP TABLE demo_resource ON COMMIT DROP AS
  WITH RECURSIVE tree AS (
    SELECT id,parent_id,0 AS depth FROM resource WHERE parent_id IS NULL
    UNION ALL
    SELECT r.id,r.parent_id,t.depth+1 FROM resource r JOIN tree t ON t.id=r.parent_id
  )
  SELECT r.id,t.depth FROM resource r JOIN tree t ON t.id=r.id
  WHERE r.resource_key NOT IN (SELECT resource_key FROM keep_resource);

CREATE TEMP TABLE demo_panel ON COMMIT DROP AS SELECT id FROM panel WHERE code<>'ADMIN';
CREATE TEMP TABLE demo_role ON COMMIT DROP AS
  SELECT id FROM application_role WHERE role_key NOT IN (SELECT role_key FROM keep_role);

-- ------------------------------------------------------------------ copy demo rows (dependency order)
CREATE TABLE dev_fixture.panel AS SELECT * FROM panel WHERE id IN (SELECT id FROM demo_panel);
CREATE TABLE dev_fixture.ui_module_artifact AS
  SELECT * FROM ui_module_artifact WHERE panel_id IN (SELECT id FROM demo_panel) ORDER BY created_at;
CREATE TABLE dev_fixture.ui_menu_override AS
  SELECT * FROM ui_menu_override WHERE panel_id IN (SELECT id FROM demo_panel);
CREATE TABLE dev_fixture.resource AS
  SELECT r.* FROM resource r JOIN demo_resource d ON d.id=r.id ORDER BY d.depth,r.resource_key;
CREATE TABLE dev_fixture.resource_action AS
  SELECT * FROM resource_action WHERE resource_id IN (SELECT id FROM demo_resource);
CREATE TABLE dev_fixture.resource_api_binding AS
  SELECT * FROM resource_api_binding WHERE resource_id IN (SELECT id FROM demo_resource);
CREATE TABLE dev_fixture.resource_external_binding AS
  SELECT * FROM resource_external_binding WHERE resource_id IN (SELECT id FROM demo_resource);
CREATE TABLE dev_fixture.resource_manifest_import AS SELECT * FROM resource_manifest_import;
CREATE TABLE dev_fixture.directory_group AS SELECT * FROM directory_group;
CREATE TABLE dev_fixture.app_user AS
  SELECT id,issuer,external_id,username,display_name,email,status,version,created_at,updated_at,
    membership_version,directory_attributes,directory_external_id,canonical_user_id FROM app_user; -- subject_key is generated
CREATE TABLE dev_fixture.external_identity AS SELECT * FROM external_identity;
CREATE TABLE dev_fixture.user_group_membership AS SELECT * FROM user_group_membership;
CREATE TABLE dev_fixture.application_role AS SELECT * FROM application_role WHERE id IN (SELECT id FROM demo_role);
CREATE TABLE dev_fixture.user_role_assignment AS SELECT * FROM user_role_assignment;
CREATE TABLE dev_fixture.group_role_assignment AS SELECT * FROM group_role_assignment;
CREATE TABLE dev_fixture.authorization_grant AS
  SELECT g.* FROM authorization_grant g
  WHERE g.resource_id IN (SELECT id FROM demo_resource)
     OR (g.subject_type='USER')
     OR (g.subject_type='GROUP')
     OR (g.subject_type='ROLE' AND g.subject_id IN (SELECT id FROM demo_role))
  ORDER BY g.created_at;
CREATE TABLE dev_fixture.service_target AS SELECT * FROM service_target;
CREATE TABLE dev_fixture.outbound_auth_profile AS
  SELECT * FROM outbound_auth_profile WHERE code NOT IN (SELECT code FROM keep_auth_profile);
CREATE TABLE dev_fixture.outbound_connection AS SELECT * FROM outbound_connection;
CREATE TABLE dev_fixture.proxy_route AS SELECT * FROM proxy_route;
CREATE TABLE dev_fixture.route_operation AS SELECT * FROM route_operation;
CREATE TABLE dev_fixture.superset_instance AS SELECT * FROM superset_instance;
CREATE TABLE dev_fixture.superset_proxy_mapping AS SELECT * FROM superset_proxy_mapping;
CREATE TABLE dev_fixture.superset_asset AS SELECT * FROM superset_asset;
CREATE TABLE dev_fixture.superset_subject_mapping AS SELECT * FROM superset_subject_mapping;
CREATE TABLE dev_fixture.superset_access_sync AS SELECT * FROM superset_access_sync;

-- ------------------------------------------------------------------ remove demo rows from Core
DELETE FROM superset_access_sync;
DELETE FROM superset_subject_mapping;
DELETE FROM superset_asset;
DELETE FROM superset_proxy_mapping;
DELETE FROM superset_instance;
DELETE FROM route_operation;
DELETE FROM proxy_route;
DELETE FROM service_target;
DELETE FROM outbound_connection;
DELETE FROM outbound_auth_profile WHERE code NOT IN (SELECT code FROM keep_auth_profile);
DELETE FROM authorization_grant WHERE id IN (SELECT id FROM dev_fixture.authorization_grant);
DELETE FROM user_role_assignment;
DELETE FROM group_role_assignment;
DELETE FROM user_group_membership;
DELETE FROM external_identity;
DELETE FROM app_user;
DELETE FROM directory_group;
DELETE FROM application_role WHERE id IN (SELECT id FROM demo_role);
DELETE FROM resource_manifest_import;
DELETE FROM resource_external_binding WHERE resource_id IN (SELECT id FROM demo_resource);
DELETE FROM resource_api_binding WHERE resource_id IN (SELECT id FROM demo_resource);
DELETE FROM resource_action WHERE resource_id IN (SELECT id FROM demo_resource);
DELETE FROM resource r USING demo_resource d WHERE d.id=r.id;
-- A Core resource may point at a demo panel registration (application:aurevia/reports -> REPORTS);
-- the real Reports micro frontend re-links it when it is registered through the Admin Panel.
UPDATE resource SET panel_id=NULL WHERE panel_id IN (SELECT id FROM demo_panel);
DELETE FROM ui_menu_override WHERE panel_id IN (SELECT id FROM demo_panel);
UPDATE panel SET active_artifact_id=NULL WHERE id IN (SELECT id FROM demo_panel);
DELETE FROM ui_module_artifact WHERE panel_id IN (SELECT id FROM demo_panel);
DELETE FROM panel WHERE id IN (SELECT id FROM demo_panel);

-- Operational history that only exists in a chain that ran repairs; Core starts empty.
DELETE FROM audit_event; DELETE FROM audit_log; DELETE FROM api_log; DELETE FROM authorization_decision_log;
DELETE FROM outbox_event;

-- ------------------------------------------------------------------ regenerate the OpenFGA outbox
-- Same formulas as JdbcOpenFgaReconciliationRepository/JdbcAccessRepository, so a fresh install
-- projects exactly the Core relationships and the startup drift check reports zero difference.
CREATE OR REPLACE FUNCTION pg_temp.fga_object(p_type text,p_key text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE p_type WHEN 'APPLICATION' THEN 'application:'||regexp_replace(p_key,'^application:','')
    WHEN 'EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(regexp_replace(p_key,'^external_resource:',''),':','/')
    ELSE 'resource:'||replace(p_key,':','/') END $$;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'resource',c.id,'RESOURCE_PARENT_WRITE',jsonb_build_object(
  'user',pg_temp.fga_object(p.type::text,p.resource_key),'relation','parent',
  'object',pg_temp.fga_object(c.type::text,c.resource_key)),'baseline:parent:'||c.id
FROM resource c JOIN resource p ON p.id=c.parent_id WHERE c.status='ACTIVE';

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','role:'||ar.role_key||'#assignee','relation',g.relation,
  'object',pg_temp.fga_object(r.type::text,r.resource_key)),'baseline:grant:'||g.id
FROM authorization_grant g JOIN application_role ar ON g.subject_type='ROLE' AND ar.id=g.subject_id
JOIN resource r ON r.id=g.resource_id WHERE g.status='ACTIVE';

-- The development fixture carries its own outbox events for the demo relationships.
CREATE TABLE dev_fixture.outbox_event AS
SELECT gen_random_uuid() AS id,'resource'::varchar AS aggregate_type,c.id AS aggregate_id,
  'RESOURCE_PARENT_WRITE'::varchar AS event_type,jsonb_build_object(
  'user',pg_temp.fga_object(p.type::text,p.resource_key),'relation','parent',
  'object',pg_temp.fga_object(c.type::text,c.resource_key)) AS payload,
  ('development-fixture:parent:'||c.id)::varchar AS idempotency_key
FROM dev_fixture.resource c JOIN (SELECT id,type,resource_key FROM resource UNION ALL SELECT id,type,resource_key FROM dev_fixture.resource) p
  ON p.id=c.parent_id WHERE c.status='ACTIVE'
UNION ALL
SELECT gen_random_uuid(),'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user',CASE g.subject_type WHEN 'USER' THEN 'user:'||u.canonical_user_id
    WHEN 'GROUP' THEN 'group:directory/'||dg.id||'#member'
    WHEN 'ROLE' THEN 'role:'||ar.role_key||'#assignee' END,
  'relation',g.relation,'object',pg_temp.fga_object(r.type::text,r.resource_key)),
  'development-fixture:grant:'||g.id
FROM dev_fixture.authorization_grant g
LEFT JOIN dev_fixture.app_user u ON g.subject_type='USER' AND u.id=g.subject_id
LEFT JOIN dev_fixture.directory_group dg ON g.subject_type='GROUP' AND dg.id=g.subject_id
LEFT JOIN (SELECT id,role_key FROM application_role UNION ALL SELECT id,role_key FROM dev_fixture.application_role) ar
  ON g.subject_type='ROLE' AND ar.id=g.subject_id
JOIN (SELECT id,type,resource_key FROM resource UNION ALL SELECT id,type,resource_key FROM dev_fixture.resource) r ON r.id=g.resource_id
WHERE g.status='ACTIVE'
UNION ALL
SELECT gen_random_uuid(),'role-assignment',x.role_id,'ROLE_ASSIGNMENT_WRITE',jsonb_build_object(
  'user','user:'||u.canonical_user_id,'relation','assignee','object','role:'||ar.role_key),
  'development-fixture:role-assignment:'||x.role_id||':'||x.user_id
FROM dev_fixture.user_role_assignment x JOIN dev_fixture.app_user u ON u.id=x.user_id
JOIN (SELECT id,role_key FROM application_role UNION ALL SELECT id,role_key FROM dev_fixture.application_role) ar ON ar.id=x.role_id;

-- Sanity: nothing demo-related may remain in Core.
DO $$
DECLARE remaining text;
BEGIN
  SELECT string_agg(resource_key,',') INTO remaining FROM resource
    WHERE resource_key NOT IN (SELECT resource_key FROM keep_resource);
  IF remaining IS NOT NULL THEN RAISE EXCEPTION 'Core still contains non-core resources: %',remaining; END IF;
  IF (SELECT count(*) FROM app_user)+(SELECT count(*) FROM panel WHERE code<>'ADMIN')
     +(SELECT count(*) FROM superset_instance)+(SELECT count(*) FROM proxy_route)<>0 THEN
    RAISE EXCEPTION 'Core still contains demo registry rows';
  END IF;
END $$;

COMMIT;
