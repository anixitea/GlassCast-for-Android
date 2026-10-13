package com.glasscast.app.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, kept on the device so it can be sent from Settings > About.
 * Nothing is sent anywhere unless the person shares it.
 *
 * Two sources, because the OPPO Pad mini's problem might be either:
 *
 * - A crash. The uncaught-exception handler writes the stack trace as the
 *   process dies (`crash/last.txt`).
 * - A hang. An app stuck on the main thread never throws, so the handler
 *   never runs; the system kills it for not responding (or ColorOS kills it
 *   without a dialog), and from the outside that looks like a crash. Android
 *   11+ keeps a record of why each of the app's processes ended, with the
 *   main thread's stack for a not-responding kill. The next launch reads that
 *   record and keeps the unusual endings (`crash/exits.txt`).
 *
 * The record survives app updates, so the first launch of a new version can
 * still report how the previous one died.
 */
object CrashLog {

    private const val DIR = "crash"
    private const val CRASH = "last.txt"
    private const val EXITS = "exits.txt"
    /** When the report was last sent; exits before this were already reported. */
    private const val CLEARED = "cleared"

    /** Sharing goes through a Binder transaction, which has a size limit. */
    private const val MAX_REPORT = 60_000
    /** Per not-responding kill, and per thread within it. */
    private const val MAX_TRACE = 24_000
    private const val MAX_THREAD = 8_000

    private val _report = MutableStateFlow<String?>(null)
    /** The report waiting to be sent, or null. Filled by [load]. */
    val report: StateFlow<String?> = _report.asStateFlow()

    /** Call first thing; every later failure in the process is then recorded. */
    fun install(context: Context) {
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** Reads the exit record and the files. Disk work: call off the main thread. */
    fun load(context: Context) {
        val dir = dir(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { recordExits(context, dir) }
        }
        _report.value = runCatching { compose(context, dir) }.getOrNull()
    }

    /** After sharing. Disk work: call off the main thread. */
    private var lastState: String? = null

    /**
     * What the app is showing right now, in a few words ("phone library
     * player playing"). Android keeps it with the process (up to 128 bytes,
     * in memory, no disk) and hands it back in the exit record if the process
     * dies, so a not-responding report says which screen was up. Call it when
     * the screen changes, not every frame: the system may throttle it.
     */
    fun noteState(context: Context, state: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || state == lastState) return
        lastState = state
        runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.setProcessStateSummary(state.take(120).toByteArray(Charsets.UTF_8))
        }
    }

    fun clear(context: Context) {
        _report.value = null
        val dir = dir(context)
        File(dir, CRASH).delete()
        File(dir, EXITS).delete()
        runCatching { File(dir, CLEARED).writeText(System.currentTimeMillis().toString()) }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR)

    private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            append(header(context))
            append("Thread: ").append(thread.name).append("\n\n")
            append(trace.take(MAX_REPORT / 2))
        }
        val dir = dir(context).apply { mkdirs() }
        File(dir, CRASH).writeText(text)
    }

    private fun compose(context: Context, dir: File): String? {
        val crash = File(dir, CRASH).takeIf { it.exists() }?.readText()
        val exits = File(dir, EXITS).takeIf { it.exists() }?.readText()
        if (crash == null && exits == null) return null
        return buildString {
            if (crash != null) append(crash) else append(header(context))
            if (exits != null) append("\n\n").append(exits)
        }.take(MAX_REPORT)
    }

    private fun header(context: Context): String = buildString {
        append("GlassCast ").append(version(context)).append('\n')
        append(stamp(System.currentTimeMillis())).append('\n')
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
    }

    private fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(ms))

    // ---------------------------------------------------------------- exits

    @RequiresApi(Build.VERSION_CODES.R)
    private fun recordExits(context: Context, dir: File) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val cleared = runCatching { File(dir, CLEARED).readText().trim().toLong() }.getOrDefault(0L)
        val unusual = am.getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { it.timestamp > cleared && it.isUnusual() }
            .sortedByDescending { it.timestamp }
        if (unusual.isEmpty()) return

        // Exits from before this version was installed belong to the old one.
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)

        val text = buildString {
            append("Recent exits, newest first:\n")
            unusual.forEach { exit ->
                append(stamp(exit.timestamp)).append("  ").append(exit.reasonName())
                append("  importance ").append(exit.importance)
                append("  pss ").append(exit.pss / 1024).append(" MB")
                append("  rss ").append(exit.rss / 1024).append(" MB")
                if (exit.status != 0) append("  status ").append(exit.status)
                if (exit.timestamp < installed) append("  (previous version)")
                exit.processStateSummary?.let { bytes ->
                    append("\n    showing: ").append(String(bytes, Charsets.UTF_8))
                }
                exit.description?.takeIf { it.isNotBlank() }?.let { append("\n    ").append(it.take(400)) }
                append('\n')
            }
            // The two newest not-responding kills: what the threads that
            // matter were doing when the system gave up (see keyThreads).
            unusual.filter { it.reason == ApplicationExitInfo.REASON_ANR }.take(2).forEach { exit ->
                val trace = runCatching {
                    exit.traceInputStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: return@forEach
                append("\nNot responding, ").append(stamp(exit.timestamp)).append(":\n")
                append(keyThreads(trace))
            }
        }
        dir.mkdirs()
        File(dir, EXITS).writeText(text)
    }

    /**
     * The threads worth reading in an ANR dump, which lists every thread in
     * the process and is far too long to send whole:
     *
     * - "main", where input and drawing are handled;
     * - "RenderThread" and the "hwui" workers, which send each frame to the
     *   GPU. The main thread waits for them every frame, so when they stall,
     *   main shows only the wait (nSyncAndDrawFrame) and the cause is here.
     *   The first report from the field (a Redmi Note 11, 1.6) was exactly
     *   that, and 1.6 only sent main;
     * - up to four other threads running GlassCast's own code.
     */
    private fun keyThreads(trace: String): String {
        val blocks = threadBlocks(trace)
        if (blocks.isEmpty()) return trace.take(MAX_TRACE) + "\n"
        fun drawing(name: String) = name == "main" || name == "RenderThread" || name.startsWith("hwui")
        val picked = blocks.filter { (name, _) -> drawing(name) } +
            blocks.filter { (name, body) -> !drawing(name) && "com.glasscast.app" in body }.take(4)
        return picked.joinToString("\n") { (_, body) -> body.take(MAX_THREAD) + "\n" }.take(MAX_TRACE)
    }

    /** (name, block) for each thread: from its quoted header line to the next blank line. */
    private fun threadBlocks(trace: String): List<Pair<String, String>> {
        val lines = trace.lines()
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith("\"")) {
                i++
                continue
            }
            val end = (i + 1 until lines.size).firstOrNull { lines[it].isBlank() } ?: lines.size
            out += line.substring(1).substringBefore('"') to lines.subList(i, end).joinToString("\n")
            i = end
        }
        return out
    }

    /*
     * Which endings are worth reporting. A crash, a not-responding kill and a
     * failed start always are. A kill or a low-memory kill only counts while
     * the app was on screen: in the background they're routine, and ColorOS
     * does them all the time. Leaving on purpose, a force stop, an update and
     * a permission change never count.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.isUnusual(): Boolean = when (reason) {
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> true
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        // Android 14's two (constants, so they compile into older versions
        // as plain numbers): an in-app update ends the process on screen.
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> false
        else -> importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.reasonName(): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit"
        ApplicationExitInfo.REASON_SIGNALED -> "killed (signal $status)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_OTHER -> "killed by the system"
        else -> "reason $reason"
    }
}
