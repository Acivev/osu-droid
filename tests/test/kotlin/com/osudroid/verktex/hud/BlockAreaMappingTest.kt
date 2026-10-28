package com.osudroid.verktex.hud

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.data.Dimension
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.GameScene

class BlockAreaMappingTest {

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()

        // The fake window is 1280x720, so one virtual unit is one window pixel.
        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)
    }

    @Test
    fun blockAreaMapsDevicePixelsToSceneUnits() {
        // A 2560 px wide display: ratio = RES_WIDTH / widthPixels.
        val ratio = 1280f / 2560f

        assertEquals(50f, GameScene.BlockAreaBox.toSceneUnits(100f, ratio), 1e-4f)
        assertEquals(0f, GameScene.BlockAreaBox.toSceneUnits(0f, ratio), 1e-4f)
        assertEquals(640f, GameScene.BlockAreaBox.toSceneUnits(1280f, ratio), 1e-4f)

        val box = GameScene.BlockAreaBox(
            GameScene.BlockAreaBox.toSceneUnits(200f, ratio),
            GameScene.BlockAreaBox.toSceneUnits(400f, ratio),
            GameScene.BlockAreaBox.toSceneUnits(300f, ratio),
            GameScene.BlockAreaBox.toSceneUnits(120f, ratio)
        ) {}

        assertEquals(100f, (box.x as Dimension.Fixed).toPixels(), 1e-4f)
        assertEquals(200f, (box.y as Dimension.Fixed).toPixels(), 1e-4f)
        assertEquals(150f, (box.width as Dimension.Fixed).toPixels(), 1e-4f)
        assertEquals(60f, (box.height as Dimension.Fixed).toPixels(), 1e-4f)
        assertEquals(0.15f, box.alpha, 1e-6f)
        assertEquals(2f, box.radius.toPixels(), 1e-4f)
    }
}
