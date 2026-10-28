package com.rian.andengine.modifier

/**
 * A callback interface for when a [UniversalModifier] has finished and is about to be removed from a
 * [UniversalModifierTargetTracker].
 */
fun interface OnModifierFinished {
    operator fun invoke(component: ModifierTarget)
}