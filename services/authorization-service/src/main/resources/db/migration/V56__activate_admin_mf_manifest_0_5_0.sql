-- V55 separated MF manifests from authorization resource manifests but deliberately
-- left the previously active ADMIN artifact untouched. Publish the repository's
-- canonical 0.5.0 MF manifest so existing installations receive the new navigation
-- contract without requiring an operator to press Sync manually.
--
-- manifest_checksum is SHA-256(JSON.stringify(mf-manifest.json)). Keeping that exact
-- source checksum makes a later server-side sync converge idempotently.
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
  "microfrontend": { "key": "admin", "name": "Administration", "version": "0.5.0" },
  "runtime": { "remoteEntry": "http://localhost:3001/remoteEntry.js", "remoteName": "aurevia_admin", "exposedModule": "./bootstrap", "contractVersion": "1.0", "apiBasePath": "/api/v1/admin" },
  "defaultRouteKey": "operator-guide",
  "routes": [
    { "key": "operator-guide", "path": "operator-guide", "title": "راهنما", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "ou-access-ous", "path": "ou-access/ous", "title": "واحدهای سازمانی", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "ou-access-groups", "path": "ou-access/groups", "title": "گروه‌ها", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "ou-access-applications", "path": "ou-access/applications", "title": "برنامه‌ها", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "ou-access-explain", "path": "ou-access/explain", "title": "تحلیل دسترسی", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "access-studio", "path": "access-studio", "title": "منابع و مجوزها", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "panels", "path": "panels", "title": "میکروفرانت‌ها", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "proxy-targets", "path": "proxy-routes/targets", "title": "مقصدها", "requiredResource": "proxy.target", "requiredAction": "admin" },
    { "key": "proxy-routes", "path": "proxy-routes/routes", "title": "مسیرها", "requiredResource": "proxy.route", "requiredAction": "admin" },
    { "key": "proxy-operations", "path": "proxy-routes/operations", "title": "عملیات API", "requiredResource": "proxy.operation", "requiredAction": "admin" },
    { "key": "outbound-connections", "path": "outbound-connections", "title": "اتصال‌ها", "requiredResource": "integration.auth-profile", "requiredAction": "admin" },
    { "key": "outbound-auth", "path": "outbound-auth", "title": "احراز هویت", "requiredResource": "integration.auth-profile", "requiredAction": "admin" },
    { "key": "integration-test", "path": "integration-test", "title": "تست اتصال", "requiredResource": "integration.auth-profile", "requiredAction": "test" },
    { "key": "superset-instances", "path": "superset-instances", "title": "محیط‌های گزارش", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "identity", "path": "identity", "title": "هویت و نقش", "requiredResource": "application:aurevia", "requiredAction": "admin" },
    { "key": "logs-api", "path": "logs/api", "title": "لاگ API", "requiredResource": "business_resource:public-zone-logs", "requiredAction": "view_api" },
    { "key": "logs-audit", "path": "logs/audit", "title": "لاگ راهبری", "requiredResource": "business_resource:public-zone-logs", "requiredAction": "view_audit" },
    { "key": "superset", "path": "superset", "title": "گزارش‌ها", "requiredResource": "module:admin.superset-catalog", "requiredAction": "view" }
  ],
  "navigation": [
    { "key": "operator-guide-menu", "type": "PAGE", "routeKey": "operator-guide", "title": "راهنما", "description": "آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت", "icon": "book", "order": 10 },
    { "key": "ou-access-ous-menu", "type": "PAGE", "routeKey": "ou-access-ous", "title": "واحدهای سازمانی", "description": "مشاهده OUهای همگام‌شده از Directory و اعضای سازمان", "icon": "apartment", "order": 20 },
    { "key": "ou-access-groups-menu", "type": "PAGE", "routeKey": "ou-access-groups", "title": "گروه‌ها", "description": "ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها", "icon": "team", "order": 21 },
    { "key": "ou-access-applications-menu", "type": "PAGE", "routeKey": "ou-access-applications", "title": "برنامه‌ها", "description": "اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی", "icon": "appstore", "order": 22 },
    { "key": "ou-access-explain-menu", "type": "PAGE", "routeKey": "ou-access-explain", "title": "تحلیل دسترسی", "description": "ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی", "icon": "audit", "order": 23 },
    { "key": "access-studio-menu", "type": "PAGE", "routeKey": "access-studio", "title": "منابع و مجوزها", "description": "مدیریت درخت منابع، عملیات و Grantهای OpenFGA", "icon": "safety", "order": 30 },
    { "key": "panels-menu", "type": "PAGE", "routeKey": "panels", "title": "میکروفرانت‌ها", "description": "ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation", "icon": "appstore", "order": 40 },
    { "key": "proxy-targets-menu", "type": "PAGE", "routeKey": "proxy-targets", "title": "مقصدها", "description": "تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check", "icon": "api", "order": 50 },
    { "key": "proxy-routes-menu", "type": "PAGE", "routeKey": "proxy-routes", "title": "مسیرها", "description": "اتصال namespace ورودی Microfrontend به مقصد سرویس", "icon": "branches", "order": 51 },
    { "key": "proxy-operations-menu", "type": "PAGE", "routeKey": "proxy-operations", "title": "عملیات API", "description": "تعریف Method، Path و Resource/Action موردنیاز هر API", "icon": "control", "order": 52 },
    { "key": "outbound-connections-menu", "type": "PAGE", "routeKey": "outbound-connections", "title": "اتصال‌ها", "description": "ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy", "icon": "link", "order": 60 },
    { "key": "outbound-auth-menu", "type": "PAGE", "routeKey": "outbound-auth", "title": "احراز هویت", "description": "تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret", "icon": "key", "order": 70 },
    { "key": "integration-test-menu", "type": "PAGE", "routeKey": "integration-test", "title": "تست اتصال", "description": "اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد", "icon": "experiment", "order": 80 },
    { "key": "superset-instances-menu", "type": "PAGE", "routeKey": "superset-instances", "title": "محیط‌های گزارش", "description": "مدیریت Instanceهای Public و Operation در Apache Superset", "icon": "cloud-server", "order": 90 },
    { "key": "identity-menu", "type": "PAGE", "routeKey": "identity", "title": "هویت و نقش", "description": "مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران", "icon": "idcard", "order": 100 },
    { "key": "logs-api-menu", "type": "PAGE", "routeKey": "logs-api", "title": "لاگ API", "description": "جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID", "icon": "file-search", "order": 110 },
    { "key": "logs-audit-menu", "type": "PAGE", "routeKey": "logs-audit", "title": "لاگ راهبری", "description": "مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات", "icon": "audit", "order": 111 },
    { "key": "superset-menu", "type": "PAGE", "routeKey": "superset", "title": "گزارش‌ها", "description": "تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset", "icon": "dashboard", "order": 120 }
  ]
}
$manifest$::jsonb AS snapshot
)
INSERT INTO ui_module_artifact(id,panel_id,artifact_version,remote_entry_url,remote_name,
  exposed_module,contract_version,schema_version,integrity,manifest_snapshot,
  validation_status,manifest_checksum,source_url,synchronized_at,created_by)
SELECT gen_random_uuid(),s.panel_id,'0.5.0',s.remote_entry_url,s.remote_name,s.exposed_module,
  s.contract_version,'1.0',s.integrity,c.snapshot,'VALID',
  'e3aebf2e0b0b8127e2454497b9cb79da4abcf92c0b77be22a2b5db5ca1d5d877',
  s.mf_manifest_url,now(),'migration-v56'
FROM source s CROSS JOIN contract c
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM panel p JOIN ui_module_artifact a ON a.panel_id=p.id
    WHERE p.code='ADMIN' AND a.artifact_version='0.5.0'
      AND a.manifest_checksum<>'e3aebf2e0b0b8127e2454497b9cb79da4abcf92c0b77be22a2b5db5ca1d5d877'
  ) THEN
    RAISE EXCEPTION 'ADMIN artifact 0.5.0 already exists with a different immutable manifest';
  END IF;
END $$;

UPDATE panel p SET active_artifact_id=a.id,semantic_version=a.artifact_version,
  version=p.version+1,updated_at=now()
FROM ui_module_artifact a
WHERE p.code='ADMIN' AND a.panel_id=p.id AND a.artifact_version='0.5.0'
  AND a.manifest_checksum='e3aebf2e0b0b8127e2454497b9cb79da4abcf92c0b77be22a2b5db5ca1d5d877';

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','56'),('admin-navigation-contract','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
