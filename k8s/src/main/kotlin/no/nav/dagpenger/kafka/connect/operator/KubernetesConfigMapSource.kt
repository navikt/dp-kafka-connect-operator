package no.nav.dagpenger.kafka.connect.operator

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nav.dagpenger.kafka.connect.operator.name
import no.nav.dagpenger.kafka.connect.operator.source.ConnectorConfigSource
import kotlin.time.Duration.Companion.minutes

class KubernetesConfigMapSource(
    private val client: KubernetesClient,
    private val namespace: String,
    private val metrics: OperatorMetrics? = null,
) : ConnectorConfigSource {
    init {
        logger.info { "Initializing ConfigMap watcher: namespace=$namespace, label=destination:connect" }
    }

    override fun events(): Flow<KafkaConnectorEvent> =
        callbackFlow {
            val handler =
                object : ResourceEventHandler<ConfigMap> {
                    override fun onAdd(obj: ConfigMap) {
                        metrics?.recordConfigMapEvent()
                        val configMapName = obj.metadata?.name
                        logger.info { "ConfigMap added: name=$configMapName" }
                        runCatching { obj.toEvents() }
                            .onSuccess { connectors ->
                                logger.info { "Parsed ${connectors.size} connector(s) from ConfigMap: name=$configMapName" }
                                connectors.forEach { trySend(KafkaConnectorEvent(EventType.ADDED, it)) }
                            }.onFailure { e ->
                                logger.error(e) { "Failed to parse ConfigMap: name=$configMapName" }
                                close(e)
                            }
                    }

                    override fun onUpdate(
                        oldObj: ConfigMap,
                        newObj: ConfigMap,
                    ) {
                        metrics?.recordConfigMapEvent()
                        val configMapName = newObj.metadata?.name
                        logger.info { "ConfigMap updated: name=$configMapName" }
                        runCatching { newObj.toEvents() }
                            .onSuccess { connectors ->
                                logger.info { "Parsed ${connectors.size} connector(s) from updated ConfigMap: name=$configMapName" }
                                connectors.forEach { trySend(KafkaConnectorEvent(EventType.UPDATED, it)) }
                            }.onFailure { e ->
                                logger.error(e) { "Failed to parse updated ConfigMap: name=$configMapName" }
                                close(e)
                            }
                    }

                    override fun onDelete(
                        obj: ConfigMap,
                        deletedFinalStateUnknown: Boolean,
                    ) {
                        metrics?.recordConfigMapEvent()
                        val configMapName = obj.metadata?.name
                        logger.info { "ConfigMap deleted: name=$configMapName, finalStateUnknown=$deletedFinalStateUnknown" }
                        runCatching { obj.toEvents() }
                            .onSuccess { connectors ->
                                logger.info { "Parsed ${connectors.size} connector(s) from deleted ConfigMap: name=$configMapName" }
                                connectors.forEach {
                                    trySend(KafkaConnectorEvent(EventType.DELETED, it))
                                }
                            }.onFailure { e ->
                                logger.error(e) { "Failed to parse deleted ConfigMap: name=$configMapName" }
                                close(e)
                            }
                    }
                }

            val watch =
                client
                    .configMaps()
                    .inNamespace(namespace)
                    .withLabel("destination", "connect")
                    .inform(
                        handler,
                        10.minutes.inWholeMilliseconds,
                    )

            logger.info { "ConfigMap watcher started successfully" }

            awaitClose {
                logger.info { "Closing ConfigMap watcher" }
                watch.close()
            }
        }

    fun close() {
        logger.info { "Closing Kubernetes client" }
        client.close()
    }

    private fun ConfigMap.toEvents(): List<KafkaConnector> = data?.map { (_, config) -> parseConnectorConfig(config) } ?: emptyList()

    private fun parseConnectorConfig(json: String): KafkaConnector {
        val parsed: ConnectorJson = objectMapper.readValue(json)
        return KafkaConnector(parsed.name, parsed.config)
    }

    private data class ConnectorJson(
        val name: String,
        val config: Map<String, String>,
    )

    private companion object {
        private val logger = KotlinLogging.logger { }
        private val objectMapper = jacksonObjectMapper()
    }
}
