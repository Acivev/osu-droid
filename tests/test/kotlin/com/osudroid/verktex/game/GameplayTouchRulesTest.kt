package com.osudroid.verktex.game

import com.osudroid.game.Cursor
import com.osudroid.game.CursorEvent
import com.reco1l.verktex.input.PointerEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.game.GameScene

/**
 * The cursor limit of `GameScene.onSceneTouchEvent`: at most three cursors are down at once.
 */
@RunWith(RobolectricTestRunner::class)
class GameplayTouchRulesTest {

    private fun cursors(downCount: Int) = Array(GameScene.CursorCount) { i ->
        Cursor().also { cursor ->
            if (i < downCount) {
                cursor.addEvent(CursorEvent.obtain().also { it.action = PointerEvent.Action.Down })
            }
        }
    }

    @Test fun fourthCursorIsRejected() {
        assertFalse(GameScene.canActivateCursor(cursors(3), 3))
        assertTrue(GameScene.canActivateCursor(cursors(2), 3))
    }
}
