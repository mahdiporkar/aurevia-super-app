# Security Policy

## Reporting

Do not open a public issue for a suspected vulnerability. Use GitHub private vulnerability reporting for this repository. Include affected commit/version, reproduction steps, impact, and suggested mitigation when available.

## Supported versions

Only the latest release and the current `main` branch receive security fixes until a formal long-term-support policy is published.

## Security invariants

- Browser code never receives OAuth access or refresh tokens.
- All browser traffic is same-origin through the BFF.
- Every operational API operation is resolved through the route registry and authorized server-side.
- Unknown routes, actions, policy fields, subjects, or synchronization states deny access.
- Only Authorization Service writes relationship tuples.
- Secrets, private keys, real passwords, and production endpoints are forbidden in Git.

Rotate any credential immediately if it is committed, even if the commit is later removed.
