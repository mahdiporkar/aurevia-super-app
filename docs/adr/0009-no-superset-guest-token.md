# ADR 0009: Authenticated Public Superset iframe

Status: Accepted. Public means zone, not anonymous. Public Superset uses Public IAM OIDC and a separate Secure/HttpOnly server session under `/reports-runtime/`. Anonymous dashboards, public links, guest tokens and Embedded SDK guest-token flow are forbidden.
