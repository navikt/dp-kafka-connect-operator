plugins {
    id("common")
    `java-library`
}

dependencies {
    implementation(libs.kotlin.logging)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:+")

    api("io.micrometer:micrometer-core:1.11.0")

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)

    testRuntimeOnly(libs.logback.classic)
}
