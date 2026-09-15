# ADR 0010: Operation Superset isolation

Status: Accepted. Operation Superset has separate identity, metadata database, secrets, cookie and network. It is reachable only by approved operational services, has no browser/Nginx route, and is never embedded by an MFE.
