// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dualmind.core.LocalModel
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ModelCatalog {
    const val QWEN_FILE = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"
    const val QWEN_SHA = "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8"
    const val QWEN_URL = "https://huggingface.co/litert-community/Qwen3-1.7B"
    const val GEMMA_URL = "https://huggingface.co/litert-community/Gemma3-1B-IT"
}
class ModelRepository(private val context: Context, private val dao: DualDao) {
    private val directory get() = File(context.filesDir, "models").apply { mkdirs() }
    suspend fun import(uri: Uri, source: String, expectedHash: String, onProgress: (Long) -> Unit): LocalModel =
        withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "modelo.litertlm"
            require(name.endsWith(".litertlm", ignoreCase = true)) { "Selecciona un archivo .litertlm, no .task ni .gguf." }
            require(expectedHash.isBlank() || expectedHash.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 inválido." }
            val trusted = if (name == ModelCatalog.QWEN_FILE) ModelCatalog.QWEN_SHA else expectedHash.trim().lowercase()
            val id = UUID.randomUUID().toString()
            val temporary = File(directory, "$id.part")
            val target = File(directory, "$id.litertlm")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var lastUpdate = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            bytes += size
                            require(bytes <= 12L * 1024 * 1024 * 1024) { "El modelo excede el límite de 12 GiB." }
                            check(directory.usableSpace > 128L * 1024 * 1024) { "Espacio insuficiente; deja al menos 128 MiB libres." }
                            output.write(buffer, 0, size); digest.update(buffer, 0, size)
                            if (bytes - lastUpdate >= 16 * 1024 * 1024) { onProgress(bytes); lastUpdate = bytes }
                        }
                    }
                } ?: error("No se pudo abrir el archivo.")
                require(bytes >= 1024 * 1024) { "Archivo demasiado pequeño para un modelo compatible." }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                require(trusted.isEmpty() || hash == trusted) { "El SHA-256 no coincide. Archivo corrupto o versión distinta; consulta el origen." }
                check(temporary.renameTo(target)) { "No se pudo guardar el modelo." }
                val catalogSource = when (source) {
                    "Qwen3-1.7B" -> ModelCatalog.QWEN_URL
                    "Gemma3-1B-IT" -> ModelCatalog.GEMMA_URL
                    else -> ""
                }
                val model = LocalModel(id, name, target.name, bytes, hash, catalogSource,
                    when (source) { "Qwen3-1.7B" -> "Apache-2.0"; "Gemma3-1B-IT" -> "Gemma Terms"; else -> "Consultar proveedor" },
                    trustedHash = trusted.isNotEmpty())
                dao.model(ModelRow(id, Json.encodeToString(model)))
                model
            } catch (e: Throwable) { temporary.delete(); target.delete(); throw e }
        }
    suspend fun delete(model: LocalModel) = withContext(Dispatchers.IO) {
        val file = File(directory, model.fileName)
        check(!file.exists() || file.delete()) { "No se pudo eliminar el archivo del modelo." }
        dao.unassign(model.id); dao.deleteModel(model.id)
    }
    suspend fun cleanOrphans(validIds: Set<String>) = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.extension == "part" || (it.extension == "litertlm" && it.nameWithoutExtension !in validIds) }
            ?.forEach { it.delete() }
    }
}
