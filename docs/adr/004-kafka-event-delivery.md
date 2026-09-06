# 4. Transactional outbox for event delivery

Date: 2026-09-05
Status: Accepted, extended 2026-09-06

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

## Extension: the transport is an interface

The outbox turned out to be the mechanism that made ADR 003 possible, which was
not why it was built.

`OutboxEventPublisher` is the delivery contract — publish synchronously, throw on
failure. `KafkaOutboxEventPublisher` builds the record and blocks on the send.
`InProcessOutboxEventPublisher`, in the `app` module, routes each topic to the
existing `@KafkaListener` bean and calls it directly. Selection is
`outbox.transport`, defaulting to `kafka`.

Everything above the seam is unchanged: claiming, backoff, retention, the
event-id prefix, the inbox check. The dispatcher does not know which transport it
has.

Two consequences specific to in-process delivery:

**Handlers run in `REQUIRES_NEW`.** `dispatch()` is `@Transactional` and catches
per event so one bad message cannot stall a batch. An inline handler would join
that transaction; its failure would mark it rollback-only, be swallowed by the
catch, and take the whole batch down at `saveAll` with
`UnexpectedRollbackException`. A separate transaction rolls back alone and the
exception reaches the dispatcher, which backs off exactly as it does for a broker
error.

**One topic routes to one consumer.** Kafka delivers a copy per consumer group;
the switch delivers to one destination. No topic has two consumers today, so this
holds — by assumption, not by construction. The same assumption appears a second
time in the consolidated `processed_events` table, which is keyed by the
publishing service's event id and would let a second consumer skip a first
consumer's row.

## Consequences

**Delivery is delayed, not immediate.** The dispatcher polls every 2 seconds, so
that is the best-case latency. Acceptable; nothing here is user-facing.

**Consumers must be idempotent.** A real constraint on future work, not a detail
— a consumer that increments or appends rather than upserting will be wrong under
redelivery.

**A backlog is silent.** The API keeps working perfectly while nothing reaches
downstream. Mitigated by `GET /api/invoices/admin/outbox/status` and an Actuator
health indicator, because otherwise the only way to see it is a psql session.

**Shared code, separate schemas** — in the distributed topology. The outbox lives
in `common`; each service runs its own migration creating its own table. The
distinction that matters is not entity-vs-not: `OutboxEvent` is private plumbing
no other service reads, whereas the event DTOs in `common` are a contract
*between* services where a change alters an agreement.

The consolidated deployment necessarily has one table, since one persistence unit
maps one entity to one table. That is a property of the assembly, not a reversal.

## Not solved

**Idempotent create short-circuits the publish.** `createInvoice` returns the
existing invoice when `contentHash` matches, recording no event. A consumer that
missed the original cannot recover it by retrying the create. The idempotent path
is correct in isolation — it must not duplicate — but "already exists" and
"downstream has it" are different questions and the code conflates them.

**Two pipelines have one end.** `invoice-imported` has a producer and no
consumer; `invoice-delete` a consumer and no producer. Both are artefacts of
earlier cleanups, and both are still open. The in-process publisher discards
`invoice-imported` explicitly rather than letting it fall to the unrouted-topic
error, which is a workaround standing in for a decision.

## Does Kafka earn its place?

The open question from the first draft, now answerable.

**In the deployed system, no.** The artifact runs one process with the outbox
dispatching in-process. Kafka is a local development and CI dependency.

**Kept anyway, and the reason is not sentiment.** The publisher seam means the
Kafka path is not dead code carried for appearance — it is exercised by every
integration test against an embedded broker, and the distributed topology it
supports is runnable with one command. Removing it in favour of
`ApplicationEventPublisher` would delete the reference architecture to simplify a
deployment that already ignores it.

Worth revisiting if the distributed topology is never deployed anywhere and the
tests become its only consumer.
