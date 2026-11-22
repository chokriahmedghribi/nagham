package app.nagham.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * تنزيل الأغاني المسموح بتحميلها فقط (يحدد صاحب العمل ذلك) إلى مساحة التطبيق الخاصة.
 * ملاحظة: التنزيل يعمل أثناء تشغيل التطبيق ولا يستمر إن أُغلق بالكامل.
 */
class DownloadManager(
    private val ctx: Context,
    private val repo: MusicRepository,
    private val dao: MusicDao,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    fun download(track: Track) {
        if (!track.downloadable || jobs.containsKey(track.id)) return
        _progress.update { it + (track.id to 0) }
        jobs[track.id] = scope.launch(Dispatchers.IO) {
            val dir = File(ctx.filesDir, "music").apply { mkdirs() }
            val tmp = File(dir, "${track.id}.part")
            val out = File(dir, "${track.id}.mp3")
            try {
                client.newCall(Request.Builder().url(repo.remoteUrl(track)).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                    val body = r.body ?: throw IllegalStateException("ملف فارغ")
                    val total = body.contentLength()
                    var read = 0L
                    body.byteStream().use { input ->
                        tmp.outputStream().use { os ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                os.write(buf, 0, n)
                                read += n
                                if (total > 0) _progress.update { it + (track.id to (read * 100 / total).toInt()) }
                            }
                        }
                    }
                }
                tmp.renameTo(out)
                repo.saveTrack(track)
                repo.addDownload(Download(track.id, out.absolutePath, out.length()))
            } catch (e: CancellationException) {
                tmp.delete()
                throw e
            } catch (e: Exception) {
                tmp.delete()
                _error.value = "تعذّر تنزيل «${track.title}»"
            } finally {
                jobs.remove(track.id)
                _progress.update { it - track.id }
            }
        }
    }

    fun delete(track: Track) {
        scope.launch(Dispatchers.IO) {
            File(ctx.filesDir, "music/${track.id}.mp3").delete()
            repo.removeDownload(track.id)
        }
    }
}
