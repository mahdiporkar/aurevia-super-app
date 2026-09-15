CREATE TYPE recalculation_job_status AS ENUM ('PENDING','RUNNING','COMPLETED','FAILED');

CREATE TABLE ou_recalculation_job (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  access_group_id uuid NOT NULL REFERENCES access_group(id),
  status recalculation_job_status NOT NULL DEFAULT 'PENDING',
  last_user_id uuid,
  processed_users bigint NOT NULL DEFAULT 0,
  attempts integer NOT NULL DEFAULT 0,
  available_at timestamptz NOT NULL DEFAULT now(),
  claimed_at timestamptz,
  claim_owner uuid,
  safe_error varchar(1000),
  requested_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE UNIQUE INDEX ou_recalculation_active_group_idx
  ON ou_recalculation_job(access_group_id)
  WHERE status IN ('PENDING','RUNNING');
CREATE INDEX ou_recalculation_pending_idx ON ou_recalculation_job(available_at,created_at)
  WHERE status='PENDING';

INSERT INTO schema_version(component,version) VALUES ('ou-recalculation','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
