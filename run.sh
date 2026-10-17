#!/usr/bin/env bash
# Demo: one process on :8080 serves the API and the web dashboard (built with -Pweb).
# Local Postgres on 5432? DB_PORT=5433 ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
export DB_PORT="${DB_PORT:-5432}"

docker compose up -d db
until docker compose exec -T db pg_isready -U yolcu -d yolcu >/dev/null 2>&1; do sleep 1; done

exec mvn -B -ntp -f backend/pom.xml -Pweb spring-boot:run
