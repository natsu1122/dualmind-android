package org.dualmind.android

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.dualmind.android.data.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    @Test fun keystoreKeyReopensEncryptedDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        val key = DatabaseKey.load(context)
        val second = DatabaseKey.load(context)
        assertArrayEquals(key, second)
        val name = "instrumentation-encrypted.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, DualDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db = open()
        try {
            db.dao().memory(MemoryRow(1, "DEVICE_TEST_SENTINEL", "", "Nota"))
            db.close()
            assertFalse(context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1).contains("DEVICE_TEST_SENTINEL"))
            db = open()
            assertEquals("DEVICE_TEST_SENTINEL", db.dao().allMemories().single().text)
        } finally { db.close(); context.deleteDatabase(name); key.fill(0); second.fill(0) }
    }
}
