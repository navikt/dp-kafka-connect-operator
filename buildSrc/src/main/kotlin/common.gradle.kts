import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    reports.junitXml.includeSystemOutLog = false
    reports.junitXml.includeSystemErrLog = false
    testLogging {
        showExceptions = true
        showStandardStreams = false
        exceptionFormat = TestExceptionFormat.FULL
        // events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("ktlintFormat")
}
