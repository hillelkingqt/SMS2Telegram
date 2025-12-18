package com.example.telegramforwarder.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {

    companion object {
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val GEMINI_API_KEYS = stringPreferencesKey("gemini_api_keys")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
        val IS_SMS_ENABLED = booleanPreferencesKey("is_sms_enabled")
        val IS_EMAIL_ENABLED = booleanPreferencesKey("is_email_enabled")

        // New features
        val IS_MISSED_CALL_ENABLED = booleanPreferencesKey("is_missed_call_enabled")
        val IS_BATTERY_NOTIFY_ENABLED = booleanPreferencesKey("is_battery_notify_enabled")
        val BATTERY_LOW_THRESHOLD = floatPreferencesKey("battery_low_threshold")
        val BATTERY_HIGH_THRESHOLD = floatPreferencesKey("battery_high_threshold")
    }

    val botToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TELEGRAM_BOT_TOKEN]
    }

    val chatId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TELEGRAM_CHAT_ID]
    }

    val geminiApiKeys: Flow<List<String>> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEYS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    val isSmsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_SMS_ENABLED] ?: true
    }

    val isEmailEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_EMAIL_ENABLED] ?: true
    }

    val isMissedCallEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_MISSED_CALL_ENABLED] ?: false // Default false or true? User requested to be able to disable it, implied enabled by default maybe? sticking to false for safety or true? Let's go with false until configured.
    }

    val isBatteryNotifyEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_BATTERY_NOTIFY_ENABLED] ?: false
    }

    val batteryLowThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BATTERY_LOW_THRESHOLD] ?: 20f
    }

    val batteryHighThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BATTERY_HIGH_THRESHOLD] ?: 90f
    }

    suspend fun saveBotToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_BOT_TOKEN] = token
        }
    }

    suspend fun saveChatId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_CHAT_ID] = id
        }
    }

    suspend fun saveGeminiKeys(keys: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEYS] = keys.joinToString(",")
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setSmsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SMS_ENABLED] = enabled
        }
    }

    suspend fun setEmailEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_EMAIL_ENABLED] = enabled
        }
    }

    suspend fun setMissedCallEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_MISSED_CALL_ENABLED] = enabled
        }
    }

    suspend fun setBatteryNotifyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BATTERY_NOTIFY_ENABLED] = enabled
        }
    }

    suspend fun setBatteryLowThreshold(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[BATTERY_LOW_THRESHOLD] = value
        }
    }

    suspend fun setBatteryHighThreshold(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[BATTERY_HIGH_THRESHOLD] = value
        }
    }
}
