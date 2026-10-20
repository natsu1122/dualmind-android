// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.inference

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dualmind.core.*
import java.io.File

@OptIn(ExperimentalApi::class)
class LiteRtEngine(private val context: Context) : InferenceEngine {
    private val gate = Mutex()
    override suspend fun generate(model: LocalModel, request: InferenceRequest,
                                  onText: suspend (String) -> Unit): Generation = gate.withLock {
        withContext(Dispatchers.IO) {
            val started = SystemClock.elapsedRealtime()
            val file = File(File(context.filesDir, "models"), model.fileName)
            require(file.isFile && file.length() == model.bytes) { "El modelo falta o cambió de tamaño. Vuelve a importarlo." }
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            // Conservative admission estimate, not a guarantee of the runtime's peak allocation.
            // In testing, the 977 MB Qwen file exhausted a 4 GiB Android emulator during native loading.
            val minimumFree = maxOf(1024L * 1024 * 1024, model.bytes * 3 + 512L * 1024 * 1024)
            check(!memory.lowMemory && memory.availMem > minimumFree) {
                "Memoria insuficiente para cargar este modelo: se estiman al menos ${minimumFree / 1024 / 1024} MiB libres. Cierra otras aplicaciones o usa un modelo menor."
            }
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            ExperimentalFlags.enableBenchmark = true
            var engine: Engine? = null
            var conversation: Conversation? = null
            var backend = if (request.settings.gpu) "GPU" else "CPU"
            val done = CompletableDeferred<Unit>()
            val chunks = Channel<String>(Channel.UNLIMITED)
            var submitted = false
            try {
                fun initialize(gpu: Boolean): Engine {
                    val candidate = Engine(EngineConfig(modelPath = file.absolutePath,
                        backend = if (gpu) Backend.GPU() else Backend.CPU(threadCount = 4),
                        maxNumTokens = request.settings.contextTokens, cacheDir = ":nocache"))
                    // The native factory owns cleanup if initialization throws before returning a handle.
                    candidate.initialize()
                    return candidate
                }
                try { engine = initialize(request.settings.gpu) }
                catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!request.settings.gpu) throw e
                    backend = "CPU (fallback de GPU)"
                    engine = initialize(false)
                }
                currentCoroutineContext().ensureActive()
                val loaded = SystemClock.elapsedRealtime()
                conversation = engine.createConversation(ConversationConfig(
                    // Some Gemma templates reject the system role; include identity in the user content.
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = request.settings.temperature),
                    maxOutputToken = request.settings.outputTokens,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                    automaticToolCalling = false
                ))
                val active = conversation
                var first: Long? = null
                val text = StringBuilder()
                active.sendMessageAsync(request.instruction + "\n\n" + request.prompt, object : MessageCallback {
                    override fun onMessage(message: Message) { chunks.trySend(message.toString()) }
                    override fun onDone() { chunks.close(); done.complete(Unit) }
                    override fun onError(throwable: Throwable) {
                        chunks.close(throwable); done.complete(Unit)
                    }
                })
                submitted = true
                var capped = false
                try { for (part in chunks) {
                    currentCoroutineContext().ensureActive()
                    if (part.isEmpty() || capped) continue
                    if (first == null) first = SystemClock.elapsedRealtime() - started
                    text.append(part.take(request.settings.maxResponseChars - text.length))
                    onText(text.toString())
                    if (text.length >= request.settings.maxResponseChars) { capped = true; active.cancelProcess() }
                } } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!capped) throw e
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                val benchmark = runCatching { active.getBenchmarkInfo() }.getOrNull()
                Generation(text.toString(), Metrics(totalMs = elapsed, firstTokenMs = first,
                    outputTokens = benchmark?.lastDecodeTokenCount,
                    tokensPerSecond = benchmark?.lastDecodeTokensPerSecond?.takeIf { it.isFinite() && it >= 0 },
                    memoryPssKb = Debug.getPss(), backend = backend, loadMs = loaded - started,
                    stopReason = if (capped) "Límite de caracteres: respuesta recortada"
                        else if ((benchmark?.lastDecodeTokenCount ?: 0) >= request.settings.outputTokens)
                            "Límite de tokens: la respuesta puede estar incompleta" else "Fin de respuesta"))
            } catch (e: OutOfMemoryError) {
                throw IllegalStateException("No hay RAM suficiente. Reduce el contexto y prueba un modelo menor.", e)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (submitted && !done.isCompleted) {
                        conversation?.cancelProcess()
                        // Never delete a native conversation while its asynchronous callback is in flight.
                        done.await()
                    }
                    try { conversation?.close() } finally { engine?.close(); chunks.cancel() }
                }
            }
        }
    }
}
