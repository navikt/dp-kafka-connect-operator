package no.nav.dagpenger.kafka.connect.operator

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait.forHttp
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Testcontainers
@EnableKubernetesMockClient(crud = true)
class E2EIntegrationTest {
    lateinit var server: KubernetesMockServer
    lateinit var client: KubernetesClient

    private companion object {
        @Container
        private val kafkaConnectServer =
            ComposeContainer(
                DockerImageName.parse("docker:25.0.2"),
                File("src/test/resources/docker-compose.yaml"),
            ).withExposedService(
                "kafka-connect",
                8083,
                forHttp("/connectors").forStatusCode(200),
            )
    }

    private val baseUrl by lazy {
        val serviceHost = kafkaConnectServer.getServiceHost("kafka-connect", 8083)
        val servicePort = kafkaConnectServer.getServicePort("kafka-connect", 8083)
        "http://$serviceHost:$servicePort"
    }

    private val ktorClient: KtorKafkaConnectClient by lazy { KtorKafkaConnectClient(baseUrl) }

    @Test
    fun `emits event for existing configmap when watch starts`(): Unit =
        runBlocking {
            // Create a pre-existing config map before starting the reconciler
            client.resource(configMap("pre-existing", "existing-connector")).inNamespace("default").create()

            val source = KubernetesConfigMapSource(client, "default")
            val reconciler = ConnectorReconciler(source, ktorClient)
            val job = reconciler.start()

            // Create a new config map to trigger a create event
            client.resource(configMap("created", "created-after-start")).inNamespace("default").create()
            delay(10.milliseconds)

            // Update the newly created config map to trigger an update event
            val updated = client.resource(configMap("created", "updated-after-start")).inNamespace("default").update()

            // Wait for all upserts to have taken place
            eventually {
                ktorClient.getConnector("updated-after-start")?.name shouldBe "updated-after-start"
            }

            // Delete the config map to trigger a delete event
            client
                .configMaps()
                .inNamespace("default")
                .withName(updated.metadata.name)
                .delete()

            eventually {
                ktorClient.getConnector("updated-after-start") shouldBe null
            }

            job.cancelAndJoin()
        }

    private fun configMap(
        configMapName: String,
        connectorName: String,
    ): ConfigMap =
        ConfigMapBuilder()
            .withData<String, String>(connectorConfig(connectorName))
            .withNewMetadata()
            .withName(configMapName)
            .addToLabels("destination", "connect")
            .endMetadata()
            .build()

    private fun connectorConfig(name: String): Map<String, String> =
        """
        {
          "name": "$name",
          "config": {
            "connector.class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
            "source.cluster.alias": "source-cluster"
          }
        }    
        """.trimIndent().let {
            mapOf("connector-$name.json" to it)
        }
}
