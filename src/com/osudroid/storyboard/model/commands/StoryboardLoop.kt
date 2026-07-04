package com.osudroid.storyboard.model.commands

/**
 * Represents a storyboard `L` (loop) command.
 *
 * The command times of this group are relative to [loopStartTime]. Consecutive iterations start at
 * `loopStartTime + iteration * commandsEndTime`, matching osu!stable, which restarts a loop at the
 * relative end time of its last command.
 */
class StoryboardLoop(
    /**
     * The absolute start time of the loop in milliseconds.
     */
    @JvmField
    val loopStartTime: Double,

    /**
     * The total number of times this loop plays. At least 1.
     */
    @JvmField
    val totalIterations: Int
) : StoryboardCommandGroup() {
    /**
     * The duration of a single iteration in milliseconds.
     */
    val iterationDuration
        get() = if (hasCommands) maxOf(commandsEndTime, 0.0) else 0.0

    /**
     * The absolute start time of the earliest command of the first iteration.
     */
    val startTime
        get() = loopStartTime + (if (hasCommands) commandsStartTime else 0.0)

    /**
     * The absolute end time of the last iteration.
     */
    val endTime
        get() = loopStartTime + iterationDuration * totalIterations
}
