package com.osudroid.storyboard.model.commands

import com.osudroid.beatmaps.constants.SampleBank

/**
 * Represents the condition of a storyboard `T` (trigger) command.
 */
sealed class StoryboardTriggerType {
    /**
     * Activated while the player is in a passing state.
     */
    object Passing : StoryboardTriggerType()

    /**
     * Activated while the player is in a failing state.
     */
    object Failing : StoryboardTriggerType()

    /**
     * Activated when a hit sample matching the given constraints is played.
     *
     * The full trigger name follows the format
     * `HitSound[SampleSet][AdditionsSampleSet][Addition][CustomSampleSet]`, where every part is
     * optional.
     */
    class HitSound(
        /**
         * The [SampleBank] the sample must belong to, or `null` for any bank.
         */
        @JvmField
        val sampleBank: SampleBank?,

        /**
         * The [SampleBank] the addition sample must belong to, or `null` for any bank.
         */
        @JvmField
        val additionsSampleBank: SampleBank?,

        /**
         * The addition sample name (`hitwhistle`, `hitfinish` or `hitclap`) the sample must
         * match, or `null` for any hit sample.
         */
        @JvmField
        val addition: String?,

        /**
         * The custom sample bank index the sample must use, or `0` for any index.
         */
        @JvmField
        val customSampleBank: Int
    ) : StoryboardTriggerType() {
        /**
         * Determines whether a played hit sample matches this trigger.
         *
         * @param name The name of the sample (e.g. `hitclap`).
         * @param bank The [SampleBank] the sample was loaded from.
         * @param customSampleBank The custom sample bank index of the sample.
         */
        fun matches(name: String, bank: SampleBank, customSampleBank: Int): Boolean {
            if (addition != null && name != addition) {
                return false
            }

            // The bank of an addition sample is its additions sample bank, so both constraints
            // apply to the same value here.
            if (sampleBank != null && bank != sampleBank) {
                return false
            }

            if (additionsSampleBank != null && bank != additionsSampleBank) {
                return false
            }

            if (this.customSampleBank > 0 && customSampleBank != this.customSampleBank) {
                return false
            }

            return true
        }
    }

    companion object {
        private val TRAILING_DIGITS_REGEX = "(\\d+)$".toRegex()

        /**
         * Parses a trigger type from its name (e.g. `HitSoundSoftWhistle`, `Passing`).
         *
         * @param value The value to parse.
         * @return The parsed [StoryboardTriggerType], or `null` if the value is not a valid
         * trigger type.
         */
        @JvmStatic
        fun parse(value: String): StoryboardTriggerType? {
            if (value == "Passing") {
                return Passing
            }

            if (value == "Failing") {
                return Failing
            }

            if (!value.startsWith("HitSound")) {
                return null
            }

            var rest = value.removePrefix("HitSound")

            val customSampleBank = TRAILING_DIGITS_REGEX.find(rest)?.value?.let {
                rest = rest.removeSuffix(it)
                it.toIntOrNull()
            } ?: 0

            fun takeBank(): SampleBank? {
                for (bank in arrayOf(SampleBank.Normal, SampleBank.Soft, SampleBank.Drum)) {
                    if (rest.startsWith(bank.name)) {
                        rest = rest.removePrefix(bank.name)
                        return bank
                    }
                }

                if (rest.startsWith("All")) {
                    rest = rest.removePrefix("All")
                }

                return null
            }

            val sampleBank = takeBank()
            val additionsSampleBank = takeBank()

            val addition = when (rest) {
                "Whistle" -> "hitwhistle"
                "Finish" -> "hitfinish"
                "Clap" -> "hitclap"
                "" -> null
                // Unknown trailing text makes the whole trigger invalid.
                else -> return null
            }

            return HitSound(sampleBank, additionsSampleBank, addition, customSampleBank)
        }
    }
}
