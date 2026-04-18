#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

echo "==> Building and starting stack (detached)..."
docker compose up --build -d

echo ""
echo "==> Services"
echo "    Temporal UI     http://localhost:8080"
echo "    Temporal gRPC   localhost:7233"
echo "    crawl-postgres  localhost:5433  (db crawl / user crawl)"
echo "    Redis           localhost:6379"
echo ""
echo "==> When Temporal is ready, start investing crawl (new workflow id each run):"
echo 'docker compose exec temporal-admin-tools temporal workflow start \'
echo '  --task-queue news-task-queue \'
echo '  --type InvestingSpiralCrawlWorkflow \'
echo '  --workflow-id investing-spiral-$(date +%s) \'
echo "  --input '{\"seedUrl\":\"https://www.investing.com/\",\"allowedHostSuffix\":\"investing.com\",\"maxDepth\":2,\"maxPages\":20,\"maxOutboundLinksPerPage\":18,\"maxPathSegments\":14}'"
echo ""
echo "==> Logs: docker compose logs -f crawl-worker"
