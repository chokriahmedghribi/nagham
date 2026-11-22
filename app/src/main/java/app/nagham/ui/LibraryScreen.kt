@file:OptIn(ExperimentalMaterial3Api::class)

package app.nagham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onOpenPlaylist: (Long) -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val playlists by c.repo.playlists().collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("مكتبتي", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "قائمة جديدة") }
            }
        }
        item {
            LibraryEntry(
                title = "الأغاني المفضلة",
                icon = { Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenPlaylist(0L) },
            )
        }
        items(playlists, key = { it.id }) { p ->
            LibraryEntry(
                title = p.name,
                icon = { Icon(Icons.Filled.LibraryMusic, null, tint = MaterialTheme.colorScheme.secondary) },
                onClick = { onOpenPlaylist(p.id) },
            )
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "أنشئ قوائم تشغيل بزر + ثم أضف إليها الأغاني من قائمة ⋮ بجانب كل أغنية.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("قائمة تشغيل جديدة") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("اسم القائمة") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val n = name
                        scope.launch { c.repo.createPlaylist(n) }
                        name = ""; creating = false
                    },
                ) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun LibraryEntry(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PlaylistScreen(id: Long, onBack: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val isFavorites = id == 0L
    val tracks by (if (isFavorites) c.repo.favoriteTracks() else c.repo.playlistTracks(id))
        .collectAsState(initial = emptyList())
    val playlists by c.repo.playlists().collectAsState(initial = emptyList())
    val title = if (isFavorites) "الأغاني المفضلة" else playlists.firstOrNull { it.id == id }?.name ?: ""
    val ps by c.player.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!isFavorites) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "حذف القائمة") }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = tracks.isNotEmpty(), onClick = { c.player.playTracks(tracks, 0) }) {
                Icon(Icons.Filled.PlayArrow, null); Text("تشغيل")
            }
            OutlinedButton(enabled = tracks.isNotEmpty(), onClick = { c.player.playTracks(tracks.shuffled(), 0) }) {
                Icon(Icons.Filled.Shuffle, null); Text(" عشوائي")
            }
        }
        if (tracks.isEmpty()) {
            Text(
                "لا توجد أغانٍ هنا بعد.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(
                    t,
                    onClick = { c.player.playTracks(tracks, i) },
                    isCurrent = ps.current?.id == t.id,
                    onRemove = if (isFavorites) null else {
                        { scope.launch { c.repo.removeFromPlaylist(id, t.id) }; Unit }
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف القائمة؟") },
            text = { Text("ستُحذف القائمة «$title» دون حذف الأغاني نفسها.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { c.repo.deletePlaylist(id) }
                    onBack()
                }) { Text("حذف", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}
