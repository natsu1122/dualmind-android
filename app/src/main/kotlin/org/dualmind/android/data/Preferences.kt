// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.dualmind.core.GenerationSettings

private val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val generation: GenerationSettings = GenerationSettings(),
    val saveHistory: Boolean = true, val theme: String = "Sistema")
class PreferenceStore(private val context: Context) {
    private val generation = stringPreferencesKey("generation")
    private val saveHistory = booleanPreferencesKey("saveHistory")
    private val theme = stringPreferencesKey("theme")
    val flow = context.preferences.data.map {
        Preferences(it[generation]?.let { value -> Json.decodeFromString<GenerationSettings>(value) }
            ?: GenerationSettings(), it[saveHistory] ?: true, it[theme] ?: "Sistema")
    }
    suspend fun save(value: Preferences) {
        value.generation.validate()
        context.preferences.edit {
            it[generation] = Json.encodeToString(value.generation)
            it[saveHistory] = value.saveHistory
            it[theme] = value.theme
        }
    }
    suspend fun reset() { context.preferences.edit { it.clear() } }
}
