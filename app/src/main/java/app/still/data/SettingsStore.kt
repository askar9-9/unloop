package app.still.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class ProtectionSettings(
    val enabled: Boolean = true,
    val instagram: Boolean = true,
    val youtube: Boolean = true,
    val consent: Boolean = false,
) {
    fun protects(packageName: String) = consent && enabled && when (packageName) {
        "com.instagram.android" -> instagram
        "com.google.android.youtube" -> youtube
        else -> false
    }
}

class SettingsStore(private val context: Context) {
    private val enabled = booleanPreferencesKey("enabled")
    private val instagram = booleanPreferencesKey("instagram")
    private val youtube = booleanPreferencesKey("youtube")
    private val consent = booleanPreferencesKey("consent")
    val settings = context.store.data.catch { error ->
        if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
        else throw error
    }.map { ProtectionSettings(it[enabled] ?: true, it[instagram] ?: true, it[youtube] ?: true, it[consent] ?: false) }

    suspend fun setEnabled(value: Boolean) { context.store.edit { it[enabled] = value } }
    suspend fun setInstagram(value: Boolean) { context.store.edit { it[instagram] = value } }
    suspend fun setYoutube(value: Boolean) { context.store.edit { it[youtube] = value } }
    suspend fun setConsent(value: Boolean) { context.store.edit { it[consent] = value } }
}
