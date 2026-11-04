package com.momosubtitle.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueBuilderTest {

    private val rules = SubtitleRules()

    @Test
    fun `short segment becomes single line`() {
        val cues = CueBuilder.build(listOf(SubtitleSegment(0, 2100, "Hello. Thank you for joining us today.")))
        assertEquals(1, cues.size)
        assertEquals(listOf("Hello. Thank you for joining us today."), cues[0].lines)
    }

    @Test
    fun `long text wraps to two lines of max 42 chars`() {
        val text = "The quick brown fox jumps over the lazy dog and then keeps running far away into the woods"
        val cues = CueBuilder.build(listOf(SubtitleSegment(0, 5000, text)))
        assertEquals(1, cues.size)
        val lines = cues[0].lines
        assertTrue(lines.size <= rules.maxLines)
        lines.forEach { assertTrue("line too long: $it", it.length <= rules.maxCharsPerLine) }
    }

    @Test
    fun `minimum duration is enforced`() {
        val cues = CueBuilder.build(listOf(SubtitleSegment(0, 100, "Hi")))
        assertEquals(rules.minDurationMs, cues[0].durationMs())
    }

    @Test
    fun `overlapping segments never produce overlapping cues`() {
        val cues = CueBuilder.build(
            listOf(
                SubtitleSegment(0, 3000, "First line of dialogue"),
                SubtitleSegment(2500, 5000, "Second line of dialogue")
            )
        )
        for (i in 0 until cues.size - 1) {
            assertTrue(cues[i].endMs < cues[i + 1].startMs)
        }
    }

    @Test
    fun `micro captions get merged`() {
        val cues = CueBuilder.build(
            listOf(
                SubtitleSegment(0, 4000, "A normal opening sentence."),
                SubtitleSegment(4000, 4600, "Hi!"),
                SubtitleSegment(4600, 9000, "And another normal sentence here.")
            )
        )
        assertEquals(2, cues.size)
    }

    @Test
    fun `blank segments are dropped`() {
        val cues = CueBuilder.build(listOf(SubtitleSegment(0, 1000, "   ")))
        assertTrue(cues.isEmpty())
    }
}

class SrtFormatterTest {

    @Test
    fun `timestamp format uses comma milliseconds`() {
        assertEquals("00:00:02,100", SrtFormatter.timestamp(2100))
        assertEquals("01:12:35,000", SrtFormatter.timestamp(4_355_000))
        assertEquals("00:00:00,999", SrtFormatter.timestamp(999))
    }

    @Test
    fun `vtt uses dot milliseconds and webvtt header`() {
        val out = VttFormatter.format(listOf(Cue(2100, 4800, listOf("Hello."))))
        assertTrue(out.startsWith("WEBVTT"))
        assertTrue(out.contains("00:00:02.100 --> 00:00:04.800"))
    }

    @Test
    fun `srt blocks are numbered and chronological`() {
        val cues = CueBuilder.build(
            listOf(
                SubtitleSegment(0, 2000, "One"),
                SubtitleSegment(2000, 4000, "Two")
            )
        )
        val out = SrtFormatter.format(cues)
        assertTrue(out.contains("1\n00:00:00,000 --> 00:00:02,000\nOne"))
        assertTrue(out.contains("2\n00:00:02,000 --> 00:00:04,000\nTwo"))
    }
}
