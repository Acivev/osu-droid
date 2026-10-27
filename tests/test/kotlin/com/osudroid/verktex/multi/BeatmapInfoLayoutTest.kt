package com.osudroid.verktex.multi

import com.osudroid.ui.v2.BeatmapInfoLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The badge texts of BeatmapInfoLayout.kt before the port: the length in GMT as "mm:ss", or
 * "HH:mm:ss" past an hour, and the bpm as the common value or "min-max (common)".
 */
class BeatmapInfoLayoutTest {

    @Test fun lengthIsMinutesSecondsUnderAnHour() {
        assertEquals("00:00", BeatmapInfoLayout.formatLength(0))
        assertEquals("03:25", BeatmapInfoLayout.formatLength(205_400))
        assertEquals("59:59", BeatmapInfoLayout.formatLength(3_599_999))

        // The hour format starts past an hour, a length of exactly one hour wraps to "00:00".
        assertEquals("00:00", BeatmapInfoLayout.formatLength(3_600_000))
        assertEquals("01:00:00", BeatmapInfoLayout.formatLength(3_600_001))
        assertEquals("01:02:03", BeatmapInfoLayout.formatLength(3_723_000))
    }

    @Test fun bpmShowsMinMaxAndCommon() {
        assertEquals("180", BeatmapInfoLayout.formatBpm(180, 180, 180))
        // A single bpm shows the common value, even when it differs.
        assertEquals("175", BeatmapInfoLayout.formatBpm(180, 180, 175))
        assertEquals("120-240 (180)", BeatmapInfoLayout.formatBpm(120, 240, 180))
    }
}
