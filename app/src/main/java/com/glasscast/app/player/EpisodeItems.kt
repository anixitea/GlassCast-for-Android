package com.glasscast.app.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.glasscast.app.data.Downloads
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed

/**
 * An episode as something the player can play. Shared by the app's player
 * connection and by the service, which builds the same items when Android
 * Auto or the Assistant asks for an episode by id or by name.
 */
internal object EpisodeItems {

    fun playable(episode: Episode, feed: Feed?, downloads: Downloads?): MediaItem {
        val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(feed?.title ?: feed?.author.orEmpty())
            .setAlbumTitle(feed?.title.orEmpty())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .apply { if (art.isNotBlank()) setArtworkUri(Uri.parse(art)) }
            // The online address travels with every item, so a Cast hand-off
            // can swap a downloaded file back to something the TV can reach.
            .setExtras(android.os.Bundle().apply { putString(PlayerConnection.REMOTE_URL, episode.audioUrl) })
            .build()

        // Downloaded: play the file. Otherwise, stream.
        val local = downloads?.fileFor(episode.guid)
        return MediaItem.Builder()
            .setMediaId(episode.guid)
            .setUri(local?.let { Uri.fromFile(it) } ?: Uri.parse(episode.audioUrl))
            // Required for Cast: the receiver is told what it's playing rather
            // than sniffing it, and the Cast converter refuses items without a
            // type. ExoPlayer only treats it as a hint, and still sniffs.
            .setMimeType(mimeTypeFor(episode.audioUrl))
            .setMediaMetadata(metadata)
            .build()
    }

    fun mimeTypeFor(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m4a") || path.endsWith(".mp4") || path.endsWith(".m4b") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") || path.endsWith(".oga") -> "audio/ogg"
            path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            else -> "audio/mpeg"
        }
    }
}
