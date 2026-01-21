package no.nav.dagpenger.kafka.connect.operator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.kafka.connect.operator.client.OperationResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import java.nio.file.Paths
import java.time.Duration

class KtorKafkaConnectClientTest {
    companion object {
        private val logger = KotlinLogging.logger { }
        private val specmatic =
            GenericContainer("specmatic/specmatic:latest").apply {
                withExposedPorts(9000)
                withCommand(
                    "stub",
                    "--strict",
                    "--port",
                    "9000",
                )

                val specsDir = Paths.get("src/test/resources/specmatic").toAbsolutePath().toString()
                withFileSystemBind(specsDir, "/usr/src/app", BindMode.READ_ONLY)

                withStartupTimeout(Duration.ofSeconds(30))
                withLogConsumer {
                    print(it.utf8String)
                }
            }

        @JvmStatic
        @BeforeAll
        fun start() {
            specmatic.start()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            specmatic.stop()
        }
    }

    private val client by lazy { KtorKafkaConnectClient(baseUrl = "http://localhost:${specmatic.getMappedPort(9000)}") }

    @Test
    fun `handles 404 when getting missing connector`() {
        runBlocking {
            val connector = client.getConnector("missing")

            connector shouldBe null
        }
    }

    @Test
    fun `handles fetching existing connector configuration`() {
        runBlocking {
            val connector = client.getConnector("existing")

            connector?.name shouldBe "existing"
        }
    }

    @Test
    fun `creates new connector successfully`() {
        runBlocking {
            val connector =
                KafkaConnector(
                    name = "new-connector",
                    config =
                        mapOf(
                            "connector.class" to "org.apache.kafka.connect.mirror.MirrorSourceConnector",
                            "source.cluster.alias" to "source-cluster",
                        ),
                )
            val result = client.upsert(connector)

            result shouldBe OperationResult.Success
        }
    }

    @Test
    fun `updates existing connector successfully`() {
        runBlocking {
            val connector =
                KafkaConnector(
                    name = "existing",
                    config =
                        mapOf(
                            "connector.class" to "org.apache.kafka.connect.mirror.MirrorSourceConnector",
                            "source.cluster.alias" to "updated-cluster",
                        ),
                )
            val result = client.upsert(connector)

            result shouldBe OperationResult.Success
        }
    }

    @Test
    fun `deletes connector successfully`() {
        runBlocking {
            val result = client.delete("to-delete")

            result shouldBe OperationResult.Success
        }
    }
}
