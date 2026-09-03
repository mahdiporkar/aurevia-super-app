-- Split immutable UI artifacts from the existing panel registration without creating a parallel module identity.
ALTER TABLE panel ADD COLUMN description varchar(1000), ADD COLUMN service_slug varchar(80),
  ADD COLUMN default_route_id varchar(100), ADD COLUMN remote_name varchar(160);
UPDATE panel SET service_slug=slug, remote_name='aurevia_'||replace(regexp_replace(slug,'^mfe-',''),'_','_'),
  default_route_id='index' WHERE service_slug IS NULL;
ALTER TABLE panel ALTER COLUMN service_slug SET NOT NULL, ALTER COLUMN remote_name SET NOT NULL;
ALTER TABLE panel ADD CONSTRAINT panel_route_prefix_format CHECK (route_base_path ~ '^/[a-z][a-z0-9-]{1,49}$');
ALTER TABLE panel ADD CONSTRAINT panel_service_slug_format CHECK (service_slug ~ '^[a-z][a-z0-9-]{1,49}$');

CREATE TABLE ui_module_artifact (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), panel_id uuid NOT NULL REFERENCES panel(id) ON DELETE RESTRICT,
  artifact_version varchar(50) NOT NULL, remote_entry_url varchar(1000) NOT NULL, remote_name varchar(160) NOT NULL,
  exposed_module varchar(160) NOT NULL DEFAULT './plugin', contract_version varchar(50) NOT NULL,
  schema_version varchar(30) NOT NULL DEFAULT '1.0', integrity varchar(255), manifest_snapshot jsonb NOT NULL,
  validation_status varchar(20) NOT NULL CHECK(validation_status IN ('VALID','INVALID','PENDING')),
  validation_error varchar(1000), immutable boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(255) NOT NULL DEFAULT 'migration', UNIQUE(panel_id,artifact_version), UNIQUE(remote_name)
);
CREATE TABLE ui_menu_override (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), panel_id uuid NOT NULL REFERENCES panel(id) ON DELETE CASCADE,
  menu_id varchar(100) NOT NULL, title varchar(255), icon varchar(100), sort_order integer, hidden boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL DEFAULT now(), updated_by varchar(255) NOT NULL DEFAULT 'migration',
  UNIQUE(panel_id,menu_id)
);
ALTER TABLE panel ADD COLUMN active_artifact_id uuid REFERENCES ui_module_artifact(id) ON DELETE RESTRICT;

-- Bootstrap immutable snapshots for the four existing registrations.
INSERT INTO ui_module_artifact(panel_id,artifact_version,remote_entry_url,remote_name,exposed_module,contract_version,manifest_snapshot,validation_status)
SELECT id,semantic_version,remote_entry_path,remote_name,exposed_module,contract_version,
 jsonb_build_object('schemaVersion','1.0','moduleKey',slug,'defaultRouteId','index','routes',jsonb_build_array(jsonb_build_object('id','index','path','','title',name_fa,'resource','application:aurevia/'||slug,'action','view')),'menus',jsonb_build_array(jsonb_build_object('id','main','routeId','index','title',name_fa,'icon',coalesce(icon,'appstore'),'order',sort_order))), 'VALID'
FROM panel;
UPDATE panel p SET active_artifact_id=a.id FROM ui_module_artifact a WHERE a.panel_id=p.id AND a.artifact_version=p.semantic_version;
UPDATE ui_module_artifact a SET remote_name='aurevia_hr_ui_0_1_0',exposed_module='./plugin',contract_version='1.0',manifest_snapshot=jsonb_build_object(
 'schemaVersion','1.0','moduleKey','human-resources','defaultRouteId','employee-list',
 'routes',jsonb_build_array(
  jsonb_build_object('id','employee-list','path','personal','title','پرسنل','resource','page:hr.employee.list','action','view'),
  jsonb_build_object('id','employee-details','path','personal/:id','title','اطلاعات پرسنل','resource','page:hr.employee.list','action','view'),
  jsonb_build_object('id','department-list','path','departments','title','واحدهای سازمانی','resource','page:hr.departments','action','view'),
  jsonb_build_object('id','position-list','path','positions','title','سمت‌ها','resource','page:hr.positions','action','view')),
 'menus',jsonb_build_array(
  jsonb_build_object('id','employees-menu','routeId','employee-list','title','پرسنل','icon','user','order',10),
  jsonb_build_object('id','departments-menu','routeId','department-list','title','واحدهای سازمانی','icon','team','order',20),
  jsonb_build_object('id','positions-menu','routeId','position-list','title','سمت‌ها','icon','solution','order',30)))
FROM panel p WHERE a.panel_id=p.id AND p.code='HR';
UPDATE panel SET remote_name='aurevia_hr_ui_0_1_0',exposed_module='./plugin',contract_version='1.0',default_route_id='employee-list' WHERE code='HR';
CREATE INDEX ui_module_artifact_panel_idx ON ui_module_artifact(panel_id,created_at DESC);

-- The browser sees only a stable public service slug; service_target keeps the internal destination.
ALTER TABLE proxy_route ADD COLUMN service_slug varchar(80);
UPDATE proxy_route r SET service_slug=p.service_slug FROM panel p WHERE p.id=r.panel_id;
UPDATE proxy_route SET service_slug=regexp_replace(trim(both '/' from path_prefix),'[^a-zA-Z0-9-].*$','') WHERE service_slug IS NULL;
ALTER TABLE proxy_route ALTER COLUMN service_slug SET NOT NULL;
ALTER TABLE proxy_route ADD CONSTRAINT proxy_route_service_slug_format CHECK(service_slug ~ '^[a-z][a-z0-9-]{1,49}$');
UPDATE proxy_route SET path_prefix='/api/proxy/'||service_slug,normalized_path_prefix='/api/proxy/'||service_slug||'/';
UPDATE route_operation ro SET normalized_path_pattern=regexp_replace(normalized_path_pattern,'^/api/v1','')
FROM proxy_route pr WHERE ro.proxy_route_id=pr.id AND pr.code IN ('hr-api','finance-api');
UPDATE proxy_route SET strip_prefix=3,rewrite_pattern='^/',rewrite_replacement=CASE code WHEN 'hr-api' THEN '/hr-service/api/v1/' ELSE '/finance-service/api/v1/' END
WHERE code IN ('hr-api','finance-api');
