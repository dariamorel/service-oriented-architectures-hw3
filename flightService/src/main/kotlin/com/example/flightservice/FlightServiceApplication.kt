package com.example.flightservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class FlightServiceApplication

fun main(args: Array<String>) {
    runApplication<FlightServiceApplication>(*args)
}
