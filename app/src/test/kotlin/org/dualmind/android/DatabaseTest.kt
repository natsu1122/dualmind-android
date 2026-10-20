package org.dualmind.android

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dualmind.android.data.*
import org.dualmind.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real SQLite/Room on Robolectric. SQLCipher/Keystore integration is a separate device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: DualDatabase
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setUp() {
        context.deleteDatabase("test.db")
        db = Room.databaseBuilder(context, DualDatabase::class.java, "test.db").build()
    }
    @After fun tearDown() { db.close(); context.deleteDatabase("test.db") }
    private suspend fun seed() {
        Identity.defaults().forEach { db.dao().identity(IdentityRow(it.agent, Json.encodeToString(it))) }
        db.dao().chat(ChatRow("chat", "Proyecto", "P", 1))
        db.dao().run(RunRow("run", "chat", "Debate", "En curso", "[]", 1))
        db.dao().turn(TurnRow("turn", "chat", "run", "A", "Propuesta", "Respuesta parcial", 1, 0))
    }
    @Test fun chatsIdentitiesAndPartialRunsSurviveRestart() = runBlocking {
        seed()
        db.dao().identity(IdentityRow("A", Json.encodeToString(Identity.defaults()[0].copy(name = "Persistente"))))
        db.close()
        db = Room.databaseBuilder(context, DualDatabase::class.java, "test.db").build()
        db.dao().recoverInterrupted()
        assertEquals("Interrumpido", db.dao().allRuns().single().status)
        assertEquals("Respuesta parcial", db.dao().allTurns().single().text)
        assertTrue(db.dao().allIdentities().first { it.agent == "A" }.json.contains("Persistente"))
        assertEquals(listOf("chat"), db.dao().searchChats("parcial"))
    }
    @Test fun ftsUpdatesDeletesAndProjectIsolation() = runBlocking {
        db.dao().memory(MemoryRow(1, "Kotlin preferido", "", "Personal"))
        db.dao().memory(MemoryRow(2, "Kotlin privado", "Otro", "Nota"))
        db.dao().memory(MemoryRow(3, "Kotlin proyecto", "P", "Decisión"))
        assertEquals(setOf(1L, 3L), db.dao().retrieve(ContextSelector.ftsQuery("Kotlin"), "P").map { it.id }.toSet())
        db.dao().memory(MemoryRow(1, "Java preferido", "", "Personal"))
        assertEquals(listOf(3L), db.dao().retrieve(ContextSelector.ftsQuery("Kotlin"), "P").map { it.id })
        db.dao().deleteMemory(3)
        assertTrue(db.dao().retrieve(ContextSelector.ftsQuery("Kotlin"), "P").isEmpty())
    }
    @Test fun archiveRestoresConversationAndIdentityWithoutTouchingModels() = runBlocking {
        seed()
        db.dao().model(ModelRow("m", "{}"))
        db.dao().memory(MemoryRow(1, "Una preferencia", "", "Personal"))
        val backup = BackupRepository(db).export()
        db.dao().clearMemories(); db.dao().clearTurns()
        BackupRepository(db).restore(backup)
        assertEquals(1, db.dao().allMemories().size)
        assertEquals(1, db.dao().allTurns().size)
        assertEquals("Interrumpido", db.dao().allRuns().single().status)
        assertEquals("m", db.dao().models().first().single().id)
    }
    @Test fun invalidArchiveLeavesExistingDataUntouched() = runBlocking {
        seed()
        val invalid = Archive(version = 999, chats = emptyList(), turns = emptyList(), runs = emptyList(),
            memories = emptyList(), projects = emptyList(), identities = emptyList())
        try { BackupRepository(db).restore(Json.encodeToString(invalid).toByteArray()); fail("Must reject") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, db.dao().allChats().size)
    }
    @Test fun replacingModelsDoesNotDeleteMemoryOrIdentity() = runBlocking {
        seed(); db.dao().memory(MemoryRow(1, "Preferencia", "", "Personal"))
        db.dao().model(ModelRow("old", "{}")); db.dao().assign(AssignmentRow("A", "old"))
        db.dao().unassign("old"); db.dao().deleteModel("old")
        db.dao().model(ModelRow("new", "{}")); db.dao().assign(AssignmentRow("A", "new"))
        assertEquals(1, db.dao().allMemories().size)
        assertEquals(2, db.dao().allIdentities().size)
        assertEquals("new", db.dao().assignments().first().single().modelId)
    }
}
