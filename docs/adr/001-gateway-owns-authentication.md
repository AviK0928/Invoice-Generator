# 1. The gateway owns authentication

Date: 2026-09-04
Status: Accepted

## Context

Five services sit behind an API gateway. Every request needs authenticating.
There is no user service and no identity provider.

## Decision

The gateway issues and validates JWTs. Downstream services perform no
authentication and trust that any request reaching them has already been
verified.

Signing is symmetric (HS256). The gateway is both issuer and verifier, so no
third party needs a public key; RS256 would add key distribution for no benefit.

Users are defined in configuration with bcrypt hashes supplied via environment
variables. There is no user table.

## Consequences

**A service reached directly is unauthenticated.** In production only the
gateway is publicly routable, so this holds. It does *not* hold in the local
compose file, which publishes 8081–8085 for development convenience — anyone on
that host can bypass auth entirely. Acceptable for a dev environment;
unacceptable if those ports are ever exposed.

**A compromised gateway compromises everything.** There is no defence in depth.

**Adding real users means adding a service.** Configuration-based users do not
support registration, password reset, or per-tenant isolation.

## Alternatives considered

*Each service as an independent resource server.* More correct — every service
verifies the token itself, and direct access is protected. Rejected because it
is five times the configuration for a system where only the gateway is public,
and the security benefit is realised only in a failure mode (a service
accidentally exposed) that better deployment hygiene addresses more cheaply.

Worth revisiting if the services are ever deployed independently or if a second
client bypasses the gateway.
