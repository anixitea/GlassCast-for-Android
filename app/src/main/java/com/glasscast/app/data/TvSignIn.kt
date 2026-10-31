package com.glasscast.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sign in with Google on the TV, by phone.
 *
 * The phone signs in through Play services' account picker, which a remote
 * can't drive. The TV uses Google's flow for TVs and limited-input devices
 * instead: it asks Google for a code, shows a QR code for google.com/device
 * and the code beside it, and waits while the person signs in on their phone
 * and types the code. Google then hands the TV a refresh token, which is kept
 * (in the app's private storage) to get short-lived access tokens from, as
 * the phone gets them from Play services.
 *
 * Same one permission as the phone, `drive.appdata` (Google allows it in this
 * flow), and the same Cloud project, so the TV reads and writes the same
 * hidden app folder: one library across the phone and the TV.
 *
 * Needs an OAuth client of the type "TVs and Limited Input devices"; its ID
 * and secret come from local.properties through the manifest (see
 * app/build.gradle.kts). Without one, [configured] is false and the TV
 * doesn't offer sign-in.
 */
class TvSignIn(context: Context, private val prefs: SharedPreferences) {

    private val clientId: String
    private val clientSecret: String

    init {
        val meta = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull()
        // Spaces and quotes off, in case they came with the value pasted
        // into local.properties: Google would say the client doesn't exist.
        clientId = meta?.getString(META_CLIENT_ID).orEmpty().trim().trim('"', '\'').trim()
        clientSecret = meta?.getString(META_CLIENT_SECRET).orEmpty().trim().trim('"', '\'').trim()
    }

    /** Whether this build has a client to sign in with. */
    val configured: Boolean get() = clientId.isNotEmpty()

    /** Signed in this way (a refresh token is kept). */
    val signedIn: Boolean get() = prefs.getString(KEY_REFRESH, null) != null

    /**
     * What Google said the last time a step failed ("invalid_client: …", or
     * the connection's error), shown small under the TV's message so a
     * failure can be told apart: a wrong client, a blocked account, no
     * network. Not translated; it's Google's text.
     */
    @Volatile
    var lastError: String? = null
        private set

    private val tokenLock = Mutex()
    private var accessToken: String? = null
    private var accessExpiresAt = 0L

    /** What the TV shows while waiting: the code to type, where, and how long it lasts. */
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresAt: Long,
        val intervalMs: Long
    )

    sealed interface Poll {
        /** Not yet: wait [intervalMs] and ask again. */
        data class Waiting(val intervalMs: Long) : Poll
        data class Granted(val accessToken: String) : Poll
        data object Denied : Poll
        data object Expired : Poll
        data class Failed(val message: String) : Poll
    }

    /** Asks Google for a code to show. Null on failure, with [lastError] saying why. */
    suspend fun start(): DeviceCode? = withContext(Dispatchers.IO) {
        lastError = null
        if (clientSecret.isEmpty()) lastError = "no client secret in this build"
        runCatching {
            val (code, body) = post(DEVICE_CODE_URL, mapOf("client_id" to clientId, "scope" to SCOPE))
            if (code !in 200..299) {
                lastError = describe(code, body)
                return@runCatching null
            }
            val o = JSONObject(body)
            DeviceCode(
                deviceCode = o.getString("device_code"),
                userCode = o.getString("user_code"),
                verificationUrl = o.optString("verification_url", o.optString("verification_uri", "https://www.google.com/device")),
                expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 1800) * 1000L,
                intervalMs = o.optLong("interval", 5).coerceAtLeast(1) * 1000L
            )
        }.getOrElse { e ->
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    /** Google's error and its description, or the status code. */
    private fun describe(code: Int, body: String): String {
        val o = runCatching { JSONObject(body) }.getOrNull()
        val error = o?.optString("error").orEmpty()
        val description = o?.optString("error_description").orEmpty()
        return listOf(error, description).filter { it.isNotBlank() }.joinToString(": ").ifBlank { "HTTP $code" }
    }

    /** Asks once whether the person has finished on their phone. */
    suspend fun poll(device: DeviceCode, intervalMs: Long): Poll = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > device.expiresAt) return@withContext Poll.Expired
        runCatching {
            val (code, body) = post(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "device_code" to device.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                )
            )
            val o = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            if (code in 200..299) {
                val access = o.getString("access_token")
                val refresh = o.optString("refresh_token")
                if (refresh.isNotEmpty()) prefs.edit().putString(KEY_REFRESH, refresh).apply()
                tokenLock.withLock {
                    accessToken = access
                    accessExpiresAt = System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 60) * 1000L
                }
                Poll.Granted(access)
            } else {
                when (o.optString("error")) {
                    "authorization_pending" -> Poll.Waiting(intervalMs)
                    "slow_down" -> Poll.Waiting(intervalMs + 5_000L)
                    "access_denied" -> Poll.Denied
                    "expired_token" -> Poll.Expired
                    else -> Poll.Failed(describe(code, body).also { lastError = it })
                }
            }
        }.getOrElse { Poll.Waiting(intervalMs) }
    }

    /**
     * A current access token: the one in hand if it has a minute left, or a
     * new one from the refresh token. Null if Google no longer accepts the
     * refresh token (access was removed): the TV needs signing in again.
     */
    suspend fun token(): String? = tokenLock.withLock {
        val now = System.currentTimeMillis()
        accessToken?.takeIf { now < accessExpiresAt }?.let { return@withLock it }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return@withLock null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                post(
                    TOKEN_URL,
                    mapOf(
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "refresh_token" to refresh,
                        "grant_type" to "refresh_token"
                    )
                )
            }.getOrNull()
        } ?: error("No connection")
        val (code, body) = result
        val o = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        if (code in 200..299) {
            accessToken = o.getString("access_token")
            accessExpiresAt = now + (o.optLong("expires_in", 3600) - 60) * 1000L
            accessToken
        } else if (o.optString("error") == "invalid_grant") {
            null
        } else {
            error("Token $code")
        }
    }

    /** The token in hand was turned down: get a fresh one next time. */
    suspend fun invalidate() = tokenLock.withLock {
        accessToken = null
        accessExpiresAt = 0L
    }

    /** Signs out: Google forgets the TV's access, and the TV forgets the token. */
    suspend fun signOut() {
        val refresh = prefs.getString(KEY_REFRESH, null)
        if (refresh != null) {
            withContext(Dispatchers.IO) { runCatching { post(REVOKE_URL, mapOf("token" to refresh)) } }
        }
        prefs.edit().remove(KEY_REFRESH).apply()
        invalidate()
    }

    private fun post(url: String, form: Map<String, String>): Pair<Int, String> {
        val body = form.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }.toByteArray()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        const val META_CLIENT_ID = "com.glasscast.app.tv_client_id"
        const val META_CLIENT_SECRET = "com.glasscast.app.tv_client_secret"
        const val KEY_REFRESH = "tv_refresh_token"
    }
}
