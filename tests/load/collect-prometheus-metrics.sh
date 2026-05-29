#!/usr/bin/env bash

set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
OUTPUT_FILE="${1:-load-test-results/prometheus-samples.jsonl}"

mkdir -p "$(dirname "$OUTPUT_FILE")"

while true; do
    timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    requests="$(curl -fsS --get "$PROMETHEUS_URL/api/v1/query" --data-urlencode 'query=sum(rate(http_requests_total{job="booking-service"}[1m]))')"
    errors="$(curl -fsS --get "$PROMETHEUS_URL/api/v1/query" --data-urlencode 'query=sum(rate(http_request_errors_total{job="booking-service"}[1m])) or vector(0)')"
    latency="$(curl -fsS --get "$PROMETHEUS_URL/api/v1/query" --data-urlencode 'query=histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket{job="booking-service"}[1m])))')"

    printf '{"timestamp":"%s","requests":%s,"errors":%s,"p95_latency":%s}\n' "$timestamp" "$requests" "$errors" "$latency" >> "$OUTPUT_FILE"
    sleep 5
done
