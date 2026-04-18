#!/usr/bin/env bash
# Resolve Temporal hostname to an IPv4 literal so gRPC/Netty avoids UnsupportedAddressTypeException
# on some Docker Desktop / mixed IPv6 setups.
set -euo pipefail

HOST="${TEMPORAL_PEER_HOST:-temporal}"
PORT="${TEMPORAL_PEER_PORT:-7233}"

if command -v getent >/dev/null 2>&1; then
  IPV4="$(getent ahosts "$HOST" | awk '$1 ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ {print $1; exit}' || true)"
  if [[ -n "${IPV4:-}" ]]; then
    export TEMPORAL_TARGET="${IPV4}:${PORT}"
    echo "[crawl-worker] Resolved ${HOST} -> ${TEMPORAL_TARGET}" >&2
  else
    export TEMPORAL_TARGET="${TEMPORAL_TARGET:-${HOST}:${PORT}}"
    echo "[crawl-worker] WARN: no IPv4 from getent for ${HOST}; using TEMPORAL_TARGET=${TEMPORAL_TARGET}" >&2
  fi
else
  export TEMPORAL_TARGET="${TEMPORAL_TARGET:-${HOST}:${PORT}}"
  echo "[crawl-worker] WARN: getent missing; using TEMPORAL_TARGET=${TEMPORAL_TARGET}" >&2
fi

exec java \
  -Djava.net.preferIPv4Stack=true \
  -Djava.net.preferIPv6Addresses=false \
  -Dio.grpc.netty.shaded.io.netty.transport.noNative=true \
  -jar /app/worker.jar
