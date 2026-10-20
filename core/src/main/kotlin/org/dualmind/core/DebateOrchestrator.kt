// SPDX-License-Identifier: Apache-2.0
package org.dualmind.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

class DebateOrchestrator(private val engine: InferenceEngine) {
    private val execution = Mutex()
    suspend fun run(request: DebateRequest, emit: suspend (DebateEvent) -> Unit): DebateResult {
        check(execution.tryLock()) { "Ya hay una generación en curso." }
        try {
            request.settings.validate()
            require(request.question.isNotBlank())
            require(request.mode == Mode.QUICK || request.modelB != null) { "Asigna un modelo al agente B." }
            request.identityA.validate(); request.identityB.validate()
            return withTimeout(request.settings.timeoutSeconds * 1000L) {
                var index = 0
                suspend fun stage(agent: String, kind: String, prior: List<String>, task: String): Generation {
                    currentCoroutineContext().ensureActive()
                    val model = if (agent == "A") request.modelA else requireNotNull(request.modelB)
                    val identity = if (agent == "A") request.identityA else request.identityB
                    val settings = request.settings.copy(contextTokens = minOf(request.settings.contextTokens, model.contextLimit))
                    settings.validate()
                    val instruction = identity.instruction()
                    val prompt = ContextSelector.prompt(request.question, request.history, request.memories,
                        prior, task, instruction, settings)
                    val current = index++
                    emit(DebateEvent.Started(current, agent, kind, model, prompt.memories))
                    val result = engine.generate(model, InferenceRequest(instruction, prompt.text, settings)) {
                        emit(DebateEvent.Text(current, it))
                    }
                    require(result.text.isNotBlank()) { "El modelo no devolvió texto. Prueba más tokens o revisa su compatibilidad." }
                    emit(DebateEvent.Finished(current, result))
                    return result
                }
                var answer = stage("A", if (request.mode == Mode.QUICK) "Final" else "Propuesta", emptyList(),
                    "Resuelve la petición. Separa hechos, supuestos e incertidumbres.").text
                if (request.mode == Mode.QUICK) return@withTimeout DebateResult(answer, 0)
                val count = if (request.mode == Mode.DEBATE) 1 else request.settings.rounds
                repeat(count) { round ->
                    val critique = stage("B", "Crítica ${round + 1}", listOf(answer),
                        "Evalúa independientemente la propuesta. Señala errores concretos, supuestos y correcciones. " +
                            "No repitas la respuesta. Si no encuentras objeciones sustanciales, dilo sin inventarlas.").text
                    val last = round == count - 1
                    answer = stage("A", if (last) "Final" else "Revisión ${round + 1}",
                        listOf("Propuesta:\n$answer", "Crítica:\n$critique"),
                        "Revisa la propuesta frente a la crítica. Resuelve objeciones con razones. " +
                            "Conserva desacuerdos y límites explícitos; no declares consenso sin evidencia. " +
                            if (last) "Entrega una respuesta final útil." else "Entrega una revisión concisa.").text
                }
                DebateResult(answer, count)
            }
        } finally { execution.unlock() }
    }
}
