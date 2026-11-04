package com.momosubtitle.core.media

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Minimal 16-bit PCM WAV IO used by the Milestone 0.1 pipeline.
 *
 * The extractor produces 16 kHz mono s16le WAV; the slicer reads only the byte
 * range it needs, so a 2-hour source never enters memory as a whole.
 */
object WavFile {

    const val SAMPLE_RATE = 16000
    const val BYTES_PER_SAMPLE = 2

    class WavInfo(
        val dataOffset: Long,
        val dataBytes: Long,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int
    )

    /** Writes a 44-byte RIFF/WAVE header for 16-bit PCM. */
    fun writeHeader(out: OutputStream, sampleRate: Int, channels: Int, dataBytes: Long) {
        val bits = BYTES_PER_SAMPLE * 8
        val blockAlign = channels * BYTES_PER_SAMPLE
        val byteRate = sampleRate * blockAlign
        fun le32(v: Long) {
            out.write((v and 0xFF).toInt())
            out.write(((v shr 8) and 0xFF).toInt())
            out.write(((v shr 16) and 0xFF).toInt())
            out.write(((v shr 24) and 0xFF).toInt())
        }

        fun le16(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        le32(36 + dataBytes)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        le32(16)
        le16(1) // PCM
        le16(channels)
        le32(sampleRate.toLong())
        le32(byteRate.toLong())
        le16(blockAlign)
        le16(bits)
        out.write("data".toByteArray(Charsets.US_ASCII))
        le32(dataBytes)
    }

    /** Patches the RIFF + data sizes once the real payload length is known. */
    fun patchDataSize(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            writeLe32(raf, 36 + dataBytes)
            raf.seek(40)
            writeLe32(raf, dataBytes)
        }
    }

    private fun writeLe32(raf: RandomAccessFile, v: Long) {
        raf.write((v and 0xFF).toInt())
        raf.write(((v shr 8) and 0xFF).toInt())
        raf.write(((v shr 16) and 0xFF).toInt())
        raf.write(((v shr 24) and 0xFF).toInt())
    }

    /** Walks chunk headers instead of assuming the 44-byte layout. */
    fun parse(file: File): WavInfo {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(4)
            raf.readFully(riff)
            require(String(riff, Charsets.US_ASCII) == "RIFF") { "Not a RIFF file: ${file.name}" }
            raf.seek(8)
            val wave = ByteArray(4)
            raf.readFully(wave)
            require(String(wave, Charsets.US_ASCII) == "WAVE") { "Not a WAVE file: ${file.name}" }

            var dataOffset = -1L
            var dataBytes = -1L
            var sampleRate = -1
            var channels = -1
            var bits = -1

            while (dataOffset < 0) {
                val id = ByteArray(4)
                if (raf.read(id) < 4) break
                val b = ByteArray(4)
                raf.readFully(b)
                val size =
                    (b[0].toLong() and 0xFF) or
                    ((b[1].toLong() and 0xFF) shl 8) or
                    ((b[2].toLong() and 0xFF) shl 16) or
                    ((b[3].toLong() and 0xFF) shl 24)

                when (String(id, Charsets.US_ASCII)) {
                    "fmt " -> {
                        val fmt = ByteArray(16)
                        raf.readFully(fmt)
                        val audioFormat = (fmt[0].toInt() and 0xFF) or ((fmt[1].toInt() and 0xFF) shl 8)
                        channels = (fmt[2].toInt() and 0xFF) or ((fmt[3].toInt() and 0xFF) shl 8)
                        sampleRate = (fmt[4].toInt() and 0xFF) or
                            ((fmt[5].toInt() and 0xFF) shl 8) or
                            ((fmt[6].toInt() and 0xFF) shl 16) or
                            ((fmt[7].toInt() and 0xFF) shl 24)
                        bits = (fmt[14].toInt() and 0xFF) or ((fmt[15].toInt() and 0xFF) shl 8)
                        require(audioFormat == 1) { "Only PCM WAV supported (format $audioFormat)" }
                        require(bits == 16) { "Only 16-bit PCM supported (got $bits-bit)" }
                        if (size > 16) raf.seek(raf.filePointer + (size - 16))
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataBytes = size
                        raf.seek(dataOffset + size)
                    }
                    else -> raf.seek(raf.filePointer + size + (size and 1))
                }
            }
            require(dataOffset >= 0) { "No data chunk found in ${file.name}" }
            return WavInfo(dataOffset, dataBytes, sampleRate, channels, bits)
        }
    }

    /**
     * Copies [startMs, endMs) of a 16 kHz mono s16le WAV into [target].
     * Returns the number of milliseconds actually written.
     */
    fun sliceMono16k(source: File, target: File, startMs: Long, endMs: Long): Long {
        val info = parse(source)
        require(info.channels == 1) { "Expected mono audio, got ${info.channels} channels" }
        require(info.sampleRate == SAMPLE_RATE) { "Expected ${SAMPLE_RATE} Hz, got ${info.sampleRate} Hz" }

        val frameBytes = BYTES_PER_SAMPLE
        val startByte = (startMs.coerceAtLeast(0) * SAMPLE_RATE / 1000) * frameBytes
        val endByte = ((endMs.coerceAtLeast(startMs) * SAMPLE_RATE / 1000) * frameBytes)
            .coerceAtMost(info.dataBytes)
        val length = (endByte - startByte).coerceAtLeast(0)

        RandomAccessFile(source, "r").use { src ->
            src.seek(info.dataOffset + startByte)
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            target.outputStream().use { out ->
                writeHeader(out, SAMPLE_RATE, 1, length)
                while (remaining > 0) {
                    val n = src.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    remaining -= n
                }
            }
        }
        return (length / frameBytes) * 1000 / SAMPLE_RATE
    }
}
