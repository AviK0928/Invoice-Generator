#!/usr/bin/env bash
#
# Start infra and run one service natively, for a fast edit-test loop
# without paying for six JVMs.
#
#   ./run.sh invoice-service
#   ./run.sh invoice-service dev     # verbose SQL + debug logging
#
set -euo pipefail

SERVICE="${1:?usage: ./run.sh <service-name> [profile]}"
PROFILE="${2:-}"

declare -A PORTS=(
  [customer-service]=8081
  [invoice-service]=8082
  [export-service]=8083
  [import-service]=8084
  [archive-service]=8085
  [api-gateway]=8080
)
declare -A DATABASES=(
  [customer-service]=customerdb
  [invoice-service]=invoicedb
  [export-service]=exportdb
  [import-service]=importdb
  [archive-service]=archivedb
)

if [[ -z "${PORTS[$SERVICE]:-}" ]]; then
  echo "unknown service: $SERVICE" >&2
  echo "one of: ${!PORTS[*]}" >&2
  exit 1
fi

[[ -f .env ]] || { echo ".env missing — cp .env.example .env" >&2; exit 1; }
set -a; source .env; set +a

echo "==> starting postgres + kafka"
docker compose up -d postgres kafka

echo -n "==> waiting for postgres "
until docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -q 2>/dev/null; do
  echo -n "."; sleep 1
done
echo " ready"

echo -n "==> waiting for kafka "
until docker compose exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh \
        --bootstrap-server localhost:9092 >/dev/null 2>&1; do
  echo -n "."; sleep 2
done
echo " ready"

echo "==> $SERVICE on http://localhost:${PORTS[$SERVICE]}"

export DB_USER="$POSTGRES_USER"
export DB_PASS="$POSTGRES_PASSWORD"
export KAFKA_BOOTSTRAP_SERVERS="localhost:29092"   # host listener, not kafka:9092
export PORT="${PORTS[$SERVICE]}"

if [[ "$SERVICE" != "api-gateway" ]]; then
  export DB_URL="jdbc:postgresql://localhost:5432/${DATABASES[$SERVICE]}"
fi

ARGS=()
[[ -n "$PROFILE" ]] && ARGS+=("-Dspring-boot.run.profiles=$PROFILE")

exec ./mvnw -pl "$SERVICE" spring-boot:run "${ARGS[@]}"
