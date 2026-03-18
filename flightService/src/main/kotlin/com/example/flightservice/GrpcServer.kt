package com.example.flightservice

import io.grpc.Server
import io.grpc.ServerBuilder
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GrpcServer(
    private val flightServiceGrpcImpl: FlightServiceGrpcImpl,
    @Value("\${service.port}") private val grpcPort: Int,
) {
    private var server: Server? = null

    @PostConstruct
    fun start() {
        server = ServerBuilder.forPort(grpcPort)
            .addService(flightServiceGrpcImpl)
            .build()
            .also { it.start() }

        Thread { server!!.awaitTermination() }.start()
    }

    @PreDestroy
    fun stop() {
        server?.shutdownNow()
    }
}

