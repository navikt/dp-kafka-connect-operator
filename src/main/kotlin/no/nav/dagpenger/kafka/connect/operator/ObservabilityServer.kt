package no.nav.dagpenger.kafka.connect.operator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

private val logger = KotlinLogging.logger {}

class ObservabilityServer(
    private val port: Int = 8080,
) {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val server =
        embeddedServer(CIO, port = port) {
            install(MicrometerMetrics) {
                registry = meterRegistry
            }

            routing {
                get("/internal/metrics") {
                    call.respondText(meterRegistry.scrape())
                }

                get("/internal/isalive") {
                    call.respondText("ALIVE")
                }

                get("/internal/isready") {
                    call.respondText("READY")
                }
            }
        }

    fun start() {
        logger.info { "Starting observability server on port $port" }
        server.start(wait = false)
        logger.info { "Observability server started successfully" }
    }

    fun stop() {
        logger.info { "Stopping observability server" }
        server.stop(1000, 2000)
        logger.info { "Observability server stopped" }
    }
}
