package com.gramayatri.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.gramayatri.data.model.BusPing
import com.gramayatri.data.model.AppLanguage
import com.gramayatri.data.model.Route
import com.gramayatri.data.model.UserPreferences
import com.gramayatri.data.model.UserRole
import com.gramayatri.utils.DeviceUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "user_preferences")

private val Context.cacheDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "route_cache")

@Singleton
class LocalCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─── Keys ─────────────────────────────────────────────────────────────
    private object PrefKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val PREFERRED_STOP_ID = stringPreferencesKey("preferred_stop_id")
        val PREFERRED_ROUTE_ID = stringPreferencesKey("preferred_route_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val USER_ROLE = stringPreferencesKey("user_role")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val HAS_SELECTED_LANGUAGE = booleanPreferencesKey("has_selected_language")
        val HAS_SEEN_INTRO = booleanPreferencesKey("has_seen_intro")
    }

    private object CacheKeys {
        val ROUTES_JSON = stringPreferencesKey("routes_json")
        val ROUTES_CACHED_AT = longPreferencesKey("routes_cached_at")
        val LAST_PING_TIME = longPreferencesKey("last_ping_time")
        val ACTIVE_PING_JSON = stringPreferencesKey("active_ping_json")
    }

    // ─── User Preferences ─────────────────────────────────────────────────

    val userPreferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            UserPreferences(
                userName = prefs[PrefKeys.USER_NAME] ?: "",
                preferredStopId = prefs[PrefKeys.PREFERRED_STOP_ID] ?: "",
                preferredRouteId = prefs[PrefKeys.PREFERRED_ROUTE_ID] ?: "",
                deviceId = prefs[PrefKeys.DEVICE_ID] ?: DeviceUtils.generateDeviceId(),
                hasCompletedOnboarding = prefs[PrefKeys.HAS_ONBOARDED] ?: false,
                notificationsEnabled = prefs[PrefKeys.NOTIFICATIONS_ENABLED] ?: true,
                role = parseRole(prefs[PrefKeys.USER_ROLE]),
                language = parseLanguage(prefs[PrefKeys.APP_LANGUAGE]),
                hasSelectedLanguage = prefs[PrefKeys.HAS_SELECTED_LANGUAGE] ?: false,
                hasSeenIntro = prefs[PrefKeys.HAS_SEEN_INTRO] ?: false
            )
        }

    suspend fun saveUserPreferences(prefs: UserPreferences) {
        context.userPrefsDataStore.edit { stored ->
            stored[PrefKeys.USER_NAME] = prefs.userName
            stored[PrefKeys.PREFERRED_STOP_ID] = prefs.preferredStopId
            stored[PrefKeys.PREFERRED_ROUTE_ID] = prefs.preferredRouteId
            stored[PrefKeys.DEVICE_ID] = prefs.deviceId
            stored[PrefKeys.HAS_ONBOARDED] = prefs.hasCompletedOnboarding
            stored[PrefKeys.NOTIFICATIONS_ENABLED] = prefs.notificationsEnabled
            stored[PrefKeys.USER_ROLE] = prefs.role.name
            stored[PrefKeys.APP_LANGUAGE] = prefs.language.name
            stored[PrefKeys.HAS_SELECTED_LANGUAGE] = prefs.hasSelectedLanguage
            stored[PrefKeys.HAS_SEEN_INTRO] = prefs.hasSeenIntro
        }
    }

    suspend fun completeOnboarding(name: String, stopId: String, routeId: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[PrefKeys.USER_NAME] = name
            prefs[PrefKeys.PREFERRED_STOP_ID] = stopId
            prefs[PrefKeys.PREFERRED_ROUTE_ID] = routeId
            prefs[PrefKeys.HAS_ONBOARDED] = true
            prefs[PrefKeys.USER_ROLE] = UserRole.PASSENGER.name
        }
    }

    suspend fun updateRole(role: UserRole) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[PrefKeys.USER_ROLE] = role.name
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[PrefKeys.APP_LANGUAGE] = language.name
            prefs[PrefKeys.HAS_SELECTED_LANGUAGE] = true
        }
    }

    suspend fun markIntroSeen() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[PrefKeys.HAS_SEEN_INTRO] = true
        }
    }

    fun getDeviceId(): String {
        // Synchronous read from SharedPreferences as fallback
        val sharedPrefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("device_id", null) ?: run {
            val newId = DeviceUtils.generateDeviceId()
            sharedPrefs.edit().putString("device_id", newId).apply()
            newId
        }
    }

    // ─── Route Cache ───────────────────────────────────────────────────────

    fun getCachedRoutes(): List<Route> {
        val sharedPrefs = context.getSharedPreferences("route_cache", Context.MODE_PRIVATE)
        val routesJson = sharedPrefs.getString("routes_json", null) ?: return emptyList()
        val cachedAt = sharedPrefs.getLong("routes_cached_at", 0L)

        // Cache valid for 24 hours
        if (System.currentTimeMillis() - cachedAt > 24 * 60 * 60 * 1000L) return emptyList()

        return try {
            json.decodeFromString<List<Route>>(routesJson)
                .filter { it.id.isNotBlank() && it.isActive }
        } catch (e: Exception) { emptyList() }
    }

    fun cacheRoutes(routes: List<Route>) {
        val validRoutes = routes.filter { it.id.isNotBlank() && it.isActive }
        val sharedPrefs = context.getSharedPreferences("route_cache", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("routes_json", json.encodeToString(validRoutes))
            .putLong("routes_cached_at", System.currentTimeMillis())
            .apply()
    }

    // ─── Ping Cache ────────────────────────────────────────────────────────

    fun getLastPingTime(deviceId: String): Long {
        val prefs = context.getSharedPreferences("ping_prefs_$deviceId", Context.MODE_PRIVATE)
        return prefs.getLong("last_ping_time", 0L)
    }

    fun saveLastPingTime(deviceId: String) {
        val prefs = context.getSharedPreferences("ping_prefs_$deviceId", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_ping_time", System.currentTimeMillis()).apply()
    }

    fun cachePing(routeId: String, ping: BusPing) {
        val prefs = context.getSharedPreferences("ping_cache", Context.MODE_PRIVATE)
        prefs.edit().putString("ping_$routeId", json.encodeToString(ping)).apply()
    }

    fun getCachedPing(routeId: String): BusPing? {
        val prefs = context.getSharedPreferences("ping_cache", Context.MODE_PRIVATE)
        val pingJson = prefs.getString("ping_$routeId", null) ?: return null
        return try {
            val ping = json.decodeFromString<BusPing>(pingJson)
            // Don't return if expired
            if (System.currentTimeMillis() - ping.timestamp > com.gramayatri.utils.Constants.PING_EXPIRY_MS) null
            else ping
        } catch (e: Exception) { null }
    }

    private fun parseRole(value: String?): UserRole {
        return runCatching {
            UserRole.valueOf(value ?: UserRole.PASSENGER.name)
        }.getOrDefault(UserRole.PASSENGER)
    }

    private fun parseLanguage(value: String?): AppLanguage {
        return runCatching {
            AppLanguage.valueOf(value ?: AppLanguage.ENGLISH.name)
        }.getOrDefault(AppLanguage.ENGLISH)
    }
}
