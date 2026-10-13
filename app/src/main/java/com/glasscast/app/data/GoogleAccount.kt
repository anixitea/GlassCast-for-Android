package com.glasscast.app.data

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.glasscast.app.GlassCastApp
import com.glasscast.app.ui.tr
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign in with Google, on. The Cloud project is published (in production, no
 * user cap: the one permission is non-sensitive), with its home page and
 * privacy policy at anixitea.github.io (site/ in this project). Off hides the
 * Settings card and stops every sync, for if that ever has to be undone; the
 * backup file is separate either way (docs/DEVELOPMENT.md, "Google sign-in
 * setup").
 */
const val GOOGLE_SIGN_IN = true

/** The signed-in Google account, as Settings shows it. */
data class AccountState(
    val signedIn: Boolean = false,
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val lastSyncAt: Long = 0L,
    val syncing: Boolean = false,
    /** Google stopped handing out access (revoked, or the password changed): sign in again. */
    val needsSignIn: Boolean = false,
    /** The last sync's failure, for Settings; cleared by the next success. */
    val error: String? = null
)

/** The result of asking Google for access. */
sealed interface GoogleAuth {
    data class Granted(val token: String) : GoogleAuth
    /** Google needs to show the account picker or the consent screen first. */
    data class NeedsConsent(val intent: PendingIntent) : GoogleAuth
    data class Failed(val message: String) : GoogleAuth
}

/**
 * Sign in with Google: your library, progress and bookmarks follow your
 * Google account to every phone you sign in on.
 *
 * There's no GlassCast server. What's kept for an account lives in that
 * account's own Google storage, in GlassCast's app folder: hidden, readable
 * only by GlassCast, and unable to touch anything else there. It's one
 * permission (`drive.appdata`), which Google classes as non-sensitive. The
 * name, email and photo shown in Settings come from the same permission.
 *
 * Access goes through Play services' AuthorizationClient: the account picker
 * and consent once, then short-lived tokens on request, silently, in the app
 * or in the background. No password or refresh token is stored. The calls
 * are plain HTTPS.
 *
 * Syncing merges one library document (the backup document plus what
 * changed) with this phone's:
 *  - shows: the latest of following and unfollowing wins, by time, so an
 *    unfollow on one phone carries to the others ([recordFollow]);
 *  - played marks and places: whichever is further along;
 *  - bookmarks: the newest memo wins; one deleted anywhere is deleted
 *    everywhere ([recordBookmarkDeleted]);
 *  - settings and the queue: taken from the account once, on a phone's first
 *    sign-in, and kept per phone after that.
 * It runs on signing in, when the app opens or closes (SyncWorker), once a
 * day with the background job, and from Sync now.
 *
 * Needs an OAuth client in a Google Cloud project for the package and the
 * signing key's SHA-1 (docs/DEVELOPMENT.md, "Google sign-in setup").
 */
class GoogleAccount(private val app: GlassCastApp) {

    private val prefs = app.getSharedPreferences("glasscast_account", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val syncLock = Mutex()

    private val _state = MutableStateFlow(
        AccountState(
            signedIn = prefs.getBoolean(KEY_SIGNED_IN, false),
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            photoUrl = prefs.getString(KEY_PHOTO, "").orEmpty(),
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
            needsSignIn = prefs.getBoolean(KEY_NEEDS_SIGN_IN, false)
        )
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    // ---------------------------------------------------------------- access

    /** Asks for access. From an activity this can come back needing consent; from the background it never shows anything. */
    suspend fun authorize(context: Context): GoogleAuth = try {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val token = result.accessToken
        val pending = result.pendingIntent
        when {
            result.hasResolution() && pending != null -> GoogleAuth.NeedsConsent(pending)
            token != null -> GoogleAuth.Granted(token)
            else -> GoogleAuth.Failed(tr("Couldn't sign in with Google"))
        }
    } catch (e: ApiException) {
        GoogleAuth.Failed(describe(e))
    } catch (e: Exception) {
        GoogleAuth.Failed(tr("Couldn't sign in with Google"))
    }

    /** The answer from the account picker or consent screen. */
    fun resultFrom(context: Context, data: Intent?): GoogleAuth = try {
        val token = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        if (token != null) GoogleAuth.Granted(token) else GoogleAuth.Failed(tr("Couldn't sign in with Google"))
    } catch (e: ApiException) {
        GoogleAuth.Failed(describe(e))
    }

    private fun describe(e: ApiException): String = when (e.statusCode) {
        // DEVELOPER_ERROR: no OAuth client for this package and signing key.
        10 -> tr("Google sign-in isn't set up for this build (code 10)")
        // CANCELED, or the picker closed.
        16, 12501 -> tr("Canceled")
        7 -> tr("No connection")
        // Google's own words after the code ("8: …"), which is what tells
        // one setup problem from another.
        else -> tr("Couldn't sign in with Google (code {0})", e.message?.trim()?.takeIf { it.isNotBlank() } ?: e.statusCode)
    }

    private class Unauthorized : Exception()
    private class NeedsSignIn : Exception()
    /** Google said no, with a message fit for Settings. */
    private class AuthFailed(message: String) : Exception(message)

    /**
     * Runs [block] with a token, silently. A token Google turns down is
     * cleared and asked for once more; if Google needs the person, the
     * account is marked as needing to sign in again.
     */
    private suspend fun <T> withToken(context: Context, block: suspend (String) -> T): T {
        repeat(2) { attempt ->
            when (val auth = authorize(context)) {
                is GoogleAuth.Granted -> try {
                    return block(auth.token)
                } catch (e: Unauthorized) {
                    runCatching {
                        Identity.getAuthorizationClient(context)
                            .clearToken(ClearTokenRequest.builder().setToken(auth.token).build())
                            .await()
                    }
                    if (attempt == 1) throw e
                }
                is GoogleAuth.NeedsConsent -> {
                    setNeedsSignIn(true)
                    throw NeedsSignIn()
                }
                is GoogleAuth.Failed -> throw AuthFailed(auth.message)
            }
        }
        error("unreachable")
    }

    // --------------------------------------------------------------- signing

    /** After access is granted: who it is, then the first sync. */
    suspend fun onSignedIn(token: String) {
        val user = runCatching {
            JSONObject(call("GET", "$API/about?fields=user(displayName,emailAddress,photoLink)", token).second)
                .optJSONObject("user")
        }.getOrNull()
        prefs.edit()
            .putBoolean(KEY_SIGNED_IN, true)
            .putString(KEY_NAME, user?.optString("displayName").orEmpty())
            .putString(KEY_EMAIL, user?.optString("emailAddress").orEmpty())
            .putString(KEY_PHOTO, user?.optString("photoLink").orEmpty())
            .putBoolean(KEY_NEEDS_SIGN_IN, false)
            .apply()
        _state.value = _state.value.copy(
            signedIn = true,
            name = user?.optString("displayName").orEmpty(),
            email = user?.optString("emailAddress").orEmpty(),
            photoUrl = user?.optString("photoLink").orEmpty(),
            needsSignIn = false,
            error = null
        )
        com.glasscast.app.background.BackgroundRefresh.apply(app, app.settings.backgroundRefreshWanted)
        // In the account's own scope: leaving Settings mustn't cut it short.
        syncNow()
    }

    /**
     * Signs out: Google forgets GlassCast's access, and the phone forgets the
     * account. The library stays on the phone; what's in the account stays
     * there for next time.
     */
    suspend fun signOut(context: Context) {
        val email = _state.value.email
        if (email.isNotBlank()) {
            runCatching {
                Identity.getAuthorizationClient(context).revokeAccess(
                    RevokeAccessRequest.builder()
                        .setAccount(Account(email, "com.google"))
                        .setScopes(listOf(Scope(SCOPE)))
                        .build()
                ).await()
            }
        }
        prefs.edit().clear().apply()
        _state.value = AccountState()
        com.glasscast.app.background.BackgroundRefresh.apply(app, app.settings.backgroundRefreshWanted)
    }

    private fun setNeedsSignIn(on: Boolean) {
        prefs.edit().putBoolean(KEY_NEEDS_SIGN_IN, on).apply()
        _state.value = _state.value.copy(needsSignIn = on)
    }

    // ------------------------------------------------------------- journal

    /** A show followed or unfollowed here, with when (FeedStore.onSubscriptionChanged). */
    fun recordFollow(feedUrl: String, followed: Boolean, at: Long = System.currentTimeMillis()) {
        val key = if (followed) KEY_FOLLOWED else KEY_REMOVED
        val map = readTimes(key)
        map[feedUrl] = at
        writeTimes(key, map)
    }

    /** A bookmark deleted here (BookmarkStore.onRemoved). */
    fun recordBookmarkDeleted(id: Long) {
        val ids = readIds()
        ids += id
        writeIds(ids)
    }

    private fun readTimes(key: String): MutableMap<String, Long> = runCatching {
        val o = JSONObject(prefs.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWithTo(LinkedHashMap()) { o.getLong(it) }
    }.getOrDefault(LinkedHashMap())

    private fun writeTimes(key: String, map: Map<String, Long>) {
        // A few hundred shows' worth is plenty; the oldest go first.
        val kept = map.entries.sortedByDescending { it.value }.take(600)
        prefs.edit().putString(key, JSONObject().apply { kept.forEach { put(it.key, it.value) } }.toString()).apply()
    }

    private fun readIds(): MutableSet<Long> = runCatching {
        val a = JSONArray(prefs.getString(KEY_DELETED_BOOKMARKS, "[]"))
        (0 until a.length()).mapTo(LinkedHashSet()) { a.getLong(it) }
    }.getOrDefault(LinkedHashSet())

    private fun writeIds(ids: Set<Long>) {
        prefs.edit().putString(KEY_DELETED_BOOKMARKS, JSONArray(ids.toList().takeLast(1000)).toString()).apply()
    }

    // ----------------------------------------------------------------- sync

    /** Syncs in the background of the app (Sync now, signing in). */
    fun syncNow() {
        scope.launch { sync(app) }
    }

    /**
     * When the app opens or closes: a sync in a few seconds, through
     * WorkManager so it finishes even if the app is closed meanwhile. Not
     * more than once every two minutes.
     */
    fun syncSoon() {
        if (!GOOGLE_SIGN_IN) return
        val s = _state.value
        if (!s.signedIn || s.needsSignIn || s.syncing) return
        if (System.currentTimeMillis() - s.lastSyncAt < 2 * 60 * 1000L) return
        com.glasscast.app.background.SyncWorker.enqueue(app)
    }

    /** The background job's: about once a day. */
    suspend fun syncIfDue() {
        if (!GOOGLE_SIGN_IN) return
        val s = _state.value
        if (!s.signedIn || s.needsSignIn) return
        if (System.currentTimeMillis() - s.lastSyncAt < DAY_MS - 2 * HOUR_MS) return
        sync(app)
    }

    /** One sync: fetch the account's library, merge it here, send the result back. True on success. */
    suspend fun sync(context: Context): Boolean {
        if (!GOOGLE_SIGN_IN || !_state.value.signedIn) return false
        return syncLock.withLock {
            _state.value = _state.value.copy(syncing = true)
            val result = runCatching {
                withToken(context) { token ->
                    app.feedStore.awaitLoaded()
                    val existing = listRemote(token)
                    val remote = existing.firstOrNull()?.let { download(token, it) }
                    val firstTime = !prefs.getBoolean(KEY_SYNCED_ONCE, false)
                    if (remote != null) merge(JSONObject(remote), firstTime)
                    // Nothing worth sending from a phone with no library and no
                    // account copy yet: that would only be an empty document.
                    if (remote != null || app.feedStore.feeds.value.isNotEmpty()) {
                        upload(token, document(), existing)
                    }
                }
            }
            val now = System.currentTimeMillis()
            if (result.isSuccess) {
                prefs.edit().putLong(KEY_LAST_SYNC, now).putBoolean(KEY_SYNCED_ONCE, true).apply()
                _state.value = _state.value.copy(syncing = false, lastSyncAt = now, error = null, needsSignIn = false)
            } else {
                val e = result.exceptionOrNull()
                _state.value = _state.value.copy(
                    syncing = false,
                    error = when (e) {
                        is NeedsSignIn -> null
                        is AuthFailed -> e.message
                        else -> tr("Couldn't sync")
                    }
                )
            }
            result.isSuccess
        }
    }

    /** This phone's library as the account document: the backup document, plus unfollows and deletions. */
    private suspend fun document(): String {
        val root = JSONObject(app.backup.build())
        root.put("kind", "library")
        val followed = readTimes(KEY_FOLLOWED)
        root.optJSONArray("shows")?.let { shows ->
            for (i in 0 until shows.length()) {
                val o = shows.optJSONObject(i) ?: continue
                o.put("followedAt", followed[o.optString("url")] ?: o.optLong("addedAt"))
            }
        }
        root.put("removed", JSONObject().apply { readTimes(KEY_REMOVED).forEach { (url, at) -> put(url, at) } })
        root.put("deletedBookmarks", JSONArray(readIds().toList()))
        return root.toString()
    }

    private suspend fun merge(remote: JSONObject, firstTime: Boolean) {
        val store = app.feedStore

        // Settings and queue: once, on this phone's first sign-in.
        if (firstTime) remote.optJSONObject("settings")?.let { app.backup.applySettings(it) }

        // Shows: the latest of following and unfollowing, by time.
        val localFollowed = readTimes(KEY_FOLLOWED)
        val localRemoved = readTimes(KEY_REMOVED)
        val remoteRemoved = remote.optJSONObject("removed")
        val remoteShows = remote.optJSONArray("shows") ?: JSONArray()
        val remoteFollowed = HashMap<String, Pair<Long, String>>()
        for (i in 0 until remoteShows.length()) {
            val o = remoteShows.optJSONObject(i) ?: continue
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
            remoteFollowed[url] = (o.optLong("followedAt").takeIf { it > 0 } ?: o.optLong("addedAt")) to o.optString("title")
        }
        val here = store.feeds.value.associateBy { it.url }
        val urls = remoteFollowed.keys + here.keys +
            (remoteRemoved?.keys()?.asSequence()?.toSet() ?: emptySet()) + localRemoved.keys
        val toFollow = mutableListOf<OpmlEntry>()
        val toDrop = mutableListOf<Feed>()
        val followTimes = HashMap<String, Long>()
        val dropTimes = HashMap<String, Long>()
        for (url in urls) {
            val followedAt = maxOf(
                localFollowed[url] ?: here[url]?.addedAt ?: 0L,
                remoteFollowed[url]?.first ?: 0L
            )
            val removedAt = maxOf(localRemoved[url] ?: 0L, remoteRemoved?.optLong(url) ?: 0L)
            val want = followedAt > removedAt
            val feed = here[url]
            if (want && feed == null) {
                toFollow += OpmlEntry(title = remoteFollowed[url]?.second.orEmpty(), url = url)
                followTimes[url] = followedAt
            } else if (!want && feed != null) {
                toDrop += feed
                dropTimes[url] = removedAt
            }
        }
        if (toFollow.isNotEmpty()) {
            store.importOpml(toFollow)
            store.clearImportProgress()
        }
        toDrop.forEach { store.unsubscribe(it) }
        // The journal keeps the account's times, not the moment this phone
        // caught up, so an unfollow made elsewhere meanwhile still wins.
        followTimes.forEach { (url, at) -> recordFollow(url, true, at) }
        dropTimes.forEach { (url, at) -> recordFollow(url, false, at) }

        // Show dates, marks and places.
        val dates = (0 until remoteShows.length()).mapNotNull { i ->
            val o = remoteShows.optJSONObject(i) ?: return@mapNotNull null
            o.optString("url") to (o.optLong("addedAt") to o.optLong("lastPlayedAt"))
        }.toMap()
        store.restoreShowDates(dates)
        val eps = remote.optJSONArray("episodes") ?: JSONArray()
        val progress = HashMap<String, SavedProgress>(eps.length())
        for (i in 0 until eps.length()) {
            val o = eps.optJSONObject(i) ?: continue
            val guid = o.optString("guid").takeIf { it.isNotBlank() } ?: continue
            progress[guid] = SavedProgress(o.optLong("position"), o.optLong("duration"), o.optBoolean("played"))
        }
        store.applyProgress(progress)

        // Bookmarks.
        val deleted = readIds()
        remote.optJSONArray("deletedBookmarks")?.let { a -> for (i in 0 until a.length()) deleted += a.optLong(i) }
        writeIds(deleted)
        val marks = remote.optJSONArray("bookmarks") ?: JSONArray()
        app.bookmarks.mergeSynced(
            (0 until marks.length()).mapNotNull { i ->
                val o = marks.optJSONObject(i) ?: return@mapNotNull null
                Bookmark(
                    id = o.optLong("id"),
                    guid = o.optString("guid").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    positionMs = o.optLong("position"),
                    memo = o.optString("memo"),
                    createdAt = o.optLong("created"),
                    editedAt = o.optLong("edited", o.optLong("created"))
                )
            },
            deleted
        )

        if (firstTime) {
            val queue = remote.optJSONArray("queue") ?: JSONArray()
            app.player.loadQueue((0 until queue.length()).mapNotNull { store.episodeByGuid(queue.optString(it)) })
        }
        store.flushNow()
    }

    // ------------------------------------------------------------------ REST

    private data class RemoteFile(val id: String, val modifiedTime: String)

    /** The account's library files, newest first (normally one). */
    private suspend fun listRemote(token: String): List<RemoteFile> {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val fields = URLEncoder.encode("files(id,modifiedTime)", "UTF-8")
        val (_, body) = call("GET", "$API/files?spaces=appDataFolder&q=$q&fields=$fields&orderBy=modifiedTime%20desc&pageSize=20", token)
        val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).mapNotNull { i ->
            val f = files.optJSONObject(i) ?: return@mapNotNull null
            RemoteFile(f.optString("id"), f.optString("modifiedTime"))
        }
    }

    private suspend fun download(token: String, file: RemoteFile): String =
        call("GET", "$API/files/${file.id}?alt=media", token).second

    /** Writes the new document, then removes the old ones, so there's never a moment with none. */
    private suspend fun upload(token: String, json: String, old: List<RemoteFile>) {
        val metadata = JSONObject().apply {
            put("name", FILE_NAME)
            put("mimeType", "application/json")
            put("parents", JSONArray().put("appDataFolder"))
        }
        val boundary = "glasscast" + UUID.randomUUID().toString().replace("-", "")
        val body = ByteArrayOutputStream().apply {
            fun line(s: String) = write((s + "\r\n").toByteArray())
            line("--$boundary")
            line("Content-Type: application/json; charset=UTF-8")
            line("")
            line(metadata.toString())
            line("--$boundary")
            line("Content-Type: application/json; charset=UTF-8")
            line("")
            write(json.toByteArray())
            write("\r\n".toByteArray())
            line("--$boundary--")
        }.toByteArray()
        val (_, created) = call(
            "POST", "$UPLOAD/files?uploadType=multipart&fields=id", token,
            body = body, contentType = "multipart/related; boundary=$boundary"
        )
        val newId = JSONObject(created).optString("id")
        old.filter { it.id != newId }.forEach { f -> runCatching { call("DELETE", "$API/files/${f.id}", token) } }
    }

    /** One request. Returns the status and the body; throws on anything but 2xx. */
    private suspend fun call(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code == 401) throw Unauthorized()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) error("Drive $code")
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "glasscast-library.json"
        const val KEY_SIGNED_IN = "signed_in"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_PHOTO = "photo"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_SYNCED_ONCE = "synced_once"
        const val KEY_NEEDS_SIGN_IN = "needs_sign_in"
        const val KEY_FOLLOWED = "followed"
        const val KEY_REMOVED = "removed"
        const val KEY_DELETED_BOOKMARKS = "deleted_bookmarks"
        const val HOUR_MS = 60 * 60 * 1000L
        const val DAY_MS = 24 * HOUR_MS
    }
}

/** A Play services Task as a suspend call. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
