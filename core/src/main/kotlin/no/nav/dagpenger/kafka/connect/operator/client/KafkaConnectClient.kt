package no.nav.dagpenger.kafka.connect.operator.client

import no.nav.dagpenger.kafka.connect.operator.KafkaConnector

interface KafkaConnectClient {
    suspend fun upsert(connector: KafkaConnector): OperationResult

    suspend fun delete(name: String): OperationResult

    suspend fun getConnector(name: String): KafkaConnector?

    suspend fun listConnectors(): List<String>
}

sealed interface OperationResult {
    data object Success : OperationResult

    data object Unchanged : OperationResult

    data class Rejected(
        val reason: String,
    ) : OperationResult

    data class TemporaryFailure(
        val reason: String,
    ) : OperationResult
}
