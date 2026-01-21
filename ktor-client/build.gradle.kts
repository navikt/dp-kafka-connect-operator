import de.undercouch.gradle.tasks.download.Download

plugins {
    id("common")
    `java-library`
    id("de.undercouch.download") version "5.6.0"
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.client)
    implementation(libs.ktor.serialization.jackson)

    testImplementation(libs.kotest.assertions.core)

    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.testcontainers:testcontainers:2.0.2")

    testRuntimeOnly(libs.logback.classic)
}

val kafkaVersion = "4.1"
val downloadOpenApiSpec by tasks.registering(Download::class) {
    src("https://kafka.apache.org/${kafkaVersion.replace(".", "")}/generated/connect_rest.yaml")
    dest(layout.projectDirectory.file("src/test/resources/specmatic/connect_rest.yaml"))
    onlyIfModified(true)
    useETag(true)
}
tasks.named("processTestResources") {
    dependsOn(downloadOpenApiSpec)
}
