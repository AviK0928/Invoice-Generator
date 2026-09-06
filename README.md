# Invoice Generator

A Spring Boot microservices system for customers, invoices, exports, imports and
archiving — six services, event-driven over Kafka, one Postgres database each.

**Deployed as a single consolidated artifact.** That is a decision with reasons,
documented in [ADR 003](docs/adr/003-deploy-as-a-single-service.md), not a
shortcut.

---

## Live demo

### **[Open the API →](https://invoice-generator-e1z8.onrender.com/swagger-ui.html)**

1. `POST /api/auth/login` with `admin` / `<password>`
2. Copy the `accessToken`, click **Authorize**, paste it
3. Every endpoint is usable from the page

**The first request takes about two minutes.** The free instance sleeps after
15 minutes idle and runs on 0.1 CPU, so a cold JVM start measures 94 seconds.
That is the hosting tier, not the application — locally it starts in 11 seconds.

`/actuator/health` is the only unauthenticated endpoint, which makes it the
cheapest way to wake the instance before doing anything else.

Prefer curl:

```bash
APP=https://invoice-generator-e1z8.onrender.com

curl -s $APP/actuator/health          # wakes it; UP when ready

TOKEN=$(curl -s -X POST $APP/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<password>"}' \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -s -H "Authorization: Bearer $TOKEN" "$APP/api/customers?page=0&size=10"
```

Everything under `/api` except login requires a bearer token. There is no signup
— users live in configuration as bcrypt hashes
([ADR 001](docs/adr/001-gateway-owns-authentication.md)).

---

## Architecture

| Module | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Routing and authentication (WebFlux) |
| `customer-service` | 8081 | Customer CRUD; publishes customer events |
| `invoice-service` | 8082 | Invoices, PDF requests, auto-archiving |
| `export-service` | 8083 | CSV/ZIP export, PDF generation from a read model |
| `import-service` | 8084 | CSV import with per-row rollback isolation |
| `archive-service` | 8085 | Archived invoices, unarchive requests |
| `common` | — | Outbox, inbox, event DTOs, error contract, auth |
| `app` | — | Consolidated deployment artifact |

Kafka (KRaft) for events, one Postgres container holding five databases, Flyway
owning every schema.

### What is worth looking at

**Transactional outbox.** Every publishing service records events in the same
transaction as the domain write, and a scheduled dispatcher delivers them. A
broker outage delays events instead of failing writes. Exponential backoff,
abandoned-event requeue via an admin endpoint, and consumers keyed on an event id
for idempotency.

**The publisher is an interface.** `OutboxEventPublisher` has a Kafka
implementation and an in-process one, selected by `outbox.transport`. That single
seam is what makes the same code run as six services or as one.

**Asynchronous PDF generation.** `POST /api/invoices/{id}/pdf` returns 202 and a
request id; export-service renders from its own read model, stores the bytes, and
announces readiness; the download deletes the row. Stale requests time out,
undownloaded documents expire.

**RFC 9457 error contract.** Every error is `application/problem+json` with a
stable `type` URI, verified by a `@WebMvcTest` slice.

**76% instruction coverage** with a per-module JaCoCo gate on merged unit and
integration data. Integration tests use Testcontainers Postgres and an embedded
Kafka broker.

---

## Running it

```bash
cp .env.example .env      # fill in the secrets
docker compose up -d --build
```

Eight containers: five services, the gateway, Kafka and Postgres. See
[docs/deployment.md](docs/deployment.md) for both topologies and
[docs/ENGINEERING-LOG.md](docs/ENGINEERING-LOG.md) for how it got here.

For one service natively against dockerised infrastructure:

```bash
./run.sh invoice-service dev
```

Build and test everything:

```bash
./mvnw -B clean verify
```

---

## Not included, deliberately

- **No metrics or tracing.** The most defensible omission to call out: a system
  built around asynchronous event delivery should expose outbox depth and
  delivery latency. There is a health indicator for pending outbox events and
  nothing more.
- **No email delivery.** The PDF flow ends at a download endpoint. Sending the
  file is a mail provider and a queue, not an architectural question.
- **No Kubernetes manifests.** They would be a claim without a cluster to verify
  against.
- **No frontend.** This is a backend project; Swagger is the interface.
- **Kafka does not run in the hosted demo.** It runs locally and in CI. The event
  contracts are real; the broker is not.
- **No automated retention or backup.** The archive grows unbounded, and
  point-in-time recovery is the database provider's job. The scheduler that used
  to do it exported to an ephemeral directory and then deleted the rows —
  [ADR 002](docs/adr/002-no-automated-retention.md).

---

## Documentation

- [ADR 001](docs/adr/001-gateway-owns-authentication.md) — the gateway owns authentication
- [ADR 002](docs/adr/002-no-automated-retention.md) — no automated retention
- [ADR 003](docs/adr/003-deploy-as-a-single-service.md) — deployed as one service, developed as six
- [ADR 004](docs/adr/004-kafka-event-delivery.md) — Kafka event delivery
- [Deployment](docs/deployment.md) — both topologies, step by step
- [Engineering log](docs/ENGINEERING-LOG.md) — every bug, dead end and decision, with reasoning
