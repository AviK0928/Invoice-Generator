#!/usr/bin/env bash
# Seed a test customer into invoicedb. Run after invoice-service has started
# at least once, so ddl-auto has created the tables.
set -euo pipefail
set -a; source .env; set +a

docker compose exec -T postgres psql -U "$POSTGRES_USER" -d invoicedb -c \
  "INSERT INTO local_customers (customer_id, email, name)
   VALUES (1, 'test@example.com', 'Test Co')
   ON CONFLICT (customer_id) DO NOTHING;"

echo "seeded customer 1"
