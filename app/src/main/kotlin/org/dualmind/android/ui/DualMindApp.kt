// SPDX-License-Identifier: Apache-2.0
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.dualmind.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.Json
import org.dualmind.android.data.*
import org.dualmind.core.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF83E5CF)
private val Lilac = Color(0xFFB9A7FF)

@Composable
fun DualMindApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dark = when (state.preferences.theme) { "Oscuro" -> true; "Claro" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(primary = Mint, secondary = Lilac,
        background = Color(0xFF10131D), surface = Color(0xFF171B28), surfaceContainer = Color(0xFF222738))
    else lightColorScheme(primary = Color(0xFF006B59), secondary = Color(0xFF6651A3),
        background = Color(0xFFF7F9FD), surface = Color.White)
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            if (state.fatal != null) {
                Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                    Text("No se pudo abrir DUAL MIND", style = MaterialTheme.typography.headlineSmall)
                    Text(state.fatal!!)
                    Text("No se borró la base existente. Reinicia la aplicación. Si persiste, conserva tu copia cifrada antes de restablecer los datos desde Android.")
                }
            } else if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else MainShell(state, vm)
        }
    }
}

@Composable private fun MainShell(s: UiState, vm: MainViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Chat", "Debate", "Memoria", "Modelos", "Identidad", "Ajustes")
    Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize()
        .widthIn(max = 1000.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(Mint, Lilac)), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, tint = Color(0xFF10131D))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("DUAL MIND", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Dos perspectivas. En tu dispositivo.", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.OfflineBolt, contentDescription = "Inferencia local", tint = MaterialTheme.colorScheme.primary)
        }
        PrimaryScrollableTabRow(selectedTabIndex = page, edgePadding = 12.dp) {
            tabs.forEachIndexed { index, label -> Tab(selected = page == index, onClick = { page = index }, text = { Text(label) }) }
        }
        AnimatedVisibility(s.busy) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.progress, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = vm::cancel) { Text("Cancelar") }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> ChatPage(s, vm)
                1 -> DebatePage(s)
                2 -> MemoryPage(s, vm)
                3 -> ModelsPage(s, vm)
                4 -> IdentityPage(s, vm)
                5 -> SettingsPage(s, vm)
            }
        }
    }
    if (s.notice != null) AlertDialog(onDismissRequest = vm::dismissNotice, title = { Text("DUAL MIND") },
        text = { SelectionContainer { Text(s.notice) } }, confirmButton = { TextButton(onClick = vm::dismissNotice) { Text("Entendido") } })
}

@Composable private fun PageTitle(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ChoiceRow(values: List<String>, selected: String, enabled: Boolean = true, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { select(value) }, enabled = enabled, label = { Text(value) }) }
    }
}
@Composable private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun ChatPage(s: UiState, vm: MainViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf(false) }
    val turns = s.currentTurns.filter { it.agent == "Tú" || it.kind == "Final" }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { history = true }, enabled = !s.busy) { Icon(Icons.Outlined.History, null); Text(" Historial") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::newChat, enabled = !s.busy) { Icon(Icons.Outlined.Add, null); Text(" Nuevo") }
        }
        ChoiceRow(Mode.entries.map { it.label }, s.mode.label, !s.busy) { label -> vm.setMode(Mode.entries.first { it.label == label }) }
        ChoiceRow(listOf("General") + s.projects.map { it.name }, s.project.ifBlank { "General" }, !s.busy) {
            vm.setProject(if (it == "General") "" else it)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)) {
            if (turns.isEmpty()) item {
                PageTitle("Piensa con dos perspectivas", "Atlas construye una respuesta. Prisma la revisa. Tú conservas el control.")
                InfoCard("Todo permanece aquí", "Importa tus modelos en Modelos. Los chats se guardan localmente si lo habilitas; los recuerdos personales solo se guardan cuando tú lo decides.")
            }
            items(turns, key = { it.id }) { turn -> TurnCard(turn, s, vm) }
            if (s.busy && s.currentTurns.lastOrNull()?.kind != "Final") item {
                InfoCard("Deliberación en curso", "Puedes seguir las intervenciones en Debate. La respuesta aparecerá aquí al llegar a la síntesis.")
            }
            s.currentRuns.filter { it.status != "Completo" && it.status != "En curso" }.forEach { run ->
                item("status-${run.id}") { InfoCard(run.status, run.error) }
            }
        }
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { if (it.length <= 12000) input = it }, Modifier.weight(1f),
                placeholder = { Text("Escribe tu pregunta…") }, maxLines = 5, enabled = !s.busy,
                shape = RoundedCornerShape(24.dp))
            FilledIconButton(onClick = { if (vm.send(input)) input = "" }, enabled = input.isNotBlank() && !s.busy,
                modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Outlined.Send, "Enviar pregunta") }
        }
        Text(if (s.preferences.saveHistory) "Historial local activado" else "Sesión temporal · no se guarda el chat",
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
    }
    if (history) {
        var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { history = false }, title = { Text("Conversaciones") },
            text = {
                Column {
                    OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text("Buscar en mensajes") })
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(s.chats.filter { s.searchIds == null || it.id in s.searchIds }, key = { it.id }) { chat ->
                            TextButton(onClick = { vm.openChat(chat); history = false }) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(chat.title, maxLines = 2)
                                    Text(chat.project.ifBlank { "General" } + " · " + DateFormat.getDateInstance().format(Date(chat.created)),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { history = false }) { Text("Cerrar") } })
    }
}
@Composable private fun TurnCard(turn: TurnRow, s: UiState? = null, vm: MainViewModel? = null) {
    val context = LocalContext.current
    val user = turn.agent == "Tú"
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (user) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (user) "TÚ" else "${turn.agent} · ${turn.kind}${if (turn.complete) "" else " · incompleto"}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (turn.text.isBlank()) Text("Preparando modelo…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else SelectionContainer { Text(turn.text, style = MaterialTheme.typography.bodyLarge) }
            if (!user && turn.model.isNotBlank()) Text(turn.model, style = MaterialTheme.typography.labelSmall)
            if (!user) {
                var showContext by remember { mutableStateOf(false) }
                val used = runCatching { Json.decodeFromString<List<Memory>>(turn.usedMemories) }.getOrDefault(emptyList())
                TextButton(onClick = { showContext = !showContext }) { Text("Recuerdos utilizados: ${used.size}") }
                if (showContext) Text(if (used.isEmpty()) "Esta intervención no incluyó recuerdos." else
                    used.joinToString("\n\n") { "#${it.id}: ${it.text}" }, style = MaterialTheme.typography.bodySmall)
            }
            if (turn.metrics.isNotBlank()) {
                val m = runCatching { Json.decodeFromString<Metrics>(turn.metrics) }.getOrNull()
                if (m != null) Text("${m.backend} · ${m.totalMs} ms · primer texto ${m.firstTokenMs ?: "—"} ms\n" +
                    "Carga ${m.loadMs} ms · PSS ${m.memoryPssKb?.div(1024) ?: "—"} MiB · " +
                    (m.tokensPerSecond?.let { String.format(Locale.ROOT, "%.1f tok/s", it) } ?: "tok/s no disponible") + "\n${m.stopReason}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (turn.text.isNotBlank()) Row {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DUAL MIND", turn.text))
                }) { Text("Copiar") }
                if (vm != null && s != null) TextButton(onClick = {
                    vm.saveMemory(MemoryRow(text = turn.text.take(10000), project = s.project, kind = "Nota"))
                }, enabled = !s.busy) { Text("Guardar recuerdo") }
            }
        }
    }
}
@Composable private fun DebatePage(s: UiState) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp)) {
        item { PageTitle("El debate, visible", "Propuestas, críticas y revisiones reales. Una conclusión no implica consenso.") }
        if (s.currentTurns.isEmpty()) item { InfoCard("Sin intervenciones", "Inicia una conversación desde Chat.") }
        s.currentRuns.reversed().forEach { run ->
            item("run-${run.id}") {
                InfoCard("${run.mode} · ${run.status}", "Tiempo total: ${run.durationMs} ms" + if (run.error.isNotEmpty()) "\n${run.error}" else "")
            }
            val memories = runCatching { Json.decodeFromString<List<Memory>>(run.usedMemories) }.getOrDefault(emptyList())
            item("memory-${run.id}") {
                var expanded by remember { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { expanded = !expanded }) { Text("Contexto seleccionado · ${memories.size} recuerdos") }
                    if (expanded) Text(if (memories.isEmpty()) "No se seleccionaron recuerdos." else
                        memories.joinToString("\n\n") { "#${it.id} · ${it.kind}\n${it.text}" })
                    if (expanded) Text("Cada ronda aplica su presupuesto y puede omitir contexto. La copia de selección conserva lo disponible al iniciar.", style = MaterialTheme.typography.labelSmall)
                }
            }
            items(s.currentTurns.filter { it.runId == run.id }, key = { it.id }) { TurnCard(it) }
        }
    }
}

@Composable private fun MemoryPage(s: UiState, vm: MainViewModel) {
    var edit by remember { mutableStateOf<MemoryRow?>(null) }
    var projectDialog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("Todos") }
    var delete by remember { mutableStateOf<MemoryRow?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp)) {
        item { PageTitle("Tu memoria, a tu criterio", "Recuperación textual local con SQLite FTS. Guarda solo lo que quieras conservar.") }
        item {
            Row {
                Button(onClick = { edit = MemoryRow(text = "", project = s.project, kind = "Personal") }, enabled = !s.busy) { Text("Nuevo recuerdo") }
                TextButton(onClick = { projectDialog = true }, enabled = !s.busy) { Text("Crear proyecto") }
            }
        }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Filtrar recuerdos") }) }
        item { ChoiceRow(listOf("Todos", "General") + s.projects.map { it.name }, selected) { selected = it } }
        items(s.memories.filter { it.text.contains(query, true) &&
            (selected == "Todos" || it.project == if (selected == "General") "" else selected) }, key = { it.id }) { memory ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${memory.kind} · ${memory.project.ifBlank { "General" }}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(memory.text, Modifier.padding(vertical = 8.dp))
                    Row {
                        TextButton(onClick = { edit = memory }, enabled = !s.busy) { Text("Editar") }
                        TextButton(onClick = { delete = memory }, enabled = !s.busy) { Text("Eliminar") }
                    }
                }
            }
        }
        if (s.memories.isEmpty()) item { InfoCard("Empieza por lo que importa", "Tus preferencias, una decisión de proyecto o un objetivo. No se extraen datos personales automáticamente.") }
    }
    edit?.let { row ->
        var text by remember(row) { mutableStateOf(row.text) }
        var project by remember(row) { mutableStateOf(row.project) }
        var kind by remember(row) { mutableStateOf(row.kind) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Recuerdo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { if (it.length <= 10000) text = it }, label = { Text("Información que autorizas guardar") }, minLines = 3)
                    ChoiceRow(listOf("Personal", "Nota", "Decisión", "Objetivo", "Pendiente"), kind) { kind = it }
                    ChoiceRow(listOf("General") + s.projects.map { it.name }, project.ifBlank { "General" }) { project = if (it == "General") "" else it }
                }
            }, confirmButton = { TextButton(onClick = { vm.saveMemory(row.copy(text = text, project = project, kind = kind)); edit = null },
                enabled = text.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancelar") } })
    }
    if (projectDialog) {
        var name by remember { mutableStateOf("") }; var goal by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { projectDialog = false }, title = { Text("Crear proyecto") },
            text = { Column {
                OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text("Nombre") })
                OutlinedTextField(goal, { if (it.length <= 2000) goal = it }, label = { Text("Objetivo inicial") })
            } }, confirmButton = { TextButton(onClick = { vm.saveProject(name, goal); projectDialog = false },
                enabled = name.isNotBlank() && name !in listOf("General", "Todos")) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { projectDialog = false }) { Text("Cancelar") } })
    }
    delete?.let { row -> Confirm("Eliminar recuerdo", "Se eliminará este recuerdo. Las copias históricas del contexto permanecen en los debates hasta limpiar el historial.",
        { delete = null }, { vm.deleteMemory(row.id); delete = null }) }
}

@Composable private fun ModelsPage(s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf("Qwen3-1.7B") }
    var hash by rememberSaveable { mutableStateOf("") }
    var gemmaAccepted by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf<LocalModel?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importModel(it, source, hash) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp)) {
        item { PageTitle("Dos modelos. Una conversación.", "Solo un motor permanece cargado a la vez. Importa archivos obtenidos de sus proveedores.") }
        item { InfoCard("Catálogo inicial", "Qwen3-1.7B dynamic INT4 · 977 MB.\nGemma3-1B-IT INT4 · 584 MB.\nEl espacio y la RAM de ejecución dependen del contexto. Prueba CPU primero en tu Xiaomi.") }
        item {
            ChoiceRow(listOf("Qwen3-1.7B", "Gemma3-1B-IT", "Otro"), source, !s.busy) { source = it }
            Row {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelCatalog.QWEN_URL))) }) { Text("Origen Qwen") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelCatalog.GEMMA_URL))) }) { Text("Origen Gemma") }
            }
            if (source == "Gemma3-1B-IT") Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(gemmaAccepted, { gemmaAccepted = it })
                Text("He revisado y aceptado las condiciones de Gemma en su sitio.", Modifier.weight(1f))
            }
            OutlinedTextField(hash, { if (it.length <= 64) hash = it.trim() }, Modifier.fillMaxWidth(),
                label = { Text("SHA-256 del proveedor (opcional)") }, singleLine = true)
            Text("Qwen: verificación automática del archivo del catálogo. Gemma: hash público no disponible sin acceso; puedes proporcionar uno confiable.",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = { import.launch(arrayOf("*/*")) }, enabled = !s.busy && (source != "Gemma3-1B-IT" || gemmaAccepted)) {
                Icon(Icons.Outlined.FileUpload, null); Text(" Importar .litertlm")
            }
        }
        items(s.models, key = { it.id }) { model ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.name, fontWeight = FontWeight.SemiBold)
                    Text("${model.bytes / 1024 / 1024} MiB · ${model.license}\nContexto configurado hasta ${model.contextLimit} tokens",
                        style = MaterialTheme.typography.bodySmall)
                    Text(if (model.trustedHash) "Integridad cotejada con SHA-256 de referencia" else "Huella local calculada; origen no autenticado por hash",
                        style = MaterialTheme.typography.labelSmall)
                    SelectionContainer { Text(model.sha256, style = MaterialTheme.typography.labelSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("A", "B").forEach { agent ->
                            FilterChip(s.assignments[agent] == model.id, { vm.assign(agent, model.id) },
                                enabled = !s.busy, label = { Text("Agente $agent") })
                        }
                    }
                    Row {
                        TextButton(onClick = { vm.testModel(model) }, enabled = !s.busy) { Text("Probar") }
                        if (model.source.isNotBlank()) TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.source)))
                        }) { Text("Origen y licencia") }
                        TextButton(onClick = { delete = model }, enabled = !s.busy) { Text("Eliminar") }
                    }
                }
            }
        }
    }
    delete?.let { model -> Confirm("Eliminar modelo", "Se borrará el archivo importado y sus asignaciones. Tus conversaciones y recuerdos se conservan.",
        { delete = null }, { vm.deleteModel(model); delete = null }) }
}

@Composable private fun IdentityPage(s: UiState, vm: MainViewModel) {
    var agent by rememberSaveable { mutableStateOf("A") }
    val identity = s.identities.find { it.agent == agent } ?: Identity.defaults().first { it.agent == agent }
    var draft by remember(identity) { mutableStateOf(identity) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Una identidad que permanece", "Configura cada perspectiva. Las identidades no dependen del modelo instalado.")
        ChoiceRow(listOf("A", "B"), agent, !s.busy) { agent = it }
        IdentityField("Nombre", draft.name) { draft = draft.copy(name = it) }
        IdentityField("Propósito", draft.purpose) { draft = draft.copy(purpose = it) }
        IdentityField("Comportamiento", draft.behavior) { draft = draft.copy(behavior = it) }
        IdentityField("Idioma", draft.language) { draft = draft.copy(language = it) }
        IdentityField("Estilo", draft.style) { draft = draft.copy(style = it) }
        IdentityField("Reglas de interacción", draft.rules) { draft = draft.copy(rules = it) }
        Button(onClick = { vm.saveIdentity(draft) }, enabled = !s.busy && draft.name.isNotBlank()) { Text("Guardar identidad") }
    }
}
@Composable private fun IdentityField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 1000) change(it) }, Modifier.fillMaxWidth(), label = { Text(label) })
}
@Composable private fun SettingsPage(s: UiState, vm: MainViewModel) {
    var prefs by remember(s.preferences) { mutableStateOf(s.preferences) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        it?.let { uri -> vm.export(uri, password) }; password = ""
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> vm.restore(uri, password) }; password = ""
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Tu dispositivo, tus límites", "Los cambios se aplican a la siguiente generación.")
        ChoiceRow(listOf("Sistema", "Oscuro", "Claro"), prefs.theme, !s.busy) { prefs = prefs.copy(theme = it) }
        SettingSlider("Contexto", prefs.generation.contextTokens, 1024..4096, 1024) {
            prefs = prefs.copy(generation = prefs.generation.copy(contextTokens = it, outputTokens = minOf(prefs.generation.outputTokens, it / 2)))
        }
        SettingSlider("Máximo de tokens de salida", prefs.generation.outputTokens, 64..1024, 64) {
            prefs = prefs.copy(generation = prefs.generation.copy(outputTokens = minOf(it, prefs.generation.contextTokens / 2)))
        }
        SettingSlider("Rondas de crítica en Profundo", prefs.generation.rounds, 1..4, 1) {
            prefs = prefs.copy(generation = prefs.generation.copy(rounds = it))
        }
        SettingSlider("Tiempo máximo (segundos)", prefs.generation.timeoutSeconds, 30..900, 30) {
            prefs = prefs.copy(generation = prefs.generation.copy(timeoutSeconds = it))
        }
        SettingSlider("Longitud máxima (caracteres)", prefs.generation.maxResponseChars, 1000..16000, 1000) {
            prefs = prefs.copy(generation = prefs.generation.copy(maxResponseChars = it))
        }
        Text("Temperatura: ${String.format(Locale.ROOT, "%.1f", prefs.generation.temperature)}")
        Slider(prefs.generation.temperature.toFloat(), { prefs = prefs.copy(generation = prefs.generation.copy(temperature = it.toDouble())) }, valueRange = 0f..1.5f, steps = 14)
        Toggle("Intentar GPU; volver a CPU si falla la carga", prefs.generation.gpu) { prefs = prefs.copy(generation = prefs.generation.copy(gpu = it)) }
        Toggle("Guardar conversaciones automáticamente", prefs.saveHistory) { prefs = prefs.copy(saveHistory = it) }
        Button(onClick = { vm.savePreferences(prefs) }, enabled = !s.busy) { Text("Guardar ajustes") }
        HorizontalDivider()
        Text("Copias cifradas", style = MaterialTheme.typography.titleLarge)
        Text("Incluyen chats, intervenciones, recuerdos, proyectos e identidades. No incluyen modelos ni claves del dispositivo.")
        OutlinedTextField(password, { if (it.length <= 512) password = it }, Modifier.fillMaxWidth(),
            label = { Text("Contraseña de la copia (mínimo 12 caracteres)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Row {
            TextButton(onClick = { export.launch("dualmind.dmbackup") }, enabled = !s.busy && password.length >= 12) { Text("Exportar") }
            TextButton(onClick = { confirmation = "restore" }, enabled = !s.busy && password.length >= 12) { Text("Importar") }
        }
        HorizontalDivider()
        InfoCard("Privacidad local", "Base cifrada con SQLCipher y clave protegida por Android Keystore. Sin telemetría, sin permiso de Internet y sin copias automáticas de Android. Las capturas de pantalla están protegidas. Copiar texto o exportar datos es una acción explícita tuya.")
        TextButton(onClick = { confirmation = "history" }, enabled = !s.busy) { Text("Limpiar historial") }
        TextButton(onClick = { confirmation = "all" }, enabled = !s.busy) { Text("Eliminar todos los datos locales", color = MaterialTheme.colorScheme.error) }
        Text("DUAL MIND 0.1.0-alpha · Apache-2.0\nLiteRT-LM 0.18.0 · Las licencias de modelos son independientes.\nLa precisión, el rendimiento GPU y el consumo requieren validación en el dispositivo.",
            style = MaterialTheme.typography.labelSmall)
    }
    when (confirmation) {
        "restore" -> Confirm("Restaurar copia", "La copia reemplazará el historial, recuerdos, proyectos e identidades actuales. Los modelos instalados se conservan. Exporta primero si necesitas preservar los datos actuales.",
            { confirmation = "" }, { confirmation = ""; restore.launch(arrayOf("*/*")) })
        "history" -> Confirm("Limpiar historial", "Se eliminarán todos los chats, debates y sus copias del contexto utilizado.",
            { confirmation = "" }, { confirmation = ""; vm.clearHistory() })
        "all" -> Confirm("Eliminar datos locales", "Se eliminarán chats, recuerdos, proyectos, identidades personalizadas, ajustes y modelos importados. Las copias exportadas permanecen fuera de la aplicación.",
            { confirmation = "" }, { confirmation = ""; vm.clearAll() })
    }
}
@Composable private fun SettingSlider(label: String, value: Int, range: IntRange, step: Int, change: (Int) -> Unit) {
    Text("$label: $value")
    Slider(value.toFloat(), { change((it / step).toInt().times(step).coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = ((range.last - range.first) / step - 1).coerceAtLeast(0))
}
@Composable private fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, change)
    }
}
@Composable private fun Confirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
