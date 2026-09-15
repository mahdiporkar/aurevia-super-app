# ADR 0001: BFF session and token vault

Status: Accepted

The BFF performs OIDC Authorization Code Flow server-side. It stores a random browser session in one Redis namespace and application-level encrypted access/refresh tokens in another, connected only by an opaque token handle. Keys carry identifiers for rotation and come from a secret provider. Tokens are prohibited from browser contracts, logs, URLs, PostgreSQL, and frontend memory.

