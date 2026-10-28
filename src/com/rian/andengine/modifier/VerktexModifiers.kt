@file:JvmName("VerktexModifiers")

package com.rian.andengine.modifier

import com.edlplan.framework.easing.Easing
import com.osudroid.math.Precision
import com.reco1l.framework.Color4
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.function.Consumer

//region Target

/**
 * What a [UniversalModifier] animates. Implemented by the reco1l
 * [com.reco1l.andengine.component.UIComponent] with its own members and, for Verktex components, by
 * [VerktexModifierTarget].
 *
 * The accessors are named after the AndEngine `Entity` members, so the reco1l component implements
 * most of them through what it inherits.
 */
interface ModifierTarget {

    /**
     * The current time of this target's time base in seconds, `null` while it has none.
     */
    val modifierTime: Float?

    /**
     * Starting time to use for new [UniversalModifier]s.
     */
    val modifierStartTime: Float

    /**
     * Appends a [UniversalModifier] to this target, which tracks it until it finishes.
     */
    fun appendModifier(modifier: UniversalModifier)

    fun getX(): Float
    fun getY(): Float
    fun setPosition(x: Float, y: Float)

    var translationX: Float
    var translationY: Float

    fun getScaleX(): Float
    fun getScaleY(): Float
    fun setScaleX(scaleX: Float)
    fun setScaleY(scaleY: Float)
    fun setScale(scaleX: Float, scaleY: Float)

    fun getAlpha(): Float
    fun setAlpha(alpha: Float)

    fun getRed(): Float
    fun getGreen(): Float
    fun getBlue(): Float
    fun setColor(red: Float, green: Float, blue: Float)

    fun getRotation(): Float
    fun setRotation(rotation: Float)

    var width: Float
    var height: Float
    fun setSize(width: Float, height: Float)

    /**
     * Removes this target from its parent.
     *
     * @return Whether it had a parent.
     */
    fun detachSelf(): Boolean
}

/**
 * Maps [ModifierTarget] onto a Verktex [UIComponent] and holds its modifier state. Obtained through
 * [modifierTarget], it lives in [ModifierRegistry] while one of its modifiers is active.
 *
 * Positions, translations and sizes are written as [Dimension.Fixed] pixels. A dimension that is not
 * fixed reads as what the layout made of it.
 */
class VerktexModifierTarget internal constructor(val component: UIComponent) : ModifierTarget {

    internal val trackers = mutableListOf<UniversalModifierTargetTracker>()

    /**
     * Delay from the current time until new [UniversalModifier]s are started, in seconds. Reset on
     * every tick.
     */
    var modifierDelay = 0f

    internal val isEmpty
        get() = trackers.all { it.modifiers.isEmpty() }

    /**
     * The topmost ancestor (or the component itself) seen last. Once it is no longer above the
     * component, the component or a subtree holding it was detached and the registry drops it, as
     * AndEngine stops updating a detached entity.
     */
    internal var top: UIComponent = ModifierRegistry.topOf(component)

    override val modifierTime: Float
        get() = ModifierRegistry.timeFor(component)

    override val modifierStartTime: Float
        get() = modifierTime + modifierDelay

    override fun appendModifier(modifier: UniversalModifier) {
        trackerFor(modifier.type, true)!!.add(modifier)
    }

    internal fun trackerFor(type: ModifierType, createIfNotExisting: Boolean = false): UniversalModifierTargetTracker? {
        for (i in trackers.indices) {
            val tracker = trackers[i]

            if (tracker.targetMember == type.targetMember) {
                return tracker
            }
        }

        if (!createIfNotExisting) {
            return null
        }

        return UniversalModifierTargetTracker(type.targetMember, this).also { trackers += it }
    }

    internal fun update(time: Float) {
        modifierDelay = 0f

        // A callback may start a modifier of another member, which appends a tracker.
        var i = 0
        while (i < trackers.size) {
            trackers[i].update(time)
            i++
        }
    }

    override fun getX() = component.x.resolve(assignedWidth())
    override fun getY() = component.y.resolve(assignedHeight())

    // An axis whose value does not change keeps its dimension, so moving one axis does not turn a
    // fraction on the other one into fixed pixels.
    override fun setPosition(x: Float, y: Float) {
        if (x != getX()) component.x = x.px
        if (y != getY()) component.y = y.px
    }

    override var translationX: Float
        get() = component.translationX.toPixels(component.bounds.width)
        set(value) { component.translationX = value.px }

    override var translationY: Float
        get() = component.translationY.toPixels(component.bounds.height)
        set(value) { component.translationY = value.px }

    override fun getScaleX() = component.scaleX
    override fun getScaleY() = component.scaleY

    override fun setScaleX(scaleX: Float) {
        component.scaleX = scaleX
    }

    override fun setScaleY(scaleY: Float) {
        component.scaleY = scaleY
    }

    override fun setScale(scaleX: Float, scaleY: Float) {
        component.scaleX = scaleX
        component.scaleY = scaleY
    }

    override fun getAlpha() = component.alpha

    override fun setAlpha(alpha: Float) {
        component.alpha = alpha
    }

    override fun getRed() = component.red
    override fun getGreen() = component.green
    override fun getBlue() = component.blue

    override fun setColor(red: Float, green: Float, blue: Float) {
        component.color = component.color.copy(red = red, green = green, blue = blue)
    }

    override fun getRotation() = component.rotationZ

    override fun setRotation(rotation: Float) {
        component.rotationZ = rotation
    }

    override var width: Float
        get() = (component.width as? Dimension.Fixed)?.toPixels() ?: component.measuredSize.x
        set(value) { component.width = value.px }

    override var height: Float
        get() = (component.height as? Dimension.Fixed)?.toPixels() ?: component.measuredSize.y
        set(value) { component.height = value.px }

    override fun setSize(width: Float, height: Float) {
        if (width != this.width) component.width = width.px
        if (height != this.height) component.height = height.px
    }

    override fun detachSelf(): Boolean {
        if (component.parent == null) {
            return false
        }

        component.removeSelf()
        return true
    }

    private fun Dimension.ConstraintDriven.toPixels(availableSize: Float?) = resolve(availableSize ?: 0f)

    // The size a plain container assigns to its children (its bounds minus its padding), which
    // layout resolves fractional positions against. Containers placing children themselves (linear,
    // constraint) assign other bounds, a fraction under them reads approximately.
    private fun assignedWidth(): Float {
        val parent = component.parent ?: return 0f
        return parent.bounds.width - parent.paddingLeft.toPixels() - parent.paddingRight.toPixels()
    }

    private fun assignedHeight(): Float {
        val parent = component.parent ?: return 0f
        return parent.bounds.height - parent.paddingTop.toPixels() - parent.paddingBottom.toPixels()
    }
}

//endregion

//region Registry

/**
 * A time base of its own for a subtree, bound with [ModifierRegistry.bindTicker]. Its components
 * are updated by [tick] instead of [ModifierRegistry.tick].
 */
class ModifierTicker {

    /**
     * Seconds accumulated by [tick]. Setting it moves the time to the given value and updates the
     * modifiers of the bound subtrees like [tick] does. Going back within a started modifier reapplies
     * it at that time; the trackers never rewind, so going back before its start does not undo it.
     */
    var time = 0f
        set(value) {
            field = value
            ModifierRegistry.update(this)
        }

    /**
     * Advances [time] and updates the modifiers of the subtrees bound to this ticker.
     */
    fun tick(deltaSeconds: Float) {
        time += deltaSeconds
    }
}

/**
 * Updates the modifiers of Verktex components. A component's state is created on first use and
 * dropped once it has no active modifier, or once it (or a subtree holding it) was detached from
 * the tree it was in (its modifiers are discarded without their callbacks). A component that was
 * never attached keeps ticking, as does one whose unattached tree gets attached somewhere.
 *
 * A paused subtree has its own time base: it stops while the subtree is paused and continues from
 * there on resume, as the clock of an AndEngine entity ignoring updates did.
 *
 * Everything here runs on the update thread under the tree lock, like the component tree it reads.
 */
object ModifierRegistry {

    private val targets = IdentityHashMap<UIComponent, VerktexModifierTarget>()

    /**
     * The time base of a subtree root marked with [setPaused]: while [paused] its local time stays at
     * [frozenAt], otherwise it is the time above it minus [offset], the total time it was paused.
     */
    private class PauseState {
        var paused = false
        var offset = 0f
        var frozenAt = 0f
    }

    private val pauseStates = WeakHashMap<UIComponent, PauseState>()

    private val chainStates = ArrayList<PauseState>()

    private val tickers = WeakHashMap<UIComponent, ModifierTicker>()

    private val updating = ArrayList<VerktexModifierTarget>()

    /**
     * Wall-clock seconds accumulated by [tick]; the time base of [modifierStartTime].
     */
    @JvmStatic
    var time = 0f
        private set

    /**
     * Advances [time], updates every registered component's trackers (skipping paused subtrees
     * and the ones bound to a [ModifierTicker]) and drops components whose trackers are empty.
     */
    @JvmStatic
    fun tick(deltaSeconds: Float) {
        time += deltaSeconds
        update(null)
    }

    /**
     * Marks or unmarks a subtree root: components with a marked ancestor (or marked themselves) are
     * not ticked and their time does not advance, so their modifiers continue where they stopped
     * once it is unmarked.
     */
    @JvmStatic
    fun setPaused(component: UIComponent, paused: Boolean) {
        val state = pauseStates[component]

        if (paused) {
            if (state?.paused == true) {
                return
            }

            val newState = state ?: PauseState().also { pauseStates[component] = it }
            newState.frozenAt = timeFor(component)
            newState.paused = true
        } else {
            if (state == null || !state.paused) {
                return
            }

            state.offset = timeFor(component, includeSelf = false) - state.frozenAt
            state.paused = false
        }
    }

    /**
     * Whether [component] or one of its ancestors is marked with [setPaused].
     */
    @JvmStatic
    fun isPaused(component: UIComponent): Boolean {
        if (pauseStates.isEmpty()) {
            return false
        }

        var current: UIComponent? = component
        while (current != null) {
            if (pauseStates[current]?.paused == true) {
                return true
            }
            current = current.parent
        }
        return false
    }

    /**
     * Number of components with at least one active modifier.
     */
    @JvmStatic
    fun activeCount() = targets.values.count { !it.isEmpty }

    /**
     * Drops the modifiers of [root] and of every component under it, without running their callbacks.
     * For a subtree that is discarded: one that was never attached keeps ticking otherwise, forever when
     * a modifier restarts itself.
     */
    @JvmStatic
    fun release(root: UIComponent) {
        val released = targets.values.filter { isStillUnder(it.component, root) }

        for (target in released) {
            target.trackers.forEach { it.clear() }
            remove(target)
        }
    }

    /**
     * Binds a subtree root to its own [ticker], `null` unbinds it. Unbound components use [tick].
     */
    @JvmStatic
    fun bindTicker(root: UIComponent, ticker: ModifierTicker?) {
        if (ticker != null) tickers[root] = ticker else tickers -= root
    }

    @JvmStatic
    fun targetFor(component: UIComponent): VerktexModifierTarget =
        targets.getOrPut(component) { VerktexModifierTarget(component) }

    internal fun existingTargetFor(component: UIComponent): VerktexModifierTarget? = targets[component]

    internal fun remove(target: VerktexModifierTarget) {
        if (targets[target.component] === target) {
            targets -= target.component
        }
    }

    /**
     * The time [component] sees: its ticker's time (or [time]) with the pause states of its
     * ancestors applied, outermost first. A pause state above the bound ticker is not applied.
     */
    internal fun timeFor(component: UIComponent, includeSelf: Boolean = true): Float {
        if (pauseStates.isEmpty() && tickers.isEmpty()) {
            return time
        }

        var base = time
        var current: UIComponent? = component

        while (current != null) {
            if (current !== component || includeSelf) {
                pauseStates[current]?.let { chainStates += it }
            }

            val ticker = tickers[current]
            if (ticker != null) {
                base = ticker.time
                break
            }

            current = current.parent
        }

        var result = base
        for (i in chainStates.indices.reversed()) {
            val state = chainStates[i]
            result = if (state.paused) state.frozenAt else result - state.offset
        }
        chainStates.clear()

        return result
    }

    internal fun topOf(component: UIComponent): UIComponent {
        var current = component
        while (true) {
            current = current.parent ?: return current
        }
    }

    private fun isStillUnder(component: UIComponent, top: UIComponent): Boolean {
        var current: UIComponent? = component
        while (current != null) {
            if (current === top) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun tickerFor(component: UIComponent): ModifierTicker? {
        if (tickers.isEmpty()) {
            return null
        }

        var current: UIComponent? = component
        while (current != null) {
            tickers[current]?.let { return it }
            current = current.parent
        }
        return null
    }

    internal fun update(ticker: ModifierTicker?) {
        // Callbacks may start modifiers on other components, so the map is not iterated directly.
        updating.addAll(targets.values)

        try {
            for (i in updating.indices) {
                val target = updating[i]
                val component = target.component

                if (!isStillUnder(component, target.top)) {
                    target.trackers.forEach { it.clear() }
                    remove(target)
                    continue
                }
                target.top = topOf(component)

                if (tickerFor(component) !== ticker || isPaused(component)) {
                    continue
                }

                target.update(timeFor(component))

                if (target.isEmpty) {
                    remove(target)
                }
            }
        } finally {
            updating.clear()
        }
    }
}

//endregion

//region Extensions

/**
 * This component's [VerktexModifierTarget], created on first use.
 */
val UIComponent.modifierTarget: VerktexModifierTarget
    get() = ModifierRegistry.targetFor(this)

private inline fun UIComponent.forEachChild(block: (UIComponent) -> Unit) {
    (this as? UIContainer)?.forEach(block)
}

private inline fun UIComponent.appendModifier(
    type: ModifierType,
    duration: Float,
    easing: Easing,
    block: UniversalModifier.() -> Unit
): UniversalModifier {
    val target = modifierTarget
    val modifier = UniversalModifier.GlobalPool.acquire() ?: UniversalModifier()

    modifier.target = target
    modifier.type = type
    modifier.startTime = target.modifierStartTime
    modifier.duration = duration
    modifier.eased(easing)
    modifier.block()

    target.appendModifier(modifier)

    return modifier
}

/**
 * Clears every [UniversalModifier] of this component.
 */
fun UIComponent.clearModifiers() {
    val target = ModifierRegistry.existingTargetFor(this) ?: return
    target.trackers.forEach { it.clear() }
    ModifierRegistry.remove(target)
}

/**
 * Clears [UniversalModifier]s of the specified [ModifierType] from this component.
 *
 * @param type The [ModifierType] to clear.
 * @param propagateChildren Whether to also clear [UniversalModifier]s of children. Defaults to `false`.
 */
@JvmOverloads
fun UIComponent.clearModifiers(type: ModifierType, propagateChildren: Boolean = false) {
    ModifierRegistry.existingTargetFor(this)?.trackers?.forEach { it.clear(type) }

    if (propagateChildren) {
        forEachChild { it.clearModifiers(type, true) }
    }
}

/**
 * Clears [UniversalModifier]s of the specified [ModifierType]s from this component.
 *
 * @param propagateChildren Whether to also clear [UniversalModifier]s of children. Defaults to `false`.
 * @param types The [ModifierType]s to remove.
 */
@JvmOverloads
fun UIComponent.clearModifiers(propagateChildren: Boolean = false, vararg types: ModifierType) {
    ModifierRegistry.existingTargetFor(this)?.trackers?.forEach { it.clear(*types) }

    if (propagateChildren) {
        forEachChild { it.clearModifiers(true, *types) }
    }
}

/**
 * Clears [UniversalModifier]s that start after [time].
 *
 * @param time The time to clear [UniversalModifier]s after.
 * @param propagateChildren Whether to also clear such [UniversalModifier]s of children. Defaults to `false`.
 */
@JvmOverloads
fun UIComponent.clearModifiersAfter(time: Float, propagateChildren: Boolean = false) {
    ModifierRegistry.existingTargetFor(this)?.trackers?.forEach { it.clearAfter(time) }

    if (propagateChildren) {
        forEachChild { it.clearModifiersAfter(time, true) }
    }
}

/**
 * Clears [UniversalModifier]s with the given [ModifierType] that start after [time].
 *
 * @param time The time to clear [UniversalModifier]s after.
 * @param type The [ModifierType] to clear.
 * @param propagateChildren Whether to also clear such [UniversalModifier]s of children. Defaults to `false`.
 */
@JvmOverloads
fun UIComponent.clearModifiersAfter(time: Float, type: ModifierType, propagateChildren: Boolean = false) {
    ModifierRegistry.existingTargetFor(this)?.trackers?.forEach { it.clearAfter(time, type) }

    if (propagateChildren) {
        forEachChild { it.clearModifiersAfter(time, type, true) }
    }
}

/**
 * Clears [UniversalModifier]s with the given [ModifierType]s that start after [time].
 *
 * @param time The time to clear [UniversalModifier]s after.
 * @param types The [ModifierType]s to clear.
 * @param propagateChildren Whether to also clear such [UniversalModifier]s of children. Defaults to `false`.
 */
@JvmOverloads
fun UIComponent.clearModifiersAfter(time: Float, propagateChildren: Boolean = false, vararg types: ModifierType) {
    ModifierRegistry.existingTargetFor(this)?.trackers?.forEach { it.clearAfter(time, *types) }

    if (propagateChildren) {
        forEachChild { it.clearModifiersAfter(time, true, *types) }
    }
}

/**
 * Finishes all [UniversalModifier]s, using their [UniversalModifier.finalValues] and calling their
 * [UniversalModifier.onFinished] callbacks.
 *
 * @param propagateChildren Whether to also finish [UniversalModifier]s of children. Defaults to `false`.
 * @param type The [ModifierType] to finish, or `null` to finish all [UniversalModifier]s regardless of their type.
 */
@JvmOverloads
fun UIComponent.finishModifiers(propagateChildren: Boolean = false, type: ModifierType? = null) {
    val target = ModifierRegistry.existingTargetFor(this)

    if (target != null) {
        if (type != null) {
            target.trackerFor(type)?.finish()
        } else {
            // A callback may append a modifier of another type, which appends a tracker.
            var i = 0
            while (i < target.trackers.size) {
                target.trackers[i].finish()
                i++
            }
        }
    }

    if (propagateChildren) {
        // A callback may detach its own component, the children are copied first.
        (this as? UIContainer)?.toList()?.forEach { it.finishModifiers(true, type) }
    }
}

/**
 * Starting time to use for new [UniversalModifier]s.
 */
val UIComponent.modifierStartTime: Float
    get() = ModifierRegistry.timeFor(this) + modifierDelay

/**
 * Delay from the current time until new [UniversalModifier]s are started, in seconds. **Public so
 * that [beginAbsoluteSequence] can be inlined, it should not be altered externally**.
 */
var UIComponent.modifierDelay: Float
    get() = ModifierRegistry.existingTargetFor(this)?.modifierDelay ?: 0f
    set(value) {
        modifierTarget.modifierDelay = value
    }

/**
 * Adds a delay duration to [modifierDelay], in seconds.
 *
 * @param duration The delay duration to add.
 * @param propagateChildren Whether we also delay down the child.
 */
@JvmOverloads
fun UIComponent.addDelay(duration: Float, propagateChildren: Boolean = false) {
    if (duration == 0f) {
        return
    }

    modifierDelay += duration

    if (propagateChildren) {
        forEachChild { it.addDelay(duration, true) }
    }
}

/**
 * Starts a sequence of [UniversalModifier]s. The block will be provided with a [UniversalModifierSequence] that can
 * be used to add [UniversalModifier]s.
 *
 * @param block The block to execute with the [UniversalModifierSequence] to add [UniversalModifier]s to.
 */
@JvmSynthetic
inline fun UIComponent.beginModifierSequence(crossinline block: UniversalModifierSequence.() -> Unit) =
    UniversalModifierSequence.obtain(modifierTarget).use { it.block() }

/**
 * Starts a sequence of [UniversalModifier]s. The block will be provided with a [UniversalModifierSequence] that can
 * be used to add [UniversalModifier]s.
 *
 * This is a Java-friendly overload of [beginModifierSequence] that accepts a [Consumer].
 *
 * @param block The block to execute with the [UniversalModifierSequence] to add [UniversalModifier]s to.
 */
fun UIComponent.beginModifierSequence(block: Consumer<UniversalModifierSequence>) =
    beginModifierSequence { block.accept(this) }

/**
 * Starts a sequence of [UniversalModifier]s from an absolute time value (adjusts [modifierStartTime]).
 *
 * @param newModifierStartTime The new value for [modifierStartTime].
 * @param propagateChildren Whether this should be applied to children. `true` by default.
 * @param block The block to execute with the [UniversalModifierSequence] to add [UniversalModifier]s to.
 */
@JvmSynthetic
inline fun UIComponent.beginAbsoluteSequence(
    newModifierStartTime: Float,
    propagateChildren: Boolean = true,
    crossinline block: UniversalModifierSequence.() -> Unit
) {
    val prevModifierStartTime = modifierStartTime

    adjustAbsoluteSequenceTime(newModifierStartTime, propagateChildren)

    try {
        beginModifierSequence(block)
    } finally {
        restoreAbsoluteSequenceTime(prevModifierStartTime, propagateChildren)
    }
}

/**
 * Starts a sequence of [UniversalModifier]s from an absolute time value (adjusts [modifierStartTime]).
 *
 * This is a Java-friendly overload of [beginAbsoluteSequence] that accepts a [Consumer].
 *
 * @param newModifierStartTime The new value for [modifierStartTime].
 * @param propagateChildren Whether this should be applied to children. `true` by default.
 * @param block The block to execute with the [UniversalModifierSequence] to add [UniversalModifier]s to.
 */
@JvmOverloads
fun UIComponent.beginAbsoluteSequence(
    newModifierStartTime: Float,
    propagateChildren: Boolean = true,
    block: Consumer<UniversalModifierSequence>
) = beginAbsoluteSequence(newModifierStartTime, propagateChildren) { block.accept(this) }

/**
 * Adjusts [modifierStartTime] to a new absolute time value. **This is used internally for [beginAbsoluteSequence]
 * but is made public for inlining, and should not be used externally**.
 *
 * @param newModifierStartTime The new [modifierStartTime].
 * @param propagateChildren Whether to propagate [newModifierStartTime] to children. Defaults to `true`.
 */
@JvmOverloads
fun UIComponent.adjustAbsoluteSequenceTime(newModifierStartTime: Float, propagateChildren: Boolean = true) {
    modifierDelay += newModifierStartTime - modifierStartTime

    if (propagateChildren) {
        forEachChild { it.adjustAbsoluteSequenceTime(newModifierStartTime) }
    }
}

/**
 * Restores [modifierStartTime] to its previous absolute time value. **This is used internally for
 * [beginAbsoluteSequence] but is made public for inlining, and should not be used externally**.
 *
 * @param prevModifierStartTime The previous [modifierStartTime].
 * @param propagateChildren Whether to propagate [prevModifierStartTime]. Defaults to `true`.
 */
@JvmOverloads
fun UIComponent.restoreAbsoluteSequenceTime(prevModifierStartTime: Float, propagateChildren: Boolean = true) {
    modifierDelay += prevModifierStartTime - modifierStartTime

    if (!Precision.almostEquals(prevModifierStartTime, modifierStartTime)) {
        throw IllegalStateException(
            "${this::class.simpleName}'s modifierStartTime at the end of absolute sequence is " +
                    "not the same as at the beginning (begin=$prevModifierStartTime end=$modifierStartTime)"
        )
    }

    if (propagateChildren) {
        forEachChild { it.restoreAbsoluteSequenceTime(prevModifierStartTime) }
    }
}

//endregion

//region Extensions - Shortcuts

/**
 * Smoothly adjusts this component's translation over time.
 *
 * @return The added [UniversalModifier].
 */
@JvmOverloads
fun UIComponent.translateTo(x: Float, y: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.TranslateXY, duration, easing) {
        finalValues[0] = x
        finalValues[1] = y
    }

@JvmOverloads
fun UIComponent.translateToX(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.TranslateX, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.translateToY(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.TranslateY, duration, easing) { finalValues[0] = value }

/**
 * Smoothly adjusts this component's position ([UIComponent.x], [UIComponent.y]) over time.
 *
 * @return The added [UniversalModifier].
 */
@JvmOverloads
fun UIComponent.moveTo(x: Float, y: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.MoveXY, duration, easing) {
        finalValues[0] = x
        finalValues[1] = y
    }

@JvmOverloads
fun UIComponent.moveToX(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.MoveX, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.moveToY(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.MoveY, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.scaleTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.ScaleXY, duration, easing) {
        finalValues[0] = value
        finalValues[1] = value
    }

@JvmOverloads
fun UIComponent.scaleToX(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.ScaleX, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.scaleToY(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.ScaleY, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.fadeTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Alpha, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.fadeIn(duration: Float = 0f, easing: Easing = Easing.None) = fadeTo(1f, duration, easing)

@JvmOverloads
fun UIComponent.fadeInFromZero(duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Alpha, duration, easing) {
        hasInitialValues = true
        initialValues[0] = 0f
        finalValues[0] = 1f
    }

@JvmOverloads
fun UIComponent.fadeOut(duration: Float = 0f, easing: Easing = Easing.None) = fadeTo(0f, duration, easing)

@JvmOverloads
fun UIComponent.fadeOutFromOne(duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Alpha, duration, easing) {
        hasInitialValues = true
        initialValues[0] = 1f
        finalValues[0] = 0f
    }

/**
 * Smoothly adjusts this component's color over time.
 *
 * @param color The final color, in 0xRRGGBB format.
 */
@JvmOverloads
fun UIComponent.colorTo(color: Long, duration: Float = 0f, easing: Easing = Easing.None) =
    colorTo(
        red = ((color ushr 16) and 0xFF) / 255f,
        green = ((color ushr 8) and 0xFF) / 255f,
        blue = (color and 0xFF) / 255f,
        duration = duration,
        easing = easing
    )

@JvmOverloads
fun UIComponent.colorTo(color: Color4, duration: Float = 0f, easing: Easing = Easing.None) =
    colorTo(color.red, color.green, color.blue, duration, easing)

@JvmOverloads
fun UIComponent.colorTo(red: Float, green: Float, blue: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Color, duration, easing) {
        finalValues[0] = red
        finalValues[1] = green
        finalValues[2] = blue
    }

/**
 * Smoothly adjusts this component's [UIComponent.rotationZ] over time, in degrees.
 */
@JvmOverloads
fun UIComponent.rotateTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Rotation, duration, easing) { finalValues[0] = value }

@JvmOverloads
fun UIComponent.sizeTo(width: Float, height: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Size, duration, easing) {
        finalValues[0] = width
        finalValues[1] = height
    }

@JvmOverloads
fun UIComponent.widthTo(width: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Width, duration, easing) { finalValues[0] = width }

@JvmOverloads
fun UIComponent.heightTo(height: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    appendModifier(ModifierType.Height, duration, easing) { finalValues[0] = height }

//endregion
