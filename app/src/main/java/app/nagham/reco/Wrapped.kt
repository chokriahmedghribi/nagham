package app.nagham.reco

import app.nagham.data.MusicDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RankItem(val name: String, val value: Int, val artworkUrl: String? = null)

data class WrappedStats(
    val year: Int,
    val totalMinutes: Int,
    val plays: Int,
    val topTracks: List<RankItem>,
    val topArtists: List<RankItem>,
    val topGenres: List<RankItem>,
    val peakHour: Int?,
    val activeDays: Int,
    val longestStreak: Int,
)

suspend fun computeWrapped(dao: MusicDao, year: Int = LocalDate.now().year): WrappedStats {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    val events = dao.eventsSince(start).filter {
        it.listenedSec >= 20 && Instant.ofEpochMilli(it.playedAt).atZone(zone).year == year
    }

    val topTracks = events.groupBy { it.trackId }.values
        .map { l -> RankItem("${l.first().title} — ${l.first().artist}", l.size, l.first().artworkUrl) }
        .sortedByDescending { it.value }.take(5)

    val topArtists = events.filter { it.artist.isNotBlank() }.groupBy { it.artist }
        .map { (artist, l) -> RankItem(artist, (l.sumOf { it.listenedSec } / 60).coerceAtLeast(1), l.first().artworkUrl) }
        .sortedByDescending { it.value }.take(5)

    val withGenre = events.filter { it.genre != null }
    val topGenres = withGenre.groupBy { it.genre!! }
        .map { (g, l) -> RankItem(g, l.size * 100 / withGenre.size) }
        .sortedByDescending { it.value }.take(4)

    val peakHour = events
        .groupingBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).hour }
        .eachCount().maxByOrNull { it.value }?.key

    val days = events.map { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }.toSortedSet().toList()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in days) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        if (run > best) best = run
        prev = d
    }

    return WrappedStats(
        year = year,
        totalMinutes = events.sumOf { it.listenedSec } / 60,
        plays = events.size,
        topTracks = topTracks,
        topArtists = topArtists,
        topGenres = topGenres,
        peakHour = peakHour,
        activeDays = days.size,
        longestStreak = best,
    )
}
