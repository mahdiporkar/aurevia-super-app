# ADR 0002: No Token Exchange

Status: Accepted

The BFF forwards the current Public IAM bearer token unchanged to the Operational API Gateway and authenticates the BFF using mTLS. The API/platform team owns issuer-boundary acceptance. This repository must not exchange, mint, rewrite, impersonate, or obtain an Operational IAM token.

