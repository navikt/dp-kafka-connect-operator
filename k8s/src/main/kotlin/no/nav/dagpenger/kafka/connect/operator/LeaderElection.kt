package no.nav.dagpenger.kafka.connect.operator

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Wraps Fabric8's Lease-based leader election so that [work] only ever runs
 * on one replica at a time. Owns the full lifecycle of that work (starting
 * it on election, cancelling it on demotion) so callers don't need to manage
 * threads, coroutine scopes or jobs themselves.
 */
class LeaderElection(
    private val client: KubernetesClient,
    private val namespace: String,
    private val lockName: String = "dp-kafka-connect-operator-lock",
    private val identity: String = System.getenv("HOSTNAME") ?: "unknown",
) {
    private val electorExecutor = Executors.newSingleThreadExecutor()
    private val workScope = CoroutineScope(Dispatchers.Default)
    private var activeWork: Job? = null

    /**
     * Blocks the calling thread for as long as the process runs, contending
     * for leadership and running [work] only while elected leader.
     * [onLeadershipChanged] is notified on every transition, e.g. to update
     * the readiness endpoint.
     */
    fun runElectedWork(
        onLeadershipChanged: (isLeader: Boolean) -> Unit = {},
        work: suspend CoroutineScope.() -> Unit,
    ) {
        val config =
            LeaderElectionConfigBuilder()
                .withName(lockName)
                .withLock(LeaseLock(namespace, lockName, identity))
                .withLeaseDuration(Duration.ofSeconds(15))
                .withRenewDeadline(Duration.ofSeconds(10))
                .withRetryPeriod(Duration.ofSeconds(2))
                .withReleaseOnCancel(true)
                .withLeaderCallbacks(
                    LeaderCallbacks(
                        { onElected(onLeadershipChanged, work) },
                        { onDemoted(onLeadershipChanged) },
                        { newLeader -> logger.info { "New leader observed: $newLeader" } },
                    ),
                ).build()

        // run() blocks and retries forever - this is why Main.kt can just
        // call this function last and never needs its own thread for it.
        LeaderElectorBuilder(client, electorExecutor)
            .withConfig(config)
            .build()
            .run()
    }

    private fun onElected(
        onLeadershipChanged: (Boolean) -> Unit,
        work: suspend CoroutineScope.() -> Unit,
    ) {
        logger.info { "Became leader: identity=$identity" }
        onLeadershipChanged(true)
        activeWork =
            workScope.launch {
                runCatching { work() }
                    .onFailure { e ->
                        logger.error(e) { "Elected work failed unexpectedly, exiting" }
                        exitProcess(1)
                    }
            }
    }

    private fun onDemoted(onLeadershipChanged: (Boolean) -> Unit) {
        logger.warn { "Lost leadership: identity=$identity" }
        onLeadershipChanged(false)
        activeWork?.cancel()
        activeWork = null
    }

    fun close() {
        workScope.cancel()
        electorExecutor.shutdownNow()
    }
}
