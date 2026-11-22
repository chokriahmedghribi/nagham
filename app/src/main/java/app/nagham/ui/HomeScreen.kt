@file:OptIn(ExperimentalMaterial3Api::class)

package app.nagham.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nagham.AppContainer
import app.nagham.data.Track
import app.nagham.reco.ListeningMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeVM(private val c: AppContainer) : ViewModel() {
    val forYou = MutableStateFlow<List<Track>>(emptyList())
    val trending = MutableStateFlow<List<Track>>(emptyList())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val (t, f) = coroutineScope {
                    val a = async { c.repo.trending() }
                    val b = async { c.recommender.forYou() }
                    a.await() to b.await()
                }
                trending.value = t
                forYou.value = f
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "تعذّر الاتصال بالإنترنت. تحقق من الشبكة وأعد المحاولة."
            }
            loading.value = false
        }
    }
}

@Composable
fun HomeScreen(onAudio: () -> Unit) {
    val c = LocalApp.current
    val vm = appViewModel { HomeVM(it) }
    val forYou by vm.forYou.collectAsState()
    val trending by vm.trending.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val mode by c.modes.mode.collectAsState()
    val modeError by c.modes.error.collectAsState()
    val ps by c.player.state.collectAsState()

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("نغمة", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onAudio) { Icon(Icons.Filled.Tune, "إعدادات الصوت") }
            }
        }
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(ListeningMode.entries) { _, m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { c.modes.start(m) },
                        label = { Text(if (m == ListeningMode.NORMAL) "عادي" else "وضع ${m.label}") },
                    )
                }
            }
            modeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (loading && trending.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        error?.let { msg ->
            item {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    Button(onClick = vm::refresh, modifier = Modifier.padding(top = 8.dp)) { Text("إعادة المحاولة") }
                }
            }
        }

        if (forYou.isNotEmpty()) {
            item {
                SectionTitle("مقترحة لك")
                Text(
                    "تُحسب على جهازك فقط من سجل استماعك",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(forYou) { i, t -> TrackCard(t) { c.player.playTracks(forYou, i) } }
                }
            }
        }

        if (trending.isNotEmpty()) {
            item { SectionTitle("الأكثر رواجاً هذا الأسبوع") }
            itemsIndexed(trending) { i, t ->
                TrackRow(t, onClick = { c.player.playTracks(trending, i) }, isCurrent = ps.current?.id == t.id)
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
    }
}
