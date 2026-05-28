package com.example.bookingservice

import io.prometheus.client.exporter.common.TextFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.StringWriter

@RestController
class MetricsController {
    @GetMapping("/metrics", produces = [TextFormat.CONTENT_TYPE_004])
    fun metrics(): ResponseEntity<String> {
        val writer = StringWriter()
        TextFormat.write004(writer, PrometheusMetrics.registry.metricFamilySamples())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(TextFormat.CONTENT_TYPE_004))
            .body(writer.toString())
    }
}
