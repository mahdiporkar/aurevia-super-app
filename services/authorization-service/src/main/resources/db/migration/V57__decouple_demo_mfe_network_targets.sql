-- Existing local installations may retain the old Core-network nginx names.
-- Normalize only the exact demo fixtures to host-facing registry URLs; production rows are untouched.
UPDATE panel SET classification='DEMO'
WHERE code='ADMIN' AND remote_entry_path='http://localhost:3001/remoteEntry.js'
  AND classification='REAL';

UPDATE panel SET mf_manifest_url=CASE code
  WHEN 'ADMIN' THEN 'http://localhost:3001/mf-manifest.json'
  WHEN 'HR' THEN 'http://localhost:3002/mf-manifest.json'
  WHEN 'FINANCE' THEN 'http://localhost:3003/mf-manifest.json'
  WHEN 'REPORTS' THEN 'http://localhost:3004/mf-manifest.json'
  ELSE mf_manifest_url END
WHERE (code,mf_manifest_url) IN (
  ('ADMIN','http://mfe-admin:8080/mf-manifest.json'),
  ('HR','http://mfe-hr:8080/mf-manifest.json'),
  ('FINANCE','http://mfe-finance:8080/mf-manifest.json'),
  ('REPORTS','http://mfe-reports:8080/mf-manifest.json'));

UPDATE panel SET resource_manifest_url=CASE code
  WHEN 'ADMIN' THEN 'http://localhost:3001/resource-manifest.json'
  WHEN 'HR' THEN 'http://localhost:3002/resource-manifest.json'
  WHEN 'FINANCE' THEN 'http://localhost:3003/resource-manifest.json'
  WHEN 'REPORTS' THEN 'http://localhost:3004/resource-manifest.json'
  ELSE resource_manifest_url END
WHERE (code,resource_manifest_url) IN (
  ('ADMIN','http://mfe-admin:8080/resource-manifest.json'),
  ('HR','http://mfe-hr:8080/resource-manifest.json'),
  ('FINANCE','http://mfe-finance:8080/resource-manifest.json'),
  ('REPORTS','http://mfe-reports:8080/resource-manifest.json'));

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','57'),('microfrontend-governance','4')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
