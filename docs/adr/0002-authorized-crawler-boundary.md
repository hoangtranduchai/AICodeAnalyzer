# ADR 0002: Authorized Crawler Boundary

## Status

Accepted.

## Context

The application analyzes source code from online judges. Some source code is public, some requires login, and some is intentionally hidden by contest rules or platform policy.

## Decision

The crawler may only fetch source code visible to the current authorized Chrome bot session. It must not bypass captcha, hidden contests, private permissions, Cloudflare, or other access controls.

If source cannot be viewed, the app records an explicit unavailable state instead of forcing access.

## Consequences

Benefits:

- Safer academic demonstration.
- Better platform compliance.
- Clear audit trail.
- Reduced legal and ethical risk.

Tradeoffs:

- Some submissions will have metadata without source.
- Live demos may require manual login.
- Captcha or hidden contest states must be handled as normal outcomes.
