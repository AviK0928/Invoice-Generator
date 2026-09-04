# 2. Automated archive retention removed, not fixed

Date: 2026-09-04
Status: Accepted

## Context

`ArchiveScheduler` ran daily: exported archived invoices older than one month to
a CSV, published `DELETE_INVOICE` events, then deleted the rows.

The export destination was `new File("exports/")` — relative to the working
directory, inside the container. On an ephemeral filesystem it does not survive
a restart. The job therefore exported to nowhere, permanently destroyed the
archive, and instructed invoice-service to delete its copies too. No
transaction, no verification, no durable output.

It had never run: `ArchiveServiceApplication` was missing `@EnableScheduling`.
Adding that annotation — on the reasoning that the scheduler was dead code —
activated it.

## Decision

Delete the scheduler and its export service rather than repair them.

## Consequences

**There is no automated retention, and no automated backup.** The README says
so. Backup and point-in-time recovery are delegated to the managed Postgres
provider, which does it better than a homegrown CSV dump would.

**The archive grows unbounded.** Irrelevant at current data volumes.

## Prerequisites before it returns

| Need | Why |
|---|---|
| Object storage | a destination that survives restart |
| Transactional outbox | dying at event 3 of 50 leaves 3 invoices deleted with no archive |
| Verify-before-delete | confirm the object exists and checksums match |
| Single-instance locking | two replicas both fire `@Scheduled` |

## Return path

Three separate operations, never one:

1. `POST /api/archives/retention/export?before=...` — writes to object storage,
   returns key and row count, **deletes nothing**.
2. Soft delete — `purgedAt` and `exportKey` columns; reads filter
   `purgedAt IS NULL`. Recoverable.
3. Hard delete — only rows already marked purged, past a grace period, whose
   `exportKey` still resolves.

A scheduler wraps this only afterwards, with locking and a flag defaulting off.

**Current recommendation: ship step 1 only.** A purge job's failure mode is
silent permanent data loss, and this system has no retention pressure to justify
that risk.

## Lesson

Dead code is sometimes load-bearing. "This has an annotation missing" is a
description, not a diagnosis.
