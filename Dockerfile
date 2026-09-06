# Builds any module with a main class, selected by the SERVICE build arg.
# Context MUST be the repo root: every service depends on `common`.
#   docker build --build-arg SERVICE=invoice-service -t invoice-service .
#   docker build -t invoice-generator .          # the consolidated artifact

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

# Defaulted to the consolidated artifact so a host that cannot pass build args
# — Render reads render.yaml, not a --build-arg — gets the deployed module
# without configuration. Compose and CI both pass it explicitly, so nothing
# else changes behaviour.
ARG SERVICE=app
WORKDIR /build

# Every module's POM, listed individually: Maven assembles the reactor from the
# root <modules> before -am narrows it, so a module listed there but missing
# here fails the build at "Scanning for projects". Adding a module means adding
# a line — COPY */pom.xml would be tidier but Docker flattens the paths, and
# COPY . . costs the dependency layer cache that makes seven parallel image
# builds tolerable.
# POMs first, so the dependency layer caches independently of source edits.
COPY pom.xml .
COPY common/pom.xml            common/
COPY api-gateway/pom.xml       api-gateway/
COPY customer-service/pom.xml  customer-service/
COPY invoice-service/pom.xml   invoice-service/
COPY export-service/pom.xml    export-service/
COPY import-service/pom.xml    import-service/
COPY archive-service/pom.xml   archive-service/
COPY app/pom.xml               app/
COPY coverage/pom.xml          coverage/

RUN mvn -B -pl ${SERVICE} -am dependency:go-offline -DskipTests

COPY common/src            common/src
COPY api-gateway/src       api-gateway/src
COPY customer-service/src  customer-service/src
COPY invoice-service/src   invoice-service/src
COPY export-service/src    export-service/src
COPY import-service/src    import-service/src
COPY archive-service/src   archive-service/src
COPY app/src               app/src

# *-exec.jar, not *.jar: since the repackage classifier every module produces
# two artifacts and the plain glob matches both, failing with "target is not a
# directory".
RUN mvn -B -pl ${SERVICE} -am clean package -DskipTests \
    && cp ${SERVICE}/target/*-exec.jar /build/app.jar

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