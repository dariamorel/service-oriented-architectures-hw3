#!/usr/bin/env python3

import json
import sys
import urllib.parse
import urllib.request

PROMETHEUS_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:9090"
OUTPUT_FILE = sys.argv[2] if len(sys.argv) > 2 else "load-test-results/prometheus-check.json"

ERROR_RATE_LIMIT = 0.01
P95_LATENCY_LIMIT_SECONDS = 0.5


def query(promql):
    url = f"{PROMETHEUS_URL}/api/v1/query?{urllib.parse.urlencode({'query': promql})}"
    with urllib.request.urlopen(url, timeout=10) as response:
        body = json.load(response)
    if body["status"] != "success":
        raise RuntimeError(body)
    result = body["data"]["result"]
    if not result:
        return 0.0
    return float(result[0]["value"][1])


requests_per_second = query('sum(rate(http_requests_total{job="booking-service"}[1m]))')
errors_per_second = query('sum(rate(http_request_errors_total{job="booking-service"}[1m])) or vector(0)')
p95_latency = query(
    'histogram_quantile(0.95, sum by (le) '
    '(rate(http_request_duration_seconds_bucket{job="booking-service"}[1m])))'
)
error_rate = errors_per_second / requests_per_second if requests_per_second > 0 else 1.0

checks = {
    "error_rate": {
        "actual": error_rate,
        "limit": ERROR_RATE_LIMIT,
        "passed": error_rate < ERROR_RATE_LIMIT,
    },
    "p95_latency_seconds": {
        "actual": p95_latency,
        "limit": P95_LATENCY_LIMIT_SECONDS,
        "passed": p95_latency < P95_LATENCY_LIMIT_SECONDS,
    },
}

report = {
    "requests_per_second": requests_per_second,
    "errors_per_second": errors_per_second,
    "checks": checks,
    "threshold_reason": "GET /flights is a read-only warmed-up CI scenario, so <1% errors and p95 <500ms should hold unless the service is degraded.",
}

with open(OUTPUT_FILE, "w", encoding="utf-8") as file:
    json.dump(report, file, indent=2)

print(json.dumps(report, indent=2))

if not all(check["passed"] for check in checks.values()):
    sys.exit(1)
