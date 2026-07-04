package com.osudroid.storyboard.model

/**
 * Represents the layer a storyboard element is rendered on.
 *
 * The rendering order corresponds to the ordinal of this enum, with [Background] rendered first.
 */
enum class StoryboardLayerType {
    Background,

    /**
     * Only visible while the player is in a failing state.
     */
    Fail,

    /**
     * Only visible while the player is in a passing state.
     */
    Pass,

    Foreground,

    /**
     * Rendered above gameplay elements.
     */
    Overlay;

    companion object {
        /**
         * Parses a layer from its name or numeric representation.
         *
         * @param value The value to parse.
         * @return The parsed [StoryboardLayerType], or `null` if the value is not a valid layer.
         */
        @JvmStatic
        fun parse(value: String) = when (value) {
            "0", "Background" -> Background
            "1", "Fail" -> Fail
            "2", "Pass" -> Pass
            "3", "Foreground" -> Foreground
            "4", "Overlay" -> Overlay
            else -> null
        }
    }
}
