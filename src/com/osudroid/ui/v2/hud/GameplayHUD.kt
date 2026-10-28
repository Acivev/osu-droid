package com.osudroid.ui.v2.hud

import android.graphics.PointF
import com.osudroid.resources.R.string
import com.osudroid.ui.v2.hud.elements.HUDAccuracyCounter
import com.osudroid.ui.v2.hud.elements.HUDPieSongProgress
import com.osudroid.ui.v2.hud.elements.HUDScoreCounter
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.framework.forEachTolerant
import com.osudroid.ui.v2.hud.editor.HUDElementSelector
import com.reco1l.osu.ui.MessageDialog
import com.osudroid.utils.updateThread
import com.osudroid.beatmaps.constants.HitObjectType
import com.osudroid.beatmaps.hitobjects.HitObject
import org.json.JSONObject
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.ToastLogger
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.scoring.*
import ru.nsu.ccfit.zuev.skins.OsuSkin
import ru.nsu.ccfit.zuev.skins.SkinJsonReader
import java.io.File
import kotlin.reflect.full.primaryConstructor
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import kotlin.reflect.*

class GameplayHUD : UIContainer(), IGameplayEvents {

    /**
     * The currently selected element.
     */
    var selected: HUDElement? = null
        set(value) {
            if (field != value) {
                field = value
                forEachElement { it.onSelectionStateChange(it == value) }

                // Move to front
                if (value != null) {
                    if (value.parent == this) value.moveToFront()

                    // Preserve overlay on top of the element.
                    if (value.editorOverlay?.parent == this) value.editorOverlay!!.moveToFront()

                    elementSelector?.collapse()
                }
            }
        }

    /**
     * The element selector used in edit mode.
     */
    var elementSelector: HUDElementSelector? = null
        private set


    private var isInEditMode = false

    /**
     * The AndEngine `setIgnoreUpdate`: while true the HUD does not update, see [GameplayLayer.setHudPaused].
     */
    var ignoreUpdate = false

    /**
     * Whether the AndEngine scene is still the game scene; the HUD removes itself once it is not.
     */
    var sceneCheck: () -> Boolean = {
        GlobalManager.getInstance().engine.scene == GlobalManager.getInstance().gameScene?.scene
    }

    /**
     * The size in virtual units, the AndEngine camera HUD's.
     */
    val size
        get() = Vec2(Config.getRES_WIDTH().toFloat(), Config.getRES_HEIGHT().toFloat())


    init {
        width = fill()
        height = fill()
    }


    override fun onUpdate(clock: TickingClock) {
        if (ignoreUpdate) {
            return
        }

        if (!sceneCheck()) {
            // Detach on the next tick to ensure that parent's entity list stays stable throughout this tick.
            updateThread { Verktex.withTreeLock { removeSelf() } }
            return
        }

        super.onUpdate(clock)
    }


    /**
     * Checks if the HUD has an element of the specified type.
     */
    fun hasElement(type: KClass<out HUDElement>): Boolean {
        return any { type.isInstance(it) }
    }

    /**
     * Adds an element to the HUD.
     */
    fun addElement(data: HUDElementSkinData, inEditMode: Boolean = isInEditMode) {
        val element = data.type.primaryConstructor!!.call()

        // In edit mode, all elements should be shown.
        if (!inEditMode && !element.shouldBeShown) {
            return
        }

        this += element
        element.restoreData = data
        element.setSkinData(data)
        element.setEditMode(inEditMode)
    }

    /**
     * Called when the back button is pressed.
     */
    fun onBackPress() {

        if (elementSelector?.isExpanded == true) {
            elementSelector?.collapse()
            return
        }

        MessageDialog()
            .setTitle(StringTable.get(string.hudEditor_modal_title))
            .addButton(StringTable.get(string.hudEditor_modal_save)) {
                it.dismiss()
                updateThread {
                    ToastLogger.showText(string.hudEditor_saving, true)
                    // The layout is read under the tree lock, the file is written outside it.
                    val data = Verktex.withTreeLock {
                        setEditMode(false)
                        getSkinData()
                    }
                    saveToSkinJSON(data)
                    ToastLogger.showText(string.hudEditor_saved, true)
                }
            }
            .addButton(StringTable.get(string.hudEditor_modal_discard)) {
                it.dismiss()
                updateThread {
                    Verktex.withTreeLock { setSkinData(OsuSkin.get().hudSkinData) }
                    ToastLogger.showText(string.hudEditor_discarded, true)
                }
            }
            .addButton(StringTable.get(string.hudEditor_modal_reset)) {
                it.dismiss()
                updateThread {
                    Verktex.withTreeLock { setSkinData(HUDSkinData.Default) }
                    ToastLogger.showText(string.hudEditor_reset, true)
                }
            }
            .addButton(StringTable.get(string.hudEditor_modal_cancel)) { it.dismiss() }
            .show()
    }


    //region Skinning

    /**
     * Saves the current HUD layout to the skin JSON file.
     */
    fun saveToSkinJSON(data: HUDSkinData = getSkinData()) {

        val jsonFile = File(Config.getSkinPath(), "skin.json")
        val json: JSONObject

        if (jsonFile.exists()) {
            json = JSONObject(jsonFile.reader().readText())
        } else {
            jsonFile.createNewFile()

            // We use the current skin data as a base to avoid losing any other skin data.
            json = SkinJsonReader.getReader().currentData
        }

        json.put("HUD", HUDSkinData.writeToJSON(data))
        jsonFile.writeText(json.toString(4))

        SkinJsonReader.getReader().currentData = json
        OsuSkin.get().hudSkinData = data
    }

    /**
     * Obtains the skin data of the HUD.
     */
    fun getSkinData(): HUDSkinData {
        return HUDSkinData(
            elements = filterIsInstance<HUDElement>()
                .map { it.getSkinData() }
        )
    }

    /**
     * Sets the skin data of the HUD.
     */
    fun setSkinData(layoutData: HUDSkinData) {
        selected = null
        filterIsInstance<HUDElement>().forEach(HUDElement::detachSelf)

        // First pass: We attach everything so that elements can reference between them when
        // applying default layout.
        layoutData.elements.forEach { data -> addElement(data) }

        if (layoutData == HUDSkinData.Default) {
            applyDefaultLayout()
        }
    }

    private inline fun <reified T : HUDElement>getFirstOf() : T? {
        return firstOrNull { it is T } as? T
    }

    private fun applyDefaultLayout() {
        // The default layout is hardcoded to keep the original layout before the HUD editor was
        // implemented, as it used cross-references between elements that are not possible to be
        // set in the editor.
        val scoreCounter = getFirstOf<HUDScoreCounter>()
        val accuracyCounter = getFirstOf<HUDAccuracyCounter>()
        val pieSongProgress = getFirstOf<HUDPieSongProgress>()

        accuracyCounter?.let { it.setPosition(it.position.x, it.position.y + (scoreCounter?.position?.y ?: 0f) + (scoreCounter?.size?.y ?: 0f)) }

        pieSongProgress?.let { it.setPosition(it.position.x, (accuracyCounter?.position?.y ?: 0f) + (accuracyCounter?.let { a -> a.size.y * a.scaleY } ?: 0f) / 2f) }
        pieSongProgress?.let { it.setPosition((accuracyCounter?.position?.x ?: 0f) - (accuracyCounter?.let { a -> a.size.x * a.scaleX } ?: 0f) - 18f, it.position.y) }

        accuracyCounter?.restoreData = accuracyCounter.getSkinData()
        pieSongProgress?.restoreData = pieSongProgress.getSkinData()
    }

    //endregion

    //region Elements events

    private fun loadEditModeAssets() {
        SkinTextures.getAsset("delete", "delete.png")
        SkinTextures.getAsset("restore", "restore.png")
    }

    /**
     * Sets the editor mode of the HUD.
     */
    fun setEditMode(value: Boolean) {
        isInEditMode = value
        GlobalManager.getInstance().gameScene.isHUDEditorMode = value

        if (value) {
            loadEditModeAssets()

            elementSelector = HUDElementSelector(this)
        } else {
            elementSelector?.removeSelf()
            elementSelector = null
        }


        // Cannot use forEachElement {} because we're modifying the list.
        filterIsInstance<HUDElement>().forEach { it.setEditMode(value) }
    }

    override fun onAttached() {
        if (isInEditMode) {
            (parent as? GameplayLayer)?.plusAssign(elementSelector!!)
        }
        super.onAttached()
    }

    //endregion

    //region Gameplay Events

    /**
     * Iterates over all the elements in the HUD.
     *
     * Note: If you need to remove elements you have to copy the list temporarily
     * using another method such as `filterIsInstance<HUDElement>()`.
     */
    fun forEachElement(action: (HUDElement) -> Unit) {
        // `action` may reorder `children` (e.g. selecting an  element moves it to front), which
        // would cause a `ConcurrentModificationException` if we used a regular `forEach`.
        children.forEachTolerant { (it as? HUDElement)?.let(action) }
    }

    override fun onGameplayUpdate(gameScene: GameScene, secondsElapsed: Float) {
        forEachElement { it.onGameplayUpdate(gameScene, secondsElapsed) }
        elementSelector?.onGameplayUpdate(gameScene, secondsElapsed)
    }

    override fun onGameplayTouchDown(time: Float) {
        forEachElement { it.onGameplayTouchDown(time) }
        elementSelector?.onGameplayTouchDown(time)
    }

    override fun onHitObjectLifetimeStart(obj: HitObject) {
        forEachElement { it.onHitObjectLifetimeStart(obj) }
        elementSelector?.onHitObjectLifetimeStart(obj)
    }

    override fun onNoteHit(statistics: StatisticV2) {
        forEachElement { it.onNoteHit(statistics) }
        elementSelector?.onNoteHit(statistics)
    }

    override fun onBreakStateChange(isBreak: Boolean) {
        forEachElement { it.onBreakStateChange(isBreak) }
        elementSelector?.onBreakStateChange(isBreak)
    }

    override fun onAccuracyRegister(type: HitObjectType, accuracy: Float) {
        forEachElement { it.onAccuracyRegister(type, accuracy) }
        elementSelector?.onAccuracyRegister(type, accuracy)
    }

    override fun onAimJudgement(objectPosition: PointF, cursorPosition: PointF, objectRadius: Float) {
        forEachElement { it.onAimJudgement(objectPosition, cursorPosition, objectRadius) }
        elementSelector?.onAimJudgement(objectPosition, cursorPosition, objectRadius)
    }

    override fun onSeek() {
        forEachElement { it.onSeek() }
        elementSelector?.onSeek()
    }

    //endregion

    override fun onInputEvent(event: InputEvent): Boolean {

        // Ignoring touches on the element selector area when expanded.
        if (event is PointerEvent && elementSelector?.isExpanded == true) {
            val localX = toLocalSpaceFromScene(event.x, event.y).x

            if (localX <= elementSelector!!.bounds.width) {
                return false
            }
        }

        if (!super.onInputEvent(event)) {
            if (event is PointerEvent && event.action == PointerEvent.Action.Down) {
                selected = null
            }
            return false
        }
        return true
    }
}


