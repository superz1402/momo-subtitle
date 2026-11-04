package com.momosubtitle.feature.export

import android.content.Context
import android.net.Uri
import com.momosubtitle.core.subtitles.Cue
import com.momosubtitle.core.subtitles.SrtFormatter
import com.momosubtitle.core.subtitles.TxtFormatter
import com.momosubtitle.core.subtitles.VttFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SubtitleFormat(val mime: String, val extension: String) {
    SRT("application/x-subrip", "srt"),
    VTT("text/vtt", "vtt"),
    TXT("text/plain", "txt")
}

/**
 * Writes subtitles to a user-chosen SAF destination. Subtitles always export
 * independently of the source video (blueprint requirement).
 */
class SubtitleFileExporter(private val context: Context) {

    suspend fun write(uri: Uri, format: SubtitleFormat, cues: List<Cue>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = when (format) {
                    SubtitleFormat.SRT -> SrtFormatter.format(cues)
                    SubtitleFormat.VTT -> VttFormatter.format(cues)
                    SubtitleFormat.TXT -> TxtFormatter.format(cues)
                }
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Could not open destination for writing")
            }
        }
}
