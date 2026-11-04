package com.momosubtitle.core.common

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.momoDataStore by preferencesDataStore(name = "momo_settings")

data class MomoSettings(
    val transcribeThreads: Int,
    val chunkSeconds: Int,
    val defaultLanguage: String,
    val defaultMode: String,
    val activeModelId: String
)

class SettingsRepository(private val context: Context) {

    private val dataStore = context.momoDataStore

    val settings: Flow<MomoSettings> = dataStore.data.map { p ->
        MomoSettings(
            transcribeThreads = p[KEY_THREADS] ?: defaultThreads(),
            chunkSeconds = p[KEY_CHUNK_SECONDS] ?: 30,
            defaultLanguage = p[KEY_LANGUAGE] ?: "auto",
            defaultMode = p[KEY_MODE] ?: "translate",
            activeModelId = p[KEY_ACTIVE_MODEL] ?: "base-q5_1"
        )
    }

    suspend fun current(): MomoSettings = settings.first()

    suspend fun setThreads(value: Int) { dataStore.edit { it[KEY_THREADS] = value } }
    suspend fun setChunkSeconds(value: Int) { dataStore.edit { it[KEY_CHUNK_SECONDS] = value } }
    suspend fun setDefaultLanguage(value: String) { dataStore.edit { it[KEY_LANGUAGE] = value } }
    suspend fun setDefaultMode(value: String) { dataStore.edit { it[KEY_MODE] = value } }
    suspend fun setActiveModel(value: String) { dataStore.edit { it[KEY_ACTIVE_MODEL] = value } }

    private fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    private companion object {
        val KEY_THREADS = intPreferencesKey("transcribe_threads")
        val KEY_CHUNK_SECONDS = intPreferencesKey("chunk_seconds")
        val KEY_LANGUAGE = stringPreferencesKey("default_language")
        val KEY_MODE = stringPreferencesKey("default_mode")
        val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model_id")
    }
}
