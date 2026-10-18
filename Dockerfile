# syntax=docker/dockerfile:1.7
# Yolçu: one image = web dashboard (Next.js static export) + Spring Boot API on one port.
# Render: render.yaml (Blueprint). Local: docker build -t yolcu . && docker run -p 8090:8080 -e ... yolcu

# ---- 1. web: Next.js static export -> /src/web/out ----
FROM node:22-bookworm-slim AS web
WORKDIR /src
RUN npm install -g pnpm@9.15.9
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./
COPY web/package.json web/
COPY packages/shared/package.json packages/shared/
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    CI=true pnpm install --frozen-lockfile
COPY packages packages
COPY mocks mocks
COPY web web
RUN CI=true NEXT_TELEMETRY_DISABLED=1 pnpm --filter web build:export

# ---- 2. api: Maven package with the export as classpath:/static ----
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /src/backend
COPY backend/pom.xml .
RUN --mount=type=cache,id=m2,target=/root/.m2 mvn -B -ntp -q dependency:go-offline
COPY backend/src src
COPY --from=web /src/web/out src/main/resources/static
RUN --mount=type=cache,id=m2,target=/root/.m2 mvn -B -ntp package -DskipTests \
    && cp target/backend-*.jar /app.jar

# ---- 3. runtime: JRE only ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Render free = 512 MB RAM. Measured with --memory=512m: base flags alone sit at ~490 MiB (code cache, direct buffers);
# with the three extra caps ~436 MiB under load (live heap after GC ~120 MB). If Render ever OOMs: -Xmx256m.
ENV JAVA_TOOL_OPTIONS="-Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:TieredStopAtLevel=1" \
    PORT=8080
RUN useradd --system --uid 10001 yolcu
COPY --from=api /app.jar app.jar
# runtime data read at startup (GeoStore, seed, simulator): resolved relative to /app
COPY data/processed data/processed
COPY mocks mocks
USER yolcu
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
