# 1. Authentication lives at the edge

Date: 2026-09-04
Status: Accepted, amended 2026-09-06 by [ADR 003](003-deploy-as-a-single-service.md)

## Context

Five services sit behind an API gateway. Every request needs authenticating.
There is no user service and no identity provider.

## Decision

**One component authenticates: whichever one is the edge.** Downstream code
performs no authentication and trusts that any request reaching it has been
verified.

In the distributed topology that component is the gateway. In the consolidated
deployment there is no gateway, and it is the servlet security chain in `app` —
the same rules, the same token, the same configuration-based users.

Signing is symmetric (HS256). The issuer and the verifier are the same process,
so no third party needs a public key; RS256 would add key distribution for no
benefit.

Users are defined in configuration with bcrypt hashes supplied via environment
variables. There is no user table.

## Amendment: what consolidation changed

The original decision named the gateway. That was right for the topology it was
written against and wrong as a general statement, and consolidation forced the
distinction.

**The credential logic is shared, the filter chain is not.** `AuthController`,
`AuthService`, `AuthProperties` and the two DTOs live in `common.auth` — they are
blocking and touch neither the servlet nor the reactive stack. Only the security
configuration and the JWT decoder differ: `ServerHttpSecurity` and
`ReactiveJwtDecoder` at the gateway, `HttpSecurity` and `JwtDecoder` in `app`.

Duplicating `AuthService` was the alternative. Rejected: it carries the
timing-attack defence that verifies a dummy bcrypt hash for unknown users, and
that is exactly the kind of thing that gets fixed in one copy and forgotten in
the other.

**One behaviour did not survive the move.** At the gateway, a
`BadCredentialsException` thrown from the controller reached Spring Security's
reactive handling and became a 401. In one servlet context it reaches the five
domain exception advices instead, whose inherited catch-all on `Exception` made a
wrong password a 500. `AuthExceptionHandler` restores the 401, scoped with
`assignableTypes` so its own inherited catch-all does not answer for the whole
application.

The lesson generalises: "the edge authenticates" survived consolidation, but the
framework machinery underneath it did not, and only production showed the
difference.

## Consequences

**A service reached directly is unauthenticated.** In the consolidated deployment
there is no such thing — one process, one chain. In the distributed topology it
holds only if the gateway is the sole public route. It does **not** hold in the
local compose file, which publishes 8081–8085 for development convenience;
anyone on that host bypasses authentication entirely. Acceptable for a dev
environment, unacceptable if those ports are ever exposed.

**A compromised edge compromises everything.** There is no defence in depth.

**Adding real users means adding a service.** Configuration-based users do not
support registration, password reset, or per-tenant isolation.

## Alternatives considered

*Each service as an independent resource server.* More correct — every service
verifies the token itself, and direct access is protected. Rejected because it is
five times the configuration for a system where only the edge is public, and the
benefit is realised only in a failure mode (a service accidentally exposed) that
deployment hygiene addresses more cheaply.

Worth revisiting if the services are ever deployed independently or if a second
client bypasses the edge.
