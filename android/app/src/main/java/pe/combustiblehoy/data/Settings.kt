package pe.combustiblehoy.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pe.combustiblehoy.core.Fuel

private val Context.dataStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val onboarded: Boolean = false,
    val fuel: Fuel = Fuel.GHR,
    val department: String = "lima",
    val radiusKm: Int = 5,
    val alertsEnabled: Boolean = true,
    val alertThreshold: Double = 0.10,
    val onlyDrops: Boolean = false,
    val lastDataDate: String = "",
    val lastSyncAt: Long = 0L,
)

class SettingsStore(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val fuel = stringPreferencesKey("fuel")
        val department = stringPreferencesKey("department")
        val radius = intPreferencesKey("radius_km")
        val alerts = booleanPreferencesKey("alerts_enabled")
        val threshold = doublePreferencesKey("alert_threshold")
        val onlyDrops = booleanPreferencesKey("only_drops")
        val lastDate = stringPreferencesKey("last_data_date")
        val lastSync = longPreferencesKey("last_sync_at")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    private fun Preferences.toSettings() = UserSettings(
        onboarded = this[K.onboarded] ?: false,
        fuel = Fuel.fromCode(this[K.fuel]) ?: Fuel.GHR,
        department = this[K.department] ?: "lima",
        radiusKm = this[K.radius] ?: 5,
        alertsEnabled = this[K.alerts] ?: true,
        alertThreshold = this[K.threshold] ?: 0.10,
        onlyDrops = this[K.onlyDrops] ?: false,
        lastDataDate = this[K.lastDate] ?: "",
        lastSyncAt = this[K.lastSync] ?: 0L,
    )

    suspend fun completeOnboarding(fuel: Fuel, department: String) = context.dataStore.edit {
        it[K.fuel] = fuel.code
        it[K.department] = department
        it[K.onboarded] = true
    }

    suspend fun setFuel(fuel: Fuel) = context.dataStore.edit { it[K.fuel] = fuel.code }
    suspend fun setDepartment(slug: String) = context.dataStore.edit { it[K.department] = slug }
    suspend fun setRadius(km: Int) = context.dataStore.edit { it[K.radius] = km }
    suspend fun setAlertsEnabled(on: Boolean) = context.dataStore.edit { it[K.alerts] = on }
    suspend fun setAlertThreshold(v: Double) = context.dataStore.edit { it[K.threshold] = v }
    suspend fun setOnlyDrops(on: Boolean) = context.dataStore.edit { it[K.onlyDrops] = on }
    suspend fun markSynced(dataDate: String) = context.dataStore.edit {
        it[K.lastDate] = dataDate
        it[K.lastSync] = System.currentTimeMillis()
    }
}
