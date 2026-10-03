package chat.stoat.api.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Custom badge IDs that can be assigned by the owner (stored locally and synced with cloud).
 */
enum class CustomBadge(val id: String, val label: String, val emoji: String) {
    Slut("slut", "Slut", "♀"),
    Homie("homie", "Homie", "🤜"),
    Simp("simp", "Simp", "💘"),
    Gigachad("gigachad", "Gigachad", "💪"),
    Clown("clown", "Clown", "🤡"),
    Goat("goat", "G.O.A.T", "🐐"),
    Rat("rat", "Rat", "🐀"),
    Nerd("nerd", "Nerd", "🤓"),
    King("king", "King", "👑"),
    Cursed("cursed", "Cursed", "😈"),
}

private val Context.customBadgeDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "custom_badges")

/**
 * Persistent store for owner-assigned custom badges.
 * Automatically synchronizes with Dismod Cloud (https://adminofdismod.netlify.app/api/badges)
 * so assigned badges and roles are immediately visible across all devices and profiles.
 */
class CustomBadgeStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, Set<CustomBadge>>()
    private val isSyncing = AtomicBoolean(false)
    private val lastSyncTime = AtomicLong(0L)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val BADGES_SYNC_URL = "https://adminofdismod.netlify.app/api/badges"

    init {
        // Trigger initial background sync on store creation
        syncWithCloud()
    }

    private fun keyFor(userId: String) = stringPreferencesKey("custom_badge_$userId")

    /**
     * Observes the set of [CustomBadge]s for [userId] as a reactive [Flow].
     * Emits immediately from cache/DataStore and updates whenever local or cloud sync updates.
     */
    fun observeBadges(userId: String): Flow<Set<CustomBadge>> {
        if (userId.isBlank()) return flowOf(emptySet())
        val now = System.currentTimeMillis()
        if (now - lastSyncTime.get() > 15_000L) {
            syncWithCloud()
        }
        return context.customBadgeDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val raw = prefs[keyFor(userId)]
                val set = if (!raw.isNullOrBlank()) {
                    raw.split(",")
                        .filter { it.isNotBlank() }
                        .mapNotNull { id -> CustomBadge.entries.firstOrNull { it.id == id } }
                        .toSet()
                } else {
                    emptySet()
                }
                memoryCache[userId] = set
                set
            }
            .distinctUntilChanged()
    }

    /** Returns the set of [CustomBadge]s assigned to the given user. */
    suspend fun getBadges(userId: String): Set<CustomBadge> {
        if (userId.isBlank()) return emptySet()

        // 1. Check in-memory cache first
        memoryCache[userId]?.let { return it }

        // 2. Read from persistent local datastore
        val raw = context.customBadgeDataStore.data.firstOrNull()
            ?.get(keyFor(userId))

        val loaded = if (!raw.isNullOrBlank()) {
            raw.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { id -> CustomBadge.entries.firstOrNull { it.id == id } }
                .toSet()
        } else {
            emptySet()
        }

        memoryCache[userId] = loaded

        // 3. Trigger periodic cloud refresh if older than 30 seconds
        val now = System.currentTimeMillis()
        if (now - lastSyncTime.get() > 30_000L) {
            syncWithCloud()
        }

        return loaded
    }

    /** Assigns [badge] to [userId]. Idempotent. */
    suspend fun addBadge(userId: String, badge: CustomBadge) {
        val current = getBadges(userId).toMutableSet()
        current.add(badge)
        save(userId, current)
    }

    /** Toggles [badge] for [userId]. */
    suspend fun toggleBadge(userId: String, badge: CustomBadge) {
        val current = getBadges(userId).toMutableSet()
        if (current.contains(badge)) {
            current.remove(badge)
        } else {
            current.add(badge)
        }
        save(userId, current)
    }

    /** Removes [badge] from [userId]. Idempotent. */
    suspend fun removeBadge(userId: String, badge: CustomBadge) {
        val current = getBadges(userId).toMutableSet()
        current.remove(badge)
        save(userId, current)
    }

    /** Overwrites the badge set for [userId]. */
    suspend fun setBadges(userId: String, badges: Set<CustomBadge>) {
        save(userId, badges)
    }

    private suspend fun save(userId: String, badges: Set<CustomBadge>) {
        memoryCache[userId] = badges

        val serialised = badges.joinToString(",") { it.id }
        context.customBadgeDataStore.edit { prefs ->
            prefs[keyFor(userId)] = serialised
        }

        // Push update to cloud asynchronously
        scope.launch {
            uploadBadgeToCloud(userId, badges)
        }
    }

    fun syncWithCloud(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncTime.get() < 5_000L) return
        if (!isSyncing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val request = Request.Builder()
                    .url(BADGES_SYNC_URL)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.string()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val incomingUids = mutableSetOf<String>()
                            context.customBadgeDataStore.edit { prefs ->
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val uid = keys.next()
                                    incomingUids.add(uid)
                                    val arr = json.optJSONArray(uid)
                                    if (arr != null) {
                                        val badgeList = mutableListOf<String>()
                                        for (i in 0 until arr.length()) {
                                            badgeList.add(arr.getString(i))
                                        }
                                        val badgeSet = badgeList
                                            .mapNotNull { id -> CustomBadge.entries.firstOrNull { it.id == id } }
                                            .toSet()
                                        memoryCache[uid] = badgeSet
                                        prefs[keyFor(uid)] = badgeSet.joinToString(",") { it.id }
                                    }
                                }
                                // Prune badges that were deleted in cloud
                                val allPrefKeys = prefs.asMap().keys.toList()
                                for (k in allPrefKeys) {
                                    if (k.name.startsWith("custom_badge_")) {
                                        val storedUid = k.name.removePrefix("custom_badge_")
                                        if (!incomingUids.contains(storedUid)) {
                                            prefs.remove(k)
                                            memoryCache.remove(storedUid)
                                        }
                                    }
                                }
                            }
                            lastSyncTime.set(System.currentTimeMillis())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CustomBadgeStore", "Cloud badge sync failed: ${e.message}")
            } finally {
                isSyncing.set(false)
            }
        }
    }

    private suspend fun uploadBadgeToCloud(userId: String, badges: Set<CustomBadge>) {
        try {
            val json = JSONObject().apply {
                put("userId", userId)
                put("badges", JSONArray(badges.map { it.id }))
            }
            val reqBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(BADGES_SYNC_URL)
                .post(reqBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CustomBadgeStore", "Badge upload failed with code ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w("CustomBadgeStore", "Failed uploading badge update to cloud: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: CustomBadgeStore? = null

        fun get(context: Context): CustomBadgeStore =
            instance ?: synchronized(this) {
                instance ?: CustomBadgeStore(context.applicationContext).also { instance = it }
            }
    }
}
