// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dualmind.android.AppContainer
import org.dualmind.android.DualMindApplication
import org.dualmind.android.data.*
import org.dualmind.core.*
import java.util.UUID

data class UiState(
    val ready: Boolean = false, val fatal: String? = null, val busy: Boolean = false,
    val progress: String = "", val notice: String? = null, val currentChat: String = UUID.randomUUID().toString(),
    val project: String = "", val mode: Mode = Mode.DEBATE,
    val chats: List<ChatRow> = emptyList(), val turns: List<TurnRow> = emptyList(),
    val transientTurns: List<TurnRow> = emptyList(), val runs: List<RunRow> = emptyList(),
    val transientRuns: List<RunRow> = emptyList(), val memories: List<MemoryRow> = emptyList(),
    val projects: List<ProjectRow> = emptyList(), val models: List<LocalModel> = emptyList(),
    val identities: List<Identity> = Identity.defaults(), val assignments: Map<String, String> = emptyMap(),
    val preferences: Preferences = Preferences(), val searchIds: Set<String>? = null
) {
    val currentTurns get() = (turns + transientTurns).filter { it.chatId == currentChat }.sortedWith(compareBy({ it.created }, { it.position }))
    val currentRuns get() = (runs + transientRuns).filter { it.chatId == currentChat }.sortedByDescending { it.created }
}
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private lateinit var container: AppContainer
    private var operation: Job? = null
    init {
        viewModelScope.launch {
            try {
                container = withContext(Dispatchers.IO) { (application as DualMindApplication).container.also { it.initialize() } }
                val dao = container.dao
                launch { dao.chats().collect { rows -> _state.update { it.copy(chats = rows) } } }
                launch { dao.turns().collect { rows -> _state.update { it.copy(turns = rows) } } }
                launch { dao.runs().collect { rows -> _state.update { it.copy(runs = rows) } } }
                launch { dao.memories().collect { rows -> _state.update { it.copy(memories = rows) } } }
                launch { dao.projects().collect { rows -> _state.update { it.copy(projects = rows) } } }
                launch { dao.models().collect { rows ->
                    _state.update { it.copy(models = rows.map { row -> Json.decodeFromString<LocalModel>(row.json) }) }
                } }
                launch { dao.identities().collect { rows ->
                    _state.update { it.copy(identities = rows.map { row -> Json.decodeFromString<Identity>(row.json) }) }
                } }
                launch { dao.assignments().collect { rows -> _state.update { it.copy(assignments = rows.associate { row -> row.agent to row.modelId }) } } }
                launch { container.preferences.flow.collect { prefs -> _state.update { it.copy(preferences = prefs) } } }
                _state.update { it.copy(ready = true) }
            } catch (e: Exception) { _state.update { it.copy(fatal = "No se pudo abrir el almacenamiento local cifrado. ${safeError(e)}") } }
        }
    }
    fun dismissNotice() { _state.update { it.copy(notice = null) } }
    fun showNotice(text: String) { _state.update { it.copy(notice = text) } }
    private fun task(label: String, action: suspend () -> Unit): Boolean {
        if (_state.value.busy || !_state.value.ready) return false
        if (!container.operations.tryLock()) {
            showNotice("Otra operación está terminando. Intenta de nuevo en unos segundos.")
            return false
        }
        _state.update { it.copy(busy = true, progress = label, notice = null) }
        operation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { action() }
            catch (e: CancellationException) { _state.update { it.copy(notice = "Operación cancelada.") } }
            catch (e: Exception) { _state.update { it.copy(notice = safeError(e)) } }
            finally { container.operations.unlock(); _state.update { it.copy(busy = false, progress = "") } }
        }
        return true
    }
    fun cancel() { operation?.cancel() }
    fun newChat() {
        if (!_state.value.busy) _state.update { it.copy(currentChat = UUID.randomUUID().toString(), transientTurns = emptyList(), transientRuns = emptyList()) }
    }
    fun openChat(chat: ChatRow) { if (!_state.value.busy) _state.update { it.copy(currentChat = chat.id, project = chat.project) } }
    fun setProject(project: String) { if (!_state.value.busy) { newChat(); _state.update { it.copy(project = project) } } }
    fun setMode(mode: Mode) { if (!_state.value.busy) _state.update { it.copy(mode = mode) } }
    fun search(query: String) {
        viewModelScope.launch {
            val ids = if (query.isBlank()) null else container.dao.searchChats(query.take(200)).toSet()
            _state.update { it.copy(searchIds = ids) }
        }
    }
    fun send(question: String): Boolean {
        if (question.isBlank() || _state.value.busy || !_state.value.ready) return false
        val snapshot = _state.value
        val modelA = snapshot.models.find { it.id == snapshot.assignments["A"] }
        val modelB = snapshot.models.find { it.id == snapshot.assignments["B"] }
        if (modelA == null || (snapshot.mode != Mode.QUICK && modelB == null)) {
            showNotice("Importa y asigna los modelos en Modelos. El modo Rápido solo necesita A."); return false
        }
        return task("Preparando contexto…") {
            val runId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val started = android.os.SystemClock.elapsedRealtime()
            val persisted = snapshot.preferences.saveHistory
            val dao = container.dao
            val query = ContextSelector.ftsQuery(question)
            val candidates = ((if (query.isBlank()) emptyList() else dao.retrieve(query, snapshot.project)) +
                dao.projectMemories(snapshot.project)).distinctBy { it.id }
            val memories = ContextSelector.choose(candidates.map { Memory(it.id, it.text, it.project, it.kind) }, 500)
            var run = RunRow(runId, snapshot.currentChat, snapshot.mode.label, "En curso", Json.encodeToString(memories), now)
            suspend fun saveRun(row: RunRow) {
                if (persisted) dao.run(row)
                else _state.update { it.copy(transientRuns = it.transientRuns.filterNot { r -> r.id == row.id } + row) }
            }
            suspend fun saveTurn(row: TurnRow) {
                if (persisted) dao.turn(row)
                else _state.update { it.copy(transientTurns = it.transientTurns.filterNot { r -> r.id == row.id } + row) }
            }
            val rows = mutableMapOf<Int, TurnRow>()
            val history = snapshot.currentTurns.filter { it.complete && (it.agent == "Tú" || it.kind == "Final") }
                .takeLast(8).joinToString("\n") { "${it.agent}: ${it.text}" }
            if (persisted) dao.chat(snapshot.chats.find { it.id == snapshot.currentChat }
                ?: ChatRow(snapshot.currentChat, question.take(100), snapshot.project, now))
            saveRun(run)
            saveTurn(TurnRow("$runId-user", snapshot.currentChat, runId, "Tú", "Pregunta", question, now, -1, true))
            var lastSaved = 0L
            try {
                container.orchestrator.run(DebateRequest(question, snapshot.mode, modelA, modelB,
                    snapshot.identities.first { it.agent == "A" }, snapshot.identities.first { it.agent == "B" },
                    memories, history, snapshot.preferences.generation)) { event ->
                    when (event) {
                        is DebateEvent.Started -> {
                            val row = TurnRow("$runId-${event.index}", snapshot.currentChat, runId, event.agent,
                                event.kind, "", now, event.index, model = event.model.name,
                                usedMemories = Json.encodeToString(event.memories))
                            rows[event.index] = row; saveTurn(row)
                            _state.update { it.copy(progress = "Agente ${event.agent} · ${event.kind} · Cargando / generando…") }
                        }
                        is DebateEvent.Text -> {
                            val row = rows.getValue(event.index).copy(text = event.text)
                            rows[event.index] = row
                            // Show streaming immediately; checkpoint at most twice per second.
                            if (persisted) _state.update { state -> state.copy(turns = state.turns.filterNot { it.id == row.id } + row) }
                            else saveTurn(row)
                            val time = android.os.SystemClock.elapsedRealtime()
                            if (time - lastSaved > 500) { saveTurn(row); lastSaved = time }
                        }
                        is DebateEvent.Finished -> {
                            val row = rows.getValue(event.index).copy(text = event.result.text, complete = true,
                                metrics = Json.encodeToString(event.result.metrics))
                            rows[event.index] = row; saveTurn(row)
                        }
                    }
                }
                run = run.copy(status = "Completo")
            } catch (e: TimeoutCancellationException) {
                run = run.copy(status = "Tiempo agotado", error = "Se alcanzó el tiempo máximo; las intervenciones parciales se conservan.")
            } catch (e: CancellationException) {
                run = run.copy(status = "Cancelado", error = "Generación detenida; resultado incompleto.")
                throw e
            } catch (e: Exception) {
                run = run.copy(status = "Error", error = safeError(e))
                throw e
            } finally {
                withContext(NonCancellable) {
                    rows.values.forEach { saveTurn(it) }
                    saveRun(run.copy(durationMs = android.os.SystemClock.elapsedRealtime() - started))
                }
            }
        }
    }
    fun saveMemory(row: MemoryRow) = task("Guardando recuerdo…") {
        require(row.text.isNotBlank() && row.text.length <= 10000 && row.project.length <= 120)
        container.dao.memory(row)
    }
    fun deleteMemory(id: Long) = task("Eliminando recuerdo…") { container.dao.deleteMemory(id) }
    fun saveProject(name: String, goal: String) = task("Guardando proyecto…") {
        require(name.isNotBlank() && name.length <= 120 && goal.length <= 2000)
        container.dao.project(ProjectRow(name.trim(), goal))
        if (goal.isNotBlank()) container.dao.memory(MemoryRow(text = goal, project = name.trim(), kind = "Objetivo"))
    }
    fun saveIdentity(identity: Identity) = task("Guardando identidad…") {
        identity.validate(); container.dao.identity(IdentityRow(identity.agent, Json.encodeToString(identity)))
    }
    fun savePreferences(preferences: Preferences) = task("Guardando configuración…") { container.preferences.save(preferences) }
    fun assign(agent: String, id: String) = task("Asignando modelo…") { container.dao.assign(AssignmentRow(agent, id)) }
    fun importModel(uri: Uri, source: String, hash: String) = task("Importando modelo…") {
        val model = container.models.import(uri, source, hash) { bytes ->
            _state.update { it.copy(progress = "Importando · ${bytes / 1024 / 1024} MiB") }
        }
        showNotice("${model.name} importado. Ejecuta «Probar» para validar la inferencia en este dispositivo.")
    }
    fun deleteModel(model: LocalModel) = task("Eliminando modelo…") { container.models.delete(model) }
    fun testModel(model: LocalModel) = task("Prueba real de ${model.name}…") {
        val result = withTimeout(120000) {
            container.engine.generate(model, InferenceRequest("Responde en español.", "Di hola en una frase.",
                _state.value.preferences.generation.copy(outputTokens = 64)), {})
        }
        require(result.text.isNotBlank()) { "El modelo no devolvió texto." }
        showNotice("Prueba real · ${result.metrics.backend} · ${result.metrics.totalMs} ms\n${result.text}")
    }
    fun export(uri: Uri, password: String) = task("Cifrando copia…") {
        withContext(Dispatchers.IO) {
            val chars = password.toCharArray()
            val bytes = try { BackupCrypto.encrypt(container.backup.export(), chars) } finally { chars.fill('\u0000') }
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("No se pudo escribir la copia.")
        }
        showNotice("Copia cifrada exportada. Conserva la contraseña: no se puede recuperar.")
    }
    fun restore(uri: Uri, password: String) = task("Validando e importando copia…") {
        withContext(Dispatchers.IO) {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = it.read(buffer); if (n < 0) break
                    require(output.size() + n <= BackupCrypto.MAX_BYTES + 128) { "Copia demasiado grande." }
                    output.write(buffer, 0, n)
                }; output.toByteArray()
            } ?: error("No se pudo leer la copia.")
            val chars = password.toCharArray()
            val plain = try { BackupCrypto.decrypt(bytes, chars) } finally { chars.fill('\u0000') }
            try { container.backup.restore(plain) } finally { plain.fill(0) }
        }
        _state.update { it.copy(currentChat = UUID.randomUUID().toString(), transientRuns = emptyList(), transientTurns = emptyList()) }
        showNotice("Copia restaurada. Los modelos instalados se conservaron.")
    }
    fun clearHistory() = task("Eliminando historial…") {
        container.db.withTransaction { container.dao.clearTurns(); container.dao.clearRuns(); container.dao.clearChats() }
        _state.update { it.copy(transientTurns = emptyList(), transientRuns = emptyList(), currentChat = UUID.randomUUID().toString()) }
    }
    fun clearAll() = task("Eliminando datos locales…") {
        state.value.models.forEach { container.models.delete(it) }
        container.db.withTransaction {
            with(container.dao) {
                clearTurns(); clearRuns(); clearChats(); clearMemories(); clearProjects(); clearIdentities()
                clearAssignments(); clearModels()
                Identity.defaults().forEach { identity(IdentityRow(it.agent, Json.encodeToString(it))) }
            }
        }
        container.preferences.reset()
        _state.update { it.copy(transientTurns = emptyList(), transientRuns = emptyList(),
            currentChat = UUID.randomUUID().toString(), project = "") }
        showNotice("Datos y modelos eliminados. Las copias exportadas se eliminan desde el selector de archivos.")
    }
    companion object {
        fun safeError(error: Exception): String = when (error) {
            is javax.crypto.AEADBadTagException -> "Contraseña incorrecta o copia dañada."
            is IllegalArgumentException, is IllegalStateException -> error.message?.take(300) ?: "Datos no válidos."
            is java.io.IOException -> "No se pudo leer o escribir el archivo. Comprueba espacio y permisos."
            else -> "La operación falló (${error.javaClass.simpleName}). Revisa el archivo, el contexto y la compatibilidad del modelo."
        }
    }
}
