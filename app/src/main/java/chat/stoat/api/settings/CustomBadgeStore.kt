package chat.stoat.api.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

/**
 * Custom badge IDs that can be assigned by the owner (stored locally, client-side only).
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
 * Key format:  custom_badge_<userId>  →  comma-separated badge ids
 */
class CustomBadgeStore(private val context: Context) {

    private fun keyFor(userId: String) = stringPreferencesKey("custom_badge_$userId")

    /** Returns the set of [CustomBadge]s assigned to the given user. */
    suspend fun getBadges(userId: String): Set<CustomBadge> {
        val raw = context.customBadgeDataStore.data.firstOrNull()
            ?.get(keyFor(userId)) ?: return emptySet()
        return raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { id -> CustomBadge.entries.firstOrNull { it.id == id } }
            .toSet()
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
        val serialised = badges.joinToString(",") { it.id }
        context.customBadgeDataStore.edit { prefs ->
            prefs[keyFor(userId)] = serialised
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
