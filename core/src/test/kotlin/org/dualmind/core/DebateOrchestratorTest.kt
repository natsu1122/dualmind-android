package org.dualmind.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DebateOrchestratorTest {
    private class RecordingEngine : InferenceEngine {
        val agents = mutableListOf<String>()
        val prompts = mutableListOf<InferenceRequest>()
        var delayMs = 0L
        var failure = false
        var active = 0
        var maximumActive = 0
        override suspend fun generate(model: LocalModel, request: InferenceRequest, onText: suspend (String) -> Unit): Generation {
            active++; maximumActive = maxOf(maximumActive, active)
            try {
                agents += model.id; prompts += request
                delay(delayMs)
                if (failure) error("Native failure")
                onText("Respuesta")
                return Generation("Respuesta ${agents.size}")
            } finally { active-- }
        }
    }
    private fun request(mode: Mode = Mode.DEBATE) = DebateRequest("¿Cómo organizar un proyecto?", mode,
        LocalModel("A", "A", "A.litertlm", 1, ""), LocalModel("B", "B", "B.litertlm", 1, ""),
        Identity.defaults()[0], Identity.defaults()[1], listOf(Memory(1, "Prefiero Kotlin")), "",
        GenerationSettings())

    @Test fun quickOnlyUsesA() = runTest {
        val engine = RecordingEngine()
        val result = DebateOrchestrator(engine).run(request(Mode.QUICK).copy(modelB = null)) {}
        assertEquals(listOf("A"), engine.agents)
        assertEquals(0, result.rounds)
    }
    @Test fun debateUsesABAAndIncludesCritique() = runTest {
        val engine = RecordingEngine(); val events = mutableListOf<DebateEvent>()
        val result = DebateOrchestrator(engine).run(request()) { events += it }
        assertEquals(listOf("A", "B", "A"), engine.agents)
        assertEquals(1, engine.maximumActive)
        assertEquals("Respuesta 3", result.answer)
        assertContains(engine.prompts.last().prompt, "Crítica:\nRespuesta 2")
        assertEquals(3, events.filterIsInstance<DebateEvent.Finished>().size)
    }
    @Test fun deepRespectsRoundLimit() = runTest {
        val engine = RecordingEngine()
        DebateOrchestrator(engine).run(request(Mode.DEEP).copy(settings = GenerationSettings(rounds = 4))) {}
        assertEquals(9, engine.agents.size)
        assertEquals(4, engine.agents.count { it == "B" })
    }
    @Test fun invalidRoundsNeverInvokeEngine() = runTest {
        val engine = RecordingEngine()
        assertFailsWith<IllegalArgumentException> {
            DebateOrchestrator(engine).run(request().copy(settings = GenerationSettings(rounds = 0))) {}
        }
        assertTrue(engine.agents.isEmpty())
    }
    @Test fun inferenceFailureDoesNotInventAnswerAndReleasesLock() = runTest {
        val engine = RecordingEngine().apply { failure = true }
        val orchestrator = DebateOrchestrator(engine)
        assertFailsWith<IllegalStateException> { orchestrator.run(request()) {} }
        assertEquals(0, engine.active)
        engine.failure = false
        assertEquals("Respuesta 4", orchestrator.run(request()) {}.answer)
    }
    @Test fun cancellationStopsFurtherRounds() = runTest {
        val engine = RecordingEngine().apply { delayMs = 10000 }
        val orchestrator = DebateOrchestrator(engine)
        val job = launch { orchestrator.run(request(Mode.DEEP)) {} }
        runCurrent(); job.cancelAndJoin()
        assertEquals(listOf("A"), engine.agents)
        assertEquals(0, engine.active)
        engine.delayMs = 0
        orchestrator.run(request(Mode.QUICK)) {}
    }
    @Test fun rejectsDuplicateExecution() = runTest {
        val engine = RecordingEngine().apply { delayMs = 10000 }
        val orchestrator = DebateOrchestrator(engine)
        val first = launch { orchestrator.run(request()) {} }
        runCurrent()
        assertFailsWith<IllegalStateException> { orchestrator.run(request()) {} }
        first.cancelAndJoin()
    }
    @Test fun timeoutReleasesResources() = runTest {
        val engine = RecordingEngine().apply { delayMs = 60000 }
        assertFailsWith<TimeoutCancellationException> {
            DebateOrchestrator(engine).run(request().copy(settings = GenerationSettings(timeoutSeconds = 30))) {}
        }
        assertEquals(0, engine.active)
    }
    @Test fun replacementAndIdentityAreReadFromEachRequest() = runTest {
        val engine = RecordingEngine(); val orchestrator = DebateOrchestrator(engine)
        orchestrator.run(request(Mode.QUICK)) {}
        orchestrator.run(request(Mode.QUICK).copy(
            modelA = LocalModel("new", "new", "new.litertlm", 1, ""),
            identityA = Identity.defaults()[0].copy(name = "Nueva identidad"))) {}
        assertEquals(listOf("A", "new"), engine.agents)
        assertContains(engine.prompts.last().instruction, "Nueva identidad")
    }
    @Test fun missingCriticDoesNotStart() = runTest {
        val engine = RecordingEngine()
        assertFailsWith<IllegalArgumentException> { DebateOrchestrator(engine).run(request().copy(modelB = null)) {} }
        assertTrue(engine.agents.isEmpty())
    }
}
