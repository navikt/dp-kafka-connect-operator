package no.nav.dagpenger.kafka.connect.operator.source

import kotlinx.coroutines.flow.Flow
import no.nav.dagpenger.kafka.connect.operator.KafkaConnector
import no.nav.dagpenger.kafka.connect.operator.KafkaConnectorEvent

interface ConnectorConfigSource {
    fun events(): Flow<KafkaConnectorEvent>

    fun getCurrentConnectors(): List<KafkaConnector>
}
