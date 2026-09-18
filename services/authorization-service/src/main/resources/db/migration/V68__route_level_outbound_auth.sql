-- Authentication is a route concern: one microfrontend and one target may expose
-- both user-token and Legacy operations without duplicating the network target.
-- A shared prefix is valid when operations are disjoint or route priorities resolve overlap.
ALTER TABLE proxy_route DROP CONSTRAINT IF EXISTS proxy_route_path_prefix_key;
ALTER TABLE service_target ALTER COLUMN code TYPE varchar(160);
ALTER TABLE proxy_route ALTER COLUMN code TYPE varchar(160);

ALTER TABLE proxy_route
  ADD COLUMN outbound_auth_profile_id uuid REFERENCES outbound_auth_profile(id);

UPDATE proxy_route pr
SET outbound_auth_profile_id=st.outbound_auth_profile_id
FROM service_target st
WHERE st.id=pr.service_target_id;

ALTER TABLE proxy_route ALTER COLUMN outbound_auth_profile_id SET NOT NULL;
CREATE INDEX proxy_route_outbound_auth_idx ON proxy_route(outbound_auth_profile_id);

INSERT INTO schema_version(component,version) VALUES('control-plane','68')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
