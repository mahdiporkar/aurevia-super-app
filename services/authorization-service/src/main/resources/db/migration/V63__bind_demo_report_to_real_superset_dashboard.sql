-- The original demo asset pointed at Superset's welcome page and used a
-- synthetic external id.  Preserve its resource/grants, but bind it to the
-- real Sales Dashboard loaded by the Superset demo initializer.
UPDATE superset_asset
SET external_id='7',
    title='Sales Dashboard',
    url_path='/superset/dashboard/7/',
    last_synced_version='superset-demo-5.0.0',
    synchronized_at=now()
WHERE external_id='welcome-dashboard'
  AND asset_type='DASHBOARD'
  AND url_path='/superset/welcome/';

UPDATE resource
SET name_fa='داشبورد فروش',
    name_en='Sales Dashboard',
    external_id='7',
    metadata=metadata||jsonb_build_object(
      'supersetAssetType','DASHBOARD',
      'supersetExternalId','7',
      'supersetUrlPath','/superset/dashboard/7/'),
    version=version+1,
    updated_at=now()
WHERE resource_key='external_resource:superset-public:dashboard:welcome-dashboard';

INSERT INTO schema_version(component,version) VALUES
  ('control-plane','63'),('superset-resource-contract','4')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
