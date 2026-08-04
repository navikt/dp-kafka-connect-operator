package no.nav.dagpenger.kafka.connect.operator

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private data class OperatorConfig(
    val baseUrl: String,
    val namespace: String,
    val metricsPort: Int,
) {
    companion object {
        fun fromEnv() =
            OperatorConfig(
                baseUrl = System.getenv("BASE_URL") ?: "http://localhost:9000",
                namespace = System.getenv("NAMESPACE") ?: "teamdagpenger",
                metricsPort = System.getenv("METRICS_PORT")?.toIntOrNull() ?: 8080,
            )
    }
}

fun main() {
    val config = OperatorConfig.fromEnv()
    logger.info { "Starting Kafka Connect Operator" }
    logger.info { "Configuration: $config" }

    val observability = ObservabilityServer(port = config.metricsPort)
    val metrics = OperatorMetrics(observability.meterRegistry)
    val connectorClient = KtorKafkaConnectClient(config.baseUrl)
    val k8sClient = KubernetesClientBuilder().build()
    val source = KubernetesConfigMapSource(k8sClient, config.namespace, metrics)
    val operator = Operator(source, connectorClient, metrics)
    val leaderElection = LeaderElection(k8sClient, config.namespace)

    observability.start()
    observability.setReady(false)
    registerShutdownHook(observability, leaderElection, source, connectorClient, k8sClient)

    // Don't contend for leadership until dependencies actually work - avoids
    // a pod that's about to crash "stealing" the lease from a healthy leader.
    waitUntilDependenciesReady(connectorClient, k8sClient, config.namespace)

    leaderElection.runElectedWork(onLeadershipChanged = observability::setReady) {
        operator.start(this).join()
    }
}

private fun waitUntilDependenciesReady(
    connectorClient: KtorKafkaConnectClient,
    k8sClient: KubernetesClient,
    namespace: String,
    retryDelay: kotlin.time.Duration = 2.seconds,
) {
    logger.info { "Waiting for dependencies before joining leader election" }
    var attempt = 0
    while (true) {
        attempt++
        val healthy =
            runCatching {
                k8sClient.pods().inNamespace(namespace).list()
                runBlocking { connectorClient.listConnectors() }
            }.isSuccess
        if (healthy) {
            logger.info { "Dependencies healthy after $attempt attempt(s)" }
            return
        }
        logger.warn { "Dependencies not ready yet (attempt $attempt), retrying in $retryDelay" }
        Thread.sleep(retryDelay.inWholeMilliseconds)
    }
}

private fun registerShutdownHook(
    observability: ObservabilityServer,
    leaderElection: LeaderElection,
    source: KubernetesConfigMapSource,
    connectorClient: KtorKafkaConnectClient,
    k8sClient: KubernetesClient,
) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info { "Shutdown initiated, cleaning up resources" }
            leaderElection.close()
            observability.stop()
            source.close()
            connectorClient.close()
            k8sClient.close()
            logger.info { "Shutdown completed" }
        },
    )
}
