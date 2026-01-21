package no.nav.dagpenger.kafka.connect.operator

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@EnableKubernetesMockClient(crud = true)
class KubernetesConfigMapSourceTest {
    lateinit var server: KubernetesMockServer
    lateinit var client: KubernetesClient

    @Test
    fun `emits event for existing configmap when watch starts`(): Unit =
        runBlocking {
            client.resource(configMap("existing", "existing-connector")).inNamespace("default").create()

            val source = KubernetesConfigMapSource(client, "default")
            val events = async { source.events().take(1).toList() }

            val result = events.await()
            result shouldHaveSize 1
            result[0].connector.name shouldBe "existing-connector"
        }

    @Test
    fun `emits event when configmap is created`(): Unit =
        runBlocking {
            val source = KubernetesConfigMapSource(client, "default")
            val events = async { source.events().take(1).toList() }

            delay(100) // La watchen starte
            client.resource(configMap("new-map", "new-connector")).inNamespace("default").create()

            val result = events.await()
            result shouldHaveSize 1
            result[0].connector.name shouldBe "new-connector"
        }

    @Test
    fun `emits event when configmap is updated`(): Unit =
        runBlocking {
            client.resource(configMap("update-test", "original-connector")).inNamespace("default").create()

            val source = KubernetesConfigMapSource(client, "default")
            val events = async { source.events().take(2).toList() }

            delay(100)
            client.resource(configMap("update-test", "updated-connector")).inNamespace("default").update()

            val result = events.await()
            result shouldHaveSize 2
            result[0].connector.name shouldBe "original-connector"
            result[1].connector.name shouldBe "updated-connector"
        }

    @Test
    fun `emits event when configmap is deleted`(): Unit =
        runBlocking {
            client.resource(configMap("delete-test", "to-be-deleted")).inNamespace("default").create()

            val source = KubernetesConfigMapSource(client, "default")

            val events = async { source.events().take(2).toList() }

            delay(100)
            client
                .configMaps()
                .inNamespace("default")
                .withName("delete-test")
                .delete()

            val result = events.await()
            result shouldHaveSize 2
            result[0].connector.name shouldBe "to-be-deleted"
            result[1].connector.name shouldBe "to-be-deleted"
        }

    @Test
    fun `ignores configmaps without correct label`(): Unit =
        runBlocking {
            val source = KubernetesConfigMapSource(client, "default")

            val events = async { source.events().take(1).toList() }

            delay(100)
            client.resource(configMapWithoutLabel("unlabeled", "ignored")).inNamespace("default").create()
            client.resource(configMap("labeled", "included")).inNamespace("default").create()

            val result = events.await()
            result shouldHaveSize 1
            result[0].connector.name shouldBe "included"
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

    private fun configMapWithoutLabel(
        configMapName: String,
        connectorName: String,
    ): ConfigMap =
        ConfigMapBuilder()
            .withData<String, String>(connectorConfig(connectorName))
            .withNewMetadata()
            .withName(configMapName)
            .endMetadata()
            .build()

    private fun connectorConfig(name: String) =
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
