# from declares the base image that we will build off of (FROM is always the first line of a Dockerfile)
# eclipse-temurin provides a java runtime container, this project uses java 25.
#
# The JRE image, not the full JDK: this runs a pre-built fat jar and needs nothing from the compiler
# toolchain, so the JDK's javac/jlink/jcmd surface is ~180 MB of attack surface with no user. Switch back
# to `eclipse-temurin:25` only if something here starts needing a JDK tool at runtime.
FROM eclipse-temurin:25-jre

# curl is the container's ONLY HTTP client — the Temurin base ships neither curl nor wget. Two things need
# one: the HEALTHCHECK below, and you, at 2am. The backend publishes no host port in production (Caddy is
# the only service that does), so `docker compose exec backend curl localhost:8080/v1/heartbeat` is the
# only way to ask the backend directly whether it is alive. Do not remove this to save 5 MB.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user. A remote-code-execution bug in the app then lands on a uid that owns
# nothing but /app and the log directory, instead of on root inside the container.
RUN groupadd --system --gid 1001 pensieve \
    && useradd --system --uid 1001 --gid pensieve --no-create-home pensieve

# Set working directory
WORKDIR /app

# these two lines will copy a built jar file to to the container the JAR_FILE is passed in during the docker build command
# example: docker build --build-arg "JAR_FILE=target/*.jar" -t sethcondie/the-game-pensieve-api .
ARG JAR_FILE
# copy everything in the : path from host machine <space> to the path on the container.
# --chown on the COPY itself, NOT a `chown -R` afterwards: chown rewrites every file it touches into a new
# layer, so doing it after these three copies would duplicate all 33 MB of jar and JSON in the image.
COPY --chown=pensieve:pensieve ${JAR_FILE} app.jar

# Copy JSON data files for backup/import functionality
COPY --chown=pensieve:pensieve sampleData.json /app/sampleData.json
COPY --chown=pensieve:pensieve myCollection.json /app/myCollection.json

# The log directory. It is created HERE, owned by the runtime user, on purpose: when an empty named volume
# is mounted over a path that already exists in the image, Docker seeds the volume with that directory's
# contents *and its ownership*. Create it only in the compose file and the volume arrives root-owned, which
# a non-root process cannot write to — the app then boots and silently logs nothing.
# PENSIEVE_LOG_PATH (see application.properties) points logging.file.path here in production.
RUN mkdir -p /var/log/pensieve && chown pensieve:pensieve /var/log/pensieve

USER pensieve

# Liveness for `docker compose ps` and for depends_on: condition: service_healthy. /v1/heartbeat is
# permitAll in both security profiles, so this needs no token. start-period covers first boot, where Flyway
# runs every migration before the port opens.
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/v1/heartbeat || exit 1

# this will run the jar file in the container
ENTRYPOINT ["java","-jar","/app/app.jar"]

# run in the terminal (in this directory) with the command
# docker build -t pensieve-api .
# docker : build : -t (create the image with this tag name) : <tag-name> : . (directory of Dockerfile, here)
# -----or run this with one of the compose files in dockerCompose/ (compose.unsecured / compose.secured)-----

# Another way to setup a docker file that copies the source code and runs it in the container
# https://www.docker.com/blog/kickstart-your-spring-boot-application-development/
#
# FROM eclipse-temurin:25-jdk-focal
#
# WORKDIR /app
#
# COPY .mvn/ .mvn
# COPY mvnw pom.xml ./
# RUN ./mvnw dependency:go-offline
#
# COPY src ./src
#
# CMD ["./mvnw", "spring-boot:run"]
