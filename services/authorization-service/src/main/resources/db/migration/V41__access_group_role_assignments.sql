-- Roles may be assigned to calculated OU access groups without conflating them with LDAP groups.
CREATE TABLE access_group_role_assignment (
  access_group_id uuid NOT NULL REFERENCES access_group(id) ON DELETE RESTRICT,
  role_id uuid NOT NULL REFERENCES application_role(id) ON DELETE RESTRICT,
  expires_at timestamptz,
  assigned_by varchar(500) NOT NULL,
  assigned_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(access_group_id, role_id)
);

ALTER TABLE user_role_assignment
  ADD COLUMN IF NOT EXISTS assigned_by varchar(500) NOT NULL DEFAULT 'migration',
  ADD COLUMN IF NOT EXISTS assigned_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE group_role_assignment
  ADD COLUMN IF NOT EXISTS assigned_by varchar(500) NOT NULL DEFAULT 'migration',
  ADD COLUMN IF NOT EXISTS assigned_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

INSERT INTO schema_version(component,version) VALUES ('identity-role-management','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
