# 3. Deployed as a single service, developed as microservices

Date: 2026-09-04
Status: Proposed

## Context

Six Spring Boot services plus Kafka and Postgres. Roughly 3–4 GB of RAM.

The deployment target's free tier provides 512 MB and 0.1 CPU per web service,
no managed Kafka at any price, and private services only on paid plans. Free
instances sleep after 15 minutes idle with a 30–60 second cold start.

Six services chained behind a gateway means six independent cold starts. A
request arriving cold can take 90+ seconds.

## Decision

Keep the microservice topology for local development — it is what the compose
file and the Kubernetes manifests describe. Deploy a single consolidated
artifact, with Kafka replaced by a transactional outbox dispatching in-process.

## Consequences

**The deployed system is not the developed system.** Integration issues that
only appear across a network boundary will not be caught by the deployment.

**Kafka is exercised locally and in CI, not in production.** The event contracts
are still real, but the broker is not.

**Reversible.** The modules keep their boundaries; consolidation is an assembly
choice, not a merge.

## Rationale

The workload does not justify distributed operational cost. Six services exist
because the exercise was to build microservices, not because the domain demands
independent scaling or deployment — nothing here scales differently from
anything else.

Choosing not to distribute is the same judgement that would apply at work with a
real budget: the cost is paid in cold starts, memory, and operational surface,
and the benefit at this scale is zero.
