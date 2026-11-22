package app.nagham.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.nagham.data.Track

fun Track.toMediaItem(streamUrl: String): MediaItem {
    val extras = Bundle().apply {
        putString("genre", genre)
        putString("mood", mood)
        putInt("dur", durationSec)
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(streamUrl)).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setExtras(extras)
                .build()
        )
        .build()
}

fun MediaItem.toTrack(): Track {
    val m = mediaMetadata
    val x = m.extras
    return Track(
        id = mediaId,
        title = m.title?.toString().orEmpty(),
        artist = m.artist?.toString().orEmpty(),
        artworkUrl = m.artworkUri?.toString(),
        durationSec = x?.getInt("dur") ?: 0,
        genre = x?.getString("genre"),
        mood = x?.getString("mood"),
    )
}
