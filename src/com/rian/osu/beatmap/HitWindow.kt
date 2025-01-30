package com.rian.osu.beatmap

/**
 * Represents a hit window.
 */
abstract class HitWindow @JvmOverloads constructor(
    /**
     * The overall difficulty of this [HitWindow]. Defaults to 5.
     */
    @JvmField
    var overallDifficulty: Float = 5f
) {
    constructor(overallDifficulty: Float?) : this(overallDifficulty ?: 5f)

    /**
     * The hit window for 300 (Great) hit result.
     */
    abstract val greatWindow: Float

    /**
     * The hit window for 100 (OK) hit result.
     */
    abstract val okWindow: Float

    /**
     * The hit window for 50 (Meh) hit result.
     */
    abstract val mehWindow: Float

    companion object {
        /**
         * A fixed miss window regardless of difficulty settings.
         */
        const val MISS_WINDOW = 400f
    }
}