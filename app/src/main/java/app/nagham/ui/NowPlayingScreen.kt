@file:OptIn(ExperimentalMaterial3Api::class)

package app.nagham.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.nagham.data.Lyrics
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun NowPlayingScreen(onBack: () -> Unit, onAudio: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val ps by c.player.state.collectAsState()
    val sleep by c.sleep.state.collectAsState()
    val t = ps.current
    if (t == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val favs = LocalFavorites.current
    val isFav = t.id in favs
    var lyrics by remember(t.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoaded by remember(t.id) { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(t.id) {
        lyricsLoaded = false
        lyrics = c.repo.lyrics(t)
        lyricsLoaded = true
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "إغلاق") }
            Text(
                "قيد التشغيل", style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAudio) { Icon(Icons.Filled.Tune, "إعدادات الصوت") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (showLyrics) {
                LyricsPanel(lyrics, lyricsLoaded, ps.positionMs) { c.player.seekTo(it) }
            } else {
                Artwork(t.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t.artist, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { scope.launch { c.repo.setFavorite(t, !isFav) } }) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "مفضلة",
                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // أدوات النقل تُعرض دائماً يسار-يمين مثل أي مشغّل
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                val maxMs = max(ps.durationMs, 1L).toFloat()
                Slider(
                    value = if (dragging) dragValue else ps.positionMs.toFloat().coerceIn(0f, maxMs),
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = { c.player.seekTo(dragValue.toLong()); dragging = false },
                    valueRange = 0f..maxMs,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val shown = if (dragging) dragValue.toLong() else ps.positionMs
                    Text(formatTime(shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTime(ps.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { c.player.toggleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle, "عشوائي",
                            tint = if (ps.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "السابق", Modifier.size(36.dp))
                    }
                    FilledIconButton(onClick = { c.player.togglePlay() }, modifier = Modifier.size(68.dp)) {
                        if (ps.buffering && !ps.isPlaying) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(
                                if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "تشغيل/إيقاف", Modifier.size(38.dp),
                            )
                        }
                    }
                    IconButton(onClick = { c.player.next() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipNext, "التالي", Modifier.size(36.dp))
                    }
                    IconButton(onClick = { c.player.cycleRepeat() }) {
                        Icon(
                            if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            "تكرار",
                            tint = if (ps.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            FilterChip(selected = showLyrics, onClick = { showLyrics = !showLyrics }, label = { Text("الكلمات") })
            FilterChip(selected = false, onClick = { showQueue = true }, label = { Text("قائمة الانتظار") })
            FilterChip(
                selected = sleep.active,
                onClick = { showSleep = true },
                label = { Text(if (sleep.active) sleepLabel(sleep.remainingMs, sleep.endOfTrack) else "مؤقت النوم") },
                leadingIcon = { Icon(Icons.Filled.Bedtime, null, Modifier.size(18.dp)) },
            )
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(ps.queue) { i, q ->
                    TrackRow(q, onClick = { c.player.skipTo(i) }, isCurrent = i == ps.index)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("مؤقت النوم") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SleepOption("15 دقيقة") { c.sleep.start(15); showSleep = false }
                    SleepOption("30 دقيقة") { c.sleep.start(30); showSleep = false }
                    SleepOption("60 دقيقة") { c.sleep.start(60); showSleep = false }
                    SleepOption("ذكي: 30 دقيقة (يخفض الصوت تدريجياً)") { c.sleep.start(30, smart = true); showSleep = false }
                    SleepOption("عند نهاية الأغنية الحالية") { c.sleep.startEndOfTrack(); showSleep = false }
                    if (sleep.active) SleepOption("إلغاء المؤقت") { c.sleep.cancel(); showSleep = false }
                }
            },
            confirmButton = { TextButton(onClick = { showSleep = false }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun SleepOption(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun sleepLabel(remainingMs: Long, endOfTrack: Boolean): String {
    if (endOfTrack) return "نهاية الأغنية"
    val m = (remainingMs / 60_000).toInt()
    return if (m >= 1) "$m د" else "أقل من دقيقة"
}

@Composable
private fun LyricsPanel(lyrics: Lyrics?, loaded: Boolean, positionMs: Long, onSeek: (Long) -> Unit) {
    when {
        !loaded -> CircularProgressIndicator()
        lyrics == null -> Text(
            "لا توجد كلمات لهذه الأغنية",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        lyrics.synced.isNotEmpty() -> {
            val lines = lyrics.synced
            val idx = lines.indexOfLast { it.timeMs <= positionMs }
            val listState = rememberLazyListState()
            LaunchedEffect(idx) { if (idx >= 0) listState.animateScrollToItem(max(0, idx - 2)) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(line.timeMs) }
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == idx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(lyrics.plain.orEmpty(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}
