# Engineering Log

A running record of what was broken, why, and what was done about it. Kept
because the *reasoning* is the part that gets lost — the diffs are in git.

Commit this as `docs/ENGINEERING-LOG.md`. Append as you go; do not rewrite
history. When something surprises you, write it down while it's fresh.

---

## Starting point

Six Spring Boot services (`api-gateway`, `customer-service`, `invoice-service`,
`export-service`, `import-service`, `archive-service`) plus a shared `common`
library. Kafka for inter-service events, Postgres per service. ~3,300 lines of
Java.

The honest assessment: **it did not build from a clean clone, and no service had
ever started successfully.** Not "had bugs" — had never run. Everything below
follows from that.

---

## Phase 0a — Environment

### Constraint that shaped everything

8 GB Windows laptop, no WSL, IntelliJ too heavy. This is not a footnote — it
determined the whole workflow:

| Tier | Runs | Why |
|---|---|---|
| Laptop | VS Code as thin client, git | 8 GB can't host Docker + 6 JVMs |
| Codespace (2-core, 8 GB) | Docker Compose, integration checks | 120 free core-hours/mo = 60 real hours |
| GitHub Actions | Full builds, image builds | Free and unlimited for public repos |

**Deliberately did not install Docker Desktop.** On Windows it runs a WSL2 VM
that takes 2–4 GB before the app starts. On 8 GB that's the difference between
slow and unusable.

### Discovery: Windows Defender was disabled by a ghost

`Add-MpPreference` failed with `0x800106ba` (service not running).
`Set-Service WinDefend` returned "Access is denied" *as Administrator*.

Root cause: Quick Heal Total Security had been uninstalled by deleting its
folder. Its Group Policy registry keys survived:

```
HKLM:\SOFTWARE\Policies\Microsoft\Windows Defender
    DisableAntiSpyware = 1
HKLM:\SOFTWARE\Policies\Microsoft\Windows Defender\Real-Time Protection
    DisableRealtimeMonitoring = 1  (and four siblings)
```

Windows Security showed "managed by your organisation" on a personal machine.
Deleting the policy key + reboot restored Defender.

**Lesson:** "Access is denied" as Administrator means policy or Tamper
Protection, not permissions. Check `HKLM:\SOFTWARE\Policies\` first.

**Lesson 2:** this ate seven exchanges for a build-cache optimisation that
blocked nothing. Timebox side quests.

### Discovery: `devcontainer.json` location is not optional

Placed at repo root instead of `.devcontainer/`, GitHub silently ignores it and
builds a default container. Things appeared to work (the base image happened to
include Docker), but none of the declared features or extensions were applied.

### Discovery: the browser is not the only Codespaces client

The Codespaces web editor rendered as unstyled black-on-white HTML — CSS and JS
blocked. Connecting local VS Code via the **GitHub Codespaces** extension
sidesteps it entirely and is lighter than a browser tab running an IDE.

### Discovery: `git-lfs` hook without `git-lfs` binary

Codespaces installs a Git LFS pre-push hook by default. The image had no
`git-lfs` binary, so every `git push` failed. Fixed by adding the
`ghcr.io/devcontainers/features/git-lfs:1` devcontainer feature — `apt-get
install` does not survive a container rebuild.

### Non-discovery worth recording

Expected a large CRLF→LF diff. `git add --renormalize .` produced **nothing**.
The repo already stored LF; Windows' `autocrlf=true` was converting on checkout
only. `.gitattributes` was still added — it guarantees LF regardless of any
contributor's local setting, and pins `mvnw` and `Dockerfile` to LF so they
can't break inside a Linux container.

---

## Phase 0b — Build system

### Root cause: no aggregator POM

`common` was a standalone artifact at version `1.5.0` that all five services
depended on. **Nothing in the repo built it.** It had only ever worked because
`mvn install` was run on it manually in an IDE.

Fixed with a root `pom.xml` listing all seven modules. Maven's reactor sorts by
inter-module dependency, so `common` builds first.

Deliberately an **aggregator only**, not a `<parent>`: each child keeps
`spring-boot-starter-parent` untouched, so the change cannot break anything that
currently works. Promoting it to real parent (which fixes the Boot 3.2.5 vs
3.5.3 drift) is a separate, later change — never debug two things at once.

### Root cause: Dockerfiles copied a jar that doesn't exist

All six were `COPY target/<service>-0.0.1-SNAPSHOT.jar app.jar`. There is no
`target/` in a fresh clone. The README's `docker compose up --build` was
fiction, and Render — which clones and builds from source — could never have
built this.

Replaced with one root multi-stage Dockerfile parameterised by a `SERVICE` build
arg. Decisions worth keeping:

- **`jammy`, not `alpine`.** Alpine uses musl and ships no fontconfig/freetype.
  PDF generation throws on first font load. 40 MB for a production-only bug
  avoided.
- **`jre`, not `jdk`.** ~150 MB smaller; no compiler needed at runtime.
- **Non-root user.** First thing a reviewer checks.
- **`-XX:MaxRAMPercentage=70`.** Without it the JVM sizes its heap from the
  *host's* RAM, not the container's cgroup limit, and gets OOM-killed on a
  512 MB Render instance.
- **POMs copied before sources.** Splits the dependency layer from the source
  layer. Measured: full build 53s, source-only rebuild 24s.

### Gotcha: the executable bit

`git update-index --chmod=+x mvnw` is required. Git on Windows doesn't record
the exec bit, and Linux (CI, Docker, Codespace) then refuses to run the script.
Symptom: `./mvnw: Permission denied`.

Also: `git update-index --chmod` only works on files Git already tracks *at that
path*. After `mv invoice-service/mvnw .` you must `git add -A` first.

---

## Phase 0c — Configuration

### Root cause: nothing could resolve its own config

Every `application.yml` had bare `${DB_URL}` with no default, and the compose
file defined **zero** `environment:` blocks for application services. All six
containers failed on placeholder resolution at boot. This is why "it doesn't
work" was the whole story rather than a symptom.

### Root cause: `localhost:9092` hardcoded in six places

Across five config classes. Fine on a dev laptop, impossible in Docker.
Externalised to `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` read via `@Value`.

### Discovery: three `@KafkaListener`s referenced beans that don't exist

| Listener | Referenced | Actual bean |
|---|---|---|
| `InvoiceDeletionConsumer` | `invoiceEventKafkaListenerContainerFactory` | `invoiceDeletionKafkaListenerFactory` |
| `UnarchiveConsumer` | `archiveEventKafkaListenerContainerFactory` | `archiveResponseKafkaListenerFactory` |
| `CustomerEventConsumer` | `kafkaListenerContainerFactory` | `customerEventKafkaListenerFactory` |

The first two throw `NoSuchBeanDefinitionException` at startup. The third is
worse — it *silently* falls back to Spring Boot's auto-configured factory using
`StringDeserializer`, so it would fail to deserialize at runtime rather than
failing fast at boot.

export-service's `InvoiceEventConsumer` had the same silent-fallback problem via
a missing `containerFactory` attribute entirely.

**Lesson:** a missing `containerFactory` is not a compile error and not always a
startup error. Naming a factory that doesn't exist is *safer* than omitting one.

### Discovery: export-service was running on H2 in "production"

Log line: `Database version: 2.3.232`. That's H2, not Postgres 16. Its
`application.yml` had no datasource block, H2 was on the classpath as a
`runtime` dependency, and Spring Boot silently auto-configured an in-memory
database.

**Removed H2 from all POMs.** Shipping H2 to production is exactly how this
happens, and it fails silently rather than loudly.

### Root cause: gateway crashed on `ServerHttpSecurity`

```
Parameter 0 of method springSecurityFilterChain required a bean of type
'org.springframework.security.config.web.server.ServerHttpSecurity'
```

`api-gateway` had **both** `spring-boot-starter-web` and
`spring-cloud-starter-gateway`. `ServerHttpSecurity` is the *reactive* security
type; with `starter-web` on the classpath, servlet security auto-config wins and
that bean is never created. `spring.main.web-application-type: reactive` forces
the server to be reactive but does **not** undo the security auto-config choice.

Removed `spring-boot-starter-web`. Gateway brings in WebFlux itself.

### Discovery: routes were defined twice, differently

`GatewayRoutesConfig.java` defined a `RouteLocator` bean *and* `application.yml`
defined routes — with **different paths** (`/api/export/**` vs
`/api/exports/**`). Both got registered. Deleted the Java config; YAML is the
single source of truth.

### Discovery: Kafka `NOT_COORDINATOR` retry storm

On startup, six consumer groups joining at once produced hundreds of:

```
rebalance failed due to 'This is not the correct coordinator'
Group coordinator kafka:9092 is unavailable or invalid ... NOT_COORDINATOR
```

**Cause:** Kafka's `__consumer_offsets` topic defaults to **50 partitions**,
sized for a real cluster. On a 2-core box with six groups arriving
simultaneously, coordinator election thrashes and consumers spin.

**Fix:** `KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS: 3`, plus `KAFKA_NUM_PARTITIONS: 1`
and `KAFKA_DEFAULT_REPLICATION_FACTOR: 1`.

**Why the force-recreate:** Kafka in KRaft mode stores cluster metadata
(including the offsets topic layout) inside the container. Partition count is
fixed at topic creation and cannot be reduced afterwards. Changing the env var
alone does nothing to an already-formatted cluster — you need
`docker compose down` (or `--force-recreate`) so the broker re-bootstraps from
scratch. Same reasoning applies to `db/init-databases.sql`: Postgres only runs
`/docker-entrypoint-initdb.d/*` on **first volume creation**, so editing it
requires `docker compose down -v`.

**General rule:** stateful containers ignore config changes that only apply at
initialisation. If a change to broker or database bootstrap config seems to have
no effect, the state is stale, not the config.

### Discovery: `kafka:9092` doesn't resolve outside Docker

Running a service natively via `mvnw` (to save CPU) failed with `Send failed`
even though the Kafka container was healthy. A broker advertises **one address
per listener**, and `kafka` is a compose-network DNS name.

**Fix:** dual listener.

```yaml
KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:29092
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:29092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
```

Containers use `kafka:9092`; host processes use `localhost:29092`. Note this
also required a force-recreate, for the reason above.

### Discovery: `mvnw` does not read `.env`

Docker Compose injects `.env`; Maven does not. Running natively gave
`password authentication failed for user "invoice"` because `application.yml`
fell back to its default password while the container had been created with the
one from `.env`. Fix: `set -a; source .env; set +a` before `mvnw`.

### Discovery: devtools in production images

`DevToolsPropertyDefaultsPostProcessor : Devtools property defaults active!`
and a `restartedMain` thread in the container logs. `spring-boot-devtools` ships
a file watcher and restart machinery into the production image and *silently
overrides properties* — including turning error stack traces back on. Removed
from all service POMs.

### Discovery: 400 responses leaked a 60-frame stack trace

A malformed enum value produced a response containing the full filter chain,
package structure, and framework versions. Cause: Spring's default
`server.error.include-stacktrace: on_param`, amplified by devtools.

Interim fix (real fix is `@RestControllerAdvice` in Phase 2):

```yaml
server:
  error:
    include-stacktrace: never
    include-message: always      # deliberately ON — "Bad Request" alone is useless
    include-binding-errors: never
    include-exception: false
```

---

## Phase 1 — Correctness

### Root cause: every invoice creation threw a constraint violation

`Invoice.contentHash` is `@Column(nullable = false)`. `InvoiceMapper.toEntity()`
built the entity **without it**. Every `POST /api/invoices` hit a NOT NULL
violation. The field was clearly intended for deduplication — import-service and
export-service both compute hashes via `HashUtil` — but invoice-service never
wired it up.

Fixed with `InvoiceContentHasher`, and used it for what it was for: idempotent
creation.

Two subtleties in the hashing:

- **Items are sorted before joining.** Same invoice, items in a different order,
  must produce the same hash.
- **`BigDecimal.stripTrailingZeros().toPlainString()`.** `10.00` and `10.0` are
  the same amount but different strings, and would otherwise hash differently.

Deliberately **excluded `paymentStatus` and `createdAt`** from the hash: those
are mutable state and server-assigned time, not content.

### Discovery: Kafka is a hard dependency of invoice creation

With Postgres up but Kafka down, `POST /api/invoices` returned
`500 "Send failed"` and **no invoice was saved** — `producer.publish()` throws
inside `@Transactional`, rolling everything back.

This is the dual-write problem, encountered live rather than read about:

- Kafka down → creation fails entirely. A messaging outage takes down a
  core write path.
- Worse inverse: DB commit succeeds, Kafka publish then fails → **the event is
  lost forever with no record it should have been sent.**

This is why the transactional outbox is on the plan. Write to the domain table
and an `outbox_event` table in one local transaction; a scheduled dispatcher
publishes and marks sent. Also explains the observed 60s hang — Kafka's
`max.block.ms` default while the producer waits for cluster metadata.

### Fixed alongside

- `jakarta.transaction.Transactional` → `org.springframework.transaction.annotation.Transactional`.
  The Jakarta annotation gives no rollback rules, isolation, or propagation control.
- `findById(...).orElseThrow()` → `existsById()` for a customer that was loaded
  into a variable and never used.

### Modelling gap noticed

`PaymentStatus` has only `SUCCESSFUL` and `FAILED`. An invoicing system with no
way to express *unpaid* or *overdue* cannot represent the normal state of an
invoice. Add `PENDING` and `OVERDUE` before building
`PATCH /api/invoices/{id}/status`, which is otherwise close to pointless.

---

## Standing rules learned the hard way

1. **Change one thing at a time.** Aggregator-before-parent, config-before-code.
   Two simultaneous changes means two unknowns when it breaks.
2. **Silent fallbacks are worse than crashes.** H2 auto-config and the missing
   `containerFactory` both "worked" while being wrong.
3. **Stateful containers cache their bootstrap config.** Kafka cluster metadata
   and Postgres init scripts both need a recreate to pick up changes.
4. **Verify the smallest thing that answers the question.** `mvn install` (40s,
   one JVM) catches most mistakes. `docker compose up postgres kafka` plus one
   native service covers most of the rest. The eight-container stack is for
   phase-end integration checks, not the working loop.
5. **A healthy container is not a working service.** `/actuator/health` returning
   UP proved nothing about whether an invoice could be created.

### Discovery: `@Data` on JPA entities is a latent stack overflow

`Invoice` has `@OneToMany List<InvoiceItem> items`; `InvoiceItem` has
`@ManyToOne Invoice invoice`. Lombok's `@Data` generates `toString`, `equals`
and `hashCode` across all fields, so `invoice.toString()` walks its items, each
item walks back to its invoice, and the stack blows.

It had never fired only because nothing logged an entity. One `log.debug("{}",
invoice)` would have done it. Replaced with `@Getter @Setter` on `Invoice`,
`InvoiceItem` and `LocalCustomer`.

**Lesson:** `@Data` is fine on DTOs and wrong on entities with bidirectional
relationships. The failure mode is a crash in whatever code first tries to log.

### Root cause: the API had no way to read anything back

`InvoiceController` had exactly two endpoints — create and archive. No get by
id, no list. `deleteInvoiceById` existed in the service with no route reaching
it. `CustomerController` was the same shape: create and delete, no reads.

An API you can write to but not read from cannot be demonstrated. Added:

- `GET /api/invoices` — paged, filterable by customer, status, archived flag and
  date range
- `GET /api/invoices/{id}`
- `DELETE /api/invoices/{id}` — routed the orphaned service method

Three implementation notes worth keeping:

- **`@EntityGraph(attributePaths = "items")` on the single fetch.** Without it,
  loading an invoice and then its items is two queries; a list of twenty is
  twenty-one. Classic N+1.
- **`CAST(:fromDate AS date) IS NULL`** in the search query. Postgres cannot
  infer the type of a null bind parameter and throws `could not determine data
  type` without the cast. The `:param IS NULL OR ...` pattern lets one method
  serve every combination of optional filters.
- **`@Transactional(readOnly = true)`** on queries. Hibernate skips
  dirty-checking and the driver can use a read-only transaction. Free.

Create now returns **201 with a `Location` header** rather than 200. Noticed
during the earlier customer test that `POST /api/customers` returned the request
DTO with no id at all — the client had no way to learn what it had just made.

### Discovery: `IllegalArgumentException` is too generic to map

`createInvoice` threw `IllegalArgumentException` for an unknown customer. Half
the JDK throws that, so a `@ExceptionHandler` for it would catch unrelated
failures and mislabel them as client errors. Replaced with a domain-specific
`InvalidCustomerException`.

**Lesson:** exceptions you intend to map to HTTP statuses must be types you own.

### Error contract: RFC 9457 Problem Details

Replaced Spring's default error page with `@RestControllerAdvice` returning
`ProblemDetail` (built into Spring 6, no dependency).

| Exception | Status | Reasoning |
|---|---|---|
| `InvoiceNotFoundException` | 404 | |
| `InvalidCustomerException` | **422** | JSON parsed and every field was well-formed; the request was semantically wrong, not malformed |
| `MethodArgumentNotValidException` | 400 | plus a per-field `errors` map |
| `HttpMessageNotReadableException` | 400 | bad enum values, type mismatches |
| `DataIntegrityViolationException` | **409** | safety net for the `contentHash` unique index if two identical creates race past the pre-check |
| `Exception` | 500 | full detail logged server-side, bland message returned |

The 409 case is the interesting one. `createInvoice` checks
`findByContentHash` before inserting, but that check and the insert are not
atomic under concurrency. The unique index is the real guarantee; the handler
turns the resulting constraint violation into a sensible status instead of a
500.

### Rule added: verify the smallest thing that answers the question

Four tiers, and the first two are where the work happens:

| Tier | Command | Cost | Answers |
|---|---|---|---|
| Compile | `./mvnw -pl <svc> -am install -DskipTests` | 40s, 1 JVM | most mistakes |
| One service | `./run.sh <svc>` | 3 containers | endpoints, persistence, publishing |
| Two services | two `./run.sh` terminals | 4 containers | one event crossing a boundary |
| Full stack | `docker compose up -d` | 8 containers, 90s | phase-end integration only |

Running the full stack "just to be safe" costs 90 seconds of startup, pegs both
cores, burns Codespace hours, and tells you nothing one service wouldn't have.
When it does break you get eight containers of logs instead of one.

`run.sh` and `seed.sh` exist to make tier 2 a single command — env loaded from
`.env`, infra waited on, `KAFKA_BOOTSTRAP_SERVERS` pointed at the host listener.


### Discovery: `@Data` on JPA entities is a latent stack overflow

`Invoice` has `@OneToMany List<InvoiceItem> items`; `InvoiceItem` has
`@ManyToOne Invoice invoice`. Lombok's `@Data` generates `toString`, `equals`
and `hashCode` across all fields, so `invoice.toString()` walks its items, each
item walks back to its invoice, and the stack blows.

It had never fired only because nothing logged an entity. One
`log.debug("{}", invoice)` would have done it. Replaced with `@Getter @Setter`
on `Invoice`, `InvoiceItem` and `LocalCustomer`.

**Lesson:** `@Data` is fine on DTOs and wrong on entities with bidirectional
relationships. The failure mode is a crash in whatever code first tries to log.

---

### Root cause: the API had no way to read anything back

`InvoiceController` had exactly two endpoints — create and archive. No get by
id, no list. `deleteInvoiceById` existed in the service with no route reaching
it. `CustomerController` was the same shape: create and delete, no reads.

An API you can write to but not read from cannot be demonstrated. Added:

- `GET /api/invoices` — paged, filterable by customer, status, archived flag and
  date range
- `GET /api/invoices/{id}`
- `DELETE /api/invoices/{id}` — routed the orphaned service method

Three implementation notes worth keeping:

- **`@EntityGraph(attributePaths = "items")` on the single fetch.** Without it,
  loading an invoice and then its items is two queries; a list of twenty is
  twenty-one. Classic N+1.
- **`CAST(:fromDate AS date) IS NULL`** in the search query. Postgres cannot
  infer the type of a null bind parameter and throws `could not determine data
  type` without the cast. The `:param IS NULL OR ...` pattern lets one method
  serve every combination of optional filters.
- **`@Transactional(readOnly = true)`** on queries. Hibernate skips
  dirty-checking and the driver can use a read-only transaction. Free.

Create now returns **201 with a `Location` header** rather than 200. Noticed
during the earlier customer test that `POST /api/customers` returned the request
DTO with no id at all — the client had no way to learn what it had just made.

---

### Discovery: `IllegalArgumentException` is too generic to map

`createInvoice` threw `IllegalArgumentException` for an unknown customer. Half
the JDK throws that, so a `@ExceptionHandler` for it would catch unrelated
failures and mislabel them as client errors. Replaced with a domain-specific
`InvalidCustomerException`.

**Lesson:** exceptions you intend to map to HTTP statuses must be types you own.

---

### Error contract: RFC 9457 Problem Details

Replaced Spring's default error page with `@RestControllerAdvice` returning
`ProblemDetail` (built into Spring 6, no dependency).

| Exception | Status | Reasoning |
|---|---|---|
| `InvoiceNotFoundException` | 404 | |
| `InvalidCustomerException` | **422** | JSON parsed and every field was well-formed; the request was semantically wrong, not malformed |
| `MethodArgumentNotValidException` | 400 | plus a per-field `errors` map |
| `HttpMessageNotReadableException` | 400 | bad enum values, type mismatches |
| `DataIntegrityViolationException` | **409** | safety net for the `contentHash` unique index if two identical creates race past the pre-check |
| `Exception` | 500 | full detail logged server-side, bland message returned |

The 409 case is the interesting one. `createInvoice` checks `findByContentHash`
before inserting, but that check and the insert are not atomic under
concurrency. The unique index is the real guarantee; the handler turns the
resulting constraint violation into a sensible status instead of a 500.

Before: a bad enum value returned a 60-frame stack trace exposing the filter
chain, package structure and framework versions. After: a four-field JSON body
with no internals.

---

### Discovery: three of five controllers were unreachable through the gateway

`ImportController` was at `/import`, `ExportController` at `/api/export`, and
`ArchiveController` at `/api/archive` — against gateway routes `/api/imports`,
`/api/exports` and `/api/archives`. No `StripPrefix` filter is applied, so the
gateway forwards the path verbatim and the service 404s.

None of this is a compile error, and each service works fine when called
directly on its own port. Only requests through the gateway fail — which is the
only way a real client would call it.

```bash
grep -rn "@RequestMapping" --include=*Controller.java .
```

Run that against the gateway's route predicates whenever either side changes.
It caught the third mismatch (`/api/archive`) that had been missed by eye.

**Diagnosing a 404 through a gateway:** an empty body with no security headers
means the gateway found no route. A body, or Spring Security's headers
(`X-Frame-Options`, `X-Content-Type-Options`), means the request reached the
service and the service returned the 404. Confirm by calling the service
directly over the compose network:

```bash
docker compose exec api-gateway curl -s -o /dev/null -w "%{http_code}\n" \
  http://export-service:8083/api/exports/invoice/9
```

---

### Discovery: `ArchiveScheduler` had never run

`ArchiveServiceApplication` was missing `@EnableScheduling`, so
`purgeArchivedInvoices()` was dead code. invoice-service and export-service both
have it, which is why the omission went unnoticed — the annotation is per
application class, not global.

---

### Discovery: the import pipeline was switched off

`ImportService` had `//eventProducer.publish(event)` commented out. CSV import
parsed the file and saved to `importdb`, then stopped. Imported invoices never
reached invoice-service — which is the entire purpose of the service.

---

### Discovery: events published with no consumer running are lost

Invoice 9 was created while invoice-service ran natively (host listener
`localhost:29092`) with the rest of the stack down. `exportdb` and `archivedb`
have no record of it and never will — export-service's consumer group had no
committed offset at the time, and `auto-offset-reset: earliest` only helps if
the broker still holds the message. The Kafka container has no volume, so
`docker compose down` discards the log entirely.

Not a bug in itself — this is what eventual consistency looks like when delivery
gaps happen. But it exposes the real problem: **the same invoice is modelled
four times in four databases, synced only by Kafka, with no reconciliation
path.** Once they diverge, nothing brings them back. There is no "rebuild
export-service's view from invoice-service" operation.

This is the argument for the consolidation in Phase 5, not just the outbox in
Phase 3. An outbox guarantees the event is *published*; it does nothing about
four copies of the same data drifting apart.

---

### Rule added: verify the smallest thing that answers the question

Four tiers, and the first two are where the work happens:

| Tier | Command | Cost | Answers |
|---|---|---|---|
| Compile | `./mvnw -pl <svc> -am install -DskipTests` | 40s, 1 JVM | most mistakes |
| One service | `./run.sh <svc>` | 3 containers | endpoints, persistence, publishing |
| Two services | two `./run.sh` terminals | 4 containers | one event crossing a boundary |
| Full stack | `docker compose up -d` | 8 containers, 90s | phase-end integration only |

Running the full stack "just to be safe" costs 90 seconds of startup, pegs both
cores, burns Codespace hours, and tells you nothing one service wouldn't have.
When it does break you get eight containers of logs instead of one.

The exception is any change that crosses a service boundary — gateway routes,
event contracts, shared DTOs. A single-service test structurally cannot catch
those, which is exactly how three broken controller mappings survived.

`run.sh` and `seed.sh` exist to make tier 2 a single command: env loaded from
`.env`, infra waited on, `KAFKA_BOOTSTRAP_SERVERS` pointed at the host listener.

---

### Minor: table naming is inconsistent across services

`invoices` / `local_customers` (plural) in invoice-service, `export_invoice` /
`export_customer` (singular) in export-service, `archived_invoices` (plural) in
archive-service. Harmless but sloppy. Settle on plural when Flyway replaces
`ddl-auto` in Phase 2 — the schema gets written explicitly there anyway, so it
costs nothing then and would cost a rename migration now.

---

## Phase 1 status

**Done**

- `contentHash` populated; creation is idempotent
- Read endpoints with pagination, filtering, and an entity graph
- `@Getter`/`@Setter` replacing `@Data` on entities
- RFC 9457 error contract across the invoice API
- `@EnableScheduling` on archive-service
- All five controller paths aligned with gateway routes
- Import event publish re-enabled

**Remaining**

1. **Filesystem writes.** export-service and `BackupScheduler` write to
   `java.io.tmpdir` and `new File("backups")`. Render's disk is ephemeral and
   restarts frequently, so both silently lose data. Needs
   `StreamingResponseBody`. This is the last thing that breaks outright on
   deployment.
2. **The other four services** still have the shape invoice-service started
   with:

   | Service | Endpoints | Missing |
   |---|---|---|
   | customer | POST, DELETE | all reads, error handling |
   | export | 2 GETs | error handling, streaming |
   | import | 1 POST | reads, error handling, upload validation |
   | archive | POST, GET check | list/search, error handling |

   `GET /api/customers/1` returns 405 — there is no GET mapping at all.
3. **`ImportController` returns 500 for a missing file.** It catches everything
   indiscriminately and reports every failure as a server error. Should be 400.
4. **`PaymentStatus` has only `SUCCESSFUL` and `FAILED`.** An invoicing system
   with no way to express *unpaid* or *overdue* cannot represent the normal
   state of an invoice. Add `PENDING` and `OVERDUE` before building
   `PATCH /api/invoices/{id}/status`.
5. **Unbounded CSV parsing** in `ImportService` — no file size cap, no MIME
   check, `.getRecords()` reads the whole file into memory.
