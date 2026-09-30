package com.glasscast.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A GitHub release that carries an APK. */
data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** Lower-case hex SHA-256, when GitHub publishes one for the asset. */
    val sha256: String?,
    val pageUrl: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val file: File) : UpdateState
    /** Android needs "install unknown apps" allowed for GlassCast first. */
    data class NeedsPermission(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * Updates from GitHub Releases, inside the app.
 *
 * Checks the repo's latest release (drafts and pre-releases excluded by
 * GitHub), compares its tag to the installed version, downloads the APK with
 * progress, verifies it, and hands it to Android's own installer.
 *
 * What it can't do, by design: install silently. Android always shows its own
 * confirmation for an app that isn't from a store, and the first time it asks
 * the user to allow GlassCast to install updates at all. Both are one tap.
 *
 * **Verification.** GitHub publishes a SHA-256 digest for each release asset.
 * When it's there, the downloaded file must match it exactly or it's deleted
 * and never offered for install — a truncated download, a flipped bit, or a
 * swapped file all fail here. Android then checks the signature itself: an
 * update only installs over GlassCast if it's signed with the same key.
 *
 * Checks are cheap (one small API call) and rate-limited to twice a day
 * automatically; GitHub allows 60 unauthenticated calls an hour per network.
 */
class AppUpdater(private val context: Context) {

    companion object {
        const val REPO = "anixitea/GlassCast-for-Android"
        private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed_version"
    }

    private val prefs = context.getSharedPreferences("glasscast_updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** A newer release to announce with a banner — null once dismissed. */
    private val _banner = MutableStateFlow<AppRelease?>(null)
    val banner: StateFlow<AppRelease?> = _banner.asStateFlow()

    @Suppress("DEPRECATION")
    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0"

    /** On launch: checks only if the last check was more than 12 hours ago. */
    fun checkIfDue() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        check(manual = false)
    }

    fun check(manual: Boolean) {
        val busy = _state.value
        if (busy is UpdateState.Checking || busy is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Checking
            val release = withContext(Dispatchers.IO) { runCatching { fetchLatest() }.getOrNull() }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            _state.value = when {
                release == null ->
                    if (manual) UpdateState.Failed("Couldn't reach GitHub. Check the connection and try again.")
                    else UpdateState.Idle
                isNewer(release.version, currentVersion) -> {
                    // A dismissed version isn't announced again on its own —
                    // but a manual check always shows what's there.
                    if (manual || prefs.getString(KEY_DISMISSED, null) != release.version) {
                        _banner.value = release
                    }
                    UpdateState.Available(release)
                }
                else -> UpdateState.UpToDate(currentVersion)
            }
        }
    }

    fun dismissBanner() {
        _banner.value?.let { prefs.edit().putString(KEY_DISMISSED, it.version).apply() }
        _banner.value = null
    }

    fun download(release: AppRelease) {
        if (_state.value is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fetchApk(release) { progress -> _state.value = UpdateState.Downloading(release, progress) }
                }
            }
            val file = result.getOrNull()
            if (file == null) {
                _state.value = UpdateState.Failed(
                    result.exceptionOrNull()?.message ?: "The download didn't finish. Try again.",
                    release
                )
                return@launch
            }
            _state.value = UpdateState.ReadyToInstall(release, file)
            install(release, file)
        }
    }

    /** Hands the verified APK to Android's installer. */
    fun install(release: AppRelease, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = UpdateState.NeedsPermission(release, file)
            return
        }
        _banner.value = null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            _state.value = UpdateState.ReadyToInstall(release, file)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("This device has no installer that can open the update.", release)
        }
    }

    /**
     * The one-time "Allow from this source" screen for GlassCast. The user
     * returns and taps Install again; nothing is lost in between.
     */
    fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            // Some TV builds have no per-app screen; the global one is there.
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun openReleasePage(release: AppRelease) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ------------------------------------------------------------------ network

    private fun fetchLatest(): AppRelease? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GlassCast-Android/$currentVersion")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (version.isBlank()) return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                return AppRelease(
                    version = version,
                    notes = json.optString("body"),
                    apkUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                    sha256 = digest?.removePrefix("sha256:")?.lowercase(),
                    pageUrl = json.optString("html_url")
                )
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchApk(release: AppRelease, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            // Only ever one pending update on disk.
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "GlassCast-${release.version}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GlassCast-Android/$currentVersion")
        }
        try {
            if (conn.responseCode !in 200..299) error("GitHub answered ${conn.responseCode}. Try again in a minute.")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            val sha = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sha.update(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (release.sizeBytes > 0 && file.length() != release.sizeBytes) {
                file.delete()
                error("The download was incomplete. Try again.")
            }
            val expected = release.sha256
            if (expected != null) {
                val actual = sha.digest().joinToString("") { "%02x".format(it) }
                if (actual != expected) {
                    file.delete()
                    error("The download didn't match the release, so it wasn't installed.")
                }
            }
            return file
        } finally {
            conn.disconnect()
        }
    }
}

/** 1.10 > 1.9, 1.1 == 1.1.0, "1.2-beta" compares as 1.2. */
internal fun isNewer(remote: String, current: String): Boolean {
    fun parts(v: String) = v.split('.', '-', '_', ' ').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val a = parts(remote)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
