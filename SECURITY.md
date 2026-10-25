# Security Policy

Estoa is a local-first desktop application. It stores cases and encrypted API
keys on your machine and does not run a server, so the remote attack surface is
limited to the sources you query and the update check.

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x | Yes |
| < 1.0 | No |

## Reporting a vulnerability

Please **do not open a public issue** for an undisclosed vulnerability.

Use GitHub private vulnerability reporting:

https://github.com/mikear/estoa/security/advisories/new

Include as much as you can:

- Estoa version (`Estoa --version` or the About dialog) and operating system.
- Steps to reproduce, with a proof of concept if possible.
- Impact and the component involved (UI, connector, persistence, packaging).
- Relevant logs. Remove API keys, case data, and personal information first.

## Response

| Time | Action |
|---|---|
| 72 hours | Acknowledge the report |
| 7 days | Initial assessment and severity |
| 30 days | Fix or mitigation plan, coordinated disclosure date |

These are best-effort targets for a single-maintainer project.

## Scope

In scope:

- The Estoa application, its persistence layer, and the packaging scripts.
- Handling of API keys and local case data.
- Integrity between published artifacts and this source tree.
- Dependency or build-chain issues that affect the released binaries.

Out of scope:

- Third-party APIs and data sources (report those to their maintainers).
- Denial of service against public APIs.
- Issues that require an already compromised machine or elevated local access.
- False positives from unsigned binaries or heuristic antivirus detections.
- Intelligence accuracy: wrong or outdated data is a normal bug, not a
  vulnerability.

## Handling secrets

- API keys are encrypted with AES-256-GCM in `~/.orion/keys.dat`.
- Never paste `keys.dat`, `.keystore.key`, or case databases into an issue.
- If you accidentally publish a key, revoke it with the provider immediately.

## Verifying releases

Every release includes SHA256 checksums. Compare the downloaded artifact against
`SHA256SUMS.txt` before running it. The v1 binaries are not code-signed, so
Windows SmartScreen or macOS Gatekeeper may show a warning.
