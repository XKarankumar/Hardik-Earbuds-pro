package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buds_pro_prefs")

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val showInAppConnectionPopup: Boolean = true,
    val connectionNotificationsEnabled: Boolean = true,
    val overlayPopupEnabled: Boolean = false,
    val lowBatteryAlertsEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 20,
    val autoReconnectHelperEnabled: Boolean = true,
    val customAlertSoundUri: String? = null,
    val eqEnabled: Boolean = true,
    val selectedEqPreset: String = "Normal",
    val customBandGainsDb: List<Float> = List(10) { 0f },
    val localOnlyPrivacyEnabled: Boolean = true
)

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IN_APP_POPUP = booleanPreferencesKey("in_app_popup")
        val CONNECTION_NOTIFS = booleanPreferencesKey("connection_notifs")
        val OVERLAY_POPUP = booleanPreferencesKey("overlay_popup")
        val LOW_BATTERY_ALERTS = booleanPreferencesKey("low_battery_alerts")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val AUTO_RECONNECT_HELPER = booleanPreferencesKey("auto_reconnect_helper")
        val CUSTOM_ALERT_SOUND_URI = stringPreferencesKey("custom_alert_sound_uri")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val SELECTED_EQ_PRESET = stringPreferencesKey("selected_eq_preset")
        val CUSTOM_EQ_BANDS = stringPreferencesKey("custom_eq_bands")
        val LOCAL_ONLY_PRIVACY = booleanPreferencesKey("local_only_privacy")
    }

    val preferencesFlow: Flow<AppPreferencesState> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK)
        val bandsStr = prefs[Keys.CUSTOM_EQ_BANDS]
        val parsedBands = if (!bandsStr.isNullOrBlank()) {
            val parts = bandsStr.split(",").mapNotNull { it.toFloatOrNull() }
            if (parts.size == 10) parts else List(10) { 0f }
        } else {
            List(10) { 0f }
        }

        AppPreferencesState(
            themeMode = themeMode,
            showInAppConnectionPopup = prefs[Keys.IN_APP_POPUP] ?: true,
            connectionNotificationsEnabled = prefs[Keys.CONNECTION_NOTIFS] ?: true,
            overlayPopupEnabled = prefs[Keys.OVERLAY_POPUP] ?: false,
            lowBatteryAlertsEnabled = prefs[Keys.LOW_BATTERY_ALERTS] ?: true,
            lowBatteryThreshold = prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20,
            autoReconnectHelperEnabled = prefs[Keys.AUTO_RECONNECT_HELPER] ?: true,
            customAlertSoundUri = prefs[Keys.CUSTOM_ALERT_SOUND_URI]?.takeIf { it.isNotBlank() },
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: true,
            selectedEqPreset = prefs[Keys.SELECTED_EQ_PRESET] ?: "Normal",
            customBandGainsDb = parsedBands,
            localOnlyPrivacyEnabled = prefs[Keys.LOCAL_ONLY_PRIVACY] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setInAppPopup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IN_APP_POPUP] = enabled }
    }

    suspend fun setConnectionNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONNECTION_NOTIFS] = enabled }
    }

    suspend fun setOverlayPopup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERLAY_POPUP] = enabled }
    }

    suspend fun setLowBatteryAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOW_BATTERY_ALERTS] = enabled }
    }

    suspend fun setLowBatteryThreshold(percent: Int) {
        context.dataStore.edit { it[Keys.LOW_BATTERY_THRESHOLD] = percent.coerceIn(5, 40) }
    }

    suspend fun setAutoReconnectHelper(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RECONNECT_HELPER] = enabled }
    }

    suspend fun setCustomAlertSoundUri(uri: String?) {
        context.dataStore.edit {
            if (uri.isNullOrBlank()) {
                it.remove(Keys.CUSTOM_ALERT_SOUND_URI)
            } else {
                it[Keys.CUSTOM_ALERT_SOUND_URI] = uri
            }
        }
    }

    suspend fun setEqState(enabled: Boolean, presetName: String, customGainsDb: List<Float>) {
        context.dataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.SELECTED_EQ_PRESET] = presetName
            if (customGainsDb.size == 10) {
                it[Keys.CUSTOM_EQ_BANDS] = customGainsDb.joinToString(",")
            }
        }
    }

    suspend fun setLocalOnlyPrivacy(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCAL_ONLY_PRIVACY] = enabled }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
