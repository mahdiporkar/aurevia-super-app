-- Publish the repository's canonical 0.3.0 REPORTS MF manifest so the in-app
-- report viewer route is part of the effective catalog on every installation
-- without an operator pressing Sync.
--
-- Superset dashboards are now embedded inside the Reports micro frontend instead
-- of being opened in a separate browser tab. The viewer is a real URL
-- (`/reports/view/<assetId>`) so refresh, deep links and browser Back work, and
-- the Shell only mounts a micro frontend on a route its manifest declares.
--
-- The new route declares exactly the resource and action the existing landing
-- route already declares, so no subject gains access to anything: the report
-- catalog still returns only authorized assets and every Superset runtime
-- request is still authorized per asset by the BFF through OpenFGA.
--
-- manifest_checksum is SHA-256(JSON.stringify(mf-manifest.json)), exactly as the
-- server-side sync computes it, so a later sync converges idempotently.
WITH reports_panel AS (
  SELECT p.id AS panel_id,p.active_artifact_id,p.mf_manifest_url
  FROM panel p WHERE p.code='REPORTS'
), source AS (
  SELECT a.*,p.mf_manifest_url
  FROM ui_module_artifact a JOIN reports_panel p ON p.active_artifact_id=a.id
), contract AS (
  SELECT $manifest$
{
  "schemaVersion": "1.0",
  "microfrontend": { "key": "reports", "name": "Reports", "version": "0.3.0" },
  "runtime": { "remoteEntry": "http://localhost:3004/remoteEntry.js", "remoteName": "aurevia_reports", "exposedModule": "./bootstrap", "contractVersion": "1.0", "apiBasePath": "/api/v1" },
  "defaultRouteKey": "index",
  "routes": [
    { "key": "index", "path": "", "requiredResource": "application:aurevia/reports", "requiredAction": "view", "title": "مرکز گزارش‌ها" },
    { "key": "view", "path": "view/:reportId", "requiredResource": "application:aurevia/reports", "requiredAction": "view", "title": "نمایش گزارش" }
  ],
  "navigation": [
    { "key": "reports.nav.root", "type": "GROUP", "title": "گزارش‌ها", "icon": "dashboard", "order": 30 },
    { "key": "main", "type": "PAGE", "parentKey": "reports.nav.root", "routeKey": "index", "title": "مرکز گزارش‌ها", "icon": "bar-chart", "order": 10 }
  ]
}
$manifest$::jsonb AS snapshot
)
INSERT INTO ui_module_artifact(id,panel_id,artifact_version,remote_entry_url,remote_name,
  exposed_module,contract_version,schema_version,integrity,manifest_snapshot,
  validation_status,manifest_checksum,source_url,synchronized_at,created_by)
SELECT gen_random_uuid(),s.panel_id,'0.3.0',s.remote_entry_url,s.remote_name,s.exposed_module,
  s.contract_version,'1.0',s.integrity,c.snapshot,'VALID',
  '2e7939448cf5bfa90cfaa0d8044d5954276b0d9928a7558f172cba1e012cf886',
  s.mf_manifest_url,now(),'migration-v82'
FROM source s CROSS JOIN contract c
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM panel p JOIN ui_module_artifact a ON a.panel_id=p.id
    WHERE p.code='REPORTS' AND a.artifact_version='0.3.0'
      AND a.manifest_checksum<>'2e7939448cf5bfa90cfaa0d8044d5954276b0d9928a7558f172cba1e012cf886'
  ) THEN
    RAISE EXCEPTION 'REPORTS artifact 0.3.0 already exists with a different immutable manifest';
  END IF;
END $$;

UPDATE panel p SET active_artifact_id=a.id,semantic_version=a.artifact_version,
  version=p.version+1,updated_at=now()
FROM ui_module_artifact a
WHERE p.code='REPORTS' AND a.panel_id=p.id AND a.artifact_version='0.3.0'
  AND a.manifest_checksum='2e7939448cf5bfa90cfaa0d8044d5954276b0d9928a7558f172cba1e012cf886';

INSERT INTO schema_version(component,version) VALUES ('control-plane','82')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
