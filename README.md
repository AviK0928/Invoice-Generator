# Invoice Generator Microservices Project

A complete microservices-based system for managing customers, invoices, exporting, importing, and archiving invoice data — built using Spring Boot 3, Docker, Kafka, and PostgreSQL.

---

##  Architecture Overview

This system consists of the following microservices:

| Service           | Port | Description                                     |
|------------------|------|-------------------------------------------------|
| API Gateway       | 8080 | Central gateway for routing requests            |
| Customer Service  | 8081 | CRUD operations for customer data               |
| Invoice Service   | 8082 | Handles invoice creation and processing         |
| Export Service    | 8083 | Exports data as PDF/CSV/ZIP                     |
| Import Service    | 8084 | Imports data from uploaded CSVs                 |
| Archive Service   | 8085 | Archives invoices via Kafka                     |
| Kafka Broker      | 9092 | Message broker for inter-service communication |
| Zookeeper         | 2181 | Kafka coordination service                      |
| PostgreSQL (×5)   | 5433–5437 | One per service for isolation             |

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
Build all Spring Boot JARs into Docker images  
Start Zookeeper, Kafka, Postgres containers  
Launch all 6 microservices  


