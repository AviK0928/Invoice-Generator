# Deployment

Two topologies. The hosted demo uses the first; the second is the reference
architecture. See [ADR 003](adr/003-deploy-as-a-single-service.md) for why.

---

## A. Single artifact (hosted demo)

One process, one database, no broker. Events dispatch in-process via the outbox.

**Live:** https://invoice-generator-e1z8.onrender.com

### What the `app` module is

An assembly. It depends on the five service jars and adds only what one process
needs that six did not:

- one datasource and one consolidated schema (`app/src/main/resources/db/migration/app`)
- `InProcessOutboxEventPublisher`, routing outbox events straight to the existing
  `@KafkaListener` beans, which are inert because Kafka auto-configuration is
  excluded
- a servlet security chain, since the gateway is WebFlux and cannot be in the
  same process
- one OpenAPI spec replacing the five per-service ones

No service class is modified. The build produces it from the same Dockerfile as
any service, selected by `SERVICE=app` — which is the default, so a host that
cannot pass build arguments needs no configuration.

### Database

Any Postgres reachable over TLS. The demo uses a Neon free tier in Singapore,
matching the web service's region — app-to-database latency matters more than
user-to-app, since a request makes several round trips.

Deliberately **not** the host's own free Postgres, which is deleted 30 days after
creation. A demo on a deletion timer is the same failure mode ADR 003 rejected
Oracle for.

### Deploying

```bash
# 1. Provision Postgres. Split the connection string into three values —
#    the driver will not accept it as given:
DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require
DB_USER=<user>
DB_PASS=<password>

# 2. Generate secrets
JWT_SECRET=$(openssl rand -hex 32)     # hex, not base64: base64 contains $
docker run --rm httpd:alpine htpasswd -bnBC 10 "" '<password>' | tr -d ':\n'

# 3. Set on the host
SPRING_PROFILES_ACTIVE=cloud
ADMIN_PASSWORD_HASH=...
USER_PASSWORD_HASH=...
SPRINGDOC_ENABLED=true

# 4. Deploy. Render reads render.yaml. Any other Docker host:
docker build -t invoice-generator .
docker run -p 8080:8080 --env-file .env invoice-generator
```

The `cloud` profile has **no default** for `JWT_SECRET`, and neither password
hash has one in any profile. Startup fails without them rather than running on
the development key committed to `application.yml`. That is intentional.

Flyway applies six migrations on first start. Health check is
`/actuator/health`, which is permitted unauthenticated.

### Known limitations

- **Cold start ~94 seconds.** The instance sleeps after 15 minutes idle, and
  0.1 CPU is the binding constraint. The platform documents 30–60 seconds; a JVM
  starting six modules' worth of context on a tenth of a core takes longer.
- **The database sleeps too**, adding a second or two on top.
- **One topic routes to one consumer.** Kafka would fan out to consumer groups;
  the in-process publisher does not. No topic has two consumers today, so this
  holds — by assumption, not by construction.
- **`processed_events` is one table** for what was three, keyed by the publishing
  service's event id. Same assumption as above, in a second place.

---

## B. Six services with Kafka (reference architecture)

What the system is designed as. Runs locally with one command.

### Locally

```bash
cp .env.example .env    # fill in the secrets
docker compose up -d --build
```

Eight containers: five services, the gateway, Kafka (KRaft, no ZooKeeper) and one
Postgres holding five databases. Gateway on 8080; services on 8081–8085,
published so a single service can be exercised directly.

```bash
docker compose ps                        # eight containers, all (healthy)
curl -s localhost:8080/actuator/health
```

For a fast edit-test loop, `./run.sh <service> [profile]` starts Postgres and
Kafka in Docker and runs one service natively.

### On a VM

Any host with 4 GB of RAM and Docker. In front of it:

- a reverse proxy terminating TLS on the gateway only
- a firewall closing 8081–8085 and 5432 — **the compose file publishes these for
  local development and they are unauthenticated**. Authentication lives at the
  gateway ([ADR 001](adr/001-gateway-owns-authentication.md)). Comment out the
  `ports:` blocks on the five services before exposing the host.
- `SPRING_PROFILES_ACTIVE=cloud`

### On Kubernetes

Not provided. Manifests would be a reasonable addition; writing them without a
cluster to verify against would be a claim rather than a capability, so the
repository does not make one.

---

## Secrets

| Variable | Notes |
|---|---|
| `JWT_SECRET` | `openssl rand -hex 32`. Hex, not base64 — base64 contains `$`, which Compose treats as variable substitution. Must be at least 32 bytes; startup fails below that. |
| `ADMIN_PASSWORD_HASH` | bcrypt. Single-quote the password when generating, or `$` and spaces are mangled by the shell. |
| `USER_PASSWORD_HASH` | as above |
| `POSTGRES_PASSWORD` | local compose only |
