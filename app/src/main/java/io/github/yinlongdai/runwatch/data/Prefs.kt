package io.github.yinlongdai.runwatch.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Small key-value store. The API key stays in app-private storage, which is
 * excluded from backups (allowBackup=false in the manifest).
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("runwatch", Context.MODE_PRIVATE)

    var apiKey: String?
        get() = sp.getString(KEY_API, null)
        set(v) = sp.edit().putString(KEY_API, v).apply()

    var baseUrl: String
        get() = sp.getString(KEY_BASE, null) ?: WandbApi.DEFAULT_BASE_URL
        set(v) = sp.edit().putString(KEY_BASE, v).apply()

    var lastEntity: String?
        get() = sp.getString(KEY_ENTITY, null)
        set(v) = sp.edit().putString(KEY_ENTITY, v).apply()

    /** Projects as "entity/project". Starred projects are pinned and watched for alerts. */
    var starred: Set<String>
        get() = sp.getStringSet(KEY_STARRED, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_STARRED, v).apply()

    var alertsEnabled: Boolean
        get() = sp.getBoolean(KEY_ALERTS, true)
        set(v) = sp.edit().putBoolean(KEY_ALERTS, v).apply()

    var notifyOnFinish: Boolean
        get() = sp.getBoolean(KEY_NOTIFY_FINISH, false)
        set(v) = sp.edit().putBoolean(KEY_NOTIFY_FINISH, v).apply()

    /** Search bars float at the bottom of the screen (true) or sit above the list (false). */
    var searchAtBottom: Boolean
        get() = sp.getBoolean(KEY_SEARCH_BOTTOM, true)
        set(v) = sp.edit().putBoolean(KEY_SEARCH_BOTTOM, v).apply()

    /** Frosted-glass bars. Off draws plain translucent bars, for slower phones. */
    var glassEnabled: Boolean
        get() = sp.getBoolean(KEY_GLASS, true)
        set(v) = sp.edit().putBoolean(KEY_GLASS, v).apply()

    /** Metric charts pinned to the top of the Charts tab, as "entity/project/metric". */
    var pinnedMetrics: Set<String>
        get() = sp.getStringSet(KEY_PINNED, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_PINNED, v).apply()

    /** X axis for metric charts: "_step" or "_runtime" (wall-clock minutes). */
    var chartXKey: String
        get() = sp.getString(KEY_X, null) ?: "_step"
        set(v) = sp.edit().putString(KEY_X, v).apply()

    /** Last seen state per run ("entity/project/run" -> state), used by the alert worker. */
    var knownStates: Map<String, String>
        get() {
            val obj = runCatching { JSONObject(sp.getString(KEY_STATES, "{}")!!) }.getOrNull() ?: return emptyMap()
            return obj.keys().asSequence().associateWith { obj.optString(it) }
        }
        set(v) {
            val obj = JSONObject()
            v.forEach { (k, s) -> obj.put(k, s) }
            sp.edit().putString(KEY_STATES, obj.toString()).apply()
        }

    fun toggleStar(path: String) {
        starred = if (path in starred) starred - path else starred + path
    }

    fun signOut() {
        sp.edit().remove(KEY_API).remove(KEY_ENTITY).remove(KEY_STATES).apply()
    }

    fun api(): WandbApi? = apiKey?.let { WandbApi(baseUrl, it) }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_BASE = "base_url"
        const val KEY_ENTITY = "last_entity"
        const val KEY_STARRED = "starred"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_NOTIFY_FINISH = "notify_finish"
        const val KEY_STATES = "known_states"
        const val KEY_SEARCH_BOTTOM = "search_at_bottom"
        const val KEY_GLASS = "glass_enabled"
        const val KEY_PINNED = "pinned_metrics"
        const val KEY_X = "chart_x_key"
    }
}
