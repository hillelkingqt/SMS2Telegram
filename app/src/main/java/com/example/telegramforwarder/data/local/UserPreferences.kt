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

        // New features
        val IS_MISSED_CALL_ENABLED = booleanPreferencesKey("is_missed_call_enabled")
        val IS_BATTERY_NOTIFY_ENABLED = booleanPreferencesKey("is_battery_notify_enabled")
        val IS_ENHANCED_BATTERY_ALERTS_ENABLED = booleanPreferencesKey("is_enhanced_battery_alerts_enabled")
        val BATTERY_LOW_THRESHOLD = floatPreferencesKey("battery_low_threshold")
        val BATTERY_HIGH_THRESHOLD = floatPreferencesKey("battery_high_threshold")
        val IS_BOT_POLLING_ENABLED = booleanPreferencesKey("is_bot_polling_enabled")

        // New System Event Triggers
        val IS_NOTIFY_BOOT_COMPLETED = booleanPreferencesKey("is_notify_boot_completed")
        val IS_NOTIFY_APP_UPDATED = booleanPreferencesKey("is_notify_app_updated")
        val IS_NOTIFY_POWER_CONNECTED = booleanPreferencesKey("is_notify_power_connected")
        val IS_NOTIFY_POWER_DISCONNECTED = booleanPreferencesKey("is_notify_power_disconnected")
        val IS_NOTIFY_AIRPLANE_MODE_ON = booleanPreferencesKey("is_notify_airplane_mode_on")
        val IS_NOTIFY_AIRPLANE_MODE_OFF = booleanPreferencesKey("is_notify_airplane_mode_off")
        val IS_NOTIFY_WIFI_CONNECTED = booleanPreferencesKey("is_notify_wifi_connected")
        val IS_NOTIFY_WIFI_DISCONNECTED = booleanPreferencesKey("is_notify_wifi_disconnected")
        val IS_NOTIFY_BLUETOOTH_CONNECTED = booleanPreferencesKey("is_notify_bluetooth_connected")
        val IS_NOTIFY_BLUETOOTH_DISCONNECTED = booleanPreferencesKey("is_notify_bluetooth_disconnected")
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

    val isMissedCallEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_MISSED_CALL_ENABLED] ?: false // Default false or true? User requested to be able to disable it, implied enabled by default maybe? sticking to false for safety or true? Let's go with false until configured.
    }

    val isBatteryNotifyEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_BATTERY_NOTIFY_ENABLED] ?: false
    }

    val isEnhancedBatteryAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_ENHANCED_BATTERY_ALERTS_ENABLED] ?: true
    }

    val batteryLowThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BATTERY_LOW_THRESHOLD] ?: 20f
    }

    val batteryHighThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BATTERY_HIGH_THRESHOLD] ?: 90f
    }

    val isBotPollingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_BOT_POLLING_ENABLED] ?: false
    }

    // New System Event Flows
    val isNotifyBootCompleted: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_BOOT_COMPLETED] ?: true }
    val isNotifyAppUpdated: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_APP_UPDATED] ?: true }
    val isNotifyPowerConnected: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_POWER_CONNECTED] ?: false }
    val isNotifyPowerDisconnected: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_POWER_DISCONNECTED] ?: false }
    val isNotifyAirplaneModeOn: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_AIRPLANE_MODE_ON] ?: false }
    val isNotifyAirplaneModeOff: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_AIRPLANE_MODE_OFF] ?: false }
    val isNotifyWifiConnected: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_WIFI_CONNECTED] ?: false }
    val isNotifyWifiDisconnected: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_WIFI_DISCONNECTED] ?: false }
    val isNotifyBluetoothConnected: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_BLUETOOTH_CONNECTED] ?: false }
    val isNotifyBluetoothDisconnected: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[IS_NOTIFY_BLUETOOTH_DISCONNECTED] ?: false }

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

    suspend fun setEnhancedBatteryAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_ENHANCED_BATTERY_ALERTS_ENABLED] = enabled
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

    suspend fun setBotPollingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BOT_POLLING_ENABLED] = enabled
        }
    }

    suspend fun setNotifyBootCompleted(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_BOOT_COMPLETED] = enabled } }
    suspend fun setNotifyAppUpdated(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_APP_UPDATED] = enabled } }
    suspend fun setNotifyPowerConnected(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_POWER_CONNECTED] = enabled } }
    suspend fun setNotifyPowerDisconnected(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_POWER_DISCONNECTED] = enabled } }
    suspend fun setNotifyAirplaneModeOn(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_AIRPLANE_MODE_ON] = enabled } }
    suspend fun setNotifyAirplaneModeOff(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_AIRPLANE_MODE_OFF] = enabled } }
    suspend fun setNotifyWifiConnected(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_WIFI_CONNECTED] = enabled } }
    suspend fun setNotifyWifiDisconnected(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_WIFI_DISCONNECTED] = enabled } }
    suspend fun setNotifyBluetoothConnected(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_BLUETOOTH_CONNECTED] = enabled } }
    suspend fun setNotifyBluetoothDisconnected(enabled: Boolean) { context.dataStore.edit { it[IS_NOTIFY_BLUETOOTH_DISCONNECTED] = enabled } }
}
