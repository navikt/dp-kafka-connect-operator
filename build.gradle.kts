plugins {
    id("common")
    application
}

dependencies {
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation(project(":k8s"))
    implementation(project(":ktor-client"))
    implementation(project(":core"))

    implementation(libs.bundles.ktor.client)

    // Ktor Server for metrics and health endpoints
    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.ktor.server.cio)

    // Micrometer with Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation("io.fabric8:kubernetes-server-mock:7.5.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
    testImplementation("org.testcontainers:testcontainers:2.0.3")
}

application {
    mainClass.set("no.nav.dagpenger.kafka.connect.operator.MainKt")
}
