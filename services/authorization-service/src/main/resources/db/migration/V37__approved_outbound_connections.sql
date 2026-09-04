CREATE TYPE outbound_connection_kind AS ENUM ('LEGACY_TOKEN');

CREATE TABLE outbound_connection (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_ref varchar(255) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  kind outbound_connection_kind NOT NULL,
  base_url varchar(1000) NOT NULL,
  tls_required boolean NOT NULL DEFAULT true,
  active boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  created_by varchar(500) NOT NULL,
  updated_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK(connection_ref ~ '^connection://[a-zA-Z0-9._/-]+$'),
  CHECK(base_url ~ '^https?://[^/?#]+$'),
  CHECK(NOT tls_required OR base_url LIKE 'https://%')
);

DO $$
BEGIN
  IF EXISTS(SELECT 1 FROM outbound_auth_profile
    WHERE client_id_secret_ref IS NOT NULL OR client_secret_ref IS NOT NULL) THEN
    RAISE EXCEPTION 'Consolidate client_id/client_secret refs into credential_secret_ref before V37';
  END IF;
END $$;

ALTER TABLE outbound_auth_profile DROP COLUMN client_id_secret_ref;
ALTER TABLE outbound_auth_profile DROP COLUMN client_secret_ref;
ALTER TABLE outbound_auth_profile DROP COLUMN refresh_token_response_pointer;
ALTER TABLE outbound_auth_profile DROP CONSTRAINT IF EXISTS outbound_auth_profile_request_format_check;
ALTER TABLE outbound_auth_profile ADD CONSTRAINT outbound_auth_profile_request_format_check
  CHECK(request_format IN ('FORM_URLENCODED','JSON','HTTP_BASIC','OAUTH_CLIENT_CREDENTIALS'));

-- Older releases kept the endpoint outside this database. Preserve references as
-- inactive placeholders so upgrades succeed without silently approving an origin.
INSERT INTO outbound_connection(connection_ref,name,kind,base_url,tls_required,active,created_by,updated_by)
SELECT DISTINCT token_connection_ref,'Migrated connection (configuration required)',
  'LEGACY_TOKEN'::outbound_connection_kind,'https://invalid.invalid',true,false,'migration','migration'
FROM outbound_auth_profile WHERE token_connection_ref IS NOT NULL
ON CONFLICT(connection_ref) DO NOTHING;

ALTER TABLE outbound_auth_profile ADD CONSTRAINT outbound_auth_profile_connection_fk
  FOREIGN KEY(token_connection_ref) REFERENCES outbound_connection(connection_ref);

INSERT INTO schema_version(component,version) VALUES ('outbound-connections','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
