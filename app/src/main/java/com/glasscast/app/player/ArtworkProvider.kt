package com.glasscast.app.player

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.glasscast.app.GlassCastApp
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Covers for Android Auto, which only loads images through content://
 * addresses. The address carries the cover's URL; the file is the one the
 * app's image cache already keeps, downloaded first if it isn't there yet.
 *
 * Read-only, and it serves covers of shows and episodes in the library and
 * nothing else — it never fetches an arbitrary address another app hands it.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Artwork is read-only")
        val url = uri.lastPathSegment
            ?.let { runCatching { String(Base64.decode(it, FLAGS)) }.getOrNull() }
            ?: throw FileNotFoundException(uri.toString())
        val app = context?.applicationContext as? GlassCastApp ?: throw FileNotFoundException(uri.toString())
        val file = runBlocking {
            withTimeoutOrNull(15_000) {
                app.feedStore.awaitLoaded()
                if (isKnown(app, url)) app.imageStore.cachedFile(url) else null
            }
        } ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun isKnown(app: GlassCastApp, url: String): Boolean =
        app.feedStore.feeds.value.any { it.imageUrl == url } ||
            app.feedStore.episodes.value.values.any { list -> list.any { it.imageUrl == url } }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

        fun uriFor(context: Context, url: String): Uri? =
            if (url.isBlank()) {
                null
            } else {
                Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(context.packageName + ".artwork")
                    .appendPath(Base64.encodeToString(url.toByteArray(), FLAGS))
                    .build()
            }
    }
}
