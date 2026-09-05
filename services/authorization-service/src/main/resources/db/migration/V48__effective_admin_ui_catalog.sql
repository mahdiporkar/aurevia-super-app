-- A Module Federation scope is stable across versions of one panel. Artifact versions are
-- already unique per panel, so remote_name must not prevent publishing the next version.
ALTER TABLE ui_module_artifact DROP CONSTRAINT IF EXISTS ui_module_artifact_remote_name_key;
CREATE INDEX IF NOT EXISTS ui_module_artifact_remote_name_idx
  ON ui_module_artifact(remote_name);

INSERT INTO ui_module_artifact(
  id,panel_id,artifact_version,remote_entry_url,remote_name,exposed_module,
  contract_version,schema_version,integrity,manifest_snapshot,validation_status,created_by)
SELECT gen_random_uuid(),p.id,'0.2.0',current_artifact.remote_entry_url,
  current_artifact.remote_name,current_artifact.exposed_module,'1.0','1.0',
  current_artifact.integrity,$manifest$
{
  "schemaVersion":"1.0",
  "moduleKey":"admin",
  "defaultRouteId":"operator-guide",
  "runtime":{"apiBasePath":"/api/v1/admin"},
  "routes":[
    {"id":"operator-guide","path":"operator-guide","title":"راهنمای فرم‌ها","resource":"application:aurevia","action":"admin"},
    {"id":"ou-access-ous","path":"ou-access/ous","title":"OUهای سازمانی","resource":"application:aurevia","action":"admin"},
    {"id":"ou-access-groups","path":"ou-access/groups","title":"Access Groupها","resource":"application:aurevia","action":"admin"},
    {"id":"ou-access-applications","path":"ou-access/applications","title":"دسترسی Microfrontend","resource":"application:aurevia","action":"admin"},
    {"id":"ou-access-explain","path":"ou-access/explain","title":"بررسی دسترسی User","resource":"application:aurevia","action":"admin"},
    {"id":"access-studio","path":"access-studio","title":"استودیوی دسترسی","resource":"application:aurevia","action":"admin"},
    {"id":"panels","path":"panels","title":"میکروفرانت‌ها","resource":"application:aurevia","action":"admin"},
    {"id":"proxy-targets","path":"proxy-routes/targets","title":"Service Targets","resource":"proxy.target","action":"admin"},
    {"id":"proxy-routes","path":"proxy-routes/routes","title":"Proxy Routes","resource":"proxy.route","action":"admin"},
    {"id":"proxy-operations","path":"proxy-routes/operations","title":"Route Operations","resource":"proxy.operation","action":"admin"},
    {"id":"outbound-connections","path":"outbound-connections","title":"اتصال‌های Legacy","resource":"integration.auth-profile","action":"admin"},
    {"id":"outbound-auth","path":"outbound-auth","title":"پروفایل‌های احراز هویت سرویس‌ها","resource":"integration.auth-profile","action":"admin"},
    {"id":"integration-test","path":"integration-test","title":"آزمایشگاه اتصال","resource":"integration.auth-profile","action":"test"},
    {"id":"superset-instances","path":"superset-instances","title":"محیط‌های Superset","resource":"application:aurevia","action":"admin"},
    {"id":"identity","path":"identity","title":"گروه‌ها و نقش‌ها","resource":"application:aurevia","action":"admin"},
    {"id":"logs-api","path":"logs/api","title":"API Logs","resource":"business_resource:public-zone-logs","action":"view_api"},
    {"id":"logs-audit","path":"logs/audit","title":"Audit Logs","resource":"business_resource:public-zone-logs","action":"view_audit"},
    {"id":"superset","path":"superset","title":"گزارش‌ها و داشبوردها","resource":"module:admin.superset-catalog","action":"view"}
  ],
  "menus":[
    {"id":"operator-guide-menu","routeId":"operator-guide","title":"راهنمای فرم‌ها","icon":"book","order":10},
    {"id":"ou-access-ous-menu","routeId":"ou-access-ous","title":"OUهای سازمانی","icon":"apartment","order":20},
    {"id":"ou-access-groups-menu","routeId":"ou-access-groups","title":"Access Groupها","icon":"team","order":21},
    {"id":"ou-access-applications-menu","routeId":"ou-access-applications","title":"دسترسی Microfrontend","icon":"appstore","order":22},
    {"id":"ou-access-explain-menu","routeId":"ou-access-explain","title":"بررسی دسترسی User","icon":"audit","order":23},
    {"id":"access-studio-menu","routeId":"access-studio","title":"استودیوی دسترسی","icon":"safety","order":30},
    {"id":"panels-menu","routeId":"panels","title":"میکروفرانت‌ها","icon":"appstore","order":40},
    {"id":"proxy-targets-menu","routeId":"proxy-targets","title":"Service Targets","icon":"api","order":50},
    {"id":"proxy-routes-menu","routeId":"proxy-routes","title":"Proxy Routes","icon":"branches","order":51},
    {"id":"proxy-operations-menu","routeId":"proxy-operations","title":"Route Operations","icon":"control","order":52},
    {"id":"outbound-connections-menu","routeId":"outbound-connections","title":"اتصال‌های Legacy","icon":"link","order":60},
    {"id":"outbound-auth-menu","routeId":"outbound-auth","title":"پروفایل‌های احراز هویت سرویس‌ها","icon":"key","order":70},
    {"id":"integration-test-menu","routeId":"integration-test","title":"آزمایشگاه اتصال","icon":"experiment","order":80},
    {"id":"superset-instances-menu","routeId":"superset-instances","title":"محیط‌های Superset","icon":"cloud-server","order":90},
    {"id":"identity-menu","routeId":"identity","title":"گروه‌ها و نقش‌ها","icon":"idcard","order":100},
    {"id":"logs-api-menu","routeId":"logs-api","title":"API Logs","icon":"file-search","order":110},
    {"id":"logs-audit-menu","routeId":"logs-audit","title":"Audit Logs","icon":"audit","order":111},
    {"id":"superset-menu","routeId":"superset","title":"گزارش‌ها و داشبوردها","icon":"dashboard","order":120}
  ]
}
$manifest$::jsonb,'VALID','migration-v48'
FROM panel p
JOIN ui_module_artifact current_artifact ON current_artifact.id=p.active_artifact_id
WHERE p.code='ADMIN'
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

UPDATE panel p SET
  active_artifact_id=artifact.id,
  semantic_version=artifact.artifact_version,
  contract_version=artifact.contract_version,
  default_route_id='operator-guide',
  version=p.version+1,
  updated_at=now()
FROM ui_module_artifact artifact
WHERE p.code='ADMIN' AND artifact.panel_id=p.id AND artifact.artifact_version='0.2.0';

INSERT INTO schema_version(component,version) VALUES('effective-ui-catalog','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
