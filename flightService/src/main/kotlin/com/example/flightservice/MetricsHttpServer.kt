package com.example.flightservice

import com.sun.net.httpserver.HttpServer
import io.prometheus.client.exporter.common.TextFormat
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.StringWriter
import java.net.InetSocketAddress

@Component
class MetricsHttpServer(
    @param:Value("\${metrics.port}") private val metricsPort: Int,
) {
    private var server: HttpServer? = null

    @PostConstruct
    fun start() {
        server = HttpServer.create(InetSocketAddress(metricsPort), 0).apply {
            createContext("/metrics") { exchange ->
                val writer = StringWriter()
                TextFormat.write004(writer, PrometheusMetrics.registry.metricFamilySamples())
                val body = writer.toString().toByteArray()
                exchange.responseHeaders.add("Content-Type", TextFormat.CONTENT_TYPE_004)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }

    @PreDestroy
    fun stop() {
        server?.stop(0)
    }
}
