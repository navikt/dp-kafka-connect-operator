package no.nav.dagpenger.kafka.connect.operator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.dagpenger.kafka.connect.operator.client.KafkaConnectClient
import no.nav.dagpenger.kafka.connect.operator.client.OperationResult
import no.nav.dagpenger.kafka.connect.operator.source.ConnectorConfigSource

class ConnectorReconciler(
    private val source: ConnectorConfigSource,
    private val kafkaConnect: KafkaConnectClient,
    private val metrics: OperatorMetrics? = null,
) {
    fun start(scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) =
        scope.launch {
            logger.info { "ConnectorReconciler started, listening for events" }
            source.events().collect { event ->
                try {
                    processEvent(event)
                } catch (e: Exception) {
                    metrics?.recordOperationFailed()
                    logger.error(e) { "Failed to process event: connector=${event.connector.name}, eventType=${event.event}" }
                    throw e
                }
            }
        }

    private suspend fun processEvent(event: KafkaConnectorEvent) {
        when (event.event) {
            EventType.ADDED,
            EventType.UPDATED,
            -> {
                val action = if (event.event == EventType.ADDED) "Creating" else "Updating"
                logger.info { "$action connector: name=${event.connector.name}" }
                handleResult(event.connector.name, event.event) {
                    metrics.measure { kafkaConnect.upsert(event.connector) }
                }
            }

            EventType.DELETED -> {
                logger.info { "Deleting connector: name=${event.connector.name}" }
                handleResult(event.connector.name, event.event) {
                    metrics.measure { kafkaConnect.delete(event.connector.name) }
                }
            }
        }
    }

    private suspend fun handleResult(
        connectorName: String,
        eventType: EventType,
        operation: suspend () -> OperationResult,
    ) {
        when (val result = operation()) {
            OperationResult.Success -> {
                when (eventType) {
                    EventType.ADDED -> {
                        metrics?.recordConnectorCreated()
                        metrics?.recordManagedConnector()
                    }

                    EventType.UPDATED -> {
                        metrics?.recordConnectorUpdated()
                    }

                    EventType.DELETED -> {
                        metrics?.recordConnectorDeleted()
                    }
                }
                logger.info { "Operation completed successfully: connector=$connectorName, result=Success" }
            }

            OperationResult.Unchanged -> {
                logger.info { "No changes needed: connector=$connectorName, result=Unchanged" }
            }

            is OperationResult.Rejected -> {
                metrics?.recordOperationRejected()
                logger.error { "Configuration rejected: connector=$connectorName, reason=${result.reason}" }
            }

            is OperationResult.TemporaryFailure -> {
                logger.warn { "Temporary failure (will retry): connector=$connectorName, reason=${result.reason}" }
            }
        }
    }

    private suspend fun <T> OperatorMetrics?.measure(operation: suspend () -> T): T = this?.recordSuspendOperation(operation) ?: operation()

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
