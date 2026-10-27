package com.osudroid.verktex.multi

import com.osudroid.multiplayer.api.data.PlayerStatus
import com.osudroid.resources.R.string
import com.osudroid.ui.v2.multi.RoomScene
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.UIContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.helper.StringTable

/**
 * RoomScene.kt before the port: the start button is shown to the host once ready and counts the
 * ready players, and the content padding is reassigned every tick from the chat button height
 * (oddity 8: the check compares the padding with the height, not with the height plus 12).
 */
@RunWith(RobolectricTestRunner::class)
class RoomSceneTest {

    @Before fun setUp() = LobbyTestSupport.setUp()

    @After fun tearDown() = LobbyTestSupport.tearDown()


    @Test fun startButtonTextFollowsHostAndStatus() {
        assertTrue(RoomScene.isStartButtonShown(isRoomHost = true, status = PlayerStatus.Ready))
        assertFalse(RoomScene.isStartButtonShown(isRoomHost = true, status = PlayerStatus.NotReady))
        assertFalse(RoomScene.isStartButtonShown(isRoomHost = true, status = PlayerStatus.MissingBeatmap))
        assertFalse(RoomScene.isStartButtonShown(isRoomHost = false, status = PlayerStatus.Ready))

        assertEquals(StringTable.get(string.multiplayer_room_start_game), RoomScene.startButtonText(playersReady = 1, players = 1))
        assertEquals(StringTable.get(string.multiplayer_room_start_game), RoomScene.startButtonText(playersReady = 3, players = 3))
        assertEquals(StringTable.format(string.multiplayer_room_force_start_game, 1, 3), RoomScene.startButtonText(playersReady = 1, players = 3))
        assertEquals(StringTable.format(string.multiplayer_room_force_start_game, 0, 2), RoomScene.startButtonText(playersReady = 0, players = 2))
    }

    @Test fun paddingIsReassignedEveryTick() {
        val root = UIContainer()

        with(RoomScene) { root.applyRoomPadding(buttonHeight = 50f) }
        assertEquals(80f.vu.px, root.paddingLeft)
        assertEquals(0f.px, root.paddingTop)
        assertEquals(80f.vu.px, root.paddingRight)
        assertEquals(62f.vu.px, root.paddingBottom)

        // The bottom padding never equals the button height, so every tick assigns it again.
        root.paddingBottom = 10f.px
        with(RoomScene) { root.applyRoomPadding(buttonHeight = 50f) }
        assertEquals(62f.vu.px, root.paddingBottom)

        with(RoomScene) { root.applyRoomPadding(buttonHeight = 70f) }
        assertEquals(82f.vu.px, root.paddingBottom)

        // Only a bottom padding equal to the button height itself is left alone.
        root.paddingBottom = 70f.vu.px
        with(RoomScene) { root.applyRoomPadding(buttonHeight = 70f) }
        assertEquals(70f.vu.px, root.paddingBottom)
    }
}
