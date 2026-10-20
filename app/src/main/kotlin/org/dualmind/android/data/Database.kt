// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable @Entity(tableName = "chats")
data class ChatRow(@PrimaryKey val id: String, val title: String, val project: String, val created: Long)
@Serializable @Entity(tableName = "turns", indices = [Index("chatId"), Index("runId")])
data class TurnRow(
    @PrimaryKey val id: String, val chatId: String, val runId: String, val agent: String,
    val kind: String, val text: String, val created: Long, val position: Int,
    val complete: Boolean = false, val metrics: String = "", val model: String = "",
    val usedMemories: String = "[]"
)
@Serializable @Entity(tableName = "runs", indices = [Index("chatId")])
data class RunRow(
    @PrimaryKey val id: String, val chatId: String, val mode: String,
    val status: String, val usedMemories: String, val created: Long, val durationMs: Long = 0,
    val error: String = ""
)
@Serializable @Entity(tableName = "memories")
data class MemoryRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val id: Long = 0,
    val text: String, val project: String, val kind: String
)
@Fts4(contentEntity = MemoryRow::class)
@Entity(tableName = "memory_fts")
data class MemoryFts(val text: String)
@Serializable @Entity(tableName = "projects")
data class ProjectRow(@PrimaryKey val name: String, val goal: String)
@Serializable @Entity(tableName = "identities")
data class IdentityRow(@PrimaryKey val agent: String, val json: String)
@Entity(tableName = "models")
data class ModelRow(@PrimaryKey val id: String, val json: String)
@Entity(tableName = "assignments")
data class AssignmentRow(@PrimaryKey val agent: String, val modelId: String)

@Dao
interface DualDao {
    @Query("SELECT * FROM chats ORDER BY created DESC") fun chats(): Flow<List<ChatRow>>
    @Query("SELECT * FROM turns ORDER BY created, position") fun turns(): Flow<List<TurnRow>>
    @Query("SELECT * FROM runs ORDER BY created DESC") fun runs(): Flow<List<RunRow>>
    @Query("SELECT * FROM memories ORDER BY rowid DESC") fun memories(): Flow<List<MemoryRow>>
    @Query("SELECT * FROM projects ORDER BY name") fun projects(): Flow<List<ProjectRow>>
    @Query("SELECT * FROM identities ORDER BY agent") fun identities(): Flow<List<IdentityRow>>
    @Query("SELECT * FROM models") fun models(): Flow<List<ModelRow>>
    @Query("SELECT * FROM assignments") fun assignments(): Flow<List<AssignmentRow>>
    @Query("SELECT memories.* FROM memories JOIN memory_fts ON memories.rowid=memory_fts.rowid WHERE memory_fts MATCH :query AND (project='' OR project=:project) ORDER BY memories.rowid DESC LIMIT 40")
    suspend fun retrieve(query: String, project: String): List<MemoryRow>
    @Query("SELECT * FROM memories WHERE project=:project AND project!='' ORDER BY rowid DESC LIMIT 8")
    suspend fun projectMemories(project: String): List<MemoryRow>
    @Query("SELECT * FROM turns WHERE chatId=:chatId AND complete=1 AND (agent='Tú' OR kind='Final') ORDER BY created DESC, position DESC LIMIT 8")
    suspend fun history(chatId: String): List<TurnRow>
    @Query("SELECT id FROM chats WHERE title LIKE '%' || :query || '%' OR id IN (SELECT chatId FROM turns WHERE text LIKE '%' || :query || '%')")
    suspend fun searchChats(query: String): List<String>
    @Upsert suspend fun chat(row: ChatRow)
    @Upsert suspend fun turn(row: TurnRow)
    @Upsert suspend fun run(row: RunRow)
    @Upsert suspend fun memory(row: MemoryRow)
    @Upsert suspend fun project(row: ProjectRow)
    @Upsert suspend fun identity(row: IdentityRow)
    @Upsert suspend fun model(row: ModelRow)
    @Upsert suspend fun assign(row: AssignmentRow)
    @Query("DELETE FROM memories WHERE rowid=:id") suspend fun deleteMemory(id: Long)
    @Query("DELETE FROM models WHERE id=:id") suspend fun deleteModel(id: String)
    @Query("DELETE FROM assignments WHERE modelId=:id") suspend fun unassign(id: String)
    @Query("UPDATE runs SET status='Interrumpido', error='La aplicación se cerró antes de terminar.' WHERE status='En curso'")
    suspend fun recoverInterrupted()
    @Query("DELETE FROM turns") suspend fun clearTurns()
    @Query("DELETE FROM runs") suspend fun clearRuns()
    @Query("DELETE FROM chats") suspend fun clearChats()
    @Query("DELETE FROM memories") suspend fun clearMemories()
    @Query("DELETE FROM projects") suspend fun clearProjects()
    @Query("DELETE FROM identities") suspend fun clearIdentities()
    @Query("DELETE FROM assignments") suspend fun clearAssignments()
    @Query("DELETE FROM models") suspend fun clearModels()
    @Query("SELECT * FROM chats") suspend fun allChats(): List<ChatRow>
    @Query("SELECT * FROM turns") suspend fun allTurns(): List<TurnRow>
    @Query("SELECT * FROM runs") suspend fun allRuns(): List<RunRow>
    @Query("SELECT * FROM memories") suspend fun allMemories(): List<MemoryRow>
    @Query("SELECT * FROM projects") suspend fun allProjects(): List<ProjectRow>
    @Query("SELECT * FROM identities") suspend fun allIdentities(): List<IdentityRow>
}

@Database(entities = [ChatRow::class, TurnRow::class, RunRow::class, MemoryRow::class,
    MemoryFts::class, ProjectRow::class, IdentityRow::class, ModelRow::class, AssignmentRow::class],
    version = 1, exportSchema = true)
abstract class DualDatabase : RoomDatabase() { abstract fun dao(): DualDao }
