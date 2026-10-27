package com.osudroid.ui.v2.multi

import android.util.Log
import com.osudroid.multiplayer.*
import com.osudroid.multiplayer.api.data.Room
import ru.nsu.ccfit.zuev.osu.SecurityUtils
import com.osudroid.multiplayer.api.LobbyAPI
import com.osudroid.utils.updateThread
import com.osudroid.resources.R.string
import com.osudroid.ui.v2.modmenu.LegacyTextButton
import com.osudroid.ui.verktex.*
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.verktex.bridge.EngineRouter
import com.osudroid.verktex.bridge.RoutedScene
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.*
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.*
import com.reco1l.verktex.ui.container.*
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.control.UITextInput
import com.reco1l.verktex.ui.shape.*
import com.reco1l.verktex.ui.text.*
import kotlinx.coroutines.*
import ru.nsu.ccfit.zuev.osu.*
import kotlin.coroutines.cancellation.CancellationException
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import ru.nsu.ccfit.zuev.osu.online.OnlineManager

class LobbyScene : UIScene(), RoutedScene {

    internal var isFetching = false
    private var shouldFetch = true
    private var lastTimeUpdateMillis: Long = 0L

    private var searchQuery: String? = null
        set(value) {
            if (field != value) {
                field = value
                lastTimeUpdateMillis = System.currentTimeMillis()
                shouldFetch = true
            }
        }

    private lateinit var backgroundSprite: UISprite
    private lateinit var messageContainer: UILinearContainer
    private lateinit var roomContainer: UILinearContainer
    internal lateinit var refreshButton: LegacyTextButton

    private val fetchScope = CoroutineScope(Dispatchers.Default)

    // Verktex has no entity modifiers, the fades and scales of switchContainers are animated in onUpdate.
    private val messageAlpha = FloatAnimation(1f, 0f, Easing.None)
    private val messageScale = FloatAnimation(1f, 0f, Easing.None)
    private val roomAlpha = FloatAnimation(1f, 0f, Easing.None)
    private val roomScale = FloatAnimation(1f, 0f, Easing.None)

    /**
     * Fetches the rooms matching a search query, on the fetch scope. Replaceable for tests.
     */
    internal var roomsSource: (query: String?) -> List<Room> = { query ->
        LobbyAPI.getRooms(
            query = query,
            sessionId = OnlineManager.getInstance().sessionId,
            uid = OnlineManager.getInstance().userId,
            sign = SecurityUtils.signRequest("${query ?: ""}_${OnlineManager.getInstance().userId}_${OnlineManager.getInstance().sessionId}")
        )
    }

    /**
     * Runs a block on the update thread under the tree lock. Replaceable for tests.
     */
    internal var post: (() -> Unit) -> Unit = { block -> updateThread { Verktex.withTreeLock(block) } }


    init {
        linearContainer {
            orientation = Orientation.Vertical
            width = fill()
            height = fill()
            padding = Padding(80f.vu.px, 0f.px)

            // The scene draws its background below the content, its texture is set in onShown.
            this@LobbyScene.background = UIContainer().apply {
                width = fill()
                height = fill()

                backgroundSprite = sprite {
                    width = fill()
                    height = fill()
                    scaleType = UISprite.ScaleType.Crop
                }

                box {
                    width = fill()
                    height = fill()
                    style = Style { theme ->
                        backgroundColor = theme.palette.accent * 0.1f
                        alpha = 0.9f
                    }
                }
            }

            container {
                width = fill()
                padding = Padding(0f.px, 20f.vu.px)

                linearContainer {
                    orientation = Orientation.Horizontal
                    spacing = 12f.vu.px

                    +LegacyTextButton(StringTable.get(string.multiplayer_lobby_back), "back-arrow", "back-arrow.png", SkinTextures::getAsset).apply {
                        onActionUp = { back() }
                    }

                    +LegacyTextButton(StringTable.get(string.multiplayer_lobby_create_room), "plus", "plus.png", SkinTextures::getAsset).apply {
                        onActionUp = {
                            // Verktex modals attach as an overlay unless they have a parent, the dialog belongs to this scene.
                            RoomCreateDialog(this@LobbyScene).also { this@LobbyScene += it }.show()
                        }
                    }

                    refreshButton = LegacyTextButton(StringTable.get(string.multiplayer_lobby_refresh), "refresh", "refresh.png", SkinTextures::getAsset).apply {
                        onActionUp = { shouldFetch = true }
                    }.also { +it }
                }

                container {
                    anchor = Anchor.TopRight
                    origin = Anchor.TopRight
                    height = fill()

                    +object : UITextInput("") {

                        init {
                            key = "search"
                            width = 500f.vu.px
                            height = fill()
                            placeholder = StringTable.get(string.multiplayer_lobby_search_rooms)
                        }

                        override fun onValueChanged() {
                            super.onValueChanged()
                            searchQuery = value
                        }

                    }

                    sprite {
                        textureRegion = SkinTextures.getAsset("search-small", "search-small.png")
                        width = 52f.vu.px
                        height = 28f.vu.px
                        anchor = Anchor.CenterRight
                        origin = Anchor.CenterRight
                        style = Style { theme -> color = theme.palette.accent }
                    }
                }
            }

            container {
                width = fill()
                height = fill()

                messageContainer = linearContainer {
                    orientation = Orientation.Vertical
                    spacing = 8f.vu.px
                    anchor = Anchor.Center
                    origin = Anchor.Center
                }

                scrollableContainer {
                    scrollAxes = Axis.Y
                    width = fill()
                    height = fill()
                    clipping = ClipMode.Bounds

                    roomContainer = linearContainer {
                        orientation = Orientation.Vertical
                        spacing = 8f.vu.px
                        width = fill()
                        scaleOrigin = Anchor.Center
                    }
                }
            }

        }
    }

    override fun onUpdate(clock: TickingClock) {

        val currentTimeMillis = System.currentTimeMillis()

        refreshButton.isEnabled = !isFetching && currentTimeMillis - lastTimeUpdateMillis > FETCH_TIMEOUT

        if (shouldFetch && currentTimeMillis - lastTimeUpdateMillis > FETCH_TIMEOUT) {
            shouldFetch = false
            lastTimeUpdateMillis = currentTimeMillis
            fetchRooms()
        }

        messageContainer.alpha = messageAlpha.update(clock)
        messageContainer.scale = messageScale.update(clock)
        roomContainer.alpha = roomAlpha.update(clock)
        roomContainer.scale = roomScale.update(clock)

        super.onUpdate(clock)
    }


    fun isShown() = Verktex.scene === this

    fun show() {
        EngineRouter.showVerktex(this)
    }

    override fun onShown() {
        // Textures are registered on the update thread, so the background is set here.
        backgroundSprite.apply {
            textureRegion = SkinTextures.get("menu-background")

            if (!Config.isSafeBeatmapBg()) {
                textureRegion = SharedBackground.region() ?: textureRegion
            }
        }
    }

    override fun onHidden() {
        fetchScope.cancel(CancellationException())
    }

    override fun onPerformLayout() {
        // The legacy scene covers the whole window, the safe area is not applied.
        padding = Padding.Zero
        super.onPerformLayout()
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        // Keys arrive on the UI thread. The isShown() check keeps a quick second Back from running back() twice.
        if (event is KeyboardEvent && event.action == KeyboardEvent.Action.Down && event.key == KeyboardEvent.Key.Back) {
            updateThread {
                Verktex.withTreeLock {
                    if (isShown()) {
                        back()
                    }
                }
            }
            return true
        }

        return super.onInputEvent(event)
    }


    private fun switchContainers(target: UIContainer) {

        val opposite = if (target == roomContainer) messageContainer else roomContainer

        // A new transition starts from the current values, as clearModifiers() then scaleTo() did.
        opposite.apply {
            scaleAnimation.animateTo(0.95f, 0.3f, Easing.None)
            alphaAnimation.animateTo(0f, 0.3f, Easing.None)
        }

        target.apply {
            scaleAnimation.jumpTo(0.95f)
            alphaAnimation.jumpTo(0f)

            scaleAnimation.animateTo(1f, 0.3f, Easing.None)
            alphaAnimation.animateTo(1f, 0.3f, Easing.None)
        }
    }

    private val UIContainer.scaleAnimation
        get() = if (this == roomContainer) roomScale else messageScale

    private val UIContainer.alphaAnimation
        get() = if (this == roomContainer) roomAlpha else messageAlpha

    private fun fetchRooms() {

        if (isFetching) {
            Log.w("LobbyScene", "Already fetching room list, skipping request.")
            return
        }
        isFetching = true

        fetchScope.launch(CoroutineExceptionHandler { _, throwable ->
            Log.e("LobbyScene", "Error fetching multiplayer rooms", throwable)

            ToastLogger.showText(
                StringTable.format(string.multiplayer_lobby_fetch_error_toast, throwable.message),
                true
            )

            post {
                messageContainer.apply {
                    clearChildren()

                    text {
                        fontSize = LegacyUnits.fontSize("smallFont").vu.px
                        wrapText = false
                        style = Style { theme -> color = theme.palette.accent }
                        text = StringTable.get(string.multiplayer_lobby_fetch_error)
                    }
                }
            }

            isFetching = false
        }) {
            post {
                messageContainer.apply {
                    clearChildren()

                    +CircularProgressBar().apply {
                        anchor = Anchor.Center
                        origin = Anchor.Center
                        width = 48f.vu.px
                        height = 48f.vu.px
                    }
                }

                switchContainers(messageContainer)
                roomContainer.clearChildren()
            }

            val list = roomsSource(searchQuery)

            post {

                list.forEach {
                    roomContainer += RoomButton(this@LobbyScene, it)
                }

                if (list.isEmpty()) {
                    messageContainer.apply {
                        clearChildren()

                        text {
                            fontSize = LegacyUnits.fontSize("smallFont").vu.px
                            wrapText = false
                            style = Style { theme -> color = theme.palette.accent }
                            text = StringTable.get(if (searchQuery.isNullOrEmpty()) string.multiplayer_lobby_no_rooms else string.multiplayer_lobby_no_results)
                        }
                    }
                } else {
                    switchContainers(roomContainer)
                }

                isFetching = false
            }
        }
    }


    fun back() {
        Multiplayer.isMultiplayer = false
        GlobalManager.getInstance().songService.isGaming = false

        GlobalManager.getInstance().mainMenuV2?.show()
    }


    companion object {
        private const val FETCH_TIMEOUT = 1000L
    }
}
