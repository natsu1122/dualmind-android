package org.dualmind.android

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun allowSyntheticQaCapture() {
        ui.runOnUiThread { ui.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    @After fun restoreCaptureProtection() {
        ui.runOnUiThread { ui.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    private fun tab(name: String) = ui.onNodeWithText(name, useUnmergedTree = true).performScrollTo().performClick()
    private fun screenshot(name: String) {
        // Only test execution can capture the synthetic, empty QA session.
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(500) // Let the emulator compositor present the frame before readback.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = File(ui.activity.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun opensAllScreensAndPersistsAnExplicitMemory() {
        ui.waitUntil(30_000) { ui.onAllNodesWithText("Chat").fetchSemanticsNodes().isNotEmpty() }
        screenshot("01-chat")
        tab("Modelos")
        ui.onNodeWithText("Dos modelos. Una conversación.").assertIsDisplayed()
        screenshot("02-modelos")
        tab("Memoria")
        val memory = "UI_TEST_EXPLICIT_MEMORY_${System.currentTimeMillis()}"
        ui.onNodeWithText("Nuevo recuerdo").performClick()
        ui.onNodeWithText("Información que autorizas guardar").performTextInput(memory)
        ui.onNodeWithText("Guardar", useUnmergedTree = true).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(memory).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(memory).assertIsDisplayed()
        screenshot("03-memoria")
        tab("Identidad")
        ui.onNodeWithText("Una identidad que permanece").assertIsDisplayed()
        screenshot("04-identidad")
        tab("Ajustes")
        ui.onNodeWithText("Tu dispositivo, tus límites").assertIsDisplayed()
        screenshot("05-ajustes")
        tab("Debate")
        ui.onNodeWithText("El debate, visible").assertIsDisplayed()
        screenshot("06-debate")
        tab("Chat")
        ui.onNodeWithText("Piensa con dos perspectivas").assertIsDisplayed()
    }
}
