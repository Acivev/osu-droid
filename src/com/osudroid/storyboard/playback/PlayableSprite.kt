package com.osudroid.storyboard.playback

import android.util.Log
import com.osudroid.storyboard.model.AnimationLoopType
import com.osudroid.storyboard.model.StoryboardAnimation
import com.osudroid.storyboard.model.StoryboardColor
import com.osudroid.storyboard.model.StoryboardElement
import com.osudroid.storyboard.model.commands.CommandTimeline
import com.osudroid.storyboard.model.commands.StoryboardCommand
import com.osudroid.storyboard.model.commands.StoryboardCommandGroup
import com.osudroid.storyboard.model.commands.StoryboardTrigger
import com.osudroid.storyboard.model.commands.StoryboardTriggerType
import kotlin.math.min

/**
 * A [StoryboardElement] compiled for playback.
 *
 * Loops are unrolled into flat, per-property command timelines at construction time, so that the
 * state of the sprite at any point in time can be resolved deterministically with binary searches.
 * This makes seeking in both directions trivial. Triggers are the only stateful part - their
 * activations are tracked in [activations] and reset by [StoryboardPlayback] on backward seeks.
 */
class PlayableSprite(
    /**
     * The element this sprite plays back.
     */
    @JvmField
    val element: StoryboardElement,

    /**
     * The declaration index of this sprite within its layer, which determines the draw order.
     */
    @JvmField
    val declarationIndex: Int = 0
) {
    private val timelines = EffectiveTimelines(element)

    /**
     * The active trigger activations of this sprite.
     */
    private val activations = mutableListOf<TriggerActivation>()

    /**
     * The time at which this sprite starts affecting the scene, in milliseconds.
     *
     * If the earliest alpha command starts with an alpha of zero, the sprite only becomes visible
     * at that command's start time.
     */
    @JvmField
    val displayStartTime: Double

    /**
     * The time at which this sprite stops affecting the scene, in milliseconds.
     */
    @JvmField
    val endTime: Double

    /**
     * Whether this sprite has hit sound triggers.
     */
    val hasHitSoundTriggers
        get() = element.commands.triggers.any { it.type is StoryboardTriggerType.HitSound }

    // The evaluated state of this sprite at the time of the last update() call.
    @JvmField var x = element.initialX
    @JvmField var y = element.initialY
    @JvmField var scaleX = 1f
    @JvmField var scaleY = 1f
    @JvmField var rotation = 0f
    @JvmField var alpha = 1f
    @JvmField var red = 1f
    @JvmField var green = 1f
    @JvmField var blue = 1f
    @JvmField var flipHorizontal = false
    @JvmField var flipVertical = false
    @JvmField var additiveBlend = false
    @JvmField var frameIndex = 0

    init {
        var start = timelines.startTime
        var end = timelines.endTime

        for (trigger in element.commands.triggers) {
            start = min(start, trigger.triggerStartTime)
            end = maxOf(end, trigger.triggerEndTime + maxOf(trigger.commandsEndTime, 0.0))
        }

        val firstAlpha = timelines.alpha.startValue
        displayStartTime = if (firstAlpha == 0f) timelines.alpha.startTime else start
        endTime = end
    }

    /**
     * Whether this sprite affects the scene at the given time.
     *
     * @param time The time in milliseconds.
     */
    fun isActive(time: Double) = time in displayStartTime..endTime

    /**
     * Activates a trigger at the given time. An active activation with the same non-zero group
     * number is cancelled, matching osu!stable.
     *
     * @param trigger The trigger to activate.
     * @param time The activation time in milliseconds.
     */
    fun activate(trigger: StoryboardTrigger, time: Double) {
        if (trigger.groupNumber != 0) {
            activations.removeAll { it.trigger.groupNumber == trigger.groupNumber }
        }

        activations.add(TriggerActivation(trigger, time))
    }

    /**
     * Removes all trigger activations. Called on backward seeks to keep playback deterministic.
     */
    fun resetActivations() = activations.clear()

    /**
     * Evaluates the state of this sprite at the given time.
     *
     * @param time The time in milliseconds.
     */
    fun update(time: Double) {
        x = evaluate(timelines.x, time, element.initialX)
        y = evaluate(timelines.y, time, element.initialY)

        val scale = evaluate(timelines.scale, time, 1f)
        scaleX = scale * evaluate(timelines.vectorScaleX, time, 1f)
        scaleY = scale * evaluate(timelines.vectorScaleY, time, 1f)

        rotation = evaluate(timelines.rotation, time, 0f)
        alpha = evaluate(timelines.alpha, time, 1f)

        evaluateColorInto(timelines.color, time)

        flipHorizontal = evaluateBoolean(timelines.flipHorizontal, time)
        flipVertical = evaluateBoolean(timelines.flipVertical, time)
        additiveBlend = evaluateBoolean(timelines.additiveBlend, time)

        applyActivations(time)

        // In stable, alpha values exceeding 1 wrap around and make the sprite disappear.
        // Storyboarders exploit this for flicker effects, so it is reproduced here (as in lazer).
        if (alpha > 1f) {
            alpha %= 1f
        }

        val animation = element as? StoryboardAnimation
        if (animation != null) {
            frameIndex = animationFrameAt(animation, time)
        }
    }

    private fun applyActivations(time: Double) {
        // Fast path for the vast majority of sprites, which have no trigger activations. This
        // also avoids allocating the removeAll lambda in the per-frame update.
        if (activations.isEmpty()) {
            return
        }

        activations.removeAll { time < it.time }

        for (activation in activations) {
            val group = activation.trigger
            val relativeTime = time - activation.time

            if (group.x.hasCommands) x = evaluate(group.x, relativeTime, x)
            if (group.y.hasCommands) y = evaluate(group.y, relativeTime, y)

            if (group.scale.hasCommands || group.vectorScaleX.hasCommands || group.vectorScaleY.hasCommands) {
                val scale = evaluate(group.scale, relativeTime, 1f)
                scaleX = scale * evaluate(group.vectorScaleX, relativeTime, 1f)
                scaleY = scale * evaluate(group.vectorScaleY, relativeTime, 1f)
            }

            if (group.rotation.hasCommands) rotation = evaluate(group.rotation, relativeTime, rotation)
            if (group.alpha.hasCommands) alpha = evaluate(group.alpha, relativeTime, alpha)

            if (group.color.hasCommands) {
                evaluateColorInto(group.color, relativeTime)
            }

            if (group.flipHorizontal.hasCommands) flipHorizontal = evaluateBoolean(group.flipHorizontal, relativeTime)
            if (group.flipVertical.hasCommands) flipVertical = evaluateBoolean(group.flipVertical, relativeTime)
            if (group.additiveBlend.hasCommands) additiveBlend = evaluateBoolean(group.additiveBlend, relativeTime)
        }
    }

    private fun animationFrameAt(animation: StoryboardAnimation, time: Double): Int {
        if (animation.frameCount <= 1 || animation.frameDelay <= 0) {
            return 0
        }

        val frame = ((time - displayStartTime) / animation.frameDelay).toInt()

        return when (animation.loopType) {
            AnimationLoopType.LoopOnce -> frame.coerceIn(0, animation.frameCount - 1)
            AnimationLoopType.LoopForever -> ((frame % animation.frameCount) + animation.frameCount) % animation.frameCount
        }
    }

    private fun evaluate(timeline: CommandTimeline<Float>, time: Double, initialValue: Float): Float {
        if (!timeline.hasCommands) {
            return initialValue
        }

        val index = timeline.indexAt(time)

        if (index < 0) {
            // Before the first command, the property takes on the first command's start value.
            return timeline.startValue ?: initialValue
        }

        val command = timeline[index]
        val progress = command.progressAt(time).toFloat()

        return command.startValue + (command.endValue - command.startValue) * progress
    }

    /**
     * Evaluates a color timeline directly into [red], [green] and [blue] to avoid allocating a
     * color object per sprite per frame.
     */
    private fun evaluateColorInto(timeline: CommandTimeline<StoryboardColor>, time: Double) {
        if (!timeline.hasCommands) {
            red = 1f
            green = 1f
            blue = 1f
            return
        }

        val index = timeline.indexAt(time)

        if (index < 0) {
            val first = timeline.startValue
            red = first?.red ?: 1f
            green = first?.green ?: 1f
            blue = first?.blue ?: 1f
            return
        }

        val command = timeline[index]
        val progress = command.progressAt(time).toFloat()
        val start = command.startValue
        val end = command.endValue

        red = start.red + (end.red - start.red) * progress
        green = start.green + (end.green - start.green) * progress
        blue = start.blue + (end.blue - start.blue) * progress
    }

    private fun evaluateBoolean(timeline: CommandTimeline<Boolean>, time: Double): Boolean {
        if (!timeline.hasCommands) {
            return false
        }

        val index = timeline.indexAt(time)

        if (index < 0) {
            // Boolean properties default to false before their first command.
            return false
        }

        val command = timeline[index]

        // A P command is active during its interval. Zero-duration commands apply permanently,
        // which the parser encodes as an end value of `true`.
        return if (time < command.endTime) command.startValue else command.endValue
    }

    private class TriggerActivation(
        @JvmField val trigger: StoryboardTrigger,
        @JvmField val time: Double
    )

    /**
     * The flat command timelines of an element, with all loops unrolled into absolute time.
     */
    private class EffectiveTimelines(element: StoryboardElement) {
        val x = CommandTimeline<Float>()
        val y = CommandTimeline<Float>()
        val scale = CommandTimeline<Float>()
        val vectorScaleX = CommandTimeline<Float>()
        val vectorScaleY = CommandTimeline<Float>()
        val rotation = CommandTimeline<Float>()
        val alpha = CommandTimeline<Float>()
        val color = CommandTimeline<StoryboardColor>()
        val flipHorizontal = CommandTimeline<Boolean>()
        val flipVertical = CommandTimeline<Boolean>()
        val additiveBlend = CommandTimeline<Boolean>()

        private val all = arrayOf<CommandTimeline<*>>(
            x, y, scale, vectorScaleX, vectorScaleY, rotation, alpha, color,
            flipHorizontal, flipVertical, additiveBlend
        )

        val startTime
            get() = all.minOf { it.startTime }

        val endTime
            get() = all.maxOf { it.endTime }

        init {
            copyGroup(element.commands, 0.0)

            for (loop in element.commands.loops) {
                val commandsPerIteration = loop.timelines.sumOf { it.size }

                if (commandsPerIteration == 0) {
                    continue
                }

                // Guard against pathological storyboards whose unrolled loops would exhaust
                // memory. Excess iterations are dropped with a warning.
                val iterations = min(loop.totalIterations, MAX_UNROLLED_COMMANDS / commandsPerIteration)

                if (iterations < loop.totalIterations) {
                    Log.w(
                        "PlayableSprite",
                        "Loop of ${element.filePath} exceeds $MAX_UNROLLED_COMMANDS unrolled commands, " +
                            "dropping ${loop.totalIterations - iterations} iterations"
                    )
                }

                for (i in 0 until iterations) {
                    copyGroup(loop, loop.loopStartTime + i * loop.iterationDuration)
                }
            }
        }

        private fun copyGroup(group: StoryboardCommandGroup, offset: Double) {
            copyTimeline(group.x, x, offset)
            copyTimeline(group.y, y, offset)
            copyTimeline(group.scale, scale, offset)
            copyTimeline(group.vectorScaleX, vectorScaleX, offset)
            copyTimeline(group.vectorScaleY, vectorScaleY, offset)
            copyTimeline(group.rotation, rotation, offset)
            copyTimeline(group.alpha, alpha, offset)
            copyTimeline(group.color, color, offset)
            copyTimeline(group.flipHorizontal, flipHorizontal, offset)
            copyTimeline(group.flipVertical, flipVertical, offset)
            copyTimeline(group.additiveBlend, additiveBlend, offset)
        }

        private fun <T> copyTimeline(source: CommandTimeline<T>, target: CommandTimeline<T>, offset: Double) {
            for (command in source) {
                target.add(if (offset == 0.0) command else command.shifted(offset))
            }
        }

        companion object {
            private const val MAX_UNROLLED_COMMANDS = 100_000
        }
    }
}
