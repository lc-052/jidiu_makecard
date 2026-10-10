package com.yike.ai.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.studyPreferences by preferencesDataStore("study")

data class PreferencesState(
    val theme: String = "DARK",
    val fontScale: Float = 1f,
    val animations: Boolean = true,
    val generationCount: Int = 20,
    val generationCloze: Int = 0,
    val generationMode: String = "基础理解",
    val generationLanguage: String = "简体中文",
    val defaultTemplate: String = "NEON",
    val templateUpdatedAt: Long = 0,
)

class Preferences(context: Context) {
    private val store = context.studyPreferences
    val flow =
        store.data.map { p ->
            PreferencesState(
                p[stringPreferencesKey("theme")] ?: "DARK",
                p[floatPreferencesKey("font")] ?: 1f,
                p[booleanPreferencesKey("animations")] ?: true,
                p[intPreferencesKey("generationCount")] ?: 20,
                p[intPreferencesKey("generationCloze")] ?: 0,
                p[stringPreferencesKey("generationMode")] ?: "基础理解",
                p[stringPreferencesKey("generationLanguage")] ?: "简体中文",
                p[stringPreferencesKey("defaultTemplate")] ?: "NEON",
                p[longPreferencesKey("templateUpdatedAt")] ?: 0L,
            )
        }

    suspend fun save(s: PreferencesState) {
        require(s.generationCount in 1..500 && s.generationCloze in 0..100)
        store.edit {
            if (it[stringPreferencesKey("defaultTemplate")] != s.defaultTemplate)
                it[longPreferencesKey("templateUpdatedAt")] = System.currentTimeMillis()
            it[stringPreferencesKey("theme")] = s.theme
            it[floatPreferencesKey("font")] = s.fontScale
            it[booleanPreferencesKey("animations")] = s.animations
            it[intPreferencesKey("generationCount")] = s.generationCount
            it[intPreferencesKey("generationCloze")] = s.generationCloze
            it[stringPreferencesKey("generationMode")] = s.generationMode
            it[stringPreferencesKey("generationLanguage")] = s.generationLanguage
            it[stringPreferencesKey("defaultTemplate")] = s.defaultTemplate
        }
    }
}
