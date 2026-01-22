package no.nav.dagpenger.kafka.connect.operator

import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun main() {
    val baseUrl = System.getenv("BASE_URL") ?: "http://localhost:9000"
    val namespace = System.getenv("NAMESPACE") ?: "teamdagpenger"
    val metricsPort = System.getenv("METRICS_PORT")?.toIntOrNull() ?: 8080

    logger.info { "Starting Kafka Connect Operator" }
    logger.info { "Configuration: baseUrl=$baseUrl, namespace=$namespace, metricsPort=$metricsPort" }

    val observability = ObservabilityServer(port = metricsPort)
    val metrics = OperatorMetrics(observability.meterRegistry)

    val connectorClient = KtorKafkaConnectClient(baseUrl)
    val source = KubernetesConfigMapSource(KubernetesClientBuilder().build(), namespace, metrics)
    val reconciler = ConnectorReconciler(source, connectorClient, metrics)

    observability.start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info { "Shutdown initiated, cleaning up resources" }
            observability.stop()
            source.close()
            connectorClient.close()
            logger.info { "Shutdown completed" }
        },
    )

    runBlocking {
        logger.info { "Operator started successfully, watching for ConfigMap changes" }
        try {
            reconciler.start(this).join()
        } catch (e: Exception) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            logger.error(rootCause) { "Uncaught exception: ${rootCause.message}" }
            exitProcess(1)
        }
    }
}
