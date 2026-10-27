package com.osudroid.ui.v2.modmenu

import com.osudroid.GameMode
import com.osudroid.beatmaps.BeatmapCache
import com.osudroid.data.BeatmapInfo
import com.osudroid.data.DatabaseManager
import com.osudroid.multiplayer.api.RoomAPI.setPlayerMods
import com.osudroid.multiplayer.api.RoomAPI.setRoomMods
import com.osudroid.multiplayer.api.data.RoomMods
import com.osudroid.multiplayer.Multiplayer
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.ModsIndicator
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.ui.verktex.StarRatingBadge
import com.osudroid.ui.verktex.times
import com.osudroid.utils.ModHashMap
import com.osudroid.utils.async
import com.osudroid.utils.updateThread
import com.osudroid.difficulty.BeatmapDifficultyCalculator.calculateDroidDifficulty
import com.osudroid.difficulty.BeatmapDifficultyCalculator.calculateStandardDifficulty
import com.osudroid.mods.*
import com.osudroid.scoring.ScoreMultiplierCalculator
import com.osudroid.utils.allModsInstances
import com.osudroid.utils.applyMods
import com.osudroid.utils.calculateRate
import com.osudroid.verktex.bridge.EngineRouter
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.input.ScrollEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIBadge
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UIScrollableContainer
import java.io.IOException
import kotlinx.coroutines.*
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.osu.DifficultyAlgorithm.*
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import java.util.LinkedList
import java.util.concurrent.CancellationException
import kotlin.math.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object ModMenu : UIContainer() {


    /**
     * List of currently enabled mods.
     */
    val enabledMods = ModHashMap()

    /**
     * List of all mod sections.
     */
    val modSections = mutableListOf<ModMenuSection>()

    /**
     * List of all mod toggles.
     */
    val modToggles = mutableListOf<ModMenuToggle>()

    /**
     * Whether the mod menu is shown.
     */
    @JvmStatic
    val isShown: Boolean
        get() = EngineRouter.isOverlayShown(this)


    private val modChangeQueue = LinkedList<Mod>()
    private val modPresetsSection: ModMenuPresetsSection

    private val customizeButton: LegacyTextButton
    private val customizationMenu: ModCustomizationMenu
    private val selectedModsIndicator: ModsIndicator
    private val searchInput: ModMenuSearchInput

    private val rankedBadge: RankedBadge
    private val arBadge: StatisticsBadge
    private val odBadge: StatisticsBadge
    private val csBadge: StatisticsBadge
    private val hpBadge: StatisticsBadge
    private val bpmBadge: StatisticsBadge
    private val starRatingBadge: StarRatingBadge
    private val scoreMultiplierBadge: StatisticsBadge

    private var calculationJob: Job? = null

    // The components sized in virtual units, kept to apply the sizes again on a scale change.
    private val topBar: UIContainer
    private val topButtons: UILinearContainer
    private val selectedModsScroll: UIScrollableContainer
    private val searchIcon: UISprite
    private val sectionsRow: VisibleChildrenContainer
    private val bottomBar: UIContainer
    private val statisticsRow: UILinearContainer
    private val difficultyRow: UILinearContainer

    private var shouldLoadSearchIcon = true

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f

    // The bottom padding of the bottom bar in virtual units and the scale it was last applied for.
    private var appliedBottomPadding = -1f
    private var bottomPaddingScale = 0f


    init {
        width = fill()
        height = fill()

        // Other AndEngine scenes take these textures from ResourceManager.
        ResourceManager.getInstance().loadHighQualityAsset("back-arrow", "back-arrow.png")
        ResourceManager.getInstance().loadHighQualityAsset("tune", "tune.png")
        ResourceManager.getInstance().loadHighQualityAsset("backspace", "backspace.png")
        ResourceManager.getInstance().loadHighQualityAsset("search-small", "search-small.png")
        ResourceManager.getInstance().loadHighQualityAsset("settings", "settings.png")

        customizationMenu = ModCustomizationMenu()

        // The AndEngine flex column spread its three children with SpaceBetween: the top and
        // bottom bars wrap their content and the sections take the height left between them.
        +UILinearContainer().apply {
            orientation = UILinearContainer.Orientation.Vertical
            width = fill()
            height = fill()
            style = Style { theme ->
                backgroundColor = (theme.palette.accent * 0.1f).copy(alpha = 0.9f)
            }

            topBar = UIContainer().apply {
                width = fill()

                topButtons = UILinearContainer().apply {
                    orientation = UILinearContainer.Orientation.Horizontal
                    anchor = Anchor.CenterLeft
                    origin = Anchor.CenterLeft

                    +LegacyTextButton("Back", "back-arrow", "back-arrow.png", SkinTextures::getAsset).apply {
                        onActionUp = {
                            ResourceManager.getInstance().getSound("click-short-confirm")?.play()
                            back()
                        }
                        onActionCancel = { ResourceManager.getInstance().getSound("click-short")?.play() }
                    }

                    customizeButton = LegacyTextButton("Customize", "tune", "tune.png").apply {
                        isEnabled = false
                        onActionUp = {
                            ResourceManager.getInstance().getSound("click-short-confirm")?.play()
                            if (customizationMenu.isVisible) {
                                customizationMenu.hide()
                            } else {
                                customizationMenu.show()
                            }
                        }
                        onActionCancel = { ResourceManager.getInstance().getSound("click-short")?.play() }
                    }

                    +customizeButton

                    +ClearButton().apply {
                        onActionUp = {
                            ResourceManager.getInstance().getSound("click-short-confirm")?.play()
                            clear()
                        }
                        onActionCancel = { ResourceManager.getInstance().getSound("click-short")?.play() }
                    }

                    // Verktex linear containers place children at the top of the row, so the
                    // indicator is centred inside a container as tall as the row.
                    selectedModsScroll = UIScrollableContainer().apply {
                        height = fill()
                        scrollAxes = Axis.X
                        clipping = ClipMode.Bounds

                        selectedModsIndicator = ModsIndicator().apply {
                            anchor = Anchor.CenterLeft
                            origin = Anchor.CenterLeft
                        }
                        +selectedModsIndicator
                    }

                    +selectedModsScroll
                }

                +topButtons

                +UIContainer().apply {
                    anchor = Anchor.CenterRight
                    origin = Anchor.CenterRight
                    height = fill()

                    searchInput = ModMenuSearchInput().apply {
                        onSearchTermUpdate = { searchTerm ->
                            modSections.forEach { it.onSearchTermUpdate(searchTerm) }

                            if (modPresetsSection.isVisible) {
                                modPresetsSection.onSearchTermUpdate(searchTerm)
                            }
                        }
                    }

                    +searchInput

                    searchIcon = UISprite().apply {
                        anchor = Anchor.CenterRight
                        origin = Anchor.CenterRight
                        style = Style { theme -> color = theme.palette.accent }
                    }

                    +searchIcon
                }
            }

            +topBar

            +UIScrollableContainer().apply {
                width = fill()
                height = fill()
                scrollAxes = Axis.X

                // Hidden sections must not take space in the row.
                sectionsRow = VisibleChildrenContainer().apply {
                    orientation = UILinearContainer.Orientation.Horizontal
                    height = fill()

                    modPresetsSection = ModMenuPresetsSection()
                    +modPresetsSection

                    val mods = allModsInstances

                    ModType.entries.forEach { type ->
                        val sectionName = StringTable.get(type.stringId)
                        val sectionToggles = mods.filter { it !is IMigratableMod && it.isUserPlayable && it.type == type }.map { ModMenuToggle(it) }

                        if (sectionToggles.isEmpty()) {
                            return@forEach
                        }

                        modToggles.addAll(sectionToggles)

                        val section = ModMenuSection(sectionName, sectionToggles)

                        +section
                        modSections.add(section)
                    }
                }

                +sectionsRow
            }

            bottomBar = UIContainer().apply {
                width = fill()

                statisticsRow = UILinearContainer().apply {
                    orientation = UILinearContainer.Orientation.Horizontal
                    anchor = Anchor.CenterLeft
                    origin = Anchor.CenterLeft

                    arBadge = StatisticsBadge().apply {
                        label = "AR"
                        value = "0.00"
                    }

                    odBadge = StatisticsBadge().apply {
                        label = "OD"
                        value = "0.00"
                    }

                    csBadge = StatisticsBadge().apply {
                        label = "CS"
                        value = "0.00"
                    }

                    hpBadge = StatisticsBadge().apply {
                        label = "HP"
                        value = "0.00"
                    }

                    bpmBadge = StatisticsBadge { it.roundToInt().toString() }.apply {
                        label = "BPM"
                        value = "0.0"
                    }

                    +arBadge
                    +odBadge
                    +csBadge
                    +hpBadge
                    +bpmBadge
                }

                +statisticsRow

                difficultyRow = UILinearContainer().apply {
                    orientation = UILinearContainer.Orientation.Horizontal
                    anchor = Anchor.CenterRight
                    origin = Anchor.CenterRight

                    starRatingBadge = StarRatingBadge()
                    +starRatingBadge

                    rankedBadge = RankedBadge()
                    +rankedBadge

                    scoreMultiplierBadge = StatisticsBadge { "%.2fx".format(it) }.apply {
                        label = "Score"
                        value = "1.00x"
                    }

                    +scoreMultiplierBadge
                }

                +difficultyRow
            }

            +bottomBar
        }

        // Customizations menu
        +customizationMenu

        modPresetsSection.loadPresets()
    }


    //region Thread

    /**
     * Runs [block] on the component tree: directly on the update thread, posted to it otherwise.
     * Mods may change off the update thread, e.g. from multiplayer room events.
     */
    private fun onTree(block: () -> Unit) {
        if (VerktexBridge.isUpdateThread) {
            Verktex.withTreeLock(block)
        } else {
            updateThread { Verktex.withTreeLock(block) }
        }
    }

    //endregion

    //region Calculation

    fun cancelCalculationJob() {
        calculationJob?.cancel(CancellationException("Difficulty calculation has been cancelled."))
        calculationJob = null
    }

    private fun parseBeatmap() {
        cancelCalculationJob()

        val selectedBeatmap = GlobalManager.getInstance().selectedBeatmap ?: return

        calculationJob = async scope@{

            val difficultyAlgorithm = Config.getDifficultyAlgorithm()
            val gameMode = if (difficultyAlgorithm == droid) GameMode.Droid else GameMode.Standard

            val beatmap = try {
                BeatmapCache.getBeatmap(selectedBeatmap, true, gameMode, this)
            } catch (e: Exception) {
                when (e) {
                    is IOException, is IllegalArgumentException -> null
                    else -> throw e
                }
            }

            val songMenu = GlobalManager.getInstance().songMenu

            if (beatmap == null) {
                updateThread { songMenu.setStarsDisplay(0f) }
                return@scope
            }

            if (selectedBeatmap.needsDifficultyCalculation) {
                val newInfo = BeatmapInfo(beatmap, selectedBeatmap.dateImported, true)
                selectedBeatmap.apply(newInfo)
                DatabaseManager.beatmapInfoTable.update(newInfo)
            }

            // This is needed to give the mod a reference to the beatmap's default difficulty values.
            enabledMods.ofType<ModDifficultyAdjust>()?.setDefaultDifficulty(beatmap.difficulty)

            onTree { customizationMenu.updateComponents() }

            // Copy the mods to avoid concurrent modification
            val mods = enabledMods.deepCopy().values
            val difficulty = beatmap.difficulty.clone()
            val rate = mods.calculateRate(Double.POSITIVE_INFINITY)

            difficulty.applyMods(gameMode, mods, true, this@scope)

            ensureActive()

            onTree {
                arBadge.updateValue(selectedBeatmap.approachRate, difficulty.ar)
                odBadge.updateValue(selectedBeatmap.overallDifficulty, difficulty.od)
                csBadge.updateValue(selectedBeatmap.circleSize, difficulty.difficultyCS)
                hpBadge.updateValue(selectedBeatmap.hpDrainRate, difficulty.hp)
                bpmBadge.updateValue(selectedBeatmap.mostCommonBPM, selectedBeatmap.mostCommonBPM * rate)

                scoreMultiplierBadge.updateValue(1f, ScoreMultiplierCalculator(beatmap.difficulty).calculateFor(enabledMods.values).toFloat())
            }

            ensureActive()

            val attributes = when (difficultyAlgorithm) {
                droid -> calculateDroidDifficulty(beatmap, mods, this@scope)
                standard -> calculateStandardDifficulty(beatmap, mods, this@scope)
            }

            ensureActive()

            updateThread {
                Verktex.withTreeLock { starRatingBadge.rating = attributes.starRating }
                songMenu.changeDimensionInfo(selectedBeatmap)
                songMenu.setStarsDisplay(attributes.starRating.toFloat())
            }
        }
    }

    //endregion

    //region Visibility

    fun show() {
        EngineRouter.showOverlay(this)

        onTree {
            // Do not show mod presets in multiplayer.
            modPresetsSection.isVisible = !Multiplayer.isMultiplayer

            if (modPresetsSection.isVisible) {
                modPresetsSection.onSearchTermUpdate(searchInput.value)
            }
        }

        // Ensure mods and customizations that can be enabled by the user are displayed and enabled.
        updateModButtonVisibility()
        updateCustomizationMenuEnabledStates()

        // Only parsing to update mod's specific settings defaults, specially those which rely on the original beatmap data.
        parseBeatmap()
    }

    fun back() {
        back(true)
    }

    fun back(updatePlayerMods: Boolean) {

        if (Multiplayer.isConnected) {
            val gameScene = GlobalManager.getInstance().gameScene
            val isInGameplay = gameScene != null &&
                GlobalManager.getInstance().engine.scene == gameScene.scene

            if (!isInGameplay) {
                Multiplayer.roomScene?.chat?.show()
                Multiplayer.roomScene?.isWaitingForModsChange?.set(true)

                // The room mods are the same as the host mods
                if (Multiplayer.isRoomHost) {
                    setRoomMods(enabledMods.serializeMods())
                } else if (updatePlayerMods) {
                    setPlayerMods(enabledMods.serializeMods())
                } else {
                    Multiplayer.roomScene?.isWaitingForModsChange?.set(false)
                }
            }
        }

        onTree { EngineRouter.hideOverlay(this) }
    }

    override fun onDetached() {
        super.onDetached()

        // Hidden by back() or by the router leaving Verktex, the search input must not keep the keyboard.
        searchInput.blur()
    }

    //endregion

    //region Mods

    fun setMods(mods: RoomMods, isFreeMod: Boolean) {
        if (isFreeMod) {
            for ((_, mod) in enabledMods.toList()) {
                if (mod.isValidForMultiplayerAsFreeMod) {
                    continue
                }

                if (mod !in mods) {
                    removeMod(mod)
                }
            }
        } else {
            clear()
        }

        for (mod in mods.values) {
            if (!mod.isValidForMultiplayer) {
                continue
            }

            if (!isFreeMod || !mod.isValidForMultiplayerAsFreeMod) {
                addMod(mod)
            }
        }

        if (!Multiplayer.isRoomHost) {
            val doubleTime = enabledMods.ofType<ModDoubleTime>()
            val nightCore = enabledMods.ofType<ModNightCore>()

            if (Config.isUseNightcoreOnMultiplayer() && doubleTime != null) {
                removeMod(doubleTime)
                addMod(ModNightCore())
            } else if (!Config.isUseNightcoreOnMultiplayer() && nightCore != null) {
                removeMod(nightCore)
                addMod(ModDoubleTime())
            }
        }

        if (ModScoreV2::class in mods) {
            addMod(ModScoreV2())
        } else {
            removeMod(ModScoreV2())
        }

        updateModButtonVisibility()
    }

    fun updateModButtonVisibility() = onTree {
        modToggles.forEach {
            it.updateVisibility(searchInput.value)
            it.applyCompatibilityState()
        }

        // Update section visibility after toggles have been updated since it may not need to be visible anymore.
        modSections.forEach { it.updateVisibility() }
    }

    fun updateCustomizationMenuEnabledStates() = onTree {
        customizationMenu.updateComponentEnabledStates()
    }

    fun clear() {
        cancelCalculationJob()

        val room = Multiplayer.room
        val isHost = Multiplayer.isRoomHost

        enabledMods.toList().forEach {
            val mod = it.second

            if (room != null) {
                // For non-host in multiplayer, we want to keep mods that are not allowed to be selected by the player
                // since only the host can change those mods.
                if (!isHost && room.gameplaySettings.isFreeMod && !mod.isValidForMultiplayerAsFreeMod) {
                    return@forEach
                }

                // Special handling for the ScoreV2 mod, which is used by the ScoreV2 win condition.
                if (mod is ModScoreV2) {
                    return@forEach
                }
            }

            removeMod(mod::class)
        }
    }

    fun queueModChange(mod: Mod) = synchronized(modChangeQueue) {
        // Adding to first place in case it is already queued.
        if (modChangeQueue.isEmpty() || modChangeQueue.first() != mod) {
            if (modChangeQueue.isNotEmpty()) {
                modChangeQueue.remove(mod)
            }
            modChangeQueue.addFirst(mod)
        }
    }

    private fun onModsChanged() = synchronized(modChangeQueue) {
        val enabledMods = enabledMods.values.toList()
        val enabledModClasses = enabledMods.mapTo(HashSet()) { it::class }
        val isRanked = enabledMods.isEmpty() || enabledMods.none { !it.isRanked }

        rankedBadge.setRanked(isRanked)

        modToggles.forEach {
            // The selection is read from the state, the toggles may not show it yet.
            it.hasIncompatibility =
                if (it.mod::class !in enabledModClasses) enabledMods.any { m -> !it.mod.isCompatibleWith(m) } else false
        }

        val selectedBeatmap = GlobalManager.getInstance().selectedBeatmap
        scoreMultiplierBadge.updateValue(1f, ScoreMultiplierCalculator(selectedBeatmap?.getBeatmapDifficulty()).calculateFor(enabledMods).toFloat())

        customizeButton.isEnabled = !customizationMenu.isEmpty()

        if (modChangeQueue.any { it is IModApplicableToTrackRate }) {
            GlobalManager.getInstance().songMenu.updateMusicEffects()
        }
        modChangeQueue.clear()

        parseBeatmap()

        selectedModsIndicator.mods = enabledMods
        modPresetsSection.onModsChanged()
    }

    fun addMod(mod: Mod) {

        if (mod in enabledMods) {
            return
        }

        // The toggles follow the enabled mods, so their selection before the change is read from the state.
        val previouslySelected = modToggles.filterTo(HashSet()) { it.mod::class in enabledMods }

        enabledMods.put(mod)

        modToggles.forEach { button ->

            val wasSelected = button in previouslySelected
            val isSelected = button.mod::class in enabledMods

            if (button.mod::class == mod::class) {
                button.mod = mod
            }

            // Handle incompatible mods with the selected mod.
            if (wasSelected && !isSelected) {
                customizationMenu.onModRemoved(button.mod)
                button.mod = button.mod::class.createInstance()
            }
        }

        customizationMenu.onModAdded(mod)
        queueModChange(mod)

        onTree(::applyToggleSelection)
    }

    fun removeMod(mod: Mod) = removeMod(mod::class)

    fun removeMod(modClass: KClass<out Mod>) {

        if (modClass !in enabledMods) {
            return
        }

        val toggle = modToggles.find { it.mod::class == modClass } ?: return

        enabledMods.remove(modClass)

        customizationMenu.onModRemoved(toggle.mod)
        queueModChange(toggle.mod)

        toggle.mod = modClass.createInstance()

        onTree(::applyToggleSelection)
    }

    /**
     * Shows the enabled mods on the toggles. Posted calls may run after later changes, so the
     * selection is taken from [enabledMods] when applied instead of from the change.
     */
    private fun applyToggleSelection() {
        modToggles.forEach { it.isSelected = it.mod::class in enabledMods }
    }

    //endregion

    //region Update

    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            topBar.padding = Padding(60f.vu.px, 12f.vu.px)
            topButtons.spacing = 10f.vu.px
            selectedModsScroll.width = 340f.vu.px
            searchInput.width = 400f.vu.px
            searchInput.height = fill()
            searchIcon.width = 52f.vu.px
            searchIcon.height = 28f.vu.px
            sectionsRow.spacing = 16f.vu.px
            sectionsRow.padding = Padding(60f.vu.px, 0f.px)
            statisticsRow.spacing = 10f.vu.px
            difficultyRow.spacing = 10f.vu.px
        }

        if (shouldLoadSearchIcon) {
            shouldLoadSearchIcon = false
            searchIcon.textureRegion = SkinTextures.get("search-small", "search-small.png")
        }

        val bottomPaddingBottom = if (Multiplayer.isConnected) (Multiplayer.roomScene?.chat?.buttonHeight ?: 0f) + 12f else 12f

        if (bottomPaddingBottom != appliedBottomPadding || appliedScale != bottomPaddingScale) {
            appliedBottomPadding = bottomPaddingBottom
            bottomPaddingScale = appliedScale

            bottomBar.padding = Padding(
                left = 60f.vu.px,
                top = 12f.vu.px,
                right = 60f.vu.px,
                bottom = bottomPaddingBottom.vu.px
            )
        }

        if (modChangeQueue.isNotEmpty()) {
            onModsChanged()
        }

        super.onUpdate(clock)
    }

    //endregion

    //region Layout

    // The overlay container keeps the safe area free, the AndEngine scene covered the whole
    // screen, so the menu takes the whole window instead of the bounds it is given. This relies
    // on the overlay container sitting at the window origin.

    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val windowSize = windowSize()
        val childConstraints = LayoutConstraints(0f, windowSize.x, 0f, windowSize.y)

        forEach { it.measure(childConstraints) }

        return windowSize
    }

    override fun layout(assignedBounds: Vec4) {
        val windowSize = windowSize()

        measuredSize = windowSize
        super.layout(Vec4(0f, 0f, windowSize.x, windowSize.y))
    }

    private fun windowSize() = Vec2(
        Verktex.graphics.window.windowWidth.toFloat(),
        Verktex.graphics.window.windowHeight.toFloat()
    )

    //endregion

    //region Input

    override fun onInputEvent(event: InputEvent): Boolean {
        // Keys first, the customization menu blocks every event while it is shown.
        if (event is KeyboardEvent) {
            // The song menu properties menu did not open over the AndEngine child scene.
            if (event.key == KeyboardEvent.Key.Menu && event.action == KeyboardEvent.Action.Down) {
                return true
            }

            // The legacy closed it on Enter only over the song menu.
            if (event.action == KeyboardEvent.Action.Down && (event.key == KeyboardEvent.Key.Back
                    || event.key == KeyboardEvent.Key.Enter && !Multiplayer.isMultiplayer)) {
                back()
                return true
            }
            return super.onInputEvent(event)
        }

        if (super.onInputEvent(event)) {
            return true
        }

        // The AndEngine child scene was modal for touches. Cancel events are dispatched to every
        // component, so they are not consumed.
        return event is ScrollEvent || event is PointerEvent && event.action != PointerEvent.Action.Unknown
    }

    //endregion

    //region Components

    /**
     * The "Clear" button, coloured by itself instead of the theme.
     */
    private class ClearButton : LegacyTextButton("Clear", "backspace", "backspace.png") {

        init {
            style = Style {}
            backgroundColor = Color4(0xFF342121)
        }

        override fun applyTint(tint: Color4) = super.applyTint(Color4(0xFFFFBFBF))
    }

    /**
     * The "Ranked" badge, coloured by itself instead of the theme. The colours fade linearly in
     * 0.1 seconds on a change, like the AndEngine colorTo().
     */
    private class RankedBadge : UIBadge() {

        private var textStart = RANKED_TEXT_COLOR
        private var textTarget = RANKED_TEXT_COLOR
        private var backgroundStart = RANKED_BACKGROUND_COLOR
        private var backgroundTarget = RANKED_BACKGROUND_COLOR
        private var elapsed = FADE_DURATION

        // LegacyUnits.scale the sizes were last applied for.
        private var appliedScale = 0f

        init {
            style = Style {}
            text = "Ranked"
            wrapText = false
            color = RANKED_TEXT_COLOR
            backgroundColor = RANKED_BACKGROUND_COLOR
        }

        fun setRanked(isRanked: Boolean) {
            val accent = Verktex.theme.palette.accent

            text = if (isRanked) "Ranked" else "Unranked"

            textStart = color
            textTarget = if (isRanked) RANKED_TEXT_COLOR else accent
            backgroundStart = backgroundColor
            backgroundTarget = if (isRanked) RANKED_BACKGROUND_COLOR else accent * 0.15f
            elapsed = 0f
        }

        override fun onUpdate(clock: TickingClock) {
            // Medium size variant of the AndEngine badge, in virtual units.
            if (appliedScale != LegacyUnits.scale) {
                appliedScale = LegacyUnits.scale

                fontSize = LegacyUnits.fontSize("smallFont").vu.px
                padding = Padding(12f.vu.px, 8f.vu.px)
                radius = 12f.vu.px
            }

            if (elapsed < FADE_DURATION) {
                elapsed = (elapsed + clock.elapsedTickTime).coerceAtMost(FADE_DURATION)

                val progress = elapsed / FADE_DURATION

                color = lerpRgb(textStart, textTarget, progress).copy(alpha = color.alpha)
                backgroundColor = lerpRgb(backgroundStart, backgroundTarget, progress).copy(alpha = backgroundColor.alpha)
            }

            super.onUpdate(clock)
        }

        private fun lerpRgb(start: Color4, end: Color4, progress: Float) = Color4(
            red = start.red + (end.red - start.red) * progress,
            green = start.green + (end.green - start.green) * progress,
            blue = start.blue + (end.blue - start.blue) * progress
        )

        companion object {
            private const val FADE_DURATION = 0.1f
            private val RANKED_TEXT_COLOR = Color4(0xFF161622)
            private val RANKED_BACKGROUND_COLOR = Color4(0xFF83DF6B)
        }
    }

    //endregion

}
