plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "service-oriented-architectures-hw3"

include("bookingService")
include("flightService")