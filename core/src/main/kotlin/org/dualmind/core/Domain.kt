// SPDX-License-Identifier: Apache-2.0
package org.dualmind.core

import kotlinx.serialization.Serializable

enum class Mode(val label: String) { QUICK("Rápido"), DEBATE("Debate"), DEEP("Profundo") }
@Serializable
data class Identity(
    val agent: String,
    val name: String,
    val purpose: String,
    val behavior: String,
    val language: String = "Español",
    val style: String = "Claro, preciso y respetuoso",
    val rules: String = "Expresa incertidumbre. No inventes hechos ni consenso."
) {
    fun instruction() = "Eres $name. Propósito: $purpose. Conducta: $behavior. " +
        "Idioma: $language. Estilo: $style. Reglas: $rules."
    fun validate() {
        require(agent in listOf("A", "B") && name.isNotBlank())
        require(listOf(name, purpose, behavior, language, style, rules).all { it.length <= 1000 })
    }
    companion object {
        fun defaults() = listOf(
            Identity("A", "Atlas", "Analizar y construir soluciones", "Sintetiza evidencias y corrige tus errores"),
            Identity("B", "Prisma", "Revisar de forma independiente", "Detecta contradicciones, supuestos y alternativas")
        )
    }
}
@Serializable
data class GenerationSettings(
    val contextTokens: Int = 4096,
    val outputTokens: Int = 512,
    val rounds: Int = 2,
    val timeoutSeconds: Int = 240,
    val temperature: Double = 0.6,
    val gpu: Boolean = false,
    val maxResponseChars: Int = 6000
) {
    fun validate() {
        require(contextTokens in 1024..8192 && outputTokens in 64..2048)
        require(outputTokens <= contextTokens / 2)
        require(rounds in 1..4 && timeoutSeconds in 30..900)
        require(temperature in 0.0..1.5 && maxResponseChars in 256..16000)
    }
}
@Serializable
data class LocalModel(
    val id: String, val name: String, val fileName: String,
    val bytes: Long, val sha256: String, val source: String = "",
    val license: String = "Revisar licencia del proveedor",
    val contextLimit: Int = 4096, val trustedHash: Boolean = false
)
@Serializable
data class Memory(val id: Long = 0, val text: String, val project: String = "", val kind: String = "Personal")
@Serializable
data class Metrics(
    val totalMs: Long = 0, val firstTokenMs: Long? = null,
    val outputTokens: Int? = null, val tokensPerSecond: Double? = null,
    val memoryPssKb: Long? = null, val backend: String = "", val loadMs: Long = 0,
    val stopReason: String = "Fin de respuesta"
)
data class Generation(val text: String, val metrics: Metrics = Metrics())
data class InferenceRequest(val instruction: String, val prompt: String, val settings: GenerationSettings)
interface InferenceEngine {
    /** Must release the native conversation and engine before returning, including on cancellation. */
    suspend fun generate(model: LocalModel, request: InferenceRequest, onText: suspend (String) -> Unit): Generation
}
data class DebateRequest(
    val question: String, val mode: Mode, val modelA: LocalModel, val modelB: LocalModel?,
    val identityA: Identity, val identityB: Identity,
    val memories: List<Memory>, val history: String, val settings: GenerationSettings
)
sealed interface DebateEvent {
    data class Started(val index: Int, val agent: String, val kind: String, val model: LocalModel,
                       val memories: List<Memory>) : DebateEvent
    data class Text(val index: Int, val text: String) : DebateEvent
    data class Finished(val index: Int, val result: Generation) : DebateEvent
}
data class DebateResult(val answer: String, val rounds: Int)
