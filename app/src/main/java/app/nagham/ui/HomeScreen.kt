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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
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
    val latest = MutableStateFlow<List<Track>>(emptyList())
    val arabic = MutableStateFlow<List<Track>>(emptyList())
    val spNew = MutableStateFlow<List<Track>>(emptyList())
    val spArabic = MutableStateFlow<List<Track>>(emptyList())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            val hasJ = c.repo.hasJamendo
            coroutineScope {
                val t = async { runCatching { c.repo.trending() } }
                val f = async { runCatching { c.recommender.forYou() } }
                val l = if (hasJ) async { runCatching { c.repo.jamendoLatest() } } else null
                val a = if (hasJ) async { runCatching { c.repo.jamendoArabic() } } else null
                val sn = if (c.spotify.connected.value) async { c.spotify.discover(listOf("top hits 2026", "new music friday", "pop 2026")) } else null
                val sa = if (c.spotify.connected.value) async { c.spotify.discover(listOf("arabic hits", "عمرو دياب", "أغاني عربية جديدة")) } else null
                spNew.value = sn?.await().orEmpty()
                spArabic.value = sa?.await().orEmpty()
                val rt = t.await(); val rf = f.await(); val rl = l?.await(); val ra = a?.await()
                trending.value = rt.getOrDefault(emptyList())
                forYou.value = rf.getOrDefault(emptyList())
                latest.value = rl?.getOrNull().orEmpty()
                arabic.value = ra?.getOrNull().orEmpty()
                val results = listOfNotNull(rt, rl, ra)
                if (results.none { it.isSuccess }) {
                    error.value = "تعذّر الاتصال بالإنترنت. تحقق من الشبكة وأعد المحاولة."
                } else {
                    // مفتاح Jamendo خاطئ مثلاً: نعرض سببه بدل الصمت
                    rl?.exceptionOrNull()?.message?.let { if (it.startsWith("Jamendo")) error.value = it }
                }
            }
            loading.value = false
        }
    }
}

@Composable
fun HomeScreen(onAudio: () -> Unit, onSources: () -> Unit) {
    val c = LocalApp.current
    val vm = appViewModel { HomeVM(it) }
    val forYou by vm.forYou.collectAsState()
    val trending by vm.trending.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val mode by c.modes.mode.collectAsState()
    val modeError by c.modes.error.collectAsState()
    val ps by c.player.state.collectAsState()
    val latest by vm.latest.collectAsState()
    val arabic by vm.arabic.collectAsState()
    val version by c.repo.sourcesVersion.collectAsState()
    val spConnected by c.spotify.connected.collectAsState()
    val spNew by vm.spNew.collectAsState()
    val spArabic by vm.spArabic.collectAsState()
    LaunchedEffect(version, spConnected) { vm.refresh() }
    val hasJamendo = c.repo.hasJamendo

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("نغمة", style = MaterialTheme.typography.headlineMedium)
                Row {
                    IconButton(onClick = onSources) { Icon(Icons.Filled.Settings, "المصادر") }
                    IconButton(onClick = onAudio) { Icon(Icons.Filled.Tune, "إعدادات الصوت") }
                }
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

        if (!hasJamendo) {
            item {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("أحدث الأغاني والأغاني العربية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "أضف مفتاح Jamendo المجاني لتظهر أغانٍ كاملة حديثة وعربية، وتُدمج نتائجه في البحث.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Button(onClick = onSources) { Text("إضافة المفتاح") }
                }
            }
        }
        if (latest.isNotEmpty()) {
            item {
                SectionTitle("أحدث الأغاني")
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { itemsIndexed(latest) { i, t -> TrackCard(t) { c.player.playTracks(latest, i) } } }
            }
        }
        if (arabic.isNotEmpty()) {
            item {
                SectionTitle("أغانٍ عربية")
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { itemsIndexed(arabic) { i, t -> TrackCard(t) { c.player.playTracks(arabic, i) } } }
            }
        }
        if (spNew.isNotEmpty()) {
            item {
                SectionTitle("جديد من Spotify")
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { itemsIndexed(spNew) { i, t -> TrackCard(t) { c.player.playTracks(spNew, i) } } }
            }
        }
        if (spArabic.isNotEmpty()) {
            item {
                SectionTitle("عربي من Spotify")
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { itemsIndexed(spArabic) { i, t -> TrackCard(t) { c.player.playTracks(spArabic, i) } } }
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
