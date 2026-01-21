plugins {
    id("common")
    `java-library`
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlin.logging)
    implementation(libs.jackson.kotlin)

    api("io.fabric8:kubernetes-client:7.5.0")

    testImplementation("io.fabric8:kubernetes-server-mock:7.5.0")
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.logback.classic)
}
