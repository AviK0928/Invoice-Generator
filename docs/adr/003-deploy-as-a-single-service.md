# 3. Deployed as a single service, developed as microservices

Date: 2026-09-04
Status: Accepted

## Context

Six Spring Boot services plus Kafka and Postgres — roughly 3–4 GB of RAM and
eight processes.

The hosted demo runs on a free tier: 512 MB and 0.1 CPU per web service, sleeping
after 15 minutes idle, and no managed Kafka at any price.

Two consequences follow. Six services plus a broker needs six paid instances
(~$42/month) and an external broker. And on sleeping instances, a request
arriving cold traverses six independent cold starts.

## Decision

Deploy a single consolidated artifact. Keep the microservice topology as the
development and reference architecture, documented in `docker-compose.yml` and
`docs/deployment.md`.

In the deployed artifact, Kafka is replaced by a transactional outbox
dispatching in-process. Module boundaries and event contracts are unchanged.

## Rationale

The primary reason is not cost. **The workload does not justify distribution.**
Nothing here scales differently from anything else: export is not hit more than
invoicing, import does not deploy on a different cadence, and no module has an
independent availability requirement. Six services exist because the exercise was
to build microservices, not because the domain demanded them.

Given that, paying for six instances and accepting a long cold path to
demonstrate an architecture the workload does not need is the wrong trade. The
same judgement would apply with a real budget.

## Consequences

**The deployed system is not the developed system.** Failures that only appear
across a network boundary will not surface in production.

**Kafka is exercised locally and in CI, not in the hosted demo.** Event contracts
remain real; the broker does not.

**Reversible.** Modules keep their boundaries. Consolidation is an assembly
choice, not a merge — the `app` module depends on the five service jars and adds
nothing to them but wiring.

**The distributed topology has never run on a hosted environment.** It runs under
Docker Compose and is verified there. "Works on my machine" is the honest
description of its deployment status.

## Alternatives considered

*Six paid instances plus a broker (~$42/month).* Faithful. Rejected on the
cold-start argument above; cost is secondary.

*Oracle Cloud Always Free.* Previously 4 OCPU / 24 GB, which would have hosted
the full topology comfortably. Halved to 2 OCPU / 12 GB on 15 June 2026 with no
announcement — the documentation was edited, and instances above the new limit
were terminated from 18 August. RAM would still fit at roughly 5.3 GB; two cores
would not start six JVMs comfortably, being the same CPU budget as the Codespace
where six simultaneous starts pegged both cores.

Rejected primarily on a different ground: a portfolio link should not depend on a
provider that changes terms silently and enforces by termination. A dead link is
worse than a modest one.

*Deploy three of six.* Arbitrary. No principled basis for which three.

## What consolidation actually cost

Recorded after implementation, because the estimate and the reality differed.

**Assembly, not merging, held up.** The `app` module is one POM, one application
class, one publisher and six migrations. No service class changed. The one
build-level change was a repackage classifier, so the service jars are usable as
libraries rather than only as executables.

**Four collisions had to be handled**, none of them deep: duplicate class simple
names across modules (solved with a fully-qualified bean-name generator),
Flyway's default location merging five `V1__baseline.sql` files off the
classpath, five `OpenAPI` beans in one context, and the Kafka configurations
needing exclusion.

**Two behavioural differences surfaced only in production.** A failed login
returned 500 rather than 401, because `BadCredentialsException` thrown from a
controller reached the domain exception advices instead of Spring Security's
reactive handling. And one topic now routes to exactly one consumer rather than
fanning out to consumer groups — true today, and an assumption rather than a
constraint.

**Cold start is ~94 seconds**, against the 30–60 estimated. 0.1 CPU is the
binding constraint, not memory.
