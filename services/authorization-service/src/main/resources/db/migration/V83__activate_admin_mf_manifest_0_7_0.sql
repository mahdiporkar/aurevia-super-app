-- Publish the repository's canonical 0.7.0 ADMIN MF manifest: the topic-based
-- control panel. The side menu now carries one entry per administrative domain
-- (guide, access, users, micro frontends, routes, reports, monitoring) and each
-- domain's individual operations are URL-addressable tabs beneath it, so an
-- administrator completes one configuration workflow on one page.
--
-- No page and no permission was removed. Every previous page is still its own
-- route with its own requiredResource/requiredAction, so the effective catalog
-- keeps filtering tabs one at a time and a restricted administrator sees only the
-- tabs it is authorized for. Each multi-tab domain also publishes a landing route
-- (`<domain>-home`) that exists purely so the side menu has one stable URL per
-- domain; landing redirects to the first tab the subject may actually open.
--
-- Renamed URLs are served by in-app redirects in the micro frontend, so existing
-- bookmarks such as /admin/proxy-routes/routes keep resolving.
--
-- manifest_checksum is SHA-256(JSON.stringify(mf-manifest.json)), exactly as the
-- server-side sync computes it, so a later sync converges idempotently.
WITH admin_panel AS (
  SELECT p.id AS panel_id,p.active_artifact_id,p.mf_manifest_url
  FROM panel p WHERE p.code='ADMIN'
), source AS (
  SELECT a.*,p.mf_manifest_url
  FROM ui_module_artifact a JOIN admin_panel p ON p.active_artifact_id=a.id
), contract AS (
  SELECT $manifest$
{
  "schemaVersion": "1.0",
  "microfrontend": {
    "key": "admin",
    "name": "Administration",
    "version": "0.7.0"
  },
  "runtime": {
    "remoteEntry": "http://localhost:3001/remoteEntry.js",
    "remoteName": "aurevia_admin",
    "exposedModule": "./bootstrap",
    "contractVersion": "1.0",
    "apiBasePath": "/api/v1/admin"
  },
  "defaultRouteKey": "operator-guide",
  "routes": [
    {
      "key": "operator-guide",
      "path": "operator-guide",
      "title": "راهنما",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "ou-access-ous",
      "path": "access/ous",
      "title": "واحدهای سازمانی",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "ou-access-groups",
      "path": "access/groups",
      "title": "گروه‌ها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "ou-access-applications",
      "path": "access/applications",
      "title": "برنامه‌ها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "access-studio",
      "path": "access/resources",
      "title": "منابع و مجوزها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "ou-access-explain",
      "path": "access/explain",
      "title": "تحلیل دسترسی",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "users",
      "path": "users/accounts",
      "title": "مدیریت کاربران",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "identity",
      "path": "users/roles",
      "title": "هویت و نقش",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "panels",
      "path": "panels",
      "title": "میکروفرانت‌ها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "proxy-targets",
      "path": "routes/targets",
      "title": "مقصدها",
      "requiredResource": "proxy.target",
      "requiredAction": "admin"
    },
    {
      "key": "proxy-routes",
      "path": "routes/routes",
      "title": "مسیرها",
      "requiredResource": "proxy.route",
      "requiredAction": "admin"
    },
    {
      "key": "proxy-operations",
      "path": "routes/operations",
      "title": "عملیات API",
      "requiredResource": "proxy.operation",
      "requiredAction": "admin"
    },
    {
      "key": "outbound-connections",
      "path": "routes/connections",
      "title": "اتصال‌ها",
      "requiredResource": "integration.auth-profile",
      "requiredAction": "admin"
    },
    {
      "key": "outbound-auth",
      "path": "routes/auth",
      "title": "احراز هویت",
      "requiredResource": "integration.auth-profile",
      "requiredAction": "admin"
    },
    {
      "key": "integration-test",
      "path": "routes/test",
      "title": "تست اتصال",
      "requiredResource": "integration.auth-profile",
      "requiredAction": "test"
    },
    {
      "key": "superset-instances",
      "path": "reports/instances",
      "title": "محیط‌های گزارش",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "superset",
      "path": "reports/assets",
      "title": "گزارش‌ها",
      "requiredResource": "module:admin.superset-catalog",
      "requiredAction": "view"
    },
    {
      "key": "logs-api",
      "path": "logs/api",
      "title": "لاگ API",
      "requiredResource": "business_resource:public-zone-logs",
      "requiredAction": "view_api"
    },
    {
      "key": "logs-audit",
      "path": "logs/audit",
      "title": "لاگ راهبری",
      "requiredResource": "business_resource:public-zone-logs",
      "requiredAction": "view_audit"
    },
    {
      "key": "access-home",
      "path": "access",
      "title": "دسترسی سازمانی و مجوزها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "users-home",
      "path": "users",
      "title": "کاربران و نقش‌ها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "routes-home",
      "path": "routes",
      "title": "مسیرها و اتصال‌ها",
      "requiredResource": "proxy.route",
      "requiredAction": "admin"
    },
    {
      "key": "reports-home",
      "path": "reports",
      "title": "گزارش‌ها",
      "requiredResource": "module:admin.superset-catalog",
      "requiredAction": "view"
    },
    {
      "key": "logs-home",
      "path": "logs",
      "title": "پایش و حسابرسی",
      "requiredResource": "business_resource:public-zone-logs",
      "requiredAction": "view_api"
    },
    {
      "key": "legacy-ou-access",
      "path": "ou-access",
      "title": "دسترسی سازمانی و مجوزها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-ou-access-ous",
      "path": "ou-access/ous",
      "title": "واحدهای سازمانی",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-ou-access-groups",
      "path": "ou-access/groups",
      "title": "گروه‌ها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-ou-access-applications",
      "path": "ou-access/applications",
      "title": "برنامه‌ها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-ou-access-explain",
      "path": "ou-access/explain",
      "title": "تحلیل دسترسی",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-access-studio",
      "path": "access-studio",
      "title": "منابع و مجوزها",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-identity",
      "path": "identity",
      "title": "هویت و نقش",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-proxy-routes",
      "path": "proxy-routes",
      "title": "مسیرها و اتصال‌ها",
      "requiredResource": "proxy.route",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-proxy-routes-targets",
      "path": "proxy-routes/targets",
      "title": "مقصدها",
      "requiredResource": "proxy.target",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-proxy-routes-routes",
      "path": "proxy-routes/routes",
      "title": "مسیرها",
      "requiredResource": "proxy.route",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-proxy-routes-operations",
      "path": "proxy-routes/operations",
      "title": "عملیات API",
      "requiredResource": "proxy.operation",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-outbound-connections",
      "path": "outbound-connections",
      "title": "اتصال‌ها",
      "requiredResource": "integration.auth-profile",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-outbound-auth",
      "path": "outbound-auth",
      "title": "احراز هویت",
      "requiredResource": "integration.auth-profile",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-integration-test",
      "path": "integration-test",
      "title": "تست اتصال",
      "requiredResource": "integration.auth-profile",
      "requiredAction": "test"
    },
    {
      "key": "legacy-superset-instances",
      "path": "superset-instances",
      "title": "محیط‌های گزارش",
      "requiredResource": "application:aurevia",
      "requiredAction": "admin"
    },
    {
      "key": "legacy-superset",
      "path": "superset",
      "title": "گزارش‌ها",
      "requiredResource": "module:admin.superset-catalog",
      "requiredAction": "view"
    }
  ],
  "navigation": [
    {
      "key": "operator-guide-menu",
      "type": "PAGE",
      "routeKey": "operator-guide",
      "title": "راهنما",
      "description": "آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت",
      "icon": "book",
      "order": 10
    },
    {
      "key": "access-menu",
      "type": "PAGE",
      "routeKey": "access-home",
      "title": "دسترسی سازمانی و مجوزها",
      "description": "واحدهای سازمانی، گروه‌ها، تخصیص برنامه، درخت منابع و تحلیل تصمیم دسترسی",
      "icon": "safety",
      "order": 20
    },
    {
      "key": "users-menu",
      "type": "PAGE",
      "routeKey": "users-home",
      "title": "کاربران و نقش‌ها",
      "description": "ایجاد کاربر در Keycloak و مدیریت نقش‌ها و گروه‌های همگام",
      "icon": "idcard",
      "order": 30
    },
    {
      "key": "panels-menu",
      "type": "PAGE",
      "routeKey": "panels",
      "title": "میکروفرانت‌ها",
      "description": "ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation",
      "icon": "appstore",
      "order": 40
    },
    {
      "key": "routes-menu",
      "type": "PAGE",
      "routeKey": "routes-home",
      "title": "مسیرها و اتصال‌ها",
      "description": "مقصد سرویس، مسیر Proxy، عملیات API، اتصال خروجی، احراز هویت و تست اتصال",
      "icon": "api",
      "order": 50
    },
    {
      "key": "reports-menu",
      "type": "PAGE",
      "routeKey": "reports-home",
      "title": "گزارش‌ها",
      "description": "محیط‌های Superset و تخصیص سطح دسترسی داشبوردها و گزارش‌ها",
      "icon": "dashboard",
      "order": 60
    },
    {
      "key": "logs-menu",
      "type": "PAGE",
      "routeKey": "logs-home",
      "title": "پایش و حسابرسی",
      "description": "جست‌وجوی لاگ API و مشاهده لاگ تغییرات راهبری",
      "icon": "file-search",
      "order": 70
    }
  ]
}
$manifest$::jsonb AS snapshot
)
INSERT INTO ui_module_artifact(id,panel_id,artifact_version,remote_entry_url,remote_name,
  exposed_module,contract_version,schema_version,integrity,manifest_snapshot,
  validation_status,manifest_checksum,source_url,synchronized_at,created_by)
SELECT gen_random_uuid(),s.panel_id,'0.7.0',s.remote_entry_url,s.remote_name,s.exposed_module,
  s.contract_version,'1.0',s.integrity,c.snapshot,'VALID',
  'f987c6cba2d9bd763f7df4e48855dd13638f4a2fe58e300cf6be697c08db24a3',
  s.mf_manifest_url,now(),'migration-v83'
FROM source s CROSS JOIN contract c
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM panel p JOIN ui_module_artifact a ON a.panel_id=p.id
    WHERE p.code='ADMIN' AND a.artifact_version='0.7.0'
      AND a.manifest_checksum<>'f987c6cba2d9bd763f7df4e48855dd13638f4a2fe58e300cf6be697c08db24a3'
  ) THEN
    RAISE EXCEPTION 'ADMIN artifact 0.7.0 already exists with a different immutable manifest';
  END IF;
END $$;

UPDATE panel p SET active_artifact_id=a.id,semantic_version=a.artifact_version,
  version=p.version+1,updated_at=now()
FROM ui_module_artifact a
WHERE p.code='ADMIN' AND a.panel_id=p.id AND a.artifact_version='0.7.0'
  AND a.manifest_checksum='f987c6cba2d9bd763f7df4e48855dd13638f4a2fe58e300cf6be697c08db24a3';

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','83'),('admin-navigation-contract','3')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
