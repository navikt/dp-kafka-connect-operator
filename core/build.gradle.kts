plugins {
    id("common")
    `java-library`
}

dependencies {
    implementation(libs.kotlin.logging)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    api("io.micrometer:micrometer-core:1.16.2")

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)

    testRuntimeOnly(libs.logback.classic)
}
