package com.glasscast.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Three modes, not five. Lights out and Show colors were separate entries in
 * the same list, which made "use the artwork's colors" and "which brightness"
 * mutually exclusive — you could have dynamic color or dark mode, not both.
 * Brightness is this enum; artwork color is [Settings.dynamicColor], and the
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
     * dropped: Lights out becomes Dark, and Show colors becomes System with
     * dynamic color switched on — the closest equivalent of what each person
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

    /**
     * How far the back and forward buttons skip: 15 or 30 seconds. The app's
     * buttons follow it, and so does anything that skips through the session
     * (the notification, Android Auto, a headset's rewind and fast-forward).
     */
    private val _skipSeconds = MutableStateFlow(if (prefs.getInt(KEY_SKIP_SECONDS, 30) == 15) 15 else 30)
    val skipSeconds: StateFlow<Int> = _skipSeconds.asStateFlow()

    fun setSkipSeconds(seconds: Int) {
        val s = if (seconds == 15) 15 else 30
        _skipSeconds.value = s
        prefs.edit().putInt(KEY_SKIP_SECONDS, s).apply()
    }

    /**
     * What a headset's next and previous buttons do: skip forward and back
     * (on), or change episode (off). On by default: earbuds mostly send next
     * and previous, and a double-tap that drops the episode you're in the
     * middle of is rarely what anyone listening to a podcast meant.
     */
    private val _headphoneSkip = MutableStateFlow(prefs.getBoolean(KEY_HEADPHONE_SKIP, true))
    val headphoneSkip: StateFlow<Boolean> = _headphoneSkip.asStateFlow()

    fun setHeadphoneSkip(on: Boolean) {
        _headphoneSkip.value = on
        prefs.edit().putBoolean(KEY_HEADPHONE_SKIP, on).apply()
    }

    /** On by default: it's what most people expect a podcast app to do. */
    private val _newEpisodeNotifications = MutableStateFlow(prefs.getBoolean(KEY_NEW_EPISODES, true))
    val newEpisodeNotifications: StateFlow<Boolean> = _newEpisodeNotifications.asStateFlow()

    fun setNewEpisodeNotifications(enabled: Boolean) {
        _newEpisodeNotifications.value = enabled
        prefs.edit().putBoolean(KEY_NEW_EPISODES, enabled).apply()
        // Turning it off cancels the background job outright rather than
        // leaving it running to do nothing (unless a show still downloads).
        com.glasscast.app.background.BackgroundRefresh.apply(appContext, backgroundRefreshWanted)
    }

    /**
     * Shows that download new episodes on their own, by feed URL, with when
     * that was turned on. Only episodes published since then are downloaded
     * (see AutoDownload), so turning it on never pulls in a back catalog.
     */
    private val _autoDownload = MutableStateFlow(loadAutoDownload())
    val autoDownload: StateFlow<Map<String, Long>> = _autoDownload.asStateFlow()

    fun setAutoDownload(feedUrl: String, on: Boolean) {
        val current = _autoDownload.value
        if (on == (feedUrl in current)) return
        val next = if (on) current + (feedUrl to System.currentTimeMillis()) else current - feedUrl
        _autoDownload.value = next
        val json = org.json.JSONObject()
        next.forEach { (url, since) -> json.put(url, since) }
        prefs.edit().putString(KEY_AUTO_DOWNLOAD, json.toString()).apply()
        com.glasscast.app.background.BackgroundRefresh.apply(appContext, backgroundRefreshWanted)
    }

    private fun loadAutoDownload(): Map<String, Long> = runCatching {
        val json = org.json.JSONObject(prefs.getString(KEY_AUTO_DOWNLOAD, "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getLong(it) }
    }.getOrDefault(emptyMap())

    /** Other reasons for the background job, outside Settings (the Drive backup). Set by the app. */
    var alsoWantsBackground: () -> Boolean = { false }

    /** The background job runs for new-episode notifications, auto-download or the Drive backup. */
    val backgroundRefreshWanted: Boolean
        get() = _newEpisodeNotifications.value || _autoDownload.value.isNotEmpty() || alsoWantsBackground()

    /** Skip the silences between sentences — ExoPlayer does this natively. */
    private val _skipSilence = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SILENCE, false))
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()
    fun setSkipSilence(on: Boolean) {
        _skipSilence.value = on
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, on).apply()
    }

    /** Lift quiet voices without clipping loud ones. */
    private val _voiceBoost = MutableStateFlow(prefs.getBoolean(KEY_VOICE_BOOST, false))
    val voiceBoost: StateFlow<Boolean> = _voiceBoost.asStateFlow()
    // Skip ads is parked (1.4): its toggle is gone, so it's pinned off — a
    // value saved while testing must not keep it running where no one can
    // see or turn it off. The engine (AdFinder, AdSkipper, the service's
    // ticker, the progress-line marks) stays in the code, dormant.
    private val _skipAds = MutableStateFlow(false)
    /** Skip ad breaks found in chapters and transcripts (see AdFinder). Off unless turned on. */
    val skipAds: StateFlow<Boolean> = _skipAds.asStateFlow()
    fun setSkipAds(on: Boolean) {
        _skipAds.value = on
        prefs.edit().putBoolean(KEY_SKIP_ADS, on).apply()
    }


    fun setVoiceBoost(on: Boolean) {
        _voiceBoost.value = on
        prefs.edit().putBoolean(KEY_VOICE_BOOST, on).apply()
    }

    /** Episode order chosen on a show's page, by feed URL. Shows not listed use [sort]. */
    private val _episodeOrder = MutableStateFlow(loadEpisodeOrder())
    val episodeOrder: StateFlow<Map<String, EpisodeSort>> = _episodeOrder.asStateFlow()
    fun setEpisodeOrder(feedUrl: String, order: EpisodeSort) {
        val next = _episodeOrder.value + (feedUrl to order)
        _episodeOrder.value = next
        val json = org.json.JSONObject()
        next.forEach { (url, o) -> json.put(url, o.name) }
        prefs.edit().putString(KEY_EPISODE_ORDER, json.toString()).apply()
    }
    private fun loadEpisodeOrder(): Map<String, EpisodeSort> = runCatching {
        val json = org.json.JSONObject(prefs.getString(KEY_EPISODE_ORDER, "{}") ?: "{}")
        json.keys().asSequence().mapNotNull { url ->
            runCatching { url to EpisodeSort.valueOf(json.getString(url)) }.getOrNull()
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Shows marked "Not interested" in Discover — never suggested again. */
    private val _discoverHidden = MutableStateFlow(
        prefs.getStringSet(KEY_DISCOVER_HIDDEN, emptySet())?.toSet() ?: emptySet()
    )
    val discoverHidden: StateFlow<Set<String>> = _discoverHidden.asStateFlow()
    private val _discoverHiddenInfo = MutableStateFlow(loadHiddenInfo())
    /**
     * Name and cover of each show marked Not interested, so Settings can list
     * them. Shows hidden before 1.4 have none (only their address was kept).
     */
    val discoverHiddenInfo: StateFlow<Map<String, HiddenShow>> = _discoverHiddenInfo.asStateFlow()

    fun hideFromDiscover(feedUrl: String, title: String = "", artworkUrl: String = "") {
        val next = _discoverHidden.value + feedUrl
        _discoverHidden.value = next
        prefs.edit().putStringSet(KEY_DISCOVER_HIDDEN, next).apply()
        if (title.isNotBlank()) saveHiddenInfo(_discoverHiddenInfo.value + (feedUrl to HiddenShow(title, artworkUrl)))
    }

    /** Back into Discover, one show. */
    fun unhideFromDiscover(feedUrl: String) {
        val next = _discoverHidden.value - feedUrl
        _discoverHidden.value = next
        prefs.edit().putStringSet(KEY_DISCOVER_HIDDEN, next).apply()
        saveHiddenInfo(_discoverHiddenInfo.value - feedUrl)
    }

    private fun saveHiddenInfo(map: Map<String, HiddenShow>) {
        _discoverHiddenInfo.value = map
        val json = org.json.JSONObject()
        map.forEach { (url, show) ->
            json.put(url, org.json.JSONObject().put("title", show.title).put("art", show.artworkUrl))
        }
        prefs.edit().putString(KEY_DISCOVER_HIDDEN_INFO, json.toString()).apply()
    }

    private fun loadHiddenInfo(): Map<String, HiddenShow> = runCatching {
        val json = org.json.JSONObject(prefs.getString(KEY_DISCOVER_HIDDEN_INFO, "{}") ?: "{}")
        json.keys().asSequence().associateWith { url ->
            val o = json.getJSONObject(url)
            HiddenShow(o.optString("title"), o.optString("art"))
        }
    }.getOrDefault(emptyMap())
    fun clearDiscoverHidden() {
        _discoverHidden.value = emptySet()
        _discoverHiddenInfo.value = emptyMap()
        prefs.edit().remove(KEY_DISCOVER_HIDDEN).remove(KEY_DISCOVER_HIDDEN_INFO).apply()
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
        // One accent at a time: the cover's, or the one picked.
        if (enabled && _customColorOn.value) setCustomColorOn(false)
    }

    /**
     * A color of the person's own for the accent (Settings > Appearance >
     * Custom color), as ARGB. Off by default; turning it on turns dynamic
     * color off, and the other way round.
     */
    private val _customColorOn = MutableStateFlow(prefs.getBoolean(KEY_CUSTOM_ON, false))
    val customColorOn: StateFlow<Boolean> = _customColorOn.asStateFlow()
    private val _customColor = MutableStateFlow(prefs.getInt(KEY_CUSTOM_COLOR, DEFAULT_CUSTOM))
    val customColor: StateFlow<Int> = _customColor.asStateFlow()

    fun setCustomColorOn(on: Boolean) {
        _customColorOn.value = on
        prefs.edit().putBoolean(KEY_CUSTOM_ON, on).apply()
        if (on && _dynamicColor.value) setDynamicColor(false)
    }

    fun setCustomColor(argb: Int) {
        _customColor.value = argb
        prefs.edit().putInt(KEY_CUSTOM_COLOR, argb).apply()
    }

    /**
     * The tab bar goes light over a dark page, as the mini player does. Off by
     * default: the bar stays dark, and only the mini player changes.
     */
    private val _adaptiveTabBar = MutableStateFlow(prefs.getBoolean(KEY_ADAPTIVE_BAR, false))
    val adaptiveTabBar: StateFlow<Boolean> = _adaptiveTabBar.asStateFlow()

    fun setAdaptiveTabBar(on: Boolean) {
        _adaptiveTabBar.value = on
        prefs.edit().putBoolean(KEY_ADAPTIVE_BAR, on).apply()
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
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_VOICE_BOOST = "voice_boost"
        const val KEY_SKIP_ADS = "skip_ads"
        const val KEY_DISCOVER_HIDDEN_INFO = "discover_hidden_info"
        const val KEY_DISCOVER_HIDDEN = "discover_hidden"
        const val KEY_EPISODE_ORDER = "episode_order"
        const val KEY_NOTIF_PROMPT = "notification_prompt_shown"
        const val KEY_SORT = "sort"
        const val KEY_SHOW_SORT = "show_sort"
        const val KEY_SHAKE = "shake_to_restart"
        const val KEY_SKIP_SECONDS = "skip_seconds"
        const val KEY_HEADPHONE_SKIP = "headphone_skip"
        const val KEY_AUTO_DOWNLOAD = "auto_download"
        const val KEY_CUSTOM_ON = "custom_color_on"
        const val KEY_CUSTOM_COLOR = "custom_color"
        const val KEY_ADAPTIVE_BAR = "adaptive_tab_bar"
        /** The picker's starting point: GlassCast's purple. */
        const val DEFAULT_CUSTOM = 0xFFAF52DE.toInt()
        const val KEY_HIDE_PLAYED = "hide_played_latest"
        const val KEY_HIDE_PLAYED_SHOWS = "hide_played_shows"
    }
}

/** A show marked Not interested, as Settings lists it. */
data class HiddenShow(val title: String, val artworkUrl: String)
