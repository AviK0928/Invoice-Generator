# Deployment

Two supported topologies. The hosted demo uses the first; the second is the
reference architecture and is what the compose file and k8s manifests describe.

See [ADR 003](adr/003-deploy-as-a-single-service.md) for why.

---

## A. Single artifact (hosted demo)

One process, one database, no broker. Events dispatch in-process via the outbox.

**Requirements:** a Postgres instance and a container host.

```bash
# 1. Provision Postgres — Neon or Supabase free tier. Not Render's, which is
#    deleted 30 days after creation.

# 2. Set environment variables on the host
DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require
DB_USER=...
DB_PASS=...
JWT_SECRET=$(openssl rand -hex 32)
ADMIN_PASSWORD_HASH=...      # docker run --rm httpd:alpine htpasswd -bnBC 10 "" pw | tr -d ':\n'
USER_PASSWORD_HASH=...
SPRING_PROFILES_ACTIVE=cloud
SPRINGDOC_ENABLED=true       # off by default in the cloud profile

# 3. Deploy
#    Render reads render.yaml. Any other Docker host:
docker build --build-arg SERVICE=app -t invoice-generator .
docker run -p 8080:8080 --env-file .env invoice-generator
```

Flyway runs the migrations on first start. Health check is `/actuator/health`.

**Known limitation:** free-tier instances sleep after 15 minutes. The first
request after idle takes 30–60 seconds.

---

## B. Six services with Kafka (reference architecture)

What the system is designed as. Runs locally with one command, and on any host
with ~4 GB of RAM.

### Locally

```bash
cp .env.example .env    # fill in the secrets
docker compose up -d --build
```

Eight containers: five services, the gateway, Kafka (KRaft, no Zookeeper) and
one Postgres holding five databases. Gateway on 8080; services on 8081–8085,
published for development so a single service can be exercised directly.

Cold start is ~90 seconds — the gateway waits for all five services to report
healthy rather than accepting traffic and returning 502s.

```bash
# Verify
docker compose ps                        # eight containers, all (healthy)
curl -s localhost:8080/actuator/health
```

### On a VM

Any host with 4 GB RAM and Docker. Costs about $20–25/month on a small VPS.

```bash
git clone https://github.com/AviK0928/Invoice-Generator
cd Invoice-Generator
cp .env.example .env    # set real secrets — see below
docker compose up -d --build
```

Then in front of it:

- A reverse proxy (Caddy or nginx) terminating TLS on the gateway only
- Firewall closing 8081–8085 and 5432 — **the compose file publishes these for
  local development, and they are unauthenticated**. Authentication lives at the
  gateway (see [ADR 001](adr/001-gateway-owns-authentication.md)).
- Set `SPRING_PROFILES_ACTIVE=cloud`

Comment out the `ports:` blocks on the five services before exposing this host
to a network.

### On Kubernetes

`k8s/` contains Deployments with liveness and readiness probes wired to
Actuator, Services, ConfigMaps, Secrets, an Ingress and an HPA, with Kustomize
overlays for dev and prod.

```bash
kind create cluster --name invoice-generator
kubectl apply -k k8s/overlays/dev
kubectl port-forward svc/api-gateway 8080:8080
```

Verified against `kind`. Not deployed to a managed cluster — see ADR 003.

---

## Secrets

| Variable | Notes |
|---|---|
| `JWT_SECRET` | `openssl rand -hex 32`. Hex, not base64 — base64 contains `$`, which Compose treats as variable substitution. |
| `ADMIN_PASSWORD_HASH` | bcrypt. Single-quote it in `.env`, or the `$` characters are substituted away. |
| `USER_PASSWORD_HASH` | as above |
| `POSTGRES_PASSWORD` | local compose only |

The `cloud` profile has **no default** for `JWT_SECRET`. Startup fails without
it, rather than falling back to the development key committed in
`application.yml`.
