
# Invoice Generator Microservices Project

A complete microservices-based system for managing customers, invoices, exporting, importing, and archiving invoice data — built using Spring Boot 3, Docker, Kafka, and PostgreSQL.

---

##  Architecture Overview

This system consists of the following microservices:

| Service           | Port     | Description                                     |
|------------------|----------|-------------------------------------------------|
| API Gateway       | 8080     | Central gateway for routing requests            |
| Customer Service  | 8081     | CRUD operations for customer data               |
| Invoice Service   | 8082     | Handles invoice creation and processing         |
| Export Service    | 8083     | Exports data as PDF/CSV/ZIP                     |
| Import Service    | 8084     | Imports data from uploaded CSVs                 |
| Archive Service   | 8085     | Archives invoices via Kafka                     |
| Kafka Broker      | 9092     | Message broker for inter-service communication |
| Zookeeper         | 2181     | Kafka coordination service                      |
| PostgreSQL (×5)   | 5433–5437| One per service for isolation                   |

---

##  Features

- ✅ Spring Boot 3.5.3 with Java 21
- ✅ Kafka messaging between services
- ✅ PostgreSQL database per service
- ✅ Dockerized using lightweight Alpine base
- ✅ Docker Compose for orchestration
- ✅ API Gateway (Spring Cloud Gateway)
- ✅ File export/import support with validation
- ✅ Modular codebase with service isolation

---

##  Running the Project

>  Prerequisite: Docker & Docker Compose installed (Docker Toolbox supported)

### 1. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/invoice-generator.git
cd invoice-generator
```

### 2. Build and Start All Services

```bash
docker-compose up --build
```

This will:
- Build all Spring Boot JARs into Docker images
- Start Zookeeper, Kafka, Postgres containers
- Launch all 6 microservices

### 3. Accessing Services

- API Gateway: [http://localhost:8080](http://localhost:8080)
- Each service can also be accessed directly via its assigned port (if exposed)

---

## Development Tips

- All service logs are visible in the terminal or Docker Dashboard
- Use Postman or curl to test endpoints exposed by the API Gateway
- Kafka messages can be inspected via Kafdrop or a Kafka CLI tool
- Ensure no port conflicts with existing services on your machine

---

## Folder Structure

```
invoice-generator/
├── gateway/
├── customer-service/
├── invoice-service/
├── export-service/
├── import-service/
├── archive-service/
├── docker-compose.yml
├── .env
└── README.md
```

## Deployment

**Live demo:** <url> · credentials `admin` / `admin123`

The hosted demo runs as a **single consolidated artifact**, while the system is
built and developed as six microservices. That is a deliberate choice, not a
limitation:

- Nothing in this domain scales or deploys independently — export is not hit
  more than invoicing, and no module has its own availability requirement.
- On free-tier instances that sleep when idle, six services chained behind a
  gateway means six cold starts. A first request takes 90+ seconds.

The distributed topology is fully preserved and runnable —
`docker compose up -d --build` starts all eight containers, and `k8s/` deploys
the same thing to a cluster. See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for
both paths and [ADR 003](docs/adr/003-deploy-as-a-single-service.md) for the
reasoning.

The demo sleeps after 15 minutes idle; the first request may take 30–60 seconds.
