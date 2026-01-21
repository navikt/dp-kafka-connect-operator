package no.nav.dagpenger.kafka.connect.operator

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicInteger

class OperatorMetrics(
    private val registry: MeterRegistry,
) {
    private val managedConnectors = AtomicInteger(0)

    // Counters for connector operations
    private val connectorsCreated: Counter =
        Counter
            .builder("kafka_connect_operator_connectors_created_total")
            .description("Total number of connectors created")
            .register(registry)

    private val connectorsUpdated: Counter =
        Counter
            .builder("kafka_connect_operator_connectors_updated_total")
            .description("Total number of connectors updated")
            .register(registry)

    private val connectorsDeleted: Counter =
        Counter
            .builder("kafka_connect_operator_connectors_deleted_total")
            .description("Total number of connectors deleted")
            .register(registry)

    private val operationsFailed: Counter =
        Counter
            .builder("kafka_connect_operator_operations_failed_total")
            .description("Total number of failed operations")
            .register(registry)

    private val operationsRejected: Counter =
        Counter
            .builder("kafka_connect_operator_operations_rejected_total")
            .description("Total number of operations rejected due to invalid config")
            .register(registry)

    private val configMapEvents: Counter =
        Counter
            .builder("kafka_connect_operator_configmap_events_total")
            .description("Total number of ConfigMap events processed")
            .tag("event_type", "all")
            .register(registry)

    // Timer for operation duration
    private val operationDuration: Timer =
        Timer
            .builder("kafka_connect_operator_operation_duration_seconds")
            .description("Duration of connector operations")
            .register(registry)

    // Gauge for current managed connectors
    init {
        registry.gauge(
            "kafka_connect_operator_managed_connectors",
            managedConnectors,
        ) { it.get().toDouble() }
    }

    fun recordConnectorCreated() = connectorsCreated.increment()

    fun recordConnectorUpdated() = connectorsUpdated.increment()

    fun recordConnectorDeleted() {
        connectorsDeleted.increment()
        managedConnectors.decrementAndGet()
    }

    fun recordOperationFailed() = operationsFailed.increment()

    fun recordOperationRejected() = operationsRejected.increment()

    fun recordConfigMapEvent() = configMapEvents.increment()

    fun recordManagedConnector() = managedConnectors.incrementAndGet()

    suspend fun <T> recordSuspendOperation(operation: suspend () -> T): T {
        val sample = Timer.start(registry)
        return try {
            operation()
        } finally {
            sample.stop(operationDuration)
        }
    }
}
