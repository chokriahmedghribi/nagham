package app.nagham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.nagham.AppContainer
import app.nagham.data.Track
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer غير متوفر") }
val LocalFavorites = staticCompositionLocalOf<Set<String>> { emptySet() }
val LocalDownloaded = staticCompositionLocalOf<Set<String>> { emptySet() }

@Composable
inline fun <reified T : ViewModel> appViewModel(crossinline create: (AppContainer) -> T): T {
    val c = LocalApp.current
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote, null,
                Modifier.align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val fav = track.id in LocalFavorites.current
    val downloaded = track.id in LocalDownloaded.current
    val progress by c.downloads.progress.collectAsState()
    val pct = progress[track.id]
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artworkUrl, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title.ifBlank { "بدون عنوان" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist + when {
                    pct != null -> "  ·  جارٍ التنزيل $pct٪"
                    downloaded -> "  ·  ✓ محمّلة"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { scope.launch { c.repo.setFavorite(track, !fav) } }) {
            Icon(
                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "مفضلة",
                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "خيارات") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("تشغيل تالياً") }, onClick = { menu = false; c.player.playNext(track) })
                DropdownMenuItem(text = { Text("إضافة إلى قائمة الانتظار") }, onClick = { menu = false; c.player.enqueue(track) })
                DropdownMenuItem(text = { Text("إضافة إلى قائمة تشغيل") }, onClick = { menu = false; picker = true })
                if (downloaded) {
                    DropdownMenuItem(text = { Text("حذف التنزيل") }, onClick = { menu = false; c.downloads.delete(track) })
                } else if (track.downloadable && pct == null) {
                    DropdownMenuItem(text = { Text("تحميل للاستماع دون إنترنت") }, onClick = { menu = false; c.downloads.download(track) })
                }
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("إزالة من هذه القائمة") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
    if (picker) PlaylistPickerDialog(track) { picker = false }
}

@Composable
fun PlaylistPickerDialog(track: Track, onDismiss: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val playlists by c.repo.playlists().collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة إلى قائمة") },
        text = {
            if (playlists.isEmpty()) {
                Text("لا توجد قوائم بعد. أنشئ قائمة من تبويب «مكتبتي».")
            } else {
                Column {
                    playlists.forEach { p ->
                        Text(
                            p.name,
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { c.repo.addToPlaylist(p.id, track) }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Artwork(track.artworkUrl, Modifier.size(140.dp), corner = 12.dp)
        Spacer(Modifier.size(6.dp))
        Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val c = LocalApp.current
    val ps by c.player.state.collectAsState()
    val t = ps.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(t.artworkUrl, Modifier.size(44.dp), corner = 6.dp)
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { c.player.togglePlay() }) {
                Icon(if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل/إيقاف")
            }
            IconButton(onClick = { c.player.next() }) { Icon(Icons.Filled.SkipNext, "التالي") }
        }
        LinearProgressIndicator(
            progress = { if (ps.durationMs > 0) (ps.positionMs.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun SpotifyBar() {
    val c = LocalApp.current
    val now by c.spotify.nowPlaying.collectAsState()
    val n = now ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(n.artworkUrl, Modifier.size(40.dp), corner = 6.dp)
        Column(Modifier.weight(1f)) {
            Text(n.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Spotify · " + n.artist, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { c.spotify.previous() }) { Icon(Icons.Filled.SkipPrevious, "السابق") }
        IconButton(onClick = { c.spotify.togglePlay() }) {
            Icon(if (n.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل/إيقاف")
        }
        IconButton(onClick = { c.spotify.next() }) { Icon(Icons.Filled.SkipNext, "التالي") }
    }
}
