package com.example.bookingservice

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

@Component
class HttpMetricsFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI == "/metrics"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        var thrown: Exception? = null

        try {
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            thrown = e
            PrometheusMetrics.errors
                .labels(request.method, endpoint(request), e.javaClass.simpleName)
                .inc()
            throw e
        } finally {
            val endpoint = endpoint(request)
            val status = if (thrown != null && response.status < 400) "500" else response.status.toString()
            PrometheusMetrics.requests.labels(request.method, endpoint, status).inc()
            if (thrown == null && response.status >= 400) {
                PrometheusMetrics.errors.labels(request.method, endpoint, status).inc()
            }
            PrometheusMetrics.requestDuration
                .labels(request.method, endpoint)
                .observe((System.nanoTime() - startedAt) / 1_000_000_000.0)
        }
    }

    private fun endpoint(request: HttpServletRequest): String =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
            ?: request.requestURI
}
