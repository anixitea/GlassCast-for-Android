package com.glasscast.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Three modes, not five. Lights out and Show colours were separate entries in
 * the same list, which made "use the artwork's colours" and "which brightness"
 * mutually exclusive — you could have dynamic colour or dark mode, not both.
 * Brightness is this enum; artwork colour is [Settings.dynamicColor], and the
 * two combine freely.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class EpisodeSort { NEWEST_FIRST, OLDEST_FIRST }
enum class ShowSort { RECENTLY_UPDATED, RECENTLY_PLAYED }

class Settings(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = context.getSharedPreferences("glasscast_settings", Context.MODE_PRIVATE)

    /*
     * Stored values from the five-mode version are migrated rather than
     * dropped: Lights out becomes Dark, and Show colours becomes System with
     * dynamic colour switched on — the closest equivalent of what each person
     * had chosen. Without this, anyone on either would silently fall back to
     * System and lose their setting.
     */
    private val storedTheme = prefs.getString(KEY_THEME, null)

    private val _theme = MutableStateFlow(
        when (val stored = storedTheme) {
            null, "SHOW_COLOURS" -> ThemeMode.SYSTEM
            "LIGHTS_OUT" -> ThemeMode.DARK
            // A `when` subject isn't narrowed by an earlier null branch, so the
            // non-null value is bound explicitly rather than assumed.
            else -> runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
        }
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(
        if (prefs.contains(KEY_DYNAMIC)) prefs.getBoolean(KEY_DYNAMIC, false)
        else storedTheme == "SHOW_COLOURS"
    )
    /** Take the app's accent from the artwork that's playing, in any mode. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    init {
        if (storedTheme == "LIGHTS_OUT" || storedTheme == "SHOW_COLOURS") {
            prefs.edit()
                .putString(KEY_THEME, _theme.value.name)
                .putBoolean(KEY_DYNAMIC, _dynamicColor.value)
                .apply()
        }
    }

    private val _sort = MutableStateFlow(
        runCatching { EpisodeSort.valueOf(prefs.getString(KEY_SORT, null) ?: "NEWEST_FIRST") }
            .getOrDefault(EpisodeSort.NEWEST_FIRST)
    )
    val sort: StateFlow<EpisodeSort> = _sort.asStateFlow()

    private val _showSort = MutableStateFlow(
        runCatching { ShowSort.valueOf(prefs.getString(KEY_SHOW_SORT, null) ?: "RECENTLY_UPDATED") }
            .getOrDefault(ShowSort.RECENTLY_UPDATED)
    )
    val showSort: StateFlow<ShowSort> = _showSort.asStateFlow()

    private val _hidePlayedInLatest = MutableStateFlow(prefs.getBoolean(KEY_HIDE_PLAYED, false))
    val hidePlayedInLatest: StateFlow<Boolean> = _hidePlayedInLatest.asStateFlow()

    /** Separate from the Latest filter: a show you're working through has its own state. */
    private val _hidePlayedInShows = MutableStateFlow(prefs.getBoolean(KEY_HIDE_PLAYED_SHOWS, false))
    val hidePlayedInShows: StateFlow<Boolean> = _hidePlayedInShows.asStateFlow()

    // Off by default: it registers a sensor listener, and most people never
    // want it. The ones who do go looking for it.
    private val _shakeToRestart = MutableStateFlow(prefs.getBoolean(KEY_SHAKE, false))
    val shakeToRestart: StateFlow<Boolean> = _shakeToRestart.asStateFlow()

    /** On by default: it's what most people expect a podcast app to do. */
    private val _newEpisodeNotifications = MutableStateFlow(prefs.getBoolean(KEY_NEW_EPISODES, true))
    val newEpisodeNotifications: StateFlow<Boolean> = _newEpisodeNotifications.asStateFlow()

    fun setNewEpisodeNotifications(enabled: Boolean) {
        _newEpisodeNotifications.value = enabled
        prefs.edit().putBoolean(KEY_NEW_EPISODES, enabled).apply()
        // Turning it off cancels the background job outright rather than
        // leaving it running to do nothing.
        com.glasscast.app.background.BackgroundRefresh.apply(appContext, enabled)
    }

    /** The notification permission is asked for once, not on every launch. */
    val notificationPromptShown: Boolean get() = prefs.getBoolean(KEY_NOTIF_PROMPT, false)
    fun markNotificationPromptShown() {
        prefs.edit().putBoolean(KEY_NOTIF_PROMPT, true).apply()
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setSort(sort: EpisodeSort) {
        _sort.value = sort
        prefs.edit().putString(KEY_SORT, sort.name).apply()
    }

    fun setShowSort(sort: ShowSort) {
        _showSort.value = sort
        prefs.edit().putString(KEY_SHOW_SORT, sort.name).apply()
    }

    fun setHidePlayedInLatest(on: Boolean) {
        _hidePlayedInLatest.value = on
        prefs.edit().putBoolean(KEY_HIDE_PLAYED, on).apply()
    }

    fun setHidePlayedInShows(on: Boolean) {
        _hidePlayedInShows.value = on
        prefs.edit().putBoolean(KEY_HIDE_PLAYED_SHOWS, on).apply()
    }

    fun setShakeToRestart(on: Boolean) {
        _shakeToRestart.value = on
        prefs.edit().putBoolean(KEY_SHAKE, on).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_NEW_EPISODES = "new_episode_notifications"
        const val KEY_NOTIF_PROMPT = "notification_prompt_shown"
        const val KEY_SORT = "sort"
        const val KEY_SHOW_SORT = "show_sort"
        const val KEY_SHAKE = "shake_to_restart"
        const val KEY_HIDE_PLAYED = "hide_played_latest"
        const val KEY_HIDE_PLAYED_SHOWS = "hide_played_shows"
    }
}
