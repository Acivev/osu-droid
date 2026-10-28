package com.osudroid.verktex.ui

import com.osudroid.ui.v2.CalibrationScene
import com.osudroid.verktex.bridge.EngineRouter
import com.osudroid.verktex.multi.LobbyTestSupport
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.input.KeyboardEvent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.audio.serviceAudio.SongService
import ru.nsu.ccfit.zuev.osu.GlobalManager

/**
 * CalibrationScene.kt before the port: a tap's error is its distance to the nearest beat of the
 * metronome (60 BPM by default), the suggested offset is the median of the last taps, a tap inside
 * the OK window extends the streak and any other tap resets it. The scene is a tracked router overlay.
 */
@RunWith(RobolectricTestRunner::class)
class CalibrationSceneTest {

    private val defaultSceneSetter = EngineRouter.andEngineSceneSetter
    // Read in setUp, the scene must be built after the fonts are set up.
    private lateinit var defaultMainThreadPost: (() -> Unit) -> Unit
    private lateinit var defaultOverlayDisplayed: () -> Boolean
    private var songService: SongService? = null
    private var previousIconFont: FontFamily? = null

    // The static field behind the lateinit BuiltIn.Fonts.FontAwesomeIconSolid, to restore it unset.
    private val iconFontField = BuiltIn.Fonts::class.java.getDeclaredField("FontAwesomeIconSolid").apply { isAccessible = true }

    // Config reads the high precision input setting from its preferences, which only loadConfig sets.
    private val preferencesField = Config::class.java.getDeclaredField("sharedPreferences").apply { isAccessible = true }
    private var previousPreferences: SharedPreferences? = null

    @Before fun setUp() {
        LobbyTestSupport.setUp()

        // The caret icons take the icon family at construction, nothing is drawn here.
        previousIconFont = iconFontField.get(null) as FontFamily?
        if (previousIconFont == null) {
            BuiltIn.Fonts.FontAwesomeIconSolid = BuiltIn.Fonts.Default
        }

        previousPreferences = preferencesField.get(null) as SharedPreferences?
        preferencesField.set(null, PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()))

        EngineRouter.andEngineSceneSetter = {}
        EngineRouter.leaveVerktex()

        songService = GlobalManager.getInstance().songService
        // Not playing, so show() and back() leave the music alone.
        GlobalManager.getInstance().songService = SongService()
        defaultMainThreadPost = Verktex.withTreeLock { CalibrationScene.mainThreadPost }
        CalibrationScene.mainThreadPost = { block -> block() }
        defaultOverlayDisplayed = CalibrationScene.isActivityOverlayDisplayed
        CalibrationScene.isActivityOverlayDisplayed = { false }

        Verktex.withTreeLock {
            CalibrationScene.tapOffsets.clear()
            CalibrationScene.successfulTapStreak = 0
            CalibrationScene.pendingOffset = 0
        }
    }

    @After fun tearDown() {
        Verktex.withTreeLock { EngineRouter.hideOverlay(CalibrationScene) }
        CalibrationScene.mainThreadPost = defaultMainThreadPost
        CalibrationScene.isActivityOverlayDisplayed = defaultOverlayDisplayed
        CalibrationScene.settingsFragment = null
        GlobalManager.getInstance().songService = songService
        EngineRouter.leaveVerktex()
        EngineRouter.andEngineSceneSetter = defaultSceneSetter
        preferencesField.set(null, previousPreferences)
        previousPreferences = null
        iconFontField.set(null, previousIconFont)
        previousIconFont = null
        LobbyTestSupport.tearDown()
    }

    /**
     * Taps [errorMs] after a beat of the 60 BPM metronome (a negative value taps before it).
     */
    private fun tapAt(errorMs: Int) = Verktex.withTreeLock {
        CalibrationScene.metronomeTime = (if (errorMs < 0) 1000 + errorMs else errorMs) / 1000.0
        CalibrationScene.onPlayfieldTap()
    }


    @Test fun offsetIsTheMeanOfTheLastTaps() {
        tapAt(10)
        tapAt(20)
        tapAt(30)

        assertEquals(3, CalibrationScene.tapOffsets.size)
        assertEquals(10.0, CalibrationScene.tapOffsets[0], 1e-6)
        assertEquals(20, CalibrationScene.pendingOffset)
    }

    @Test fun streakResetsOnAMissedBeat() {
        tapAt(0)
        tapAt(-5)
        assertEquals(2, CalibrationScene.successfulTapStreak)

        // 400 ms off the beat is outside every hit window.
        tapAt(400)
        assertEquals(0, CalibrationScene.successfulTapStreak)

        tapAt(5)
        assertEquals(1, CalibrationScene.successfulTapStreak)
    }

    @Test fun showIsATrackedOverlay() {
        Verktex.withTreeLock { CalibrationScene.show() }
        assertTrue(EngineRouter.isOverlayShown(CalibrationScene))
        assertTrue(EngineRouter.isVerktexActive)

        Verktex.withTreeLock { CalibrationScene.back() }
        assertFalse(EngineRouter.isOverlayShown(CalibrationScene))
        assertFalse(EngineRouter.isVerktexActive)
    }

    @Test fun backKeyGoesToADisplayedDialogFirst() {
        val back = KeyboardEvent(action = KeyboardEvent.Action.Down, key = KeyboardEvent.Key.Back)
        Verktex.withTreeLock { CalibrationScene.show() }

        // The BPM or offset input dialog is shown: the key goes to ActivityOverlay.onBackPress() on the
        // main thread and the scene stays.
        var posts = 0
        CalibrationScene.mainThreadPost = { posts++ }
        CalibrationScene.isActivityOverlayDisplayed = { true }
        assertTrue(Verktex.withTreeLock { CalibrationScene.onInputEvent(back) })
        assertTrue(EngineRouter.isOverlayShown(CalibrationScene))
        assertEquals(1, posts)
        CalibrationScene.mainThreadPost = { block -> block() }

        CalibrationScene.isActivityOverlayDisplayed = { false }
        assertTrue(Verktex.withTreeLock { CalibrationScene.onInputEvent(back) })
        assertFalse(EngineRouter.isOverlayShown(CalibrationScene))
    }
}
