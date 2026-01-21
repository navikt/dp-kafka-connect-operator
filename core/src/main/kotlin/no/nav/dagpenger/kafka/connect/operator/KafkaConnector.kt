package no.nav.dagpenger.kafka.connect.operator

data class KafkaConnectorEvent(
    val event: EventType,
    val connector: KafkaConnector,
)

enum class EventType {
    ADDED,
    UPDATED,
    DELETED,
}

data class KafkaConnector(
    val name: String,
    val config: ConnectorConfig,
)

typealias ConnectorConfig = Map<String, String>

val ConnectorConfig.name get() = this["name"] ?: throw IllegalArgumentException("Connector config must contain a 'name' field")
