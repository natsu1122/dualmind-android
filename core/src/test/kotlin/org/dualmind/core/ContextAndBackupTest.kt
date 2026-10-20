package org.dualmind.core

import org.junit.Test
import kotlin.test.*

class ContextAndBackupTest {
    @Test fun ftsInputIsQuotedAndOperatorsAreNotExecutable() {
        assertEquals("\"hola\" OR \"or\" OR \"proyecto\"", ContextSelector.ftsQuery("hola\" OR * proyecto"))
        assertEquals("", ContextSelector.ftsQuery("** \" -"))
    }
    @Test fun unicodeClippingPreservesCodepointsAndBudget() {
        val text = ContextSelector.clip("😀á東京", 7)
        assertEquals("😀á", text)
        assertTrue(ContextSelector.cost(text) <= 7)
    }
    @Test fun selectionIsBoundedAndDoesNotTakeWholeDatabase() {
        val memories = (1..100).map { Memory(it.toLong(), "Kotlin") }
        assertTrue(ContextSelector.choose(memories, 10000).size <= 8)
        assertTrue(ContextSelector.choose(memories, 1).isEmpty())
    }
    @Test fun promptKeepsBothViewsAndReportsOnlyIncludedMemories() {
        val settings = GenerationSettings(contextTokens = 2048, outputTokens = 256)
        val output = ContextSelector.prompt("Pregunta", "h".repeat(10000),
            listOf(Memory(1, "x".repeat(10000)), Memory(2, "Kotlin")),
            listOf("PROPUESTA " + "a".repeat(5000), "CRÍTICA " + "b".repeat(5000)),
            "Responde", "Identidad", settings)
        assertContains(output.text, "PROPUESTA")
        assertContains(output.text, "CRÍTICA")
        assertEquals(listOf(2L), output.memories.map { it.id })
        assertTrue(ContextSelector.cost(output.text) + settings.outputTokens < settings.contextTokens)
    }
    @Test fun oversizedQuestionRejectedInsteadOfSilentlyTruncated() {
        assertFailsWith<IllegalArgumentException> {
            ContextSelector.prompt("x".repeat(50000), "", emptyList(), emptyList(), "", "", GenerationSettings())
        }
    }
    @Test fun encryptedBackupRoundTripsAndIsRandomized() {
        val password = "long-test-password".toCharArray()
        val plain = "memorias á 東京".toByteArray()
        val one = BackupCrypto.encrypt(plain, password)
        val two = BackupCrypto.encrypt(plain, password)
        assertFalse(one.contentEquals(two))
        assertContentEquals(plain, BackupCrypto.decrypt(one, password))
        assertFalse(one.toString(Charsets.UTF_8).contains("memorias"))
    }
    @Test fun wrongPasswordAndTamperFailClosed() {
        val encrypted = BackupCrypto.encrypt("private".toByteArray(), "correct-password".toCharArray())
        assertFails { BackupCrypto.decrypt(encrypted, "wrong-password".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertFails { BackupCrypto.decrypt(encrypted, "correct-password".toCharArray()) }
    }
    @Test fun malformedBackupRejected() {
        assertFailsWith<IllegalArgumentException> { BackupCrypto.decrypt(ByteArray(4), "correct-password".toCharArray()) }
        assertFailsWith<IllegalArgumentException> { BackupCrypto.encrypt(ByteArray(1), "short".toCharArray()) }
    }
}
