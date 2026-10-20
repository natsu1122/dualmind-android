// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.dualmind.android.data.*
import org.dualmind.android.inference.LiteRtEngine
import org.dualmind.core.*

class AppContainer(context: Context) {
    val operations = Mutex()
    private val initialization = Mutex()
    private var initialized = false
    val db: DualDatabase
    init {
        System.loadLibrary("sqlcipher")
        val key = DatabaseKey.load(context)
        db = Room.databaseBuilder(context, DualDatabase::class.java, "dualmind.db")
            .openHelperFactory(SupportOpenHelperFactory(key)).build()
    }
    val dao get() = db.dao()
    val preferences = PreferenceStore(context)
    val models = ModelRepository(context, dao)
    val backup = BackupRepository(db)
    val engine = LiteRtEngine(context)
    val orchestrator = DebateOrchestrator(engine)
    suspend fun initialize() = initialization.withLock {
        if (initialized) return@withLock
        dao.recoverInterrupted()
        if (dao.allIdentities().isEmpty()) Identity.defaults().forEach {
            dao.identity(IdentityRow(it.agent, Json.encodeToString(it)))
        }
        models.cleanOrphans(dao.models().first().map { it.id }.toSet())
        initialized = true
    }
}
