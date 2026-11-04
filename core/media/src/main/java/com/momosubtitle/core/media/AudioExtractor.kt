package com.momosubtitle.core.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Milestone 0.1 media preparation: decode any platform-supported container/codec
 * (MP4, MKV, MOV, WebM audio tracks; MP3, WAV, M4A, AAC, Opus, FLAC) into a
 * 16 kHz mono 16-bit PCM WAV, preserving the original duration mapping.
 *
 * FFmpeg replaces this in Phase 1 for exotic codecs and multi-track selection
 * (docs/PROCESSING_PIPELINE.md) — the pipeline API stays identical.
 */
class AudioExtractor(private val context: Context) {

    data class ExtractResult(val wavFile: File, val durationMs: Long)

    fun probeDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
        } catch (t: Throwable) {
            -1L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Streams decoded PCM straight to disk; RAM use stays bounded regardless of length. */
    fun extractToWavMono16k(
        uri: Uri,
        outFile: File,
        onProgress: (Float) -> Unit
    ): ExtractResult {
        val totalDurationMs = probeDurationMs(uri)
        val extractor = MediaExtractor()
        outFile.parentFile?.mkdirs()
        if (outFile.exists()) outFile.delete()

        var writtenSamples = 0L
        val ex = extractor
        try {
            ex.setDataSource(context, uri, null)

            var audioTrack = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    trackFormat = f
                    break
                }
            }
            val format = trackFormat
            require(audioTrack >= 0 && format != null) { "No audio track found in $uri" }
            ex.selectTrack(audioTrack)

            val srcMime = format!!.getString(MediaFormat.KEY_MIME)!!
            var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val out = BufferedOutputStream(FileOutputStream(outFile), 1 shl 20)
            WavFile.writeHeader(out, WavFile.SAMPLE_RATE, 1, 0)

            var lastProgress = -1

            fun emit(channelSamples: ShortArray, channelCount: Int, resampler: Resampler) {
                // 1. Downmix to mono (average channels).
                val mono: ShortArray = if (channelCount == 1) {
                    channelSamples
                } else {
                    val frames = channelSamples.size / channelCount
                    ShortArray(frames).also { dst ->
                        for (frame in 0 until frames) {
                            var acc = 0
                            for (c in 0 until channelCount) acc += channelSamples[frame * channelCount + c]
                            dst[frame] = (acc.toDouble() / channelCount).roundToLong()
                                .coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                        }
                    }
                }
                // 2. Linear-resample to 16 kHz and write little-endian s16le.
                writtenSamples += resampler.process(mono, out)
            }

            if (srcMime == "audio/raw") {
                // Uncompressed source (PCM WAV): no decoder required.
                val resampler = Resampler(srcRate)
                val byteBuffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.nativeOrder())
                while (true) {
                    val size = ex.readSampleData(byteBuffer, 0)
                    if (size < 0) break
                    val shorts = ShortArray(size / 2)
                    byteBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts, 0, shorts.size)
                    emit(shorts, srcChannels, resampler)
                    ex.advance()
                    if (totalDurationMs > 0) {
                        val pct = (ex.sampleTime * 100 / totalDurationMs).toInt()
                        if (pct != lastProgress) {
                            lastProgress = pct
                            onProgress((pct / 100.0f))
                        }
                    }
                }
            } else {
                val codec = MediaCodec.createDecoderByType(srcMime)
                val resampler = Resampler(srcRate)
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false

                    while (!outputDone) {
                        if (!inputDone) {
                            val inIdx = codec.dequeueInputBuffer(10_000)
                            if (inIdx >= 0) {
                                val ib = codec.getInputBuffer(inIdx)!!
                                val size = ex.readSampleData(ib, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                                    ex.advance()
                                }
                            }
                        }
                        val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                        when {
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val of = codec.outputFormat
                                srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                srcChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                resampler.reset(srcRate)
                            }
                            outIdx >= 0 -> {
                                val ob = codec.getOutputBuffer(outIdx)!!
                                if (info.size > 0) {
                                    val shorts = ShortArray(info.size / 2)
                                    ob.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts, 0, shorts.size)
                                    emit(shorts, srcChannels, resampler)
                                }
                                codec.releaseOutputBuffer(outIdx, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                                if (totalDurationMs > 0 && info.presentationTimeUs > 0) {
                                    val pct = (info.presentationTimeUs / 1000 * 100 / totalDurationMs).toInt()
                                    if (pct != lastProgress && pct in 0..100) {
                                        lastProgress = pct
                                        onProgress(pct / 100.0f)
                                    }
                                }
                            }
                            else -> Unit // TRY_AGAIN_LATER
                        }
                    }
                } finally {
                    runCatching { codec.stop() }
                    runCatching { codec.release() }
                }
            }
            out.flush()
            out.close()
        } finally {
            runCatching { ex.release() }
        }

        WavFile.patchDataSize(outFile, writtenSamples * WavFile.BYTES_PER_SAMPLE)
        val durationMs = writtenSamples * 1000 / WavFile.SAMPLE_RATE
        return ExtractResult(outFile, durationMs)
    }

    /** Streaming linear resampler with sub-sample carry across buffer boundaries. */
    private class Resampler(private var srcRate: Int) {
        private var position = 0.0

        fun reset(rate: Int) {
            srcRate = rate
            position = 0.0
        }

        fun process(input: ShortArray, out: java.io.OutputStream): Long {
            if (input.isEmpty()) return 0
            var written = 0L
            if (srcRate == WavFile.SAMPLE_RATE) {
                for (s in input) writeSample(out, s)
                return input.size.toLong()
            }
            val ratio = srcRate.toDouble() / WavFile.SAMPLE_RATE
            while (position + 1 < input.size) {
                val i0 = position.toInt()
                val frac = position - i0
                val value = (input[i0] * (1.0 - frac) + input[i0 + 1] * frac).roundToLong()
                    .coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                writeSample(out, value)
                position += ratio
                written++
            }
            position -= input.size
            if (position < 0) position = 0.0
            return written
        }

        private fun writeSample(out: java.io.OutputStream, s: Short) {
            val v = s.toInt()
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }
    }
}
