# ADR 0004: Explicit route registry

Status: Accepted

`service_target`, `proxy_route`, and `route_operation` separate target allowlisting, deterministic longest-prefix routing, and resource/action authorization. User-supplied target URLs and secrets in route tables are forbidden. There is no `proxy_permission` table.

