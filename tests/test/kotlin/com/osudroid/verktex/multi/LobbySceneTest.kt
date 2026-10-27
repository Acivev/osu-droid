package com.osudroid.verktex.multi

import com.osudroid.multiplayer.api.data.Room
import com.osudroid.resources.R.string
import com.osudroid.ui.v2.multi.LobbyScene
import com.osudroid.ui.v2.multi.RoomButton
import com.reco1l.verktex.ui.control.UITextInput
import com.reco1l.verktex.ui.text.UIText
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
 * The fetch of LobbyScene.kt before the port: a fetch at most every second (FETCH_TIMEOUT), refresh
 * disabled while fetching and for a second after the last fetch, the search query sent to the server
 * after the same second, and a cancelled fetchScope after the scene was hidden.
 */
@RunWith(RobolectricTestRunner::class)
class LobbySceneTest {

    private val clock = TestClock()

    // The updateThread blocks of the fetch, run on the test thread by drain().
    private val posted: MutableList<() -> Unit> = Collections.synchronizedList(mutableListOf())

    private lateinit var scene: LobbyScene

    @Before fun setUp() {
        LobbyTestSupport.setUp()
        scene = LobbyScene().also { it.post = { block -> posted += block } }
    }

    @After fun tearDown() {
        // Stops a fetch still running on the lobby's scope.
        scene.onHidden()
        LobbyTestSupport.tearDown()
    }

    private fun waitForPosts(count: Int) {
        val deadline = System.currentTimeMillis() + 5000
        while (posted.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("expected $count posted blocks, got ${posted.size}", posted.size >= count)
    }

    private fun drain() {
        while (posted.isNotEmpty()) {
            posted.removeAt(0)()
        }
    }

    private fun texts() = scene.descendants().filterIsInstance<UIText>().map { it.text }.toList()


    @Test fun refreshIsDisabledWhileFetchingAndReenabledAfter() {
        val gate = CountDownLatch(1)
        scene.roomsSource = {
            gate.await(5, TimeUnit.SECONDS)
            listOf(LobbyTestSupport.room(), LobbyTestSupport.room())
        }

        // The first frame starts the fetch.
        scene.update(clock)
        assertTrue(scene.isFetching)

        scene.update(clock)
        assertFalse(scene.refreshButton.isEnabled)

        gate.countDown()
        waitForPosts(2)
        drain()

        assertFalse(scene.isFetching)
        assertEquals(2, scene.descendants().filterIsInstance<RoomButton>().count())

        // Still within a second of the fetch.
        scene.update(clock)
        assertFalse(scene.refreshButton.isEnabled)

        Thread.sleep(1100)
        scene.update(clock)
        assertTrue(scene.refreshButton.isEnabled)
    }

    @Test fun fetchOnACancelledScopeNeverRunsAndKeepsRefreshDisabled() {
        var calls = 0
        scene.roomsSource = { calls++; emptyList() }

        // Hidden before the first fetch: onDetached cancelled fetchScope for good (oddity 1).
        scene.onHidden()

        scene.update(clock)
        Thread.sleep(200)

        assertEquals(0, calls)
        assertTrue(posted.isEmpty())
        assertTrue(scene.isFetching)

        Thread.sleep(1100)
        scene.update(clock)
        assertFalse(scene.refreshButton.isEnabled)
        assertEquals(0, calls)
    }

    @Test fun searchQueryIsSentToTheServerASecondAfterTheLastChange() {
        val queries = Collections.synchronizedList(mutableListOf<String?>())
        scene.roomsSource = { query -> queries += query; emptyList<Room>() }

        scene.update(clock)
        waitForPosts(2)
        drain()
        assertEquals(listOf<String?>(null), queries.toList())
        assertTrue(StringTable.get(string.multiplayer_lobby_no_rooms) in texts())

        val search = scene.descendants().filterIsInstance<UITextInput>().single { it.key == "search" }
        search.value = "a"

        // The debounce counts from the change.
        scene.update(clock)
        Thread.sleep(200)
        assertEquals(listOf<String?>(null), queries.toList())

        Thread.sleep(1000)
        scene.update(clock)
        waitForPosts(2)
        drain()

        assertEquals(listOf(null, "a"), queries.toList())
        assertTrue(StringTable.get(string.multiplayer_lobby_no_results) in texts())
    }
}
