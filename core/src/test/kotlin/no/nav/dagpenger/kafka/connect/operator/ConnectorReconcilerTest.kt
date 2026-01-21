package no.nav.dagpenger.kafka.connect.operator

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.kafka.connect.operator.client.KafkaConnectClient
import no.nav.dagpenger.kafka.connect.operator.source.ConnectorConfigSource
import org.junit.jupiter.api.Test

class ConnectorReconcilerTest {
    private val source = mockk<ConnectorConfigSource>()
    private val kafkaConnect = mockk<KafkaConnectClient>(relaxed = true)
    private val reconciler = ConnectorReconciler(source, kafkaConnect)

    @Test
    fun `reconciles desired state for a connector`() {
        coEvery { source.events() } returns
            listOf(KafkaConnectorEvent(EventType.ADDED, KafkaConnector("foo", mapOf("connector.class" to "foo")))).asFlow()

        runBlocking {
            reconciler.start()

            coVerify {
                kafkaConnect.upsert(any())
            }
        }
    }

    @Test
    fun `deletes connector if the event type is deleted`() {
        coEvery { source.events() } returns listOf(KafkaConnectorEvent(EventType.DELETED, KafkaConnector("foo", emptyMap()))).asFlow()

        runBlocking {
            reconciler.start()

            coVerify {
                kafkaConnect.delete(any())
            }
        }
    }
}
