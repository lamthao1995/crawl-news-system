#!/usr/bin/env bash
# Stop the whole stack, then start again with a fresh image build (fast local reset).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

echo "==> docker compose down"
docker compose down

echo ""
echo "==> docker compose up --build -d"
docker compose up --build -d

echo ""
echo "==> Done. Services:"
echo "    Temporal UI     http://localhost:8080"
echo "    crawl-postgres  localhost:5433"
echo "    Redis           localhost:6379"
echo ""
echo "==> Follow worker: docker compose logs -f crawl-worker"
echo "==> (Optional wipe ALL data: docker compose down -v && $0)"
