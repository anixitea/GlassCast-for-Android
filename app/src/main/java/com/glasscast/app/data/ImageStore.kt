package com.glasscast.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Artwork pipeline. Lesson 3, in code:
 *
 *  - never decode inside list-item composition
 *  - decode downsampled via inSampleSize at roughly the drawn size
 *  - key the LruCache by URL *and* size
 *  - return the SAME Bitmap instance for the same key, so Compose doesn't think
 *    the row changed, recompose, and decode again
 *
 * This replaces Coil for the whole app rather than running two image pipelines
 * side by side — Palette needs a real Bitmap anyway, and the caching rule above
 * has to hold for the artwork that drives the player's color.
 */
class ImageStore(context: Context) {

    private val dir = File(context.cacheDir, "artwork").apply { mkdirs() }

    private val memory = object : LruCache<String, Bitmap>(
        // A quarter of the heap, in KB.
        ((Runtime.getRuntime().maxMemory() / 1024) / 4).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun key(url: String, px: Int) = "$url@$px"

    /** Synchronous cache peek, for the first frame. Never touches disk or network. */
    fun peek(url: String, px: Int): Bitmap? = if (url.isBlank()) null else memory.get(key(url, px))

    suspend fun load(url: String, px: Int): Bitmap? {
        if (url.isBlank()) return null
        memory.get(key(url, px))?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = fileFor(url)
            if (!file.exists() && !download(url, file)) return@withContext null
            val bmp = decodeDownsampled(file, px) ?: return@withContext null
            // Re-check: another coroutine may have won the race. Always hand back
            // the instance that is already in the cache.
            synchronized(memory) {
                val existing = memory.get(key(url, px))
                if (existing != null) existing
                else {
                    memory.put(key(url, px), bmp)
                    bmp
                }
            }
        }
    }

    /**
     * The cover's file on disk, downloaded first if it isn't cached — what the
     * Android Auto artwork provider hands to the car.
     */
    suspend fun cachedFile(url: String): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val file = fileFor(url)
        if (file.exists() || download(url, file)) file else null
    }

    private fun fileFor(url: String): File {
        val md = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(dir, md.joinToString("") { "%02x".format(it) })
    }

    private fun download(url: String, into: File): Boolean = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "GlassCast/0.1 (Android)")
        }
        if (conn.responseCode in 200..299) {
            val tmp = File(into.absolutePath + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(into)
            conn.disconnect()
            true
        } else {
            conn.disconnect(); false
        }
    } catch (_: Exception) {
        false
    }

    private fun decodeDownsampled(file: File, targetPx: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > targetPx * 2) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    } catch (_: Exception) {
        null
    }

    fun clearDisk() {
        dir.listFiles()?.forEach { it.delete() }
        memory.evictAll()
    }

    fun diskUsageBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
