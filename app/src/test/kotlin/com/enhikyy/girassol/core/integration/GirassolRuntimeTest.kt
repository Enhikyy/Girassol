package com.enhikyy.girassol.core.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GirassolRuntimeTest {
    private class FakeSubsystem(
        override val id: String,
        override val dependsOn: Set<String> = emptySet(),
        private val events: MutableList<String>,
        private val failStart: Boolean = false,
    ) : GirassolSubsystem {
        override suspend fun start() {
            events += "start:$id"
            check(!failStart) { "failed:$id" }
        }

        override suspend fun stop() {
            events += "stop:$id"
        }
    }

    @Test
    fun startsInDependencyOrderAndStopsInReverse() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = GirassolRuntime(
            listOf(
                FakeSubsystem("memory", setOf("world"), events),
                FakeSubsystem("world", events = events),
                FakeSubsystem("cognition", setOf("memory"), events),
            ),
        )

        assertEquals(listOf("world", "memory", "cognition"), runtime.subsystemOrder)
        runtime.start()
        assertEquals(RuntimeStatus.RUNNING, runtime.status)
        runtime.stop()
        assertEquals(
            listOf(
                "start:world", "start:memory", "start:cognition",
                "stop:cognition", "stop:memory", "stop:world",
            ),
            events,
        )
        assertEquals(RuntimeStatus.STOPPED, runtime.status)
    }

    @Test
    fun rejectsMissingDependenciesAndCycles() {
        assertThrows(IllegalArgumentException::class.java) {
            GirassolRuntime(listOf(FakeSubsystem("chat", setOf("missing"), mutableListOf())))
        }
        assertThrows(IllegalStateException::class.java) {
            GirassolRuntime(
                listOf(
                    FakeSubsystem("a", setOf("b"), mutableListOf()),
                    FakeSubsystem("b", setOf("a"), mutableListOf()),
                ),
            )
        }
    }

    @Test
    fun rollsBackAlreadyStartedSubsystemsWhenStartupFails() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = GirassolRuntime(
            listOf(
                FakeSubsystem("a", events = events),
                FakeSubsystem("b", setOf("a"), events, failStart = true),
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { runtime.start() }
        }
        assertEquals(listOf("start:a", "start:b", "stop:a"), events)
        assertEquals(RuntimeStatus.FAILED, runtime.status)
    }
}
