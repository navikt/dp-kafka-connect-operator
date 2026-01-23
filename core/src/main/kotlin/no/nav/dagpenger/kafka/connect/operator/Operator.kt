package no.nav.dagpenger.kafka.connect.operator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import no.nav.dagpenger.kafka.connect.operator.client.KafkaConnectClient
import no.nav.dagpenger.kafka.connect.operator.source.ConnectorConfigSource

class Operator(
    source: ConnectorConfigSource,
    kafkaConnect: KafkaConnectClient,
    metrics: OperatorMetrics? = null,
) {
    private val initialReconciler = InitialReconciler(source, kafkaConnect, metrics)
    private val reconciler = ConnectorReconciler(source, kafkaConnect, metrics)

    suspend fun start(scope: CoroutineScope): Job {
        performInitialReconciliation()
        logger.info { "Watching for ConfigMap changes" }
        return reconciler.start(scope)
    }

    private suspend fun performInitialReconciliation() {
        logger.info { "Performing initial reconciliation" }
        try {
            val result = initialReconciler.reconcile()
            if (result.failed.isNotEmpty()) {
                logger.warn {
                    "Initial reconciliation completed with failures: upserted=${result.upserted}, deleted=${result.deleted}, failed=${result.failed}"
                }
            } else {
                logger.info {
                    "Initial reconciliation completed: upserted ${result.upserted.size}, deleted ${result.deleted.size} connector(s)"
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Initial reconciliation failed" }
            throw e
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
