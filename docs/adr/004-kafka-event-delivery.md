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
