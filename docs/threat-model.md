# Threat model

| Asset / boundary | Threat | Controls | Residual risk |
|---|---|---|---|
| Browser ↔ BFF session | Token theft, fixation, CSRF | Opaque rotated Secure/HttpOnly/SameSite cookie, server-side OAuth, origin and CSRF checks | Compromised browser session remains usable until revoked |
| Redis token vault | Token disclosure | Separate namespaces, ACL/TLS, TTL, AES-256-GCM envelope with key IDs, redacted telemetry | Host or active-key compromise requires incident rotation |
| BFF ↔ Operation Gateway | SSRF, credential forwarding, confused deputy | Registry-only targets, normalized paths, method/action mapping, mTLS, unchanged bearer, header allowlist | Gateway issuer acceptance remains platform-team owned |
| Authorization Service ↔ OpenFGA | Stale or partial grants | Default deny, transactional outbox, idempotency, reconciliation, versioned cache keys | Brief revocation latency bounded by cache TTL |
| Public Superset iframe | Direct-URL authorization bypass | OIDC, no anonymous/guest tokens, OpenFGA pre-check plus synchronized native subject grants | Drift denies launch until reconciled |
| Supply chain | Malicious dependency/image | Exact versions, lockfile, SBOM and audit jobs, internal mirror support | Upstream compromise between scan and deploy |

Tokens, passwords, private keys, cookies, connection strings and sensitive context are excluded from logs, traces, analytics and error contracts.
