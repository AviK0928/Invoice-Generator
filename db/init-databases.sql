-- Runs once, on first creation of the Postgres volume.
-- Five databases in one container instead of five containers.
-- To re-run: docker compose down -v

CREATE DATABASE customerdb;
CREATE DATABASE invoicedb;
CREATE DATABASE exportdb;
CREATE DATABASE importdb;
CREATE DATABASE archivedb;