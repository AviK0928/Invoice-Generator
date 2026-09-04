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
