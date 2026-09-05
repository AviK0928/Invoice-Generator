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

# 3. Deployed as a single service, developed as microservices

Date: 2026-09-04
Status: Accepted

## Context

Six Spring Boot services plus Kafka and Postgres — roughly 3–4 GB of RAM and
eight processes.

The hosted demo runs on Render's free tier: 512 MB and 0.1 CPU per web service,
sleeping after 15 minutes idle with a 30–60 second cold start, and no managed
Kafka at any price.

Two consequences follow. Six services plus a broker requires six paid instances
(~$42/month) and an external broker. And on sleeping instances, a request
arriving cold traverses six independent cold starts — 90+ seconds before the
first response.

## Decision

Deploy a single consolidated artifact. Keep the microservice topology as the
development and reference architecture, documented in `docker-compose.yml`,
`k8s/`, and `docs/DEPLOYMENT.md`.

In the deployed artifact, Kafka is replaced by a transactional outbox
dispatching in-process. The module boundaries and event contracts are unchanged.

## Rationale

The primary reason is not cost. **The workload does not justify distribution.**
Nothing here scales differently from anything else: export is not hit more than
invoicing, import does not deploy on a different cadence, and no module has an
independent availability requirement. Six services exist because the exercise
was to build microservices, not because the domain demanded them.

Given that, paying for six instances and accepting a 90-second cold path to
demonstrate an architecture the workload does not need is the wrong trade. The
same judgement would apply with a real budget.

## Consequences

**The deployed system is not the developed system.** Failures that only appear
across a network boundary will not surface in production.

**Kafka is exercised locally and in CI, not in the hosted demo.** Event
contracts remain real; the broker does not.

**Reversible.** Modules keep their boundaries. Consolidation is an assembly
choice, not a merge — `docs/DEPLOYMENT.md` documents the distributed path and it
remains runnable.

## Alternatives considered

*Six paid Render services (~$42/month plus a broker).* Faithful. Rejected on the
cold-start argument above; cost is secondary.

*Oracle Cloud Always Free.* Was 4 OCPU / 24 GB, **halved to 2 OCPU / 12 GB on
15 June 2026** with no announcement — documentation was edited and
over-limit instances terminated from 18 August. RAM would still fit; 2 cores
would not start six JVMs comfortably. Rejected primarily because a portfolio
link should not depend on a provider that changes terms silently and enforces by
termination.

*Deploy three of six.* Arbitrary. No principled basis for which three.

LATEST OVERRIDES ANYTHING THAT IS CONFLICTORY ABOVE

*Oracle Cloud Always Free.* Previously 4 OCPU / 24 GB, which would have hosted
the full six-service topology comfortably. **Halved to 2 OCPU / 12 GB on
15 June 2026** with no announcement — the documentation was edited, and
instances above the new limit were terminated from 18 August. RAM would still
fit at roughly 5.3 GB; two cores would not start six JVMs comfortably, being the
same CPU budget as the Codespace where six simultaneous starts pegged both cores.

Rejected primarily on a different ground: a portfolio link should not depend on
a provider that changes terms silently and enforces by termination. A dead link
is worse than a modest one.

And add to Consequences:


**The distributed topology has never run on a hosted environment.** It runs
locally under Docker Compose and is verified there, but "works on my machine"
is the honest description of its deployment status. `docs/DEPLOYMENT.md`
documents the path; it has not been walked.
