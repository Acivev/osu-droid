package com.osudroid.verktex.multi

import com.osudroid.resources.R.string
import com.osudroid.ui.v2.multi.LobbyScene
import com.osudroid.ui.v2.multi.RoomButton
import com.osudroid.ui.verktex.LegacyLabeledBadge
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.text.UIText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.helper.StringTable

/**
 * RoomButton.kt before the port: the names line falls back to "N player(s)" and then to
 * multiplayer_room_no_players, the players badge shows "count/max", and the leading icon is lock or
 * unlock.
 */
@RunWith(RobolectricTestRunner::class)
class RoomButtonTest {

    private lateinit var lobby: LobbyScene

    @Before fun setUp() {
        LobbyTestSupport.setUp()
        lobby = LobbyScene()
    }

    @After fun tearDown() {
        lobby.onHidden()
        LobbyTestSupport.tearDown()
    }

    private fun RoomButton.texts() = descendants().filterIsInstance<UIText>().map { it.text }.toList()

    @Test fun playersTextShowsCountAndMax() {
        val button = RoomButton(lobby, LobbyTestSupport.room(playerCount = 3, maxPlayers = 8))

        assertEquals("3/8", button.descendants().filterIsInstance<LegacyLabeledBadge>().single().value)
        assertEquals(StringTable.get(string.multiplayer_room_players), button.descendants().filterIsInstance<LegacyLabeledBadge>().single().label)
        assertTrue("3 players" in button.texts())

        assertTrue("1 player" in RoomButton(lobby, LobbyTestSupport.room(playerCount = 1)).texts())
        assertTrue(StringTable.get(string.multiplayer_room_no_players) in RoomButton(lobby, LobbyTestSupport.room(playerCount = 0)).texts())
        assertTrue("Alice, Bob" in RoomButton(lobby, LobbyTestSupport.room(playerCount = 2, playerNames = "Alice, Bob")).texts())
    }

    @Test fun lockedRoomShowsTheLockIcon() {
        val lock = SkinTextures.getAsset("lock", "lock.png")
        val unlock = SkinTextures.getAsset("unlock", "unlock.png")
        assertNotNull(lock)
        assertNotNull(unlock)

        val locked = RoomButton(lobby, LobbyTestSupport.room(isLocked = true))
        assertSame(lock, locked.descendants().filterIsInstance<UISprite>().first().textureRegion)

        val unlocked = RoomButton(lobby, LobbyTestSupport.room(isLocked = false))
        assertSame(unlock, unlocked.descendants().filterIsInstance<UISprite>().first().textureRegion)
    }
}
