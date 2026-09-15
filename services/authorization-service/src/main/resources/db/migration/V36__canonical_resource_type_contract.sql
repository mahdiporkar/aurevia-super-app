-- Keep one canonical prefix per logical resource type across DB, Java and TypeScript.
UPDATE resource SET resource_key='external_resource:'||substring(resource_key from 10),
  version=version+1,updated_at=now()
WHERE resource_key LIKE 'external:%'
  AND NOT EXISTS (
    SELECT 1 FROM resource existing
    WHERE existing.resource_key='external_resource:'||substring(resource.resource_key from 10)
  );

INSERT INTO schema_version(component,version) VALUES ('resource-contract','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
