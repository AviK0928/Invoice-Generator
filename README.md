# Invoice Generator

A Spring Boot microservices system for customers, invoices, exports, imports and
archiving — six services, event-driven over Kafka, one Postgres database each.

**Deployed as a single consolidated artifact.** That is a decision with reasons,
documented in [ADR 003](docs/adr/003-deploy-as-a-single-service.md), not a
shortcut.

---

## Live demo

### **[Open the API →](https://invoice-generator-e1z8.onrender.com/swagger-ui.html)**

Every endpoint under `/api` requires a bearer token except the login itself. You
get one by logging in with the demo account below — there is no signup, and the
token is valid for 60 minutes.

**Demo account**

| Username | Password |
|---|---|
| `user` | `pO?MW4pr#n` |

### Using it from Swagger

1. **Wake the instance first.** Open
   [`/actuator/health`](https://invoice-generator-e1z8.onrender.com/actuator/health)
   and wait for `{"status":"UP"}`. This takes **up to two minutes** on a cold
   start — see below.
2. Find **`POST /api/auth/login`** on the page and click **Try it out**.
3. Replace the request body with:
```json
   {"username":"user","password":"pO?MW4pr#n"}
```
4. Click **Execute**. The response contains an `accessToken`.
5. Copy the token value — **just the token, without `Bearer`**; Swagger adds that
   itself.
6. Click the **Authorize** button at the top right, paste it, then **Authorize**
   and **Close**.
7. Every endpoint on the page now works. `GET /api/customers` and
   `GET /api/invoices` are the ones to start with; there is seeded data.

Worth trying: `POST /api/invoices/{id}/pdf` returns 202 immediately and a request
id, because generation happens in another module reacting to an event. Poll
`GET /api/invoices/pdf-requests/{requestId}` until it reads `READY`, then
download it from `GET /api/exports/pdf/{requestId}`. The download deletes the
document — it exists only between generation and first retrieval.

### Using it from the terminal

```bash
APP=https://invoice-generator-e1z8.onrender.com

# Wakes the instance. Repeat until it answers UP.
curl -s $APP/actuator/health

TOKEN=$(curl -s -X POST $APP/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"<user-password>"}' \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -s -H "Authorization: Bearer $TOKEN" "$APP/api/customers?page=0&size=10"
curl -s -H "Authorization: Bearer $TOKEN" "$APP/api/invoices?page=0&size=10"
```

### What to expect

**The first request takes about two minutes.** The free instance sleeps after 15
minutes idle and runs on 0.1 CPU, so a cold JVM start measures 94 seconds —
locally the same artifact starts in 11. That is the hosting tier, not the
application. Everything after the first request is fast.

**Without a token you get 401**, including on paths that do not exist. That is
`anyRequest().denyAll()` doing its job, not a broken deployment. The root `/`
redirects to Swagger so the bare URL is not a dead end.

**The database is public and writable.** The demo account can create and delete,
so what you find there may not be what was seeded. Nothing sensitive is stored.

**The demo account can do everything.** Roles are issued in the JWT and mapped to
`ROLE_`-prefixed authorities, but no endpoint requires one yet — method-level
authorization is a gap, listed among the omissions below rather than dressed up
as a design.

Fill in <user-password> in all four places — the table, the Swagger step, and the curl block. Use the one you verified returns 200 against $APP.

The last paragraph is what makes publishing a single account honest: you say plainly that the role mechanism exists and isn't enforced, rather than letting a reviewer discover it and wonder what else is claimed but absent. It pairs with the Not included, deliberately bullet:

markdown
- **No method-level authorization.** Roles are issued in the token and mapped to
  authorities; nothing consumes them. The `admin` and `user` accounts have
  identical access. The mechanism is complete, the policy is not.

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

**77% instruction coverage** (4,114 of 5,368) with a per-module JaCoCo gate on
merged unit and integration data. Integration tests use Testcontainers Postgres
and an embedded Kafka broker.

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
- **No method-level authorization.** The mechanism is complete and the policy is
  missing. `AuthService` issues a `roles` claim, `SecurityConfig` wires a
  `JwtGrantedAuthoritiesConverter` that maps it to `ROLE_`-prefixed authorities,
  and `admin` and `user` genuinely carry different claims — the tokens differ in
  length because of it. But no endpoint requires an authority: the filter chain
  asks only for `authenticated()` on `/api/**`, and there is no
  `@EnableMethodSecurity` or `@PreAuthorize` anywhere. A `USER` token returns 200
  from `/api/invoices/admin/outbox/requeue` and can delete any invoice.

  The gap is one change, not a redesign: `@EnableMethodSecurity` plus
  `@PreAuthorize("hasRole('ADMIN')")` on the destructive and administrative
  endpoints. The part that needs care is the error path — `@PreAuthorize` throws
  `AccessDeniedException` from inside the method, so it lands in the same advice
  chain that turned a `BadCredentialsException` into a 500 until it was handled
  explicitly. Adding the annotations without an `AccessDeniedHandler` and a test
  would produce a 500 where a 403 belongs.

  Left undone rather than half-done. Authorization that returns the wrong status
  is worse than none, because it looks enforced.  

---

## Documentation

- [ADR 001](docs/adr/001-gateway-owns-authentication.md) — the gateway owns authentication
- [ADR 002](docs/adr/002-no-automated-retention.md) — no automated retention
- [ADR 003](docs/adr/003-deploy-as-a-single-service.md) — deployed as one service, developed as six
- [ADR 004](docs/adr/004-kafka-event-delivery.md) — Kafka event delivery
- [Deployment](docs/deployment.md) — both topologies, step by step
- [Engineering log](docs/ENGINEERING-LOG.md) — every bug, dead end and decision, with reasoning
