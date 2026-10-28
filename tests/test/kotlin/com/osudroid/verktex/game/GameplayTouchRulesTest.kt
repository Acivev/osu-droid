package com.osudroid.verktex.game

import com.osudroid.game.Cursor
import com.osudroid.game.CursorEvent
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.UIScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.game.GameScene.TouchBranch

/**
 * The touch rules of `GameScene.onSceneTouchEvent`: at most three cursors are down at once, a press in a
 * block area is consumed without a cursor, a cancel or an unknown action lifts every cursor; and
 * `GameScene.isInGameplay`.
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

    private fun branch(action: PointerEvent.Action, cursors: Array<Cursor>, index: Int) =
        GameScene.touchBranch(action, cursors[index], cursors, 3)

    @Test fun fourthFingerIsIgnored() {
        val cursors = cursors(3)

        assertEquals(TouchBranch.Rejected, branch(PointerEvent.Action.Down, cursors, 3))
        // Its move and release are not handled either, it never went down.
        assertEquals(TouchBranch.Ignored, branch(PointerEvent.Action.Move, cursors, 3))
        assertEquals(TouchBranch.Ignored, branch(PointerEvent.Action.Up, cursors, 3))
        assertEquals(TouchBranch.Down, branch(PointerEvent.Action.Down, cursors(2), 2))
    }

    @Test fun blockedDownIsConsumedWithoutACursor() {
        val cursors = cursors(0)
        cursors[0].mouseBlocked = true

        assertEquals(TouchBranch.Blocked, branch(PointerEvent.Action.Down, cursors, 0))
        // Blocked before the limit: a blocked press is consumed even with three cursors down.
        val full = cursors(3).also { it[3].mouseBlocked = true }
        assertEquals(TouchBranch.Blocked, branch(PointerEvent.Action.Down, full, 3))
    }

    @Test fun cancelAndUnknownRemoveAllCursors() {
        for (index in listOf(0, 3)) {
            assertEquals(TouchBranch.RemoveAll, branch(PointerEvent.Action.Cancel, cursors(1), index))
            assertEquals(TouchBranch.RemoveAll, branch(PointerEvent.Action.Unknown, cursors(1), index))
        }
    }

    @Test fun moveAndUpOfADownCursor() {
        val cursors = cursors(1)

        assertEquals(TouchBranch.Move, branch(PointerEvent.Action.Move, cursors, 0))
        assertEquals(TouchBranch.Up, branch(PointerEvent.Action.Up, cursors, 0))
        assertEquals(TouchBranch.Ignored, branch(PointerEvent.Action.Move, cursors, 1))
    }

    @Test fun inGameplayOnlyWhileTheCurrentSceneIsShown() {
        VerktexTestFixtures.ensureInitialized()
        val oldScene = UIScene()
        Verktex.pushScene(oldScene)

        try {
            assertTrue(GameScene.isInGameplay(Verktex.scene, oldScene))

            // Retry or quit reassigned the scene field while the old scene is still shown.
            val newScene = UIScene()
            assertFalse(GameScene.isInGameplay(Verktex.scene, newScene))
        } finally {
            Verktex.popScene()
        }
    }
}
