package no.nav.dagpenger.kafka.connect.operator

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.kafka.connect.operator.client.KafkaConnectClient
import no.nav.dagpenger.kafka.connect.operator.client.OperationResult
import no.nav.dagpenger.kafka.connect.operator.source.ConnectorConfigSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InitialReconcilerTest {
    private val source = mockk<ConnectorConfigSource>()
    private val kafkaConnect = mockk<KafkaConnectClient>(relaxed = true)
    private val reconciler = InitialReconciler(source, kafkaConnect)

    @Test
    fun `upserts all connectors and does nothing else when in sync`() =
        runBlocking {
            coEvery { source.getCurrentConnectors() } returns
                listOf(
                    KafkaConnector("connector-1", mapOf("connector.class" to "foo")),
                    KafkaConnector("connector-2", mapOf("connector.class" to "bar")),
                )
            coEvery { kafkaConnect.listConnectors() } returns listOf("connector-1", "connector-2")
            coEvery { kafkaConnect.upsert(any()) } returns OperationResult.Success

            val result = reconciler.reconcile()

            assertEquals(listOf("connector-1", "connector-2"), result.upserted)
            assertEquals(emptyList<String>(), result.deleted)
            assertEquals(emptyList<String>(), result.failed)
            coVerify(exactly = 2) { kafkaConnect.upsert(any()) }
            coVerify(exactly = 0) { kafkaConnect.delete(any()) }
        }

    @Test
    fun `upserts connectors and deletes orphaned ones`() =
        runBlocking {
            coEvery { source.getCurrentConnectors() } returns
                listOf(KafkaConnector("connector-1", mapOf("connector.class" to "foo")))
            coEvery { kafkaConnect.listConnectors() } returns listOf("connector-1", "orphan-1", "orphan-2")
            coEvery { kafkaConnect.upsert(any()) } returns OperationResult.Success
            coEvery { kafkaConnect.delete(any()) } returns OperationResult.Success

            val result = reconciler.reconcile()

            assertEquals(listOf("connector-1"), result.upserted)
            assertEquals(listOf("orphan-1", "orphan-2"), result.deleted)
            assertEquals(emptyList<String>(), result.failed)
            coVerify(exactly = 1) { kafkaConnect.upsert(any()) }
            coVerify(exactly = 1) { kafkaConnect.delete("orphan-1") }
            coVerify(exactly = 1) { kafkaConnect.delete("orphan-2") }
        }

    @Test
    fun `handles delete failures gracefully`() =
        runBlocking {
            coEvery { source.getCurrentConnectors() } returns emptyList()
            coEvery { kafkaConnect.listConnectors() } returns listOf("orphan-1", "orphan-2")
            coEvery { kafkaConnect.delete("orphan-1") } returns OperationResult.Success
            coEvery { kafkaConnect.delete("orphan-2") } throws RuntimeException("Connection failed")

            val result = reconciler.reconcile()

            assertEquals(emptyList<String>(), result.upserted)
            assertEquals(listOf("orphan-1"), result.deleted)
            assertEquals(listOf("orphan-2"), result.failed)
        }

    @Test
    fun `handles upsert failures gracefully`() =
        runBlocking {
            coEvery { source.getCurrentConnectors() } returns
                listOf(
                    KafkaConnector("connector-1", mapOf("connector.class" to "foo")),
                    KafkaConnector("connector-2", mapOf("connector.class" to "bar")),
                )
            coEvery { kafkaConnect.listConnectors() } returns emptyList()
            coEvery { kafkaConnect.upsert(match { it.name == "connector-1" }) } returns OperationResult.Success
            coEvery { kafkaConnect.upsert(match { it.name == "connector-2" }) } returns OperationResult.Rejected("Invalid config")

            val result = reconciler.reconcile()

            assertEquals(listOf("connector-1"), result.upserted)
            assertEquals(emptyList<String>(), result.deleted)
            assertEquals(listOf("connector-2"), result.failed)
        }

    @Test
    fun `does nothing when both K8s and Kafka Connect are empty`() =
        runBlocking {
            coEvery { source.getCurrentConnectors() } returns emptyList()
            coEvery { kafkaConnect.listConnectors() } returns emptyList()

            val result = reconciler.reconcile()

            assertEquals(emptyList<String>(), result.upserted)
            assertEquals(emptyList<String>(), result.deleted)
            assertEquals(emptyList<String>(), result.failed)
        }
}
