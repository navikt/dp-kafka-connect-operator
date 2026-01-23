package no.nav.dagpenger.kafka.connect.operator

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.kafka.connect.operator.client.KafkaConnectClient
import no.nav.dagpenger.kafka.connect.operator.client.OperationResult
import no.nav.dagpenger.kafka.connect.operator.source.ConnectorConfigSource

/**
 * Performs initial reconciliation at startup to ensure Kafka Connect state
 * matches the desired state defined in Kubernetes ConfigMaps.
 *
 * This handles the case where changes occurred while the operator was not running:
 * - Connectors defined in K8s will be upserted to ensure config is in sync
 * - Connectors in Kafka Connect that are no longer defined in K8s will be deleted
 */
class InitialReconciler(
    private val source: ConnectorConfigSource,
    private val kafkaConnect: KafkaConnectClient,
    private val metrics: OperatorMetrics? = null,
) {
    suspend fun reconcile(): ReconcileResult {
        logger.info { "Starting initial reconciliation" }

        val k8sConnectors = source.getCurrentConnectors()
        val kafkaConnectConnectorNames = kafkaConnect.listConnectors()

        logger.info { "Found ${k8sConnectors.size} connectors in Kubernetes, ${kafkaConnectConnectorNames.size} in Kafka Connect" }

        val upsertResults = k8sConnectors.map { upsertConnector(it) }
        val deleteResults = findOrphanedConnectors(k8sConnectors, kafkaConnectConnectorNames).map { deleteConnector(it) }

        val results = upsertResults + deleteResults
        val succeeded = results.filter { it.success }
        val failed = results.filter { !it.success }

        logger.info { "Initial reconciliation complete: ${succeeded.size} succeeded, ${failed.size} failed" }

        return ReconcileResult(
            upserted = upsertResults.filter { it.success }.map { it.name },
            deleted = deleteResults.filter { it.success }.map { it.name },
            failed = failed.map { it.name },
        )
    }

    private fun findOrphanedConnectors(
        k8sConnectors: List<KafkaConnector>,
        kafkaConnectConnectorNames: List<String>,
    ): List<String> {
        val k8sNames = k8sConnectors.map { it.name }.toSet()
        return kafkaConnectConnectorNames.filter { it !in k8sNames }
    }

    private suspend fun upsertConnector(connector: KafkaConnector): OperationOutcome =
        runCatching {
            when (val result = kafkaConnect.upsert(connector)) {
                OperationResult.Success -> {
                    logger.info { "Upserted connector: ${connector.name}" }
                    true
                }

                OperationResult.Unchanged -> {
                    logger.debug { "Connector unchanged: ${connector.name}" }
                    true
                }

                is OperationResult.Rejected -> {
                    logger.error { "Connector rejected: ${connector.name}, reason=${result.reason}" }
                    false
                }

                is OperationResult.TemporaryFailure -> {
                    logger.warn { "Temporary failure: ${connector.name}, reason=${result.reason}" }
                    false
                }
            }
        }.getOrElse { e ->
            logger.error(e) { "Exception upserting connector: ${connector.name}" }
            false
        }.let { OperationOutcome(connector.name, it) }

    private suspend fun deleteConnector(name: String): OperationOutcome =
        runCatching {
            when (kafkaConnect.delete(name)) {
                OperationResult.Success -> {
                    logger.info { "Deleted orphaned connector: $name" }
                    metrics?.recordConnectorDeleted()
                    true
                }

                else -> {
                    logger.error { "Failed to delete orphaned connector: $name" }
                    false
                }
            }
        }.getOrElse { e ->
            logger.error(e) { "Exception deleting connector: $name" }
            false
        }.let { OperationOutcome(name, it) }

    private data class OperationOutcome(
        val name: String,
        val success: Boolean,
    )

    data class ReconcileResult(
        val upserted: List<String>,
        val deleted: List<String>,
        val failed: List<String>,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
