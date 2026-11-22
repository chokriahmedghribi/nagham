@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.nagham.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagham.reco.RankItem
import app.nagham.reco.WrappedStats
import app.nagham.reco.computeWrapped

private val pageColors = listOf(
    listOf(Color(0xFF3B2A8F), Color(0xFF0E1014)),
    listOf(Color(0xFF0E6B62), Color(0xFF0E1014)),
    listOf(Color(0xFF8F2A5E), Color(0xFF0E1014)),
    listOf(Color(0xFF9A5B12), Color(0xFF0E1014)),
)

@Composable
fun WrappedScreen() {
    val c = LocalApp.current
    var stats by remember { mutableStateOf<WrappedStats?>(null) }
    LaunchedEffect(Unit) { stats = computeWrapped(c.dao) }

    val s = stats
    if (s == null) return
    if (s.plays == 0) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "ملخصك السنوي ${s.year} سيظهر هنا بعد أن تستمع لبعض الأغاني.\nكل شيء يُحسب على هاتفك فقط.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val pager = rememberPagerState(pageCount = { 4 })
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(pageColors[page])).padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ملخصك لعام ${s.year}", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(24.dp))
                        Text("${s.totalMinutes}", fontSize = 84.sp, fontWeight = FontWeight.Black)
                        Text("دقيقة من الموسيقى", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Text("${s.plays} استماعاً في ${s.activeDays} يوماً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> RankPage("فنانوك الأكثر استماعاً", s.topArtists) { "${it.value} د" }
                    2 -> RankPage("أغانيك الأكثر تشغيلاً", s.topTracks) { "×${it.value}" }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("عاداتك", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        s.peakHour?.let { Fact("ساعة الذروة", hourLabel(it)) }
                        Fact("أطول سلسلة أيام متتالية", "${s.longestStreak} يوم")
                        if (s.topGenres.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("أنواعك المفضلة", style = MaterialTheme.typography.titleMedium)
                            s.topGenres.forEach { g ->
                                Text("${g.name} — ${g.value}٪", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(4) { i ->
                Box(
                    Modifier.size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RankPage(title: String, items: List<RankItem>, trailing: (RankItem) -> String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        items.forEachIndexed { i, it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 14.dp))
                Artwork(it.artworkUrl, Modifier.size(44.dp), corner = 6.dp)
                Text(
                    it.name,
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
                Text(trailing(it), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun hourLabel(h: Int): String {
    val period = if (h < 12) "صباحاً" else "مساءً"
    val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
    return "$h12 $period"
}
