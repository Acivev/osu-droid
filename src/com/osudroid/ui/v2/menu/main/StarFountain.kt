package com.osudroid.ui.v2.menu.main

import com.osudroid.ui.verktex.ISkinnable
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Mat2
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIScene
import ru.nsu.ccfit.zuev.audio.BassSoundProvider
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.ResourceManager
import kotlin.random.Random

/**
 * Lazer-style star fountain, two particle emitters (left + right) that fire a sweeping arc
 * of stars on kiai onset.
 *
 * Verktex has no particle system, so the emitters of the original AndEngine particle systems are
 * reproduced by [ParticleSystem]: same spawn rate, initial velocity, acceleration, rotation, scale
 * and alpha spans and lifetime. Particles are kept in virtual units and converted to window pixels
 * when drawn.
 */
class StarFountain {

    private val systemLeft = ParticleSystem(EMITTER_LEFT_X)
    private val systemRight = ParticleSystem(EMITTER_RIGHT_X)

    // Shoot state
    var isActive = false
        private set
    private var order = 0
    private var beginMs = 0

    private val shootSound: BassSoundProvider? by lazy {
        ResourceManager.getInstance().loadSound("fountain-shoot", "sfx/fountain-shoot.ogg", false)
    }

    private val loopSound: BassSoundProvider? by lazy {
        ResourceManager.getInstance().loadSound("fountain-loop", "sfx/fountain-loop.ogg", false)
            ?.also { it.setLooping(true) }
    }

    private var loopPlaying = false
    /** Timestamp of the last shoot() call, for the 500 ms retrigger guard. */
    private var lastShootMs = -1


    /** Attach the particle systems to [scene] so they are rendered and updated. */
    fun attachTo(scene: UIScene) {
        scene += systemLeft
        scene += systemRight
    }

    /**
     * Fire a burst with a randomly-chosen direction.
     * Call once when kiai starts.
     *
     * @param songPosMs current song position in milliseconds
     */
    fun shoot(songPosMs: Int) {
        // Pick direction: -1 = inward cross, 0 = straight up, 1 = outward spread
        order = listOf(-1, 0, 1).random()
        beginMs = songPosMs
        isActive = true

        // first shoot / gap > 500 ms → stop loop, play one-shot
        // rapid shoot (< 500 ms) → start loop (if not already looping)
        if (lastShootMs < 0 || songPosMs - lastShootMs > SHOOT_RETRIGGER_MS) {
            loopSound?.stop(); loopPlaying = false
            shootSound?.play()
        } else if (!loopPlaying) {
            loopSound?.play(); loopPlaying = true
        }
        lastShootMs = songPosMs

        // Prime initial X angle immediately so frame-0 particles already have the right lean
        applyVelocity(
            leftX = (-order * SWEEP_X_VEL),
            rightX = ( order * SWEEP_X_VEL)
        )
        systemLeft.isParticlesSpawnEnabled = true
        systemRight.isParticlesSpawnEnabled = true
    }

    fun update(songPosMs: Int) {
        if (!isActive) return

        val elapsed = (songPosMs - beginMs).toFloat() / 1000f
        if (elapsed >= SHOOT_DURATION_SEC) {
            systemLeft.isParticlesSpawnEnabled  = false
            systemRight.isParticlesSpawnEnabled = false
            isActive = false
        } else {
            val sweep = SWEEP_X_VEL * (1f - 2f * elapsed / SHOOT_DURATION_SEC)
            applyVelocity(leftX = -order * sweep, rightX = order * sweep)
        }

        // Stop loop SHOOT_RETRIGGER_MS after the last shoot
        if (loopPlaying && lastShootMs >= 0 && songPosMs - lastShootMs > SHOOT_RETRIGGER_MS) {
            loopSound?.stop(); loopPlaying = false
        }
    }


    /** Immediately stop all particle emission and audio — called on scene exit. */
    fun stop() {
        systemLeft.isParticlesSpawnEnabled  = false
        systemRight.isParticlesSpawnEnabled = false
        isActive = false
        if (loopPlaying) { loopSound?.stop(); loopPlaying = false }
        // Reset so the next kiai onset always plays the shoot sound fresh,
        // even if it arrives within SHOOT_RETRIGGER_MS of this stop.
        lastShootMs = -1
    }

    private fun applyVelocity(leftX: Float, rightX: Float) {
        systemLeft.setVelocityX(leftX   - X_VARIANCE, leftX   + X_VARIANCE)
        systemRight.setVelocityX(rightX - X_VARIANCE, rightX  + X_VARIANCE)
    }


    /** A star, in virtual units. */
    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
    ) {
        var scale = SCALE_FROM
        var alpha = ALPHA_FROM
        var age = 0f
    }

    /**
     * One point emitter covering the whole window, as the AndEngine ParticleSystem built by the
     * original: spawns between [RATE_MIN] and [RATE_MAX] stars per second, up to [MAX_PARTICLES] alive.
     */
    private class ParticleSystem(private val xFraction: Float) : UIComponent(), ISkinnable {

        private val particles = ArrayList<Particle>(MAX_PARTICLES)

        var isParticlesSpawnEnabled = false

        private var velocityXMin = 0f
        private var velocityXMax = 0f

        private var particlesDueToSpawn = 0f

        private var starTexture: GraphicsTexture.Region? = null

        /** Set when the skin region must be looked up again on the next update. */
        private var texturesInvalid = true

        /** Reused for every particle, the renderer reads it right away when drawing. */
        private val particleMatrix = Mat2()
        private val sceneMatrix = Mat2()

        init {
            width = fill()
            height = fill()
        }


        override fun onSkinChanged() {
            texturesInvalid = true
        }

        fun setVelocityX(min: Float, max: Float) {
            velocityXMin = min
            velocityXMax = max
        }

        override fun onUpdate(clock: TickingClock) {
            if (texturesInvalid) {
                texturesInvalid = false
                starTexture = SkinTextures.get("star2", "gfx/star2.png")
            }

            // Without a texture nothing could be spawned, as the original built no particle system.
            if (starTexture == null) return

            val dt = clock.elapsedTickTime
            val emitY = Config.getRES_HEIGHT().toFloat()
            val virtualWidth = Verktex.graphics.window.windowWidth / LegacyUnits.scale

            if (isParticlesSpawnEnabled) {
                particlesDueToSpawn += random(RATE_MIN, RATE_MAX) * dt

                val count = minOf(MAX_PARTICLES - particles.size, particlesDueToSpawn.toInt())
                particlesDueToSpawn -= count

                repeat(count) {
                    particles += Particle(
                        x = virtualWidth * xFraction,
                        y = emitY,
                        vx = random(velocityXMin, velocityXMax),
                        vy = random(VELOCITY_Y_MIN, VELOCITY_Y_MAX),
                        rotation = random(0f, 360f),
                    )
                }
            }

            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val particle = iterator.next()

                // PhysicsHandler: velocity first, then position.
                particle.vy += ACCELERATION_Y * dt
                particle.x += particle.vx * dt
                particle.y += particle.vy * dt
                particle.age += dt

                if (particle.age >= LIFETIME_SEC) {
                    iterator.remove()
                    continue
                }

                // Span modifiers only apply inside their time range and keep the last value after it.
                if (particle.age < SCALE_TO_TIME) {
                    particle.scale = SCALE_FROM + (SCALE_TO - SCALE_FROM) * particle.age / SCALE_TO_TIME
                }
                if (particle.age < ALPHA_TO_TIME) {
                    particle.alpha = ALPHA_FROM + (ALPHA_TO - ALPHA_FROM) * particle.age / ALPHA_TO_TIME
                }
            }
        }

        override fun onDraw(renderer: GraphicsRenderer) {
            val region = starTexture ?: return
            if (particles.isEmpty()) return

            val scale = LegacyUnits.scale
            val width = region.width * scale
            val height = region.height * scale

            for (particle in particles) {
                // Rotated and scaled around the sprite centre, the position is its top-left corner.
                particleMatrix.apply {
                    identity()
                    translate(-width / 2f, -height / 2f)
                    scale(particle.scale, particle.scale)
                    rotate(particle.rotation)
                    translate(particle.x * scale + width / 2f, particle.y * scale + height / 2f)
                }
                sceneMatrix.set(localToSceneMatrix)
                sceneMatrix.concat(particleMatrix)

                renderer.pushMatrix(sceneMatrix)
                renderer.drawTexture(region.texture, region, 0f, 0f, width, height, color = Color4(1f, 1f, 1f, particle.alpha))
                renderer.popMatrix()
            }
        }

        private fun random(min: Float, max: Float) =
            if (min == max) min else min + Random.nextFloat() * (max - min)
    }

    companion object {
        /** Burst duration*/
        const val SHOOT_DURATION_SEC = 0.8f

        /** Peak X velocity magnitude*/
        const val SWEEP_X_VEL = 500f

        /** Per-particle X random variance */
        const val X_VARIANCE = 60f

        /** Minimum ms between shoot sound retriggering */
        private const val SHOOT_RETRIGGER_MS = 500

        // Emitter positions, as fractions of the window width, at the bottom edge.
        private const val EMITTER_LEFT_X = 0.25f
        private const val EMITTER_RIGHT_X = 0.75f

        // ParticleSystem(rateMin = 32, rateMax = 48, maximum = 128)
        private const val RATE_MIN = 32f
        private const val RATE_MAX = 48f
        private const val MAX_PARTICLES = 128

        // VelocityParticleInitializer Y range, AccelerationParticleInitializer(0, 150)
        private const val VELOCITY_Y_MIN = -480f
        private const val VELOCITY_Y_MAX = -520f
        private const val ACCELERATION_Y = 150f

        // ScaleParticleModifier(0s, 0.8s, 0.5, 2.2)
        private const val SCALE_TO_TIME = 0.8f
        private const val SCALE_FROM = 0.5f
        private const val SCALE_TO = 2.2f

        // AlphaParticleModifier(0s, 1s, 1, 0)
        private const val ALPHA_TO_TIME = 1f
        private const val ALPHA_FROM = 1f
        private const val ALPHA_TO = 0f

        // ExpireParticleInitializer(0.9s)
        private const val LIFETIME_SEC = 0.9f
    }
}
