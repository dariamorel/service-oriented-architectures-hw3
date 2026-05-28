plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

group = "org.example"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs integration tests for all services"
    dependsOn(":bookingService:integrationTest")
}