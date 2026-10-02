package com.glasscast.app.data

import com.glasscast.app.ui.tr
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * gPodder sync: subscriptions and listening progress, shared through a
 * gPodder-compatible server with other devices and apps (AntennaPod, gPodder
 * desktop, Kasts, another GlassCast — including the TV).
 *
 * Two kinds of server speak the same data with different addresses:
 *  - **gPodder** — gpodder.net, or a self-hosted server with the same API
 *    (/api/2/…), with a registered device;
 *  - **Nextcloud** — the "GPodder Sync" app on a Nextcloud server
 *    (/index.php/apps/gpoddersync/…), signed in with an app password.
 *
 * How a sync runs:
 *  1. **Subscriptions.** Changes since the last sync come down (added and
 *     removed feed URLs) and are applied; this device's own changes, kept in
 *     an outbox as they happen, go up. The first sync merges: everything here
 *     is uploaded, everything there is added, nothing is deleted.
 *  2. **Progress.** "play" actions since the last sync come down; the newest
 *     per episode is applied — position set, played if it reached the end —
 *     except to whatever is playing right now. This device's actions go up in
 *     batches of 30. An action is recorded when playback pauses or an episode
 *     is marked played, not on every position save.
 *
 * Episodes are matched by GUID when the action carries one, otherwise by
 * media URL within the show. The password is encrypted with an AES key that
 * lives in the Android Keystore and never leaves it.
 */
class GPodderSync(private val context: Context, private val store: FeedStore) {

    enum class Kind { GPODDER, NEXTCLOUD }

    data class Status(
        val connected: Boolean = false,
        val kind: Kind = Kind.GPODDER,
        val server: String = "",
        val username: String = "",
        val syncing: Boolean = false,
        val lastSync: Long = 0L,
        val error: String? = null
    )

    private val prefs = context.getSharedPreferences("glasscast_gpodder", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var soon: Job? = null

    /** Set while remote changes are being applied, so they aren't queued to go back up. */
    @Volatile private var applying = false

    /** The episode playing right now: remote progress never moves it. */
    var playingGuid: () -> String? = { null }

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    val isConnected: Boolean get() = _status.value.connected

    // ---------------------------------------------------------------- account

    /** Signs in and runs the first sync. Returns an error message, or null on success. */
    suspend fun connect(kind: Kind, rawServer: String, username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val server = normalizeServer(rawServer, kind)
            val user = username.trim()
            if (server.isBlank() || user.isBlank() || password.isBlank()) return@withContext tr("Fill in every field")
            val device = prefs.getString(KEY_DEVICE, null) ?: ("glasscast-" + java.util.UUID.randomUUID().toString().take(8))
            val api = Api(kind, server, user, password, device)
            val problem = runCatching { api.signIn() }.exceptionOrNull()
            if (problem != null) return@withContext describe(problem)
            prefs.edit()
                .putString(KEY_KIND, kind.name)
                .putString(KEY_SERVER, server)
                .putString(KEY_USER, user)
                .putString(KEY_PASSWORD, Secret.seal(password))
                .putString(KEY_DEVICE, device)
                .putLong(KEY_SUBS_SINCE, 0L)
                .putLong(KEY_ACTIONS_SINCE, 0L)
                .remove(KEY_PENDING_ADD).remove(KEY_PENDING_REMOVE).remove(KEY_PENDING_ACTIONS)
                .apply()
            _status.value = readStatus()
            sync()
        }

    fun disconnect() {
        prefs.edit()
            .remove(KEY_KIND).remove(KEY_SERVER).remove(KEY_USER).remove(KEY_PASSWORD)
            .remove(KEY_SUBS_SINCE).remove(KEY_ACTIONS_SINCE)
            .remove(KEY_PENDING_ADD).remove(KEY_PENDING_REMOVE).remove(KEY_PENDING_ACTIONS)
            .remove(KEY_LAST_SYNC)
            .apply()
        _status.value = readStatus()
    }

    // ---------------------------------------------------------------- outbox

    fun recordSubscription(feedUrl: String, added: Boolean) {
        if (!isConnected || applying) return
        val adds = prefs.getStringSet(KEY_PENDING_ADD, emptySet())!!.toMutableSet()
        val removes = prefs.getStringSet(KEY_PENDING_REMOVE, emptySet())!!.toMutableSet()
        if (added) { adds += feedUrl; removes -= feedUrl } else { removes += feedUrl; adds -= feedUrl }
        prefs.edit().putStringSet(KEY_PENDING_ADD, adds).putStringSet(KEY_PENDING_REMOVE, removes).apply()
        syncSoon()
    }

    /** Where playback stopped in an episode (or its end, when marked played). */
    fun recordPlay(episode: Episode, startedMs: Long, positionMs: Long, durationMs: Long) {
        if (!isConnected || applying || episode.audioUrl.isBlank()) return
        val position = (positionMs / 1000).toInt().coerceAtLeast(0)
        val total = (durationMs / 1000).toInt().coerceAtLeast(position).coerceAtLeast(1)
        val action = JSONObject()
            .put("podcast", episode.feedUrl)
            .put("episode", episode.audioUrl)
            .put("guid", episode.guid)
            .put("action", "play")
            .put("timestamp", synchronized(utc) { utc.format(java.util.Date()) })
            .put("started", (startedMs / 1000).toInt().coerceIn(0, position))
            .put("position", position)
            .put("total", total)
        // One pending action per episode is enough: the latest wins.
        val pending = pendingActions().filterNot { it.optString("episode") == episode.audioUrl } + action
        prefs.edit().putString(KEY_PENDING_ACTIONS, JSONArray(pending).toString()).apply()
        syncSoon()
    }

    /** A sync a little after a burst of changes, rather than one per change. */
    private fun syncSoon() {
        soon?.cancel()
        soon = scope.launch {
            delay(8_000)
            sync()
        }
    }

    // ---------------------------------------------------------------- sync

    /** Runs a full sync now. Returns an error message, or null on success. */
    suspend fun sync(): String? = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext null
        mutex.withLock {
            _status.value = _status.value.copy(syncing = true, error = null)
            val error = runCatching {
                store.awaitLoaded()
                val api = api() ?: error(tr("Signed out"))
                syncSubscriptions(api)
                syncProgress(api)
            }.exceptionOrNull()?.let(::describe)
            if (error == null) prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
            _status.value = readStatus().copy(error = error)
            error
        }
    }

    private suspend fun syncSubscriptions(api: Api) {
        val since = prefs.getLong(KEY_SUBS_SINCE, 0L)
        val first = since == 0L
        val remote = api.subscriptionsSince(since)
        val pendingAdd = prefs.getStringSet(KEY_PENDING_ADD, emptySet())!!.toSet()
        val pendingRemove = prefs.getStringSet(KEY_PENDING_REMOVE, emptySet())!!.toSet()
        val local = store.feeds.value.associateBy { it.url.lowercase() }

        applying = true
        try {
            for (url in remote.added) {
                if (url in pendingRemove || url.lowercase() in local) continue
                runCatching { store.subscribe(url) }
            }
            if (!first) {
                for (url in remote.removed) {
                    if (url in pendingAdd) continue
                    local[url.lowercase()]?.let { runCatching { store.unsubscribe(it) } }
                }
            }
        } finally {
            applying = false
        }

        // First sync: everything here goes up. Afterwards: only the outbox.
        val remoteSet = remote.added.map { it.lowercase() }.toSet()
        val toAdd = if (first) {
            (store.feeds.value.map { it.url }.filter { it.lowercase() !in remoteSet } + pendingAdd).distinct()
        } else pendingAdd.toList()
        val toRemove = pendingRemove.toList()
        var timestamp = remote.timestamp
        if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
            timestamp = maxOf(timestamp, api.uploadSubscriptions(toAdd, toRemove))
        }
        prefs.edit()
            .putLong(KEY_SUBS_SINCE, timestamp)
            .remove(KEY_PENDING_ADD).remove(KEY_PENDING_REMOVE)
            .apply()
    }

    private suspend fun syncProgress(api: Api) {
        val since = prefs.getLong(KEY_ACTIONS_SINCE, 0L)
        val remote = api.actionsSince(since)

        // The newest play action per episode.
        val latest = HashMap<String, JSONObject>()
        for (a in remote.actions) {
            if (a.optString("action").lowercase() != "play") continue
            val key = a.optString("episode")
            if (key.isBlank()) continue
            val prev = latest[key]
            if (prev == null || a.optString("timestamp") >= prev.optString("timestamp")) latest[key] = a
        }
        if (latest.isNotEmpty()) {
            val byGuid = HashMap<String, Episode>()
            val byUrl = HashMap<String, Episode>()
            store.episodes.value.values.flatten().forEach {
                byGuid[it.guid] = it
                if (it.audioUrl.isNotBlank()) byUrl[stripQuery(it.audioUrl)] = it
            }
            applying = true
            try {
                for (a in latest.values) {
                    val ep = a.optString("guid").takeIf { it.isNotBlank() }?.let { byGuid[it] }
                        ?: byUrl[stripQuery(a.optString("episode"))]
                        ?: continue
                    if (ep.guid == playingGuid()) continue
                    val positionMs = a.optInt("position", -1).toLong() * 1000
                    val totalMs = a.optInt("total", -1).toLong() * 1000
                    if (positionMs < 0) continue
                    val finished = totalMs > 0 && positionMs >= totalMs * 0.95
                    store.updateEpisode(
                        ep.copy(
                            positionMs = if (finished) 0L else positionMs,
                            durationMs = if (totalMs > 0) totalMs else ep.durationMs,
                            played = ep.played || finished
                        )
                    )
                }
            } finally {
                applying = false
            }
        }

        var timestamp = remote.timestamp
        val outgoing = pendingActions()
        if (outgoing.isNotEmpty()) {
            outgoing.chunked(30).forEach { batch ->
                timestamp = maxOf(timestamp, api.uploadActions(batch))
            }
        }
        prefs.edit()
            .putLong(KEY_ACTIONS_SINCE, timestamp)
            .remove(KEY_PENDING_ACTIONS)
            .apply()
    }

    // ---------------------------------------------------------------- plumbing

    private fun pendingActions(): List<JSONObject> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_PENDING_ACTIONS, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun readStatus(): Status {
        val server = prefs.getString(KEY_SERVER, null)
        val user = prefs.getString(KEY_USER, null)
        return Status(
            connected = server != null && user != null && prefs.contains(KEY_PASSWORD),
            kind = runCatching { Kind.valueOf(prefs.getString(KEY_KIND, "GPODDER")!!) }.getOrDefault(Kind.GPODDER),
            server = server.orEmpty(),
            username = user.orEmpty(),
            lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        )
    }

    private fun api(): Api? {
        val s = readStatus()
        if (!s.connected) return null
        val password = Secret.open(prefs.getString(KEY_PASSWORD, null)) ?: return null
        val device = prefs.getString(KEY_DEVICE, null) ?: return null
        return Api(s.kind, s.server, s.username, password, device)
    }

    private fun describe(t: Throwable): String = when (t) {
        is HttpError -> when (t.code) {
            401, 403 -> tr("Wrong username or password")
            404 -> tr("No gPodder sync found at that address")
            in 500..599 -> tr("The server had a problem ({0}) — try again later", t.code)
            else -> tr("The server said {0}", t.code)
        }
        is java.net.UnknownHostException -> tr("Couldn't find that server")
        is java.net.SocketTimeoutException -> tr("The server took too long to answer")
        is java.io.IOException -> tr("Couldn't reach the server")
        else -> t.message ?: tr("Sync failed")
    }

    private fun stripQuery(url: String) = url.substringBefore('?').substringBefore('#').lowercase()

    private fun normalizeServer(raw: String, kind: Kind): String {
        var s = raw.trim().trimEnd('/')
        if (s.isBlank()) return if (kind == Kind.GPODDER) "https://gpodder.net" else ""
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
        // Pasted the full Nextcloud path: keep only the server.
        s = s.substringBefore("/index.php").trimEnd('/')
        return s
    }

    private class HttpError(val code: Int) : java.io.IOException("HTTP $code")

    private class SubscriptionChanges(val added: List<String>, val removed: List<String>, val timestamp: Long)
    private class ActionChanges(val actions: List<JSONObject>, val timestamp: Long)

    /** The two server dialects. */
    private class Api(
        val kind: Kind,
        val server: String,
        val user: String,
        val password: String,
        val device: String
    ) {
        private val auth = "Basic " + Base64.encodeToString("$user:$password".toByteArray(), Base64.NO_WRAP)
        private val u get() = URLEncoder.encode(user, "UTF-8")

        fun signIn() {
            when (kind) {
                Kind.GPODDER -> {
                    request("POST", "$server/api/2/auth/$u/login.json", "")
                    request(
                        "POST", "$server/api/2/devices/$u/$device.json",
                        JSONObject().put("caption", "GlassCast").put("type", "mobile").toString()
                    )
                    // gpodder.net keeps one subscription list per device; two
                    // devices share theirs only once they're in a sync group.
                    // Join every device on the account into one, so the phone
                    // and the TV (and AntennaPod) see each other's follows.
                    // Servers without this endpoint just skip it.
                    runCatching {
                        val devices = JSONArray(request("GET", "$server/api/2/devices/$u.json", null))
                        val ids = (0 until devices.length())
                            .mapNotNull { devices.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
                        val group = (ids + device).distinct()
                        if (group.size > 1) {
                            request(
                                "POST", "$server/api/2/sync-devices/$u.json",
                                JSONObject().put("synchronize", JSONArray().put(JSONArray(group))).toString()
                            )
                        }
                    }
                }
                Kind.NEXTCLOUD -> request("GET", "$server/index.php/apps/gpoddersync/subscriptions?since=0", null)
            }
        }

        fun subscriptionsSince(since: Long): SubscriptionChanges {
            val url = when (kind) {
                Kind.GPODDER -> "$server/api/2/subscriptions/$u/$device.json?since=$since"
                Kind.NEXTCLOUD -> "$server/index.php/apps/gpoddersync/subscriptions?since=$since"
            }
            val json = JSONObject(request("GET", url, null))
            return SubscriptionChanges(
                added = json.optJSONArray("add").strings(),
                removed = json.optJSONArray("remove").strings(),
                timestamp = json.optLong("timestamp", 0L)
            )
        }

        fun uploadSubscriptions(add: List<String>, remove: List<String>): Long {
            val url = when (kind) {
                Kind.GPODDER -> "$server/api/2/subscriptions/$u/$device.json"
                Kind.NEXTCLOUD -> "$server/index.php/apps/gpoddersync/subscription_change/create"
            }
            val body = JSONObject().put("add", JSONArray(add)).put("remove", JSONArray(remove)).toString()
            return runCatching { JSONObject(request("POST", url, body)).optLong("timestamp", 0L) }.getOrDefault(0L)
        }

        fun actionsSince(since: Long): ActionChanges {
            val url = when (kind) {
                Kind.GPODDER -> "$server/api/2/episodes/$u.json?since=$since"
                Kind.NEXTCLOUD -> "$server/index.php/apps/gpoddersync/episode_action?since=$since"
            }
            val json = JSONObject(request("GET", url, null))
            val arr = json.optJSONArray("actions") ?: JSONArray()
            return ActionChanges((0 until arr.length()).mapNotNull { arr.optJSONObject(it) }, json.optLong("timestamp", 0L))
        }

        fun uploadActions(actions: List<JSONObject>): Long {
            val url = when (kind) {
                Kind.GPODDER -> "$server/api/2/episodes/$u.json"
                Kind.NEXTCLOUD -> "$server/index.php/apps/gpoddersync/episode_action/create"
            }
            val body = JSONArray(actions.map { JSONObject(it.toString()).put("device", device) }).toString()
            return runCatching { JSONObject(request("POST", url, body)).optLong("timestamp", 0L) }.getOrDefault(0L)
        }

        private fun request(method: String, url: String, body: String?): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", auth)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "GlassCast")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                if (code !in 200..299) throw HttpError(code)
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    /** AES-GCM with a key held by the Android Keystore; the key never leaves it. */
    private object Secret {
        private const val ALIAS = "glasscast_gpodder_key"
        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return gen.generateKey()
        }

        fun seal(plain: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.doFinal(plain.toByteArray())
            return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun open(sealed: String?): String? = runCatching {
            val (iv, data) = sealed!!.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        }.getOrNull()
    }

    private companion object {
        const val KEY_KIND = "kind"
        const val KEY_SERVER = "server"
        const val KEY_USER = "user"
        const val KEY_PASSWORD = "password"
        const val KEY_DEVICE = "device"
        const val KEY_SUBS_SINCE = "subs_since"
        const val KEY_ACTIONS_SINCE = "actions_since"
        const val KEY_PENDING_ADD = "pending_add"
        const val KEY_PENDING_REMOVE = "pending_remove"
        const val KEY_PENDING_ACTIONS = "pending_actions"
        const val KEY_LAST_SYNC = "last_sync"

        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
