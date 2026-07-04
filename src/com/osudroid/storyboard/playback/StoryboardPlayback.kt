package com.osudroid.storyboard.playback

import com.osudroid.beatmaps.constants.SampleBank
import com.osudroid.storyboard.model.Storyboard
import com.osudroid.storyboard.model.StoryboardLayerType
import com.osudroid.storyboard.model.commands.StoryboardTriggerType

/**
 * Drives the playback of a [Storyboard].
 *
 * Playback is deterministic: [update] evaluates the state of every active sprite at the given
 * time, and seeking in either direction is supported. Trigger activations are the only stateful
 * part and are reset when a backward seek is detected.
 *
 * To keep per-frame costs proportional to the number of *currently visible* sprites rather than
 * the total sprite count (heavy storyboards contain tens of thousands of elements), each layer
 * maintains an active window: sprites sorted by display start time are swept in as time advances
 * and pruned once their end time passes. Backward seeks rebuild the window from scratch.
 */
class StoryboardPlayback(
    /**
     * The storyboard to play back.
     */
    @JvmField
    val storyboard: Storyboard
) {
    /**
     * The compiled sprites of this storyboard, grouped per layer in declaration order.
     */
    @JvmField
    val layers = LinkedHashMap<StoryboardLayerType, List<PlayableSprite>>().also {
        for ((layer, elements) in storyboard.layers) {
            it[layer] = elements.mapIndexed { index, element -> PlayableSprite(element, index) }
        }
    }

    private val layerStates = layers.mapValues { (_, sprites) -> LayerState(sprites) }

    /**
     * All sprites that have triggers, across all layers.
     */
    private val spritesWithTriggers = layers.values.flatten().filter { it.element.commands.triggers.isNotEmpty() }

    /**
     * Whether any sprite of this storyboard has hit sound triggers. When `false`, hit sample
     * notifications can be skipped entirely.
     */
    @JvmField
    val hasHitSoundTriggers = spritesWithTriggers.any { it.hasHitSoundTriggers }

    /**
     * Whether the player is currently in a passing state, controlling the visibility of the
     * [Pass][StoryboardLayerType.Pass] and [Fail][StoryboardLayerType.Fail] layers.
     */
    var isPassing = true
        private set

    /**
     * The time of the last [update] call in milliseconds.
     */
    var currentTime = -Double.MAX_VALUE
        private set

    /**
     * The sprites of the given layer that are active at [currentTime], in declaration (draw)
     * order.
     *
     * @param layer The layer to get the active sprites of.
     */
    fun activeSprites(layer: StoryboardLayerType): List<PlayableSprite> =
        layerStates[layer]?.active ?: emptyList()

    /**
     * Advances the playback time and updates the active sprite windows without evaluating sprite
     * states. This is cheap and safe to call from the update thread every tick - the actual
     * (expensive) state evaluation happens per drawn sprite in the renderer, so that heavy
     * storyboards can never stall the gameplay clock.
     *
     * @param time The time in milliseconds.
     */
    fun setTime(time: Double) {
        if (time < currentTime) {
            // Backward seeks invalidate all trigger activations and rebuild the active windows
            // to keep playback deterministic.
            for (sprite in spritesWithTriggers) {
                sprite.resetActivations()
            }

            for (state in layerStates.values) {
                state.reset()
            }
        }

        currentTime = time

        for (state in layerStates.values) {
            state.sweep(time)
        }
    }

    /**
     * Advances the playback time and evaluates the state of all active sprites on visible
     * layers. Prefer [setTime] plus per-sprite evaluation during drawing in production - this is
     * primarily a convenience for tests.
     *
     * @param time The time in milliseconds.
     */
    fun update(time: Double) {
        setTime(time)

        for ((layer, state) in layerStates) {
            if (!isLayerVisible(layer)) {
                continue
            }

            val active = state.active

            for (i in active.indices) {
                active[i].update(time)
            }
        }
    }

    /**
     * Whether the given layer is currently visible.
     *
     * @param layer The layer to check.
     */
    fun isLayerVisible(layer: StoryboardLayerType) = when (layer) {
        StoryboardLayerType.Pass -> isPassing
        StoryboardLayerType.Fail -> !isPassing
        else -> true
    }

    /**
     * Notifies this playback that a hit sample was played, activating matching hit sound triggers
     * whose window contains the given time.
     *
     * @param name The name of the sample (e.g. `hitclap`).
     * @param bank The [SampleBank] the sample was loaded from.
     * @param customSampleBank The custom sample bank index of the sample.
     * @param time The time the sample was played at, in milliseconds.
     */
    fun onHitSound(name: String, bank: SampleBank, customSampleBank: Int, time: Double) {
        if (!hasHitSoundTriggers) {
            return
        }

        for (sprite in spritesWithTriggers) {
            for (trigger in sprite.element.commands.triggers) {
                val type = trigger.type as? StoryboardTriggerType.HitSound ?: continue

                if (time in trigger.triggerStartTime..trigger.triggerEndTime &&
                    type.matches(name, bank, customSampleBank)) {
                    sprite.activate(trigger, time)
                }
            }
        }
    }

    /**
     * Updates the passing state, activating `Passing`/`Failing` triggers whose window contains
     * the given time when the state changes.
     *
     * @param passing Whether the player is in a passing state.
     * @param time The time of the state change in milliseconds.
     */
    fun setPassing(passing: Boolean, time: Double) {
        if (isPassing == passing) {
            return
        }

        isPassing = passing

        for (sprite in spritesWithTriggers) {
            for (trigger in sprite.element.commands.triggers) {
                val matches = when (trigger.type) {
                    is StoryboardTriggerType.Passing -> passing
                    is StoryboardTriggerType.Failing -> !passing
                    else -> false
                }

                if (matches && time in trigger.triggerStartTime..trigger.triggerEndTime) {
                    sprite.activate(trigger, time)
                }
            }
        }
    }

    /**
     * The active sprite window of a layer.
     */
    private class LayerState(sprites: List<PlayableSprite>) {
        /**
         * The sprites of the layer sorted by display start time, used to sweep sprites into the
         * active window as time advances.
         */
        private val spritesByStartTime = sprites.sortedBy { it.displayStartTime }

        /**
         * The sprites that are active at the current time, in declaration (draw) order.
         */
        val active = ArrayList<PlayableSprite>()

        private var nextIndex = 0

        fun reset() {
            nextIndex = 0
            active.clear()
        }

        fun sweep(time: Double) {
            while (nextIndex < spritesByStartTime.size &&
                spritesByStartTime[nextIndex].displayStartTime <= time) {
                val sprite = spritesByStartTime[nextIndex]
                ++nextIndex

                if (time <= sprite.endTime) {
                    insert(sprite)
                }
            }

            var removed = 0

            for (i in active.indices) {
                val sprite = active[i]

                if (time > sprite.endTime) {
                    ++removed
                } else if (removed > 0) {
                    active[i - removed] = sprite
                }
            }

            if (removed > 0) {
                active.subList(active.size - removed, active.size).clear()
            }
        }

        /**
         * Inserts a sprite into [active], keeping the list sorted by declaration index so that
         * the draw order matches the file order.
         */
        private fun insert(sprite: PlayableSprite) {
            var low = 0
            var high = active.size

            while (low < high) {
                val mid = (low + high) ushr 1

                if (active[mid].declarationIndex < sprite.declarationIndex) {
                    low = mid + 1
                } else {
                    high = mid
                }
            }

            active.add(low, sprite)
        }
    }
}
