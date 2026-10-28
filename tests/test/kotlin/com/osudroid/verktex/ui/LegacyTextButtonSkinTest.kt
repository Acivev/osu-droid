package com.osudroid.verktex.ui

import com.osudroid.ui.v2.modmenu.LegacyTextButton
import com.osudroid.ui.verktex.ISkinnable
import com.osudroid.verktex.multi.LobbyTestSupport
import com.osudroid.verktex.multi.TestClock
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.container.UIContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A skin change unloads every skin region, so a text button looks its icon up again after the
 * skin changed, as ModMenu.show() tells its tree when it is shown.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyTextButtonSkinTest {

    @Before fun setUp() = LobbyTestSupport.setUp()

    @After fun tearDown() = LobbyTestSupport.tearDown()

    @Test fun iconIsLookedUpAgainAfterASkinChange() = Verktex.withTreeLock {
        var lookups = 0
        val button = LegacyTextButton("Clear", "backspace", "backspace.png") { _, _ ->
            lookups++
            null
        }
        val tree = UIContainer().apply { +button }

        button.update(TestClock())
        button.update(TestClock())
        assertEquals(1, lookups)

        tree.propagate { (it as? ISkinnable)?.onSkinChanged() }
        button.update(TestClock())
        assertEquals(2, lookups)
    }
}
