package no.nav.dagpenger.kafka.connect.operator

import com.fasterxml.jackson.databind.DeserializationFeature
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.jackson
import no.nav.dagpenger.kafka.connect.operator.client.KafkaConnectClient
import no.nav.dagpenger.kafka.connect.operator.client.OperationResult

private val logger = KotlinLogging.logger {}

class KtorKafkaConnectClient(
    private val baseUrl: String,
) : KafkaConnectClient,
    AutoCloseable {
    private val client =
        HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                jackson {
                    // Allow extra properties in responses
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            defaultRequest {
                url(baseUrl.trimEnd('/'))
            }
        }

    override suspend fun getConnector(name: String): KafkaConnector? {
        logger.debug { "Fetching connector: name=$name" }
        val response = client.get("/connectors/$name")
        return when (response.status) {
            HttpStatusCode.NotFound -> {
                logger.debug { "Connector not found: name=$name" }
                null
            }

            HttpStatusCode.OK -> {
                logger.debug { "Connector retrieved: name=$name" }
                response.body()
            }

            else -> {
                logger.error { "Unexpected response when fetching connector: name=$name, status=${response.status}" }
                throw IllegalStateException("Unexpected response status: ${response.status}, message: ${response.bodyAsText()}")
            }
        }
    }

    override suspend fun upsert(connector: KafkaConnector): OperationResult {
        val name = connector.name
        val hasConnector = client.get("/connectors/$name").status.isSuccess()
        when (hasConnector) {
            false -> {
                logger.debug { "Creating new connector via POST: name=$name" }
                val response =
                    client.post("/connectors") {
                        contentType(ContentType.Application.Json)
                        setBody(connector)
                    }

                return when (response.status) {
                    HttpStatusCode.Created -> {
                        logger.info { "Connector created successfully: name=$name" }
                        OperationResult.Success
                    }

                    else -> {
                        logger.error { "Failed to create connector: name=$name, status=${response.status}" }
                        throw IllegalStateException("Unexpected response status: ${response.status}, message: ${response.bodyAsText()}")
                    }
                }
            }

            true -> {
                logger.debug { "Updating existing connector via PUT: name=$name" }
                val response =
                    client.put("/connectors/$name/config") {
                        contentType(ContentType.Application.Json)
                        setBody(connector.config)
                    }

                return when (response.status) {
                    HttpStatusCode.OK -> {
                        logger.info { "Connector updated successfully: name=$name" }
                        OperationResult.Success
                    }

                    else -> {
                        logger.error { "Failed to update connector: name=$name, status=${response.status}" }
                        throw IllegalStateException("Unexpected response status: ${response.status}, message: ${response.bodyAsText()}")
                    }
                }
            }
        }
    }

    override suspend fun delete(name: String): OperationResult {
        logger.debug { "Deleting connector: name=$name" }
        val response = client.delete("/connectors/$name")
        return when (response.status) {
            HttpStatusCode.NoContent -> {
                logger.info { "Connector deleted successfully: name=$name" }
                OperationResult.Success
            }

            else -> {
                logger.error { "Failed to delete connector: name=$name, status=${response.status}" }
                throw IllegalStateException("Unexpected response status: ${response.status}")
            }
        }
    }

    override fun close() {
        logger.info { "Closing Kafka Connect client" }
        client.close()
    }
}
