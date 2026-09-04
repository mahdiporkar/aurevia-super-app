CREATE TYPE superset_zone AS ENUM ('PUBLIC','OPERATION');

CREATE TABLE superset_instance (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  zone superset_zone NOT NULL,
  base_url varchar(1000) NOT NULL,
  connection_ref varchar(255) NOT NULL,
  auth_mode varchar(40) NOT NULL DEFAULT 'REMOTE_USER',
  tls_required boolean NOT NULL DEFAULT true,
  active boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  created_by varchar(500) NOT NULL,
  updated_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (code ~ '^[a-z][a-z0-9-]{2,79}$'),
  CHECK (base_url ~ '^https?://[^/?#]+(?:/[^?#]*)?$'),
  CHECK (connection_ref ~ '^connection://[a-zA-Z0-9._/-]+$'),
  CHECK (auth_mode IN ('REMOTE_USER','OIDC','GUEST_TOKEN')),
  CHECK (NOT tls_required OR base_url LIKE 'https://%')
);

CREATE TABLE superset_proxy_mapping (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  public_instance_id uuid NOT NULL REFERENCES superset_instance(id) ON DELETE RESTRICT,
  operation_instance_id uuid NOT NULL REFERENCES superset_instance(id) ON DELETE RESTRICT,
  public_path varchar(255) NOT NULL UNIQUE,
  is_default boolean NOT NULL DEFAULT false,
  active boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  created_by varchar(500) NOT NULL,
  updated_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(public_instance_id),
  CHECK (public_path ~ '^/[a-zA-Z0-9/_-]*$')
);

CREATE UNIQUE INDEX superset_single_default_mapping_idx
  ON superset_proxy_mapping(is_default) WHERE is_default AND active;

INSERT INTO superset_instance(
  code,name,zone,base_url,connection_ref,auth_mode,tls_required,created_by,updated_by
) VALUES
  ('public-default','Superset Public','PUBLIC','http://public-superset:8088',
    'connection://superset/public-default','REMOTE_USER',false,'system','system'),
  ('operation-default','Superset Operation','OPERATION','http://operation-superset:8088',
    'connection://superset/operation-default','REMOTE_USER',false,'system','system')
ON CONFLICT(code) DO NOTHING;

INSERT INTO superset_proxy_mapping(
  public_instance_id,operation_instance_id,public_path,is_default,created_by,updated_by
)
SELECT public.id,operation.id,'/reports-runtime',true,'system','system'
FROM superset_instance public,superset_instance operation
WHERE public.code='public-default' AND operation.code='operation-default'
ON CONFLICT(public_instance_id) DO NOTHING;

ALTER TABLE superset_asset ADD COLUMN instance_id uuid REFERENCES superset_instance(id);
UPDATE superset_asset SET instance_id=(
  SELECT id FROM superset_instance WHERE code='operation-default'
) WHERE instance_id IS NULL;
ALTER TABLE superset_asset ALTER COLUMN instance_id SET NOT NULL;
ALTER TABLE superset_asset DROP CONSTRAINT IF EXISTS superset_asset_external_id_key;
ALTER TABLE superset_asset ADD CONSTRAINT superset_asset_instance_external_unique
  UNIQUE(instance_id,asset_type,external_id);
CREATE INDEX superset_asset_instance_idx ON superset_asset(instance_id,published);

INSERT INTO schema_version(component,version) VALUES ('superset-registry','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
