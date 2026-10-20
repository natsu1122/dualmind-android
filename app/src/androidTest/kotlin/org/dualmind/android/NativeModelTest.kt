package org.dualmind.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.dualmind.android.inference.LiteRtEngine
import org.dualmind.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Optional explicit native smoke test. No weights are bundled and no download occurs in tests. */
@RunWith(AndroidJUnit4::class)
class NativeModelTest {
    @Test fun cpuDebateGeneratesInThreeSequentialLoads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelPath = InstrumentationRegistry.getArguments().getString("modelPath")
        assumeTrue("Supply -e modelPath with an app-readable .litertlm file", modelPath != null)
        val directory = File(context.filesDir, "models").apply { mkdirs() }
        val modelFile = File(directory, "instrumentation.litertlm")
        File(requireNotNull(modelPath)).copyTo(modelFile, overwrite = true)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val model = LocalModel("instrumentation", "Explicit test model", modelFile.name, modelFile.length(), hash)
            val engine = LiteRtEngine(context)
            val identities = Identity.defaults()
            val agents = mutableListOf<String>()
            val results = mutableListOf<Generation>()
            // One supplied real file, three separate native loads. This does not certify a second model family.
            val debate = DebateOrchestrator(engine).run(DebateRequest(
                "¿Cuánto es dos más dos? Responde brevemente.", Mode.DEBATE, model, model,
                identities[0], identities[1], emptyList(), "",
                GenerationSettings(contextTokens = 2048, outputTokens = 64, timeoutSeconds = 900)
            )) { event ->
                when (event) {
                    is DebateEvent.Started -> agents += event.agent
                    is DebateEvent.Finished -> {
                        results += event.result
                        println("DUALMIND_NATIVE_SMOKE: ${event.result.text} | ${event.result.metrics}")
                    }
                    is DebateEvent.Text -> Unit
                }
            }
            assertEquals(listOf("A", "B", "A"), agents)
            assertEquals(3, results.size)
            assertEquals(1, debate.rounds)
            assertTrue(debate.answer.isNotBlank())
            results.forEach { result ->
                assertTrue(result.text.isNotBlank())
                assertEquals("CPU", result.metrics.backend)
                assertTrue(result.metrics.totalMs > 0)
            }
        } finally { modelFile.delete() }
    }
}
