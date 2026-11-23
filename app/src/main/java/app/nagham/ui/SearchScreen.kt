@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalCoroutinesApi::class)

package app.nagham.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchVM(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val genre = MutableStateFlow<String?>(null)
    private val results = MutableStateFlow<List<Track>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    val genres: StateFlow<List<String>> = results
        .map { r -> r.mapNotNull { it.genre }.distinct() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shown: StateFlow<List<Track>> = combine(results, genre) { r, g ->
        if (g == null) r else r.filter { it.genre == g }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            query.debounce(400).distinctUntilChanged().collectLatest { q ->
                genre.value = null
                error.value = null
                if (q.isBlank()) {
                    results.value = emptyList()
                    return@collectLatest
                }
                loading.value = true
                try {
                    val sp = if (c.spotify.connected.value) {
                        kotlinx.coroutines.coroutineScope {
                            val a = kotlinx.coroutines.async { runCatching { c.repo.search(q.trim()) } }
                            val b = kotlinx.coroutines.async { runCatching { c.spotify.search(q.trim()) } }
                            val ra = a.await(); val rb = b.await()
                            val lists = listOfNotNull(ra.getOrNull(), rb.getOrNull())
                            if (lists.isEmpty()) throw (ra.exceptionOrNull() ?: java.io.IOException())
                            val out = ArrayList<Track>()
                            for (i in 0 until lists.maxOf { it.size }) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
                            out.distinctBy { it.id }
                        }
                    } else c.repo.search(q.trim())
                    results.value = sp
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    results.value = emptyList()
                    error.value = "تعذّر البحث. تحقق من الاتصال."
                }
                loading.value = false
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val c = LocalApp.current
    val vm = appViewModel { SearchVM(it) }
    val query by vm.query.collectAsState()
    val genre by vm.genre.collectAsState()
    val genres by vm.genres.collectAsState()
    val shown by vm.shown.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val ps by c.player.state.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("ابحث عن أغنية أو فنان") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "مسح") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        if (genres.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { g ->
                    FilterChip(
                        selected = genre == g,
                        onClick = { vm.genre.value = if (genre == g) null else g },
                        label = { Text(g) },
                    )
                }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (!loading && query.isNotBlank() && shown.isEmpty() && error == null) {
            Text("لا نتائج", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            itemsIndexed(shown) { i, t ->
                TrackRow(t, onClick = { c.player.playTracks(shown, i) }, isCurrent = ps.current?.id == t.id)
            }
        }
    }
}
