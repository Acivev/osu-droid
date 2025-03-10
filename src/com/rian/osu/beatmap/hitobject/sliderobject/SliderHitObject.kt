package com.rian.osu.beatmap.hitobject.sliderobject

import com.rian.osu.beatmap.hitobject.HitObject
import com.rian.osu.beatmap.hitobject.Slider
import com.rian.osu.math.Vector2

/**
 * Represents a hit object that can be nested into a [Slider].
 */
abstract class SliderHitObject(
    /**
     * The time at which this [SliderHitObject] starts, in milliseconds.
     */
    startTime: Double,

    /**
     * The position of this [SliderHitObject] relative to the play field.
     */
    position: Vector2
) : HitObject(startTime, position, false, 0)
