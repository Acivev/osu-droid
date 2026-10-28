package com.osudroid.verktex.bridge

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.UIScene
import org.andengine.entity.scene.Scene
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngineRouterTest {
    private val set = mutableListOf<Scene>()
    private val defaultSceneSetter = EngineRouter.andEngineSceneSetter

    private class FakeRoutedScene : UIScene(), RoutedScene {
        var shownCount = 0

        override fun onShown() {
            shownCount++
        }
    }

    private class RecordingScene : UIScene(), RoutedScene {
        var shown = 0
        var hidden = 0

        override fun onShown() {
            shown++
        }

        override fun onHidden() {
            hidden++
        }
    }

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
        EngineRouter.andEngineSceneSetter = { set += it }
        EngineRouter.leaveVerktex()
    }

    @After
    fun tearDown() {
        EngineRouter.leaveVerktex()
        EngineRouter.andEngineSceneSetter = defaultSceneSetter
    }

    @Test
    fun showVerktexEmptiesAndEngineAndPushesScene() {
        val s = UIScene()
        EngineRouter.showVerktex(s)
        assertSame(EngineRouter.emptyAndEngineScene, set.single())
        assertSame(s, Verktex.scene)
        assertTrue(EngineRouter.isVerktexActive)
    }

    @Test
    fun showVerktexReplacesPreviousVerktexScene() {
        EngineRouter.showVerktex(UIScene())
        val s2 = UIScene()
        EngineRouter.showVerktex(s2)
        assertSame(s2, Verktex.scene)
        EngineRouter.leaveVerktex()
        assertSame(UIScene.EmptyScene, Verktex.scene) // exactly one scene was stacked
    }

    @Test
    fun showAndEngineLeavesVerktexAndSetsScene() {
        EngineRouter.showVerktex(UIScene())
        val a = Scene()
        EngineRouter.showAndEngine(a)
        assertFalse(EngineRouter.isVerktexActive)
        assertSame(a, set.last())
    }

    @Test
    fun leaveVerktexClearsStack() {
        EngineRouter.showVerktex(UIScene())
        EngineRouter.leaveVerktex()
        assertSame(UIScene.EmptyScene, Verktex.scene)
        assertFalse(EngineRouter.isVerktexActive)
    }

    @Test
    fun showDispatchesOnDestinationType() {
        val v = UIScene()
        EngineRouter.show(Destination.Verktex(v))
        assertSame(v, Verktex.scene)
        val a = Scene()
        EngineRouter.show(Destination.AndEngine(a))
        assertSame(a, set.last())
        assertFalse(EngineRouter.isVerktexActive)
    }

    @Test
    fun showVerktexNotifiesRoutedScene() {
        val s = FakeRoutedScene()
        EngineRouter.showVerktex(s)
        assertEquals(1, s.shownCount)
    }

    @Test
    fun showVerktexDestinationNotifiesRoutedScene() {
        val s = FakeRoutedScene()
        EngineRouter.show(Destination.Verktex(s))
        assertEquals(1, s.shownCount)
    }

    @Test
    fun showVerktexAcceptsSceneWithoutHook() {
        val s = UIScene()
        EngineRouter.showVerktex(s)
        assertSame(s, Verktex.scene)
    }

    @Test
    fun showOverlayMakesVerktexActiveWithoutAScene() {
        val o = UIContainer()
        EngineRouter.showOverlay(o)
        assertTrue(EngineRouter.isOverlayShown(o))
        assertTrue(EngineRouter.isVerktexActive)
        assertSame(UIScene.EmptyScene, Verktex.scene)
    }

    @Test
    fun hideOverlayDeactivates() {
        val o = UIContainer()
        EngineRouter.showOverlay(o)
        EngineRouter.hideOverlay(o)
        assertFalse(EngineRouter.isOverlayShown(o))
        assertFalse(EngineRouter.isVerktexActive)
    }

    @Test
    fun leaveVerktexHidesTrackedOverlays() {
        val o = UIContainer()
        EngineRouter.showVerktex(UIScene())
        EngineRouter.showOverlay(o)
        EngineRouter.leaveVerktex()
        assertFalse(EngineRouter.isOverlayShown(o))
        assertFalse(EngineRouter.isVerktexActive)
    }

    @Test
    fun showVerktexHidesTrackedOverlays() {
        val o = UIContainer()
        EngineRouter.showVerktex(UIScene())
        EngineRouter.showOverlay(o)
        val s = UIScene()
        EngineRouter.showVerktex(s)
        assertFalse(EngineRouter.isOverlayShown(o))
        assertSame(s, Verktex.scene)
    }

    @Test
    fun showOverlayIsIdempotent() {
        val o = UIContainer()
        EngineRouter.showOverlay(o)
        EngineRouter.showOverlay(o)
        EngineRouter.hideOverlay(o)
        assertFalse(EngineRouter.isVerktexActive)
    }

    @Test
    fun showingAnotherVerktexSceneHidesThePrevious() {
        val a = RecordingScene()
        val b = RecordingScene()
        EngineRouter.showVerktex(a)
        EngineRouter.showVerktex(b)
        assertEquals(1, a.hidden)
        assertEquals(1, b.shown)
        assertEquals(0, b.hidden)
    }

    @Test
    fun showingAndEngineHidesTheVerktexScene() {
        val a = RecordingScene()
        EngineRouter.showVerktex(a)
        EngineRouter.showAndEngine(Scene())
        assertEquals(1, a.hidden)
    }

    @Test
    fun leaveVerktexHidesTheVerktexScene() {
        val a = RecordingScene()
        EngineRouter.showVerktex(a)
        EngineRouter.leaveVerktex()
        assertEquals(1, a.hidden)
        EngineRouter.leaveVerktex()
        assertEquals(1, a.hidden)
    }

    @Test
    fun showVerktexWithBackdropSetsIt() {
        val backdrop = Scene()
        val scene = RecordingScene()
        EngineRouter.showVerktex(scene, backdrop)
        assertSame(backdrop, set.single())
        assertSame(scene, Verktex.scene)
        assertEquals(1, scene.shown)

        EngineRouter.showVerktex(UIScene())
        assertSame(EngineRouter.emptyAndEngineScene, set.last())
        assertEquals(1, scene.hidden)
    }
}
