plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositories {
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.dagpenger:dp-version-catalog:20260123.238.f6b1ed")

            // Override version
            version("ktor", "3.4.0")
        }
    }
}

rootProject.name = "dp-kafka-connect-operator"

include("ktor-client")
include("k8s")
include("core")
