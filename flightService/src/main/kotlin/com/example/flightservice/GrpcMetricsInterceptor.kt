package com.example.flightservice

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.springframework.stereotype.Component

@Component
class GrpcMetricsInterceptor : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val method = "grpc"
        val endpoint = call.methodDescriptor.fullMethodName
        val timer = PrometheusMetrics.requestDuration.labels(method, endpoint).startTimer()

        val monitoredCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun close(status: Status, trailers: Metadata) {
                val code = status.code.name
                PrometheusMetrics.requests.labels(method, endpoint, code).inc()
                if (!status.isOk) {
                    PrometheusMetrics.errors.labels(method, endpoint, code).inc()
                }
                timer.observeDuration()
                super.close(status, trailers)
            }
        }

        return next.startCall(monitoredCall, headers)
    }
}
