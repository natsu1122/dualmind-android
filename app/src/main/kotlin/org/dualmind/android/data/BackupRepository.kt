// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dualmind.core.Identity

@Serializable
data class Archive(
    val version: Int = 1, val chats: List<ChatRow>, val turns: List<TurnRow>,
    val runs: List<RunRow>, val memories: List<MemoryRow>, val projects: List<ProjectRow>,
    val identities: List<IdentityRow>
)
class BackupRepository(private val db: DualDatabase) {
    private val dao get() = db.dao()
    suspend fun export(): ByteArray = db.withTransaction {
        Json.encodeToString(Archive(chats = dao.allChats(), turns = dao.allTurns(), runs = dao.allRuns(),
            memories = dao.allMemories(), projects = dao.allProjects(), identities = dao.allIdentities())).toByteArray()
    }
    suspend fun restore(bytes: ByteArray) {
        val archive = Json.decodeFromString<Archive>(bytes.toString(Charsets.UTF_8))
        validate(archive)
        db.withTransaction {
            dao.clearTurns(); dao.clearRuns(); dao.clearChats(); dao.clearMemories()
            dao.clearProjects(); dao.clearIdentities()
            archive.projects.forEach { dao.project(it) }
            archive.chats.forEach { dao.chat(it) }
            archive.runs.forEach { dao.run(if (it.status == "En curso") it.copy(status = "Interrumpido") else it) }
            archive.turns.forEach { dao.turn(it) }
            archive.memories.forEach { dao.memory(it) }
            archive.identities.forEach { dao.identity(it) }
        }
    }
    companion object {
        fun validate(a: Archive) {
            require(a.version == 1) { "Versión de copia no compatible." }
            require(a.chats.size <= 10000 && a.turns.size <= 100000 && a.memories.size <= 10000 && a.runs.size <= 50000)
            fun bounded(s: String, max: Int = 100000) = s.length <= max
            require(a.chats.all { bounded(it.id, 80) && bounded(it.title, 300) && bounded(it.project, 120) })
            require(a.chats.map { it.id }.distinct().size == a.chats.size)
            val chats = a.chats.map { it.id }.toSet()
            val runs = a.runs.map { it.id }.toSet()
            require(runs.size == a.runs.size)
            require(a.turns.map { it.id }.distinct().size == a.turns.size)
            require(a.memories.map { it.id }.distinct().size == a.memories.size)
            require(a.runs.all { it.chatId in chats && bounded(it.usedMemories) && bounded(it.error, 1000) })
            require(a.turns.all { it.chatId in chats && it.runId in runs && bounded(it.text) && bounded(it.metrics, 5000) })
            require(a.memories.all { it.id > 0 && it.text.isNotBlank() && bounded(it.text, 10000) && bounded(it.project, 120) })
            require(a.projects.size <= 1000 && a.projects.all { bounded(it.name, 120) && bounded(it.goal, 2000) })
            require(a.identities.size == 2 && a.identities.map { it.agent }.toSet() == setOf("A", "B"))
            a.identities.forEach { row ->
                val identity = Json.decodeFromString<Identity>(row.json)
                identity.validate()
                require(identity.agent == row.agent)
            }
        }
    }
}
