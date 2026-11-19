package com.enhikyy.girassol.core.integration

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lifecycle contract for a first-party Girassol subsystem.
 *
 * Dependencies are explicit so that the application has one composition root
 * instead of screens independently constructing competing engines or stores.
 */
interface GirassolSubsystem {
    val id: String
    val dependsOn: Set<String> get() = emptySet()

    suspend fun start()
    suspend fun stop()
}

enum class RuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

/**
 * Deterministic lifecycle coordinator shared by the World, Cognitive, Memory,
 * Evolution, Learning, Social, Economy, Research and Native AI subsystems.
 *
 * This class deliberately does not instantiate fake adapters: concrete modules
 * must be registered by the Android composition root when their real adapters
 * are available.
 */
class GirassolRuntime(subsystems: Collection<GirassolSubsystem>) {
    private val mutex = Mutex()
    private val ordered = resolveOrder(subsystems)
    private val started = mutableListOf<GirassolSubsystem>()

    @Volatile
    var status: RuntimeStatus = RuntimeStatus.STOPPED
        private set

    val subsystemOrder: List<String>
        get() = ordered.map(GirassolSubsystem::id)

    suspend fun start() = mutex.withLock {
        check(status == RuntimeStatus.STOPPED) {
            "Runtime cannot start from status $status"
        }
        status = RuntimeStatus.STARTING
        try {
            for (subsystem in ordered) {
                subsystem.start()
                started += subsystem
            }
            status = RuntimeStatus.RUNNING
        } catch (failure: Throwable) {
            status = RuntimeStatus.FAILED
            stopStartedAfterFailure()
            throw failure
        }
    }

    suspend fun stop() = mutex.withLock {
        if (status == RuntimeStatus.STOPPED) return@withLock
        status = RuntimeStatus.STOPPING
        var firstFailure: Throwable? = null
        for (subsystem in started.asReversed()) {
            try {
                subsystem.stop()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        started.clear()
        status = if (firstFailure == null) RuntimeStatus.STOPPED else RuntimeStatus.FAILED
        firstFailure?.let { throw it }
    }

    private suspend fun stopStartedAfterFailure() {
        for (subsystem in started.asReversed()) {
            runCatching { subsystem.stop() }
        }
        started.clear()
    }

    private fun resolveOrder(input: Collection<GirassolSubsystem>): List<GirassolSubsystem> {
        val byId = input.associateBy(GirassolSubsystem::id)
        require(byId.size == input.size) { "Duplicate Girassol subsystem id" }
        require(byId.keys.none(String::isBlank)) { "Subsystem id cannot be blank" }

        byId.values.forEach { subsystem ->
            val missing = subsystem.dependsOn - byId.keys
            require(missing.isEmpty()) {
                "Subsystem ${subsystem.id} has missing dependencies: ${missing.sorted()}"
            }
        }

        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val result = mutableListOf<GirassolSubsystem>()

        fun visit(id: String) {
            if (id in visited) return
            check(visiting.add(id)) { "Subsystem dependency cycle at $id" }
            val subsystem = byId.getValue(id)
            subsystem.dependsOn.sorted().forEach(::visit)
            visiting.remove(id)
            visited += id
            result += subsystem
        }

        byId.keys.sorted().forEach(::visit)
        return result
    }
}
