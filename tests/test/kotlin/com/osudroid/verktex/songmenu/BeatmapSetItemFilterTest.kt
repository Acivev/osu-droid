package com.osudroid.verktex.songmenu

import com.osudroid.data.BeatmapInfo
import com.osudroid.data.BeatmapSetInfo
import ru.nsu.ccfit.zuev.osu.menu.BeatmapSetItem
import ru.nsu.ccfit.zuev.osu.menu.MenuItemListener
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.nsu.ccfit.zuev.osu.helper.StringTable

@RunWith(RobolectricTestRunner::class)
class BeatmapSetItemFilterTest {

    private val listener = object : MenuItemListener {
        override fun select(item: BeatmapSetItem) {}
        override fun selectBeatmap(beatmapInfo: BeatmapInfo, reloadBG: Boolean) {}
        override fun stopScroll(y: Float) {}
        override fun setY(y: Float) {}
        override fun openScore(id: Int, showOnline: Boolean, playerName: String?) {}
        override fun playMusic(filename: String, previewTime: Int) {}
        override fun isSelectAllowed() = true
        override fun showPropertiesMenu(item: BeatmapSetItem?) {}
        override fun showDeleteScoreMenu(scoreId: Int) {}
    }

    private lateinit var fastSet: BeatmapSetInfo
    private lateinit var slowSet: BeatmapSetInfo

    @Before
    fun setUp() {
        StringTable.setContext(RuntimeEnvironment.getApplication())

        fastSet = beatmapSet("1 Fast", beatmap("1 Fast", "Normal", "FastMapper", 8f), beatmap("1 Fast", "Insane", "FastMapper", 9.5f))
        slowSet = beatmapSet("2 Slow", beatmap("2 Slow", "Easy", "SlowMapper", 5f), beatmap("2 Slow", "Hard", "SlowMapper", 9f))
    }

    @Test
    fun `Approach rate filter keeps sets with a matching difficulty`() {
        val fast = BeatmapSetItem(listener, fastSet)
        val slow = BeatmapSetItem(listener, slowSet)

        fast.applyFilter("ar>9", false, null)
        slow.applyFilter("ar>9", false, null)

        assertTrue(fast.isVisible())
        // 9 is not greater than 9.
        assertFalse(slow.isVisible())
    }

    @Test
    fun `Approach rate filter on a single difficulty item checks only that difficulty`() {
        val normal = BeatmapSetItem(listener, fastSet, 0)
        val insane = BeatmapSetItem(listener, fastSet, 1)

        normal.applyFilter("ar>9", false, null)
        insane.applyFilter("ar>9", false, null)

        assertFalse(normal.isVisible())
        assertTrue(insane.isVisible())
    }

    @Test
    fun `Text filter matches the creator case insensitively`() {
        val fast = BeatmapSetItem(listener, fastSet)
        val slow = BeatmapSetItem(listener, slowSet)

        fast.applyFilter("fastmapper", false, null)
        slow.applyFilter("fastmapper", false, null)

        assertTrue(fast.isVisible())
        assertFalse(slow.isVisible())
    }

    @Test
    fun `Empty filter shows a hidden set again`() {
        val slow = BeatmapSetItem(listener, slowSet)

        slow.applyFilter("fastmapper", false, null)
        assertFalse(slow.isVisible())

        slow.applyFilter("", false, null)
        assertTrue(slow.isVisible())
    }

    @Test
    fun `Favorites and directory limit hide the set`() {
        val fast = BeatmapSetItem(listener, fastSet)
        fast.setFavorite(false)

        fast.applyFilter("", true, null)
        assertFalse(fast.isVisible())

        fast.applyFilter("", false, listOf("2 Slow"))
        assertFalse(fast.isVisible())

        fast.applyFilter("", false, listOf("1 Fast"))
        assertTrue(fast.isVisible())
    }

    private fun beatmapSet(directory: String, vararg beatmaps: BeatmapInfo) =
        BeatmapSetInfo(id = null, directory = directory, beatmaps = beatmaps.toMutableList())

    private fun beatmap(directory: String, version: String, creator: String, approachRate: Float) = BeatmapInfo(
        filename = "$version.osu",
        md5 = version,
        id = null,
        audioFilename = "audio.mp3",
        backgroundFilename = null,
        status = null,
        setDirectory = directory,
        setId = null,
        title = "Title $directory",
        titleUnicode = "",
        artist = "Artist",
        artistUnicode = "",
        creator = creator,
        version = version,
        tags = "",
        source = "",
        dateImported = 0L,
        approachRate = approachRate,
        overallDifficulty = 5f,
        circleSize = 4f,
        hpDrainRate = 5f,
        droidStarRating = 3f,
        standardStarRating = 3f,
        bpmMax = 120f,
        bpmMin = 120f,
        mostCommonBPM = 120f,
        length = 60000L,
        previewTime = 0,
        hitCircleCount = 0,
        spinnerCount = 0,
        sliderCount = 0,
        maxCombo = 0,
        epilepsyWarning = false
    )
}
