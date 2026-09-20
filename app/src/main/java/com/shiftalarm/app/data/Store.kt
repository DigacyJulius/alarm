package com.shiftalarm.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "shiftalarm")

class Store(private val context: Context) {

    private val key = stringPreferencesKey("app_data")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val data: Flow<AppData> = context.dataStore.data.map { prefs ->
        prefs[key]?.let {
            runCatching { json.decodeFromString<AppData>(it) }.getOrNull()
        } ?: AppData()
    }

    suspend fun save(d: AppData) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(d)
        }
    }
}
