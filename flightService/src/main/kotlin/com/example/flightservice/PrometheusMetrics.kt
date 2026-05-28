package com.example.flightservice

import io.prometheus.client.Counter
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Histogram

object PrometheusMetrics {
    val registry = CollectorRegistry()

    val requests: Counter = Counter.build()
        .name("http_requests_total")
        .help("Number of gRPC requests.")
        .labelNames("method", "endpoint", "status")
        .register(registry)

    val errors: Counter = Counter.build()
        .name("http_request_errors_total")
        .help("Number of gRPC request errors.")
        .labelNames("method", "endpoint", "error_type")
        .register(registry)

    val requestDuration: Histogram = Histogram.build()
        .name("http_request_duration_seconds")
        .help("gRPC request duration in seconds.")
        .labelNames("method", "endpoint")
        .register(registry)
}
