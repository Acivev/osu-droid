package com.osudroid.ui.v2.game

import com.edlplan.framework.easing.*
import com.osudroid.beatmaps.hitobjects.HitObject
import com.osudroid.utils.IPoolable
import com.osudroid.utils.SynchronizedPool
import com.osudroid.utils.updateThreadUnderTreeLock
import com.osudroid.ui.v2.hud.GameplayLayer
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIAnimatedSprite
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.rian.andengine.modifier.OnModifierFinished
import com.rian.andengine.modifier.VerktexModifierTarget
import com.rian.andengine.modifier.beginAbsoluteSequence
import com.rian.andengine.modifier.clearModifiers
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.skins.OsuSkin
import kotlin.math.*

object FollowPointConnection {


    private const val SPACING = 32

    private const val PREEMPT = 800

    private val pool = SynchronizedPool<IPoolable>(20)

    private fun obtainFollowPoint(): UISprite {
        val sprite = pool.acquire()

        if (sprite != null) {
            return sprite as UISprite
        }

        return if (ResourceManager.getInstance().isTextureLoaded("followpoint-0")) {
            PoolableAnimatedFollowPoint("followpoint", true, OsuSkin.get().animationFramerate).also { sprite ->
                sprite.frames.forEach { it.applyFollowPointMaxSize() }

                sprite.invalidateMeasure()
                sprite.isLoop = false
            }
        } else {
            PoolableFollowPoint(SkinTextures.getWithBeatmapSkin("followpoint")).also {
                it.textureRegion?.applyFollowPointMaxSize()
                it.invalidateMeasure()
            }
        }
    }

    private val expire = OnModifierFinished { target ->
        // The finished modifier's target wraps the follow point.
        val fp = (target as VerktexModifierTarget).component

        updateThreadUnderTreeLock {
            val poolable = fp as IPoolable

            // `clearAll` may have already recycled this follow point, so we need to check if it's already recycled
            // before releasing it again.
            if (poolable.isRecycled) {
                return@updateThreadUnderTreeLock
            }

            fp.removeSelf()
            (fp as UISprite).reset()
            pool.release(poolable)
        }
    }


    private fun GraphicsTexture.Region.applyFollowPointMaxSize() {
        // Reference: https://github.com/ppy/osu/blob/0811de728e4205a45e485d53ccdaf19a937c6033/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs#L95-L97
        val newWidth = min(width, HitObject.OBJECT_RADIUS * 2)
        val newHeight = min(height, HitObject.OBJECT_RADIUS)

        if (width != newWidth || height != newHeight) {

            // Crop the texture from the center.
            x += width / 2 - newWidth / 2
            y += height / 2 - newHeight / 2

            width = newWidth
            height = newHeight
        }
    }

    @JvmStatic
    fun clearAll(scene: UIContainer) {
        for (child in scene.toList().asReversed()) {
            if (child is PoolableFollowPoint || child is PoolableAnimatedFollowPoint) {
                child.clearModifiers()
                child.removeSelf()
                (child as UISprite).reset()
                pool.release(child as IPoolable)
            }
        }
    }

    /**
     * The AndEngine `reset()` of a pooled follow point: visible, unrotated, unscaled and white again, an
     * animation restarted.
     */
    private fun UISprite.reset() {
        isVisible = true
        rotationZ = 0f
        scale = 1f
        color = Color4(1f, 1f, 1f, 1f)

        if (this is UIAnimatedSprite) {
            stop()
            play()
        }
    }

    @JvmStatic
    fun renew(size: Int) {
        pool.clear()
        val isAnimated = ResourceManager.getInstance().isTextureLoaded("followpoint-0")

        repeat(size) {
            pool.release(
                if (isAnimated) PoolableAnimatedFollowPoint("followpoint", true, OsuSkin.get().animationFramerate)
                else PoolableFollowPoint(SkinTextures.getWithBeatmapSkin("followpoint"))
            )
        }
    }

    @JvmStatic
    fun addConnection(scene: UIContainer, start: HitObject, end: HitObject) {

        // Reference: https://github.com/ppy/osu/blob/7bc8908ca9c026fed1d831eb6e58df7624a8d614/osu.Game.Rulesets.Osu/Objects/Drawables/Connections/FollowPointConnection.cs

        val scale = start.screenSpaceGameplayScale
        val startTime = (start.endTime / 1000f).toFloat()

        val startPosition = start.screenSpaceGameplayStackedEndPosition
        val endPosition = end.screenSpaceGameplayStackedPosition

        val distanceX = endPosition.x - startPosition.x
        val distanceY = endPosition.y - startPosition.y
        val rotation = atan2(distanceY, distanceX) * (180f / Math.PI).toFloat()

        // HitObject.timeFadeIn may be modified by mods, but reference uses the unmodified version (which is 400ms).
        val endFadeInTime = 0.4f
        val duration = (end.startTime - start.endTime).toFloat() / 1000f

        // Preempt time can go below 800ms. Normally, this is achieved via the DT mod which uniformly speeds up all animations game wide regardless of AR.
        // This uniform speedup is hard to match 1:1, however we can at least make AR>10 (via mods) feel good by extending the upper linear preempt function.
        // Note that this doesn't exactly match the AR>10 visuals as they're classically known, but it feels good.
        val preempt = PREEMPT * min(1.0, start.timePreempt / HitObject.PREEMPT_MIN).toFloat() / 1000f

        // Since the unit of spacing is in osu!pixels, we cannot directly port the reference code. As such, we need to
        // approach it with another method. We use the distance between the start and end positions in osu!pixels to
        // determine the amount of sprites to spawn, and then map them into gameplay positions in pixels.
        val osuPixelsStartPosition = start.difficultyStackedEndPosition
        val osuPixelsEndPosition = end.difficultyStackedPosition

        val osuPixelsDistance = osuPixelsEndPosition.getDistance(osuPixelsStartPosition).toInt()

        var d = (SPACING * 1.5f).toInt()
        while (d < osuPixelsDistance - SPACING) {

            val fraction = d.toFloat() / osuPixelsDistance

            val pointStartX = startPosition.x + distanceX * (fraction - 0.1f)
            val pointStartY = startPosition.y + distanceY * (fraction - 0.1f)

            val pointEndX = startPosition.x + distanceX * fraction
            val pointEndY = startPosition.y + distanceY * fraction

            val fadeOutTime = startTime + fraction * duration
            val fadeInTime = fadeOutTime - preempt

            val fp = obtainFollowPoint()

            fp.clearModifiers()
            fp.x = pointStartX.px
            fp.y = pointStartY.px
            fp.scale = 1.5f * scale
            fp.origin = Anchor.Center
            fp.rotationZ = rotation
            fp.alpha = 0f

            scene.addToBack(fp)

            fp.beginAbsoluteSequence(fadeInTime) {
                fadeIn(endFadeInTime)
                scaleTo(scale, endFadeInTime, Easing.Out)
                moveTo(pointEndX, pointEndY, endFadeInTime, Easing.Out)

                delay(fadeOutTime - fadeInTime)
                fadeOut(endFadeInTime)
                after(expire)
            }

            d += SPACING
        }
    }

    private class PoolableFollowPoint(textureRegion: GraphicsTexture.Region? = null) : UISprite(textureRegion), IPoolable {
        override var isRecycled = false
    }

    private class PoolableAnimatedFollowPoint(
        textureName: String,
        withHyphen: Boolean,
        framePerSecond: Float
    ) : UIAnimatedSprite(GameplayLayer.skinAnimationFrames(textureName), framePerSecond), IPoolable {
        override var isRecycled = false
    }
}
