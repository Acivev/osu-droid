package com.osudroid.ui.v2.hud

import android.graphics.PointF
import com.osudroid.ui.v2.hud.elements.*
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.*
import com.osudroid.ui.v2.hud.editor.HUDElementOverlay
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.fadeTo
import com.osudroid.beatmaps.constants.HitObjectType
import com.osudroid.beatmaps.hitobjects.HitObject
import kotlin.math.abs
import kotlin.reflect.KClass
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2


/**
 * List all the elements that can be added to the HUD.
 */
@Suppress("EnumEntryName")
enum class HUDElements(val type: KClass<out HUDElement>) {

    accuracy_counter(HUDAccuracyCounter::class),
    combo_counter(HUDComboCounter::class),
    health_bar(HUDHealthBar::class),
    pie_song_progress(HUDPieSongProgress::class),
    pp_counter(HUDPPCounter::class),
    score_counter(HUDScoreCounter::class),
    ur_counter(HUDUnstableRateCounter::class),
    avg_offset_counter(HUDAverageOffsetCounter::class),
    hit_error_meter(HUDBarHitErrorMeter::class),
    color_hit_error_meter(HUDColorHitErrorMeter::class),
    aim_error_meter(HUDAimErrorMeter::class),
    linear_song_progress(HUDLinearSongProgress::class),
    great_counter(HUDGreatCounter::class),
    ok_counter(HUDOkCounter::class),
    meh_counter(HUDMehCounter::class),
    miss_counter(HUDMissCounter::class),
    bpm_counter(HUDBPMCounter::class),
    notes_per_second_counter(HUDNotesPerSecondCounter::class),
    taps_per_second_counter(HUDTapsPerSecondCounter::class),
    back_button(HUDBackButton::class),
    leaderboard(HUDLeaderboard::class);

    companion object {
        operator fun get(type: KClass<out HUDElement>) = entries.first { it.type == type }
        operator fun get(name: String) = entries.find { it.name == name }
    }
}


abstract class HUDElement : UIContainer(), IGameplayEvents {

    /**
     * Returns the name of this element.
     */
    open val name
        get() = HUDElements[this::class].name.replace('_', ' ').capitalize()

    /**
     * Indicates whether the element is selected.
     */
    val isSelected
        get() = (parent as? GameplayHUD)?.selected == this

    /**
     * Whether this [HUDElement] should be shown.
     */
    open val shouldBeShown = !Config.isHideInGameUI()

    /**
     * The overlay for this element to be used in edit mode.
     */
    var editorOverlay: HUDElementOverlay? = null

    /**
     * The restore data of the element.
     */
    var restoreData: HUDElementSkinData? = null


    protected var isInEditMode = false
        private set


    private var connectionLine: UILine? = null


    //region Legacy units

    /**
     * The position in virtual units, the AndEngine `x` and `y`. The Verktex [x] and [y] follow it.
     */
    var position = Vec2.Zero
        set(value) {
            field = value
            x = value.x.vu.px
            y = value.y.vu.px
        }

    /**
     * The size in virtual units without the scale, the AndEngine `width` and `height`. It is measured
     * on demand because the AndEngine size was known before the first layout pass.
     */
    val size: Vec2
        get() {
            if (measureInvalid) {
                measure(UNBOUNDED)
            }
            return measuredSize / LegacyUnits.scale
        }

    /**
     * The position of the anchor in the parent, in virtual units.
     */
    val anchorPosition: Vec2
        get() = parentSize * anchor

    private val parentSize: Vec2
        get() = when (val parent = parent) {
            is GameplayHUD -> parent.size
            null -> Vec2.Zero
            else -> parent.bounds.let { Vec2(it.width, it.height) } / LegacyUnits.scale
        }

    /**
     * The AndEngine background of the element, drawn behind its children over its whole size.
     * It is not a child, so it is animated by the modifier registry while unattached.
     */
    protected var background: UIBox? = null

    fun setPosition(x: Float, y: Float) {
        position = Vec2(x, y)
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        background?.let { renderer.drawRect(0f, 0f, bounds.width, bounds.height, color = it.color) }
        super.onDraw(renderer)
    }

    //endregion

    //region Skinning

    /**
     * Sets the skin data of the element.
     */
    open fun setSkinData(data: HUDElementSkinData?) {
        if (data == null) {
            return
        }

        anchor = data.anchor
        origin = data.origin
        // The AndEngine origin was also the scale and rotation center.
        scaleOrigin = data.origin
        rotationOrigin = data.origin
        setElementScale(data.scale)
        setPosition(data.position.x, data.position.y)

        // When the element is restored it's usually selected so we need to update the connection line.
        if (isSelected) {
            editorOverlay?.updateOutline()
            updateConnectionLine()
        }
    }

    /**
     * Creates a [HUDElementSkinData] object with the current element's skin data.
     */
    open fun getSkinData() = HUDElementSkinData(
        type = this::class,
        anchor = anchor,
        origin = origin,
        scale = (scaleX + scaleY) / 2f,
        position = position
    )

    /**
     * Restores the element to its original state before any changes were made.
     */
    fun restore() {
        setSkinData(restoreData)
    }

    //endregion

    //region Element events

    /**
     * Sets the edit mode of the element.
     */
    open fun setEditMode(value: Boolean) {
        isInEditMode = value

        if (value) {
            background = UIBox().apply {
                color = Color4(0x29F27272)
                alpha = 0.15f
            }
            editorOverlay = HUDElementOverlay(this)

            parent!! += editorOverlay!!
        } else {
            connectionLine?.removeSelf()
            editorOverlay?.removeSelf()

            connectionLine = null
            background = null
            editorOverlay = null
        }
    }

    /**
     * Called when the selection state of the element changes.
     */
    open fun onSelectionStateChange(isSelected: Boolean) {

        background?.clearModifiers()
        background?.fadeTo(if (isSelected) 0.5f else 0.15f, 0.1f)

        editorOverlay?.clearModifiers()
        editorOverlay?.fadeTo(if (isSelected) 1f else 0f, 0.1f)

        if (isSelected) {
            updateConnectionLine()
        } else {
            wasSelected = false
        }

        connectionLine?.clearModifiers()
        connectionLine?.fadeTo(if (isSelected) 1f else 0f, 0.1f)
    }

    //endregion

    //region Gameplay events

    override fun onGameplayUpdate(gameScene: GameScene, secondsElapsed: Float) {}

    override fun onGameplayTouchDown(time: Float) {}

    override fun onHitObjectLifetimeStart(obj: HitObject) {}

    override fun onNoteHit(statistics: StatisticV2) {}

    override fun onBreakStateChange(isBreak: Boolean) {}

    override fun onAccuracyRegister(type: HitObjectType, accuracy: Float) {}

    override fun onAimJudgement(objectPosition: PointF, cursorPosition: PointF, objectRadius: Float) {}

    override fun onSeek() {}

    //endregion

    //region Entity events

    private var initialX = 0f
    private var initialY = 0f

    private var wasSelected = false
    private var wasMoved = false

    // Verktex offers moves and releases to every child, AndEngine bound them to the pressed area.
    private var pressedPointerId = -1


    override fun onInputEvent(event: InputEvent): Boolean {

        if (isInEditMode && event is PointerEvent) {
            val hud = parent as GameplayHUD

            val parentLocal = hud.toLocalSpaceFromScene(event.x, event.y) / LegacyUnits.scale

            val parentLocalX = parentLocal.x
            val parentLocalY = parentLocal.y

            val deltaX = parentLocalX - initialX
            val deltaY = parentLocalY - initialY

            if (event.action == PointerEvent.Action.Down) {
                initialX = parentLocalX
                initialY = parentLocalY

                wasMoved = false
                wasSelected = hud.selected == this
                pressedPointerId = event.pointerId

                hud.selected = this
                return true
            }

            if (event.pointerId != pressedPointerId) {
                return false
            }

            if (event.action == PointerEvent.Action.Move) {
                // Preventing from moving the element if it's not selected.
                if (isSelected) {
                    move(deltaX, deltaY)
                    wasMoved = true

                    initialX = parentLocalX
                    initialY = parentLocalY
                    return true
                }
            }

            if (event.action == PointerEvent.Action.Up || event.action == PointerEvent.Action.Cancel) {
                pressedPointerId = -1

                if (event.action == PointerEvent.Action.Up && wasSelected && isSelected && !wasMoved) {
                    hud.forEachElement { element ->

                        if (element != this && element.containsScenePoint(event.x, event.y)) {
                            hud.selected = element
                            return@forEachElement
                        }
                    }
                }
                return true
            }
        }

        return false
    }

    //endregion

    //region Edit mode

    private fun applyClosestAnchorOrigin() {

        val drawSize = size * scale
        val drawPosition = anchorPosition + position - drawSize * origin
        val parentDrawSize = parentSize

        val relativeTopLeft = drawPosition / parentDrawSize
        val relativeTopRight = (drawPosition + Vec2(drawSize.x, 0f)) / parentDrawSize
        val relativeBottomRight = (drawPosition + drawSize) / parentDrawSize
        val relativeBottomLeft = (drawPosition + Vec2(0f, drawSize.y)) / parentDrawSize

        val closest = AnchorNames.all.minBy {
            minOf(
                abs(relativeTopLeft.distance(it)),
                abs(relativeTopRight.distance(it)),
                abs(relativeBottomRight.distance(it)),
                abs(relativeBottomLeft.distance(it))
            )
        }

        if (anchor != closest) {
            val previousAnchorOffset = anchorPosition
            anchor = closest
            position -= anchorPosition - previousAnchorOffset
        }

        if (origin != closest) {
            val previousOriginOffset = -(size * scale * origin)
            val originOffset = -(size * scale * closest)
            origin = closest
            scaleOrigin = closest
            rotationOrigin = closest
            position -= originOffset - previousOriginOffset
        }
    }

    private fun updateConnectionLine() {

        if (connectionLine == null) {
            connectionLine = UILine().apply {
                // The line draws in its own space, which covers the parent.
                width = fill()
                height = fill()
                color = Color4(0xFFF27272)
                lineWidth = 10f.px
                alpha = 0f
            }
            parent!! += connectionLine!!
        }

        connectionLine!!.fromPoint = anchorPosition * LegacyUnits.scale
        connectionLine!!.toPoint = ((editorOverlay?.outlinePosition ?: Vec2.Zero) + size * scale * origin) * LegacyUnits.scale
    }

    // The AndEngine setScale(Float) override; the name is taken by the Verktex scale setter.
    fun setElementScale(value: Float) {
        scale = value
        updateConnectionLine()
    }

    fun setScale(scaleX: Float, scaleY: Float) {
        this.scaleX = scaleX
        this.scaleY = scaleY
        updateConnectionLine()
    }

    fun detachSelf(): Boolean {
        editorOverlay?.removeSelf()
        connectionLine?.removeSelf()

        val hadParent = parent != null
        removeSelf()
        return hadParent
    }

    /**
     * Moves the element by the specified delta.
     */
    fun move(deltaX: Float, deltaY: Float) {
        setPosition(position.x + deltaX, position.y + deltaY)
        applyClosestAnchorOrigin()
        updateConnectionLine()
    }

    /**
     * Removes the element from the HUD.
     */
    fun remove() {
        setEditMode(false)
        detachSelf()
    }

    //endregion


    companion object {
        private val UNBOUNDED = LayoutConstraints(0f, Float.POSITIVE_INFINITY, 0f, Float.POSITIVE_INFINITY)
    }
}
