-- OU based access is deliberately separate from directory_group: an AD OU is a
-- location in the directory tree, while a directory_group is an independent membership.
CREATE TYPE directory_source AS ENUM ('ACTIVE_DIRECTORY');
CREATE TYPE access_group_type AS ENUM ('DIRECTORY','CALCULATED','MANUAL');
CREATE TYPE ou_match_mode AS ENUM ('EXACT','SUBTREE');
CREATE TYPE rule_combiner AS ENUM ('ANY_OF','ALL_OF');
CREATE TYPE membership_source_type AS ENUM ('OU_RULE','LDAP_ATTRIBUTE','MANUAL');
CREATE TYPE projection_status AS ENUM ('PENDING','APPLIED','RETRYING','FAILED','REVOKED');

CREATE TABLE directory_ou (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  issuer varchar(255) NOT NULL,
  external_id varchar(512) NOT NULL,
  external_dn varchar(2048) NOT NULL,
  external_path varchar(2048) NOT NULL,
  name varchar(255) NOT NULL,
  parent_ou_id uuid REFERENCES directory_ou(id) ON DELETE RESTRICT,
  source directory_source NOT NULL DEFAULT 'ACTIVE_DIRECTORY',
  active boolean NOT NULL DEFAULT true,
  directory_managed boolean NOT NULL DEFAULT true,
  last_synced_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  UNIQUE(issuer, external_id),
  UNIQUE(issuer, external_dn)
);
CREATE INDEX directory_ou_parent_idx ON directory_ou(parent_ou_id);
CREATE INDEX directory_ou_path_idx ON directory_ou(issuer, external_path) WHERE active;

CREATE TABLE user_ou_assignment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  ou_id uuid NOT NULL REFERENCES directory_ou(id) ON DELETE RESTRICT,
  source directory_source NOT NULL DEFAULT 'ACTIVE_DIRECTORY',
  active boolean NOT NULL DEFAULT true,
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  removed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX user_primary_ad_ou_idx ON user_ou_assignment(user_id)
  WHERE active AND source='ACTIVE_DIRECTORY';
CREATE INDEX user_ou_assignment_ou_idx ON user_ou_assignment(ou_id) WHERE active;

CREATE TABLE access_group (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(160) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  description varchar(1000),
  group_type access_group_type NOT NULL DEFAULT 'CALCULATED',
  rule_combiner rule_combiner NOT NULL DEFAULT 'ANY_OF',
  active boolean NOT NULL DEFAULT true,
  created_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CHECK(code ~ '^[A-Z][A-Z0-9_]{2,159}$')
);

CREATE TABLE access_group_ou_rule (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  access_group_id uuid NOT NULL REFERENCES access_group(id) ON DELETE CASCADE,
  ou_id uuid NOT NULL REFERENCES directory_ou(id) ON DELETE RESTRICT,
  match_mode ou_match_mode NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  UNIQUE(access_group_id, ou_id, match_mode)
);
CREATE INDEX access_group_ou_rule_ou_idx ON access_group_ou_rule(ou_id) WHERE active;

CREATE TABLE effective_group_membership (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  access_group_id uuid NOT NULL REFERENCES access_group(id) ON DELETE CASCADE,
  source_type membership_source_type NOT NULL,
  source_id uuid NOT NULL,
  active boolean NOT NULL DEFAULT true,
  calculated_at timestamptz NOT NULL DEFAULT now(),
  removed_at timestamptz,
  membership_version bigint NOT NULL DEFAULT 1,
  UNIQUE(user_id, access_group_id, source_type, source_id)
);
CREATE INDEX effective_membership_lookup_idx
  ON effective_group_membership(user_id, access_group_id) WHERE active;

CREATE TABLE application_group_grant (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id uuid NOT NULL REFERENCES panel(id) ON DELETE RESTRICT,
  access_group_id uuid NOT NULL REFERENCES access_group(id) ON DELETE RESTRICT,
  relation varchar(40) NOT NULL DEFAULT 'VIEWER',
  status projection_status NOT NULL DEFAULT 'PENDING',
  granted_by varchar(500) NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  revoked_by varchar(500),
  revoked_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  CHECK(relation='VIEWER')
);
CREATE UNIQUE INDEX active_application_group_grant_idx
  ON application_group_grant(application_id,access_group_id,relation)
  WHERE revoked_at IS NULL;

CREATE TABLE directory_sync_run (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), source directory_source NOT NULL,
  status varchar(32) NOT NULL, started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz, discovered_ous integer NOT NULL DEFAULT 0,
  discovered_users integer NOT NULL DEFAULT 0, safe_error varchar(1000)
);

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS membership_version bigint NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS directory_attributes jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS directory_external_id varchar(512);
CREATE UNIQUE INDEX app_user_directory_external_idx ON app_user(issuer,directory_external_id)
  WHERE directory_external_id IS NOT NULL;

INSERT INTO schema_version(component,version) VALUES ('ou-authorization','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
