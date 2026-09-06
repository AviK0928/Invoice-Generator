# Builds any service, selected by the SERVICE build arg.
# Context MUST be the repo root: every service depends on `common`.
#   docker build --build-arg SERVICE=invoice-service -t invoice-service .

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

ARG SERVICE
WORKDIR /build

# POMs first, so the dependency layer caches independently of source edits.
COPY pom.xml .
COPY common/pom.xml            common/
COPY api-gateway/pom.xml       api-gateway/
COPY customer-service/pom.xml  customer-service/
COPY invoice-service/pom.xml   invoice-service/
COPY export-service/pom.xml    export-service/
COPY import-service/pom.xml    import-service/
COPY archive-service/pom.xml   archive-service/
COPY coverage/pom.xml          coverage/

RUN mvn -B -pl ${SERVICE} -am dependency:go-offline -DskipTests

COPY common/src            common/src
COPY api-gateway/src       api-gateway/src
COPY customer-service/src  customer-service/src
COPY invoice-service/src   invoice-service/src
COPY export-service/src    export-service/src
COPY import-service/src    import-service/src
COPY archive-service/src   archive-service/src

RUN mvn -B -pl ${SERVICE} -am clean package -DskipTests \
    && cp ${SERVICE}/target/*.jar /build/app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --create-home appuser
USER appuser

WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/app.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]