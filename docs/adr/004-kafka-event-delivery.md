# 4. Transactional outbox for event delivery

Date: TBD
Status: Proposed

## Context

Three incidents recorded in the engineering log:

1. **Kafka down means invoice creation fails.** `producer.publish()` throws
   inside `@Transactional`, rolling back the entire write. A messaging outage
   takes down a core write path.
2. **Events published with no consumer are lost.** An invoice created while
   downstream services were stopped never reached them and never will. There is
   no reconciliation path — the same invoice is modelled in four databases with
   nothing that can rebuild one from another.
3. **A failed event is silently discarded.** After a validation failure the
   consumer group committed the offset and moved on. No dead-letter topic, no
   record it failed, no way to replay.

Compounding this: idempotent create short-circuits *before* publishing, so
retrying the create does not re-emit the event.

## Decision

To be recorded after Phase 3.

## Options under consideration

- Transactional outbox with a scheduled dispatcher
- `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`
- Consumer-side idempotency keyed on `invoiceId` + `contentHash`
- Whether Kafka earns its place at all, given ADR 003 removes it from the
  deployed system

LATEST OVERRIDES ANYTHING THAT CONFLICTS WITH ABOVE

# 4. Transactional outbox for event delivery

Date: 2026-09-05
Status: Accepted

## Context

Three incidents, all recorded in the engineering log:

1. **Kafka down meant invoice creation failed.** `producer.publish()` threw
   inside `@Transactional`, rolling back the write. A messaging outage took down
   a core write path.
2. **Events published with no consumer were lost.** An invoice created while
   downstream services were stopped never reached them and never will. The same
   invoice is modelled in four databases with nothing able to rebuild one from
   another.
3. **A failed event was silently discarded.** After a validation failure the
   consumer group committed the offset and moved on — no dead-letter topic, no
   record it failed, no replay.

One root cause: the domain write and the event publish were two operations with
no atomicity between them.

## Decision

**Transactional outbox.** `OutboxWriter.record()` is called inside the same
transaction as the domain write. A scheduled dispatcher polls and owns delivery.

**At-least-once, with idempotent consumers.** A publish that succeeds but whose
mark-published fails will republish. Each consuming service keeps a
`processed_events` inbox keyed on an `X-Event-Id` header, checked and written in
the same transaction as the projection.

**Dead letters.** `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`:
five retries at exponential backoff, then `{topic}-dlt` carrying the original
payload, source topic, consumer group and stack trace.

## Consequences

**Delivery is delayed, not immediate.** The dispatcher polls every 2 seconds, so
that is the best-case latency. Acceptable; nothing here is user-facing.

**Consumers must be idempotent.** This is a real constraint on future work, not
a detail — a consumer that increments or appends rather than upserting will be
wrong under redelivery.

**A backlog is silent.** The API keeps working perfectly while nothing reaches
downstream. Mitigated by `GET /api/invoices/admin/outbox/status` and an
Actuator health indicator, because otherwise the only way to see it is a psql
session.

**Shared code, separate schemas.** The outbox lives in `common`; each service
runs its own migration creating its own table. This reverses an earlier decision
not to share the inbox entity. The distinction that matters is not
entity-vs-not: `OutboxEvent` is private plumbing no other service reads, whereas
the event DTOs in `common` are a contract *between* services where a change
alters an agreement.

## Not solved

**Idempotent create short-circuits the publish.** `createInvoice` returns the
existing invoice when `contentHash` matches, recording no event. A consumer that
missed the original cannot recover it by retrying the create. The idempotent
path is correct in isolation — it must not duplicate — but "already exists" and
"downstream has it" are different questions and the code conflates them.

**Two pipelines have one end.** `invoice-imported` has a producer and no
consumer; `invoice-delete` a consumer and no producer. Both are artefacts of
earlier cleanups. Wire up or delete.

## Does Kafka earn its place?

Open question, and ADR 003 sharpens it: the deployed artifact runs a single
process with the outbox dispatching in-process, so **Kafka is a local
development dependency and nothing more**.

The honest positions are either to keep it — the event contracts are real, the
topology is the reference architecture, and it demonstrates the pattern — or to
remove it and use Spring's `ApplicationEventPublisher`, which is what actually
runs in production.

Kept for now, deliberately: removing it would make the local environment
diverge from the documented architecture for no benefit the deployment can
realise. Worth revisiting if the distributed topology is never deployed.
