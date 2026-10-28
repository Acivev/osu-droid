package com.osudroid.ui.v2.multi

import android.icu.text.SimpleDateFormat
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.edlplan.framework.easing.Easing
import com.osudroid.multiplayer.*
import com.osudroid.multiplayer.api.*
import com.osudroid.multiplayer.api.data.*
import com.osudroid.utils.*
import com.osudroid.resources.R
import com.osudroid.ui.v2.modmenu.LegacyButton
import com.osudroid.ui.verktex.FullWindowContainer
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.ui.verktex.legacyLinearContainer
import com.osudroid.ui.verktex.times
import com.osudroid.ui.verktex.toVerktex
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.framework.*
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.container.container
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.container.scrollableContainer
import com.reco1l.verktex.ui.control.UITextInput
import com.reco1l.verktex.ui.sprite
import com.reco1l.verktex.ui.text.UIText
import com.reco1l.verktex.ui.text.text
import com.rian.andengine.modifier.ModifierType
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.heightTo
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import java.util.LinkedList
import kotlin.math.max

/**
 * Because we're pros we want to highlight us.
 */
private val DEVELOPERS = longArrayOf(
    51076, // Rian8337
    55374, // Acivev
    307054 // Reco1l
)


class RoomChat : FullWindowContainer() {

    /**
     * Height of the chat button.
     */
    val buttonHeight
        // In virtual units like the AndEngine button's, the callers convert it.
        get() = button.measuredSize.y / LegacyUnits.scale

    /**
     * Whether the chat is currently expanded.
     */
    var isExpanded = false
        private set(value) {
            if (field != value) {
                field = value
                button.updateState()
            }
        }

    /**
     * Whether the chat is shown as an overlay.
     */
    val isShown
        get() = parent != null

    @VisibleForTesting
    internal val messages = LinkedList<Message>()
    private val timestampFormat = SimpleDateFormat("HH:mm:ss")

    private val button = ChatButton()
    @VisibleForTesting
    internal val body: UILinearContainer = object : UILinearContainer() {
        // The AndEngine body kept its content at full size while its own height animated, the content
        // overflowing it below the window. Verktex limits children to their parent's height.
        override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints) =
            super.onMeasure(parentConstraints, contentConstraints.copy(maxHeight = Float.POSITIVE_INFINITY))
    }

    // The AndEngine text and box buffers shared by the messages have no Verktex counterpart, every
    // message has its own texts.

    private lateinit var input: UITextInput
    private lateinit var messageContainer: UILinearContainer
    private lateinit var panel: UILinearContainer

    private var messagesChanged = false

    // The input's bottom edge in window pixels when it was focused, NaN while it is not.
    private var focusedInputBottom = Float.NaN

    /**
     * Runs a block on the update thread under the tree lock. Replaced by the tests.
     */
    @VisibleForTesting
    internal var post: (() -> Unit) -> Unit = { block -> updateThread { Verktex.withTreeLock(block) } }

    /**
     * Whether the gameplay scene is shown, no message sound is played then. Replaced by the tests.
     */
    @VisibleForTesting
    internal var isInGameplay: () -> Boolean = {
        GlobalManager.getInstance().gameScene.isInGameplay
    }

    init {

        // The container fills the entire window (FullWindowContainer) so that the chat can be closed
        // by tapping outside of it (see onInputEvent).

        panel = linearContainer {
            width = fill()
            orientation = Orientation.Vertical
            anchor = Anchor.BottomCenter
            origin = Anchor.BottomCenter
            style = Style { theme ->
                backgroundColor = (theme.palette.accent * 0.1f).copy(alpha = 0.9f)
            }

            +button
            +body.apply {
                width = fill()
                height = 0f.px
                orientation = Orientation.Vertical

                scrollableContainer {
                    width = fill()
                    height = (body_height - 84f).vu.px // Input height based of button height plus padding
                    scrollAxes = Axis.Y
                    clipping = ClipMode.Bounds

                    messageContainer = legacyLinearContainer {
                        width = fill()
                        orientation = Orientation.Vertical

                        repeat(max_messages) {
                            +MessageComponent()
                        }
                    }
                }

                linearContainer {
                    width = fill()
                    orientation = Orientation.Horizontal
                    padding = Padding(80f.vu.px, 12f.vu.px)
                    spacing = 8f.vu.px

                    +UITextInput("").apply {
                        width = fill()
                        height = fill()
                        maxCharacters = 200
                        placeholder = "Type a message..."
                        onConfirm = { sendMessage() }

                        input = this
                    }

                    +TrailingIconTextButton("send", "send.png").apply {
                        isSelected = true
                        setText(R.string.multiplayer_room_chat_send)
                        onActionUp = { sendMessage() }
                    }
                }
            }
        }
    }


    private fun sendMessage() {

        val text = input.value.trim().take(200)
        if (text.isEmpty()) {
            return
        }

        input.value = ""

        async {
            try {
                RoomAPI.sendMessage(text)
            } catch (e: Exception) {
                onSystemChatMessage(StringTable.format(R.string.multiplayer_room_chat_error, e.message), "#FFBFBF")
                e.printStackTrace()
            }
        }
    }

    private fun appendMessage(message: Message) {

        if (!isInGameplay()) {
            ResourceManager.getInstance().getSound("heartbeat")?.play(0.75f)
        }

        messages.add(message)
        if (messages.size > max_messages) {
            messages.pollFirst()
        }

        button.updateState()
        messagesChanged = true
    }


    // The settings fragment calls this on the main thread, it is moved to the update thread.
    fun show() {
        if (VerktexBridge.isInitialized && !VerktexBridge.isUpdateThread) {
            post { show() }
            return
        }

        Verktex.withTreeLock {
            if (parent == null) {
                Verktex.addOverlay(this)
            }
        }
    }

    fun hide() {
        if (VerktexBridge.isInitialized && !VerktexBridge.isUpdateThread) {
            post { hide() }
            return
        }

        Verktex.withTreeLock {
            if (parent != null) {
                Verktex.removeOverlay(this)
            }
        }
    }

    fun expand() {
        if (!isExpanded) {
            isExpanded = true
            body.apply {
                clearModifiers(ModifierType.Height)
                heightTo(body_height.vu, 0.4f, Easing.OutExpo)
            }
        }
    }

    @JvmOverloads
    fun collapse(immediate: Boolean = false) {
        if (isExpanded) {
            isExpanded = false
            input.blur()

            body.apply {
                clearModifiers(ModifierType.Height)

                if (immediate) {
                    height = 0f.px
                } else {
                    heightTo(0f, 0.4f, Easing.OutExpo)
                }
            }
        }
    }


    fun onRoomChatMessage(player: RoomPlayer, message: String) = post {
        appendMessage(
            PlayerMessage(
                player = player,
                content = message
            )
        )
    }

    fun onSystemChatMessage(message: String, color: String) = post {
        Multiplayer.log("System message: $message")

        appendMessage(
            SystemMessage(
                color = Color4(color),
                content = message
            )
        )
    }

    override fun onDetached() {
        collapse(true)

        // A collapse still animating when the chat is hidden would leave the body half open: the
        // registry drops the detached animation and collapse() above sees it already collapsed.
        if (!isExpanded) {
            body.clearModifiers(ModifierType.Height)
            body.height = 0f.px
        }
    }

    override fun onUpdate(clock: TickingClock) {

        if (messagesChanged) {
            messagesChanged = false

            for (i in max_messages - 1 downTo 0) {
                val messageComponent = messageContainer[i] as MessageComponent
                messageComponent.message = if (i < messages.size) messages[i] else null
            }
        }

        // The AndEngine engine moved the focused input up just enough for its bottom edge to sit on the
        // keyboard, from the input's bottom when it was focused. The offset is negative while the
        // keyboard is shown.
        if (input.isFocused && focusedInputBottom.isNaN()) {
            focusedInputBottom = input.toSceneSpace(0f, input.bounds.height).y
        } else if (!input.isFocused) {
            focusedInputBottom = Float.NaN
        }

        val imeTranslation = (if (input.isFocused) -imeLift(focusedInputBottom, Verktex.graphics.window.windowHeight.toFloat(), -Verktex.imeOffset) else 0f).px
        if (panel.translationY != imeTranslation) {
            panel.translationY = imeTranslation
        }

        super.onUpdate(clock)
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        if (super.onInputEvent(event)) {
            return true
        }

        if (event !is PointerEvent) {
            return false
        }

        // The AndEngine scrollable container took the presses on the messages, the Verktex one only
        // takes drags.
        if (body.containsScenePoint(event.x, event.y)) {
            return true
        }

        collapse()
        return false
    }


    private fun getPlayerTagColor(player: RoomPlayer): Color4 {
        return Color4(
            when {
                player.id in DEVELOPERS -> "#F280FF"
                player.isHost -> "#00FFEA"
                else -> "#8282A8"
            }
        )
    }

    // The AndEngine UIText: the small font, no wrapping and overflowing text scrolled horizontally.
    private fun UIText.applyLegacyText() {
        fontSize = LegacyUnits.fontSize("smallFont").vu.px
        wrapText = false
        autoScrollAxes = Axis.X
        autoScrollSpeed = 15f.vu
    }


    inner class ChatButton : UILinearContainer() {

        private lateinit var tagText: UIText
        private lateinit var messageText: UIText
        private lateinit var icon: UISprite
        private var isPressed = false
        private var shouldLoadTexture = true

        // Verktex offers moves and releases to every child, AndEngine bound them to the pressed area.
        private var pressedPointerId = -1


        init {
            width = fill()
            orientation = Orientation.Horizontal
            padding = Padding(80f.vu.px, 18f.vu.px)
            spacing = 12f.vu.px
            style = Style { theme ->
                backgroundColor = (theme.palette.accent * 0.15f).copy(alpha = 0.5f)
            }

            // The texture is looked up on the first update, the chat may be built off the update thread.
            icon = sprite {
                anchor = Anchor.CenterLeft
                origin = Anchor.CenterLeft
                style = Style { theme -> color = theme.palette.accent }
                width = 28f.vu.px
                height = 28f.vu.px
            }

            linearContainer {
                width = fill()
                height = fill()
                orientation = Orientation.Horizontal

                tagText = text {
                    applyLegacyText()
                    text = StringTable.get(R.string.multiplayer_room_chat)
                    anchor = Anchor.CenterLeft
                    origin = Anchor.CenterLeft
                    color = Verktex.theme.palette.accent
                }

                messageText = text {
                    applyLegacyText()
                    width = fill()
                    height = fill()
                    clipping = ClipMode.Bounds
                    anchor = Anchor.CenterLeft
                    origin = Anchor.CenterLeft
                    style = Style { theme -> color = theme.palette.accent }
                }
            }
        }


        fun updateState() {
            val lastMessage = messages.peekLast()

            if (isExpanded || lastMessage == null) {
                tagText.apply {
                    text = StringTable.get(R.string.multiplayer_room_chat)
                    color = Verktex.theme.palette.accent
                }
                messageText.text = ""
            } else {
                tagText.apply {
                    text = "${if (lastMessage is PlayerMessage) lastMessage.player.name else StringTable.get(R.string.multiplayer_room_chat_system)}: "
                    color = if (lastMessage is PlayerMessage) getPlayerTagColor(lastMessage.player).toVerktex() else Verktex.theme.palette.accent
                }
                messageText.text = lastMessage.content.substringBefore('\n')
            }
        }


        override fun onUpdate(clock: TickingClock) {
            if (shouldLoadTexture) {
                shouldLoadTexture = false
                icon.textureRegion = SkinTextures.getAsset("chat", "chat.png")
            }

            super.onUpdate(clock)
        }

        override fun onInputEvent(event: InputEvent): Boolean {
            if (event !is PointerEvent) {
                return false
            }

            if (event.action == PointerEvent.Action.Down) {
                isPressed = true
                pressedPointerId = event.pointerId
                return true
            }

            if (event.pointerId != pressedPointerId) {
                return false
            }

            when (event.action) {
                PointerEvent.Action.Up -> {
                    val local = toLocalSpaceFromScene(event.x, event.y)

                    if (local.x <= bounds.width && local.y <= bounds.height && isPressed) {
                        if (!isExpanded) {
                            expand()
                        } else {
                            collapse()
                        }
                    }

                    isPressed = false
                    pressedPointerId = -1
                }

                PointerEvent.Action.Move -> {}

                else -> {
                    isPressed = false
                    pressedPointerId = -1
                }
            }

            return true
        }

    }


    inner class MessageComponent : UILinearContainer() {

        var message: Message? = null
            set(value) {
                if (field != value) {
                    field = value
                    messageChanged = true
                }
            }


        private var messageChanged = false

        // The AndEngine container was a quarter of the message's width.
        private var senderContainer: UIContainer? = null


        init {
            width = fill()
            padding = Padding(80f.vu.px, 0f.px)
            spacing = 12f.vu.px
            orientation = Orientation.Horizontal
            style = Style { theme ->
                backgroundColor = (theme.palette.accent * 0.09f).copy(alpha = 0f)
            }
        }


        override fun onUpdate(clock: TickingClock) {

            if (messageChanged) {
                messageChanged = false

                clearChildren()
                senderContainer = null

                val message = message ?: return
                val messageIndex = messages.indexOf(message)

                backgroundColor = backgroundColor.copy(alpha = if (messageIndex % 2 == 0) 0.5f else 0f)

                if (message is SystemMessage) {
                    text {
                        applyLegacyText()
                        width = fill()
                        anchor = Anchor.CenterLeft
                        origin = Anchor.CenterLeft
                        text = message.content
                        padding = Padding(12f.vu.px)
                        color = message.color.toVerktex()
                        alignment = Anchor.Center
                    }
                } else if (message is PlayerMessage) {

                    val showSender = messageIndex == 0
                        || messages[messageIndex - 1] !is PlayerMessage
                        || (messages[messageIndex - 1] as PlayerMessage).player.id != message.player.id

                    senderContainer = container {
                        anchor = Anchor.CenterLeft
                        origin = Anchor.CenterLeft

                        text {
                            applyLegacyText()
                            style = Style { theme -> color = theme.palette.accent * 0.5f }
                            text = timestampFormat.format(message.time)
                        }

                        if (showSender) {
                            text {
                                applyLegacyText()
                                anchor = Anchor.TopRight
                                origin = Anchor.TopRight
                                color = getPlayerTagColor(message.player).toVerktex()
                                text = message.player.name
                            }
                        }
                    }

                    text {
                        applyLegacyText()
                        width = fill()
                        style = Style { theme -> color = theme.palette.accent }
                        text = message.content
                    }
                } else {
                    // Technically should never happen.
                    isVisible = false
                }
            }

            senderContainer?.let {
                val senderWidth = ((bounds.width - paddingLeft.toPixels() - paddingRight.toPixels()) * 0.25f).px
                if (it.width != senderWidth) {
                    it.width = senderWidth
                }
            }

            super.onUpdate(clock)
        }

    }


    companion object {

        /**
         * How far the AndEngine engine moved the focused input up: until its [inputBottom] sits on the
         * keyboard of [keyboardHeight] in a window of [windowHeight], never down.
         */
        @VisibleForTesting
        internal fun imeLift(inputBottom: Float, windowHeight: Float, keyboardHeight: Float) =
            max(0f, inputBottom - (windowHeight - keyboardHeight))

        private const val max_messages = 50
        private const val body_height = 420f
    }
}


/**
 * The AndEngine UITextButton with a trailing icon: the icon is sized to the text height and 8 apart
 * from it, both centred in the button. The icon is a bundled asset, looked up on the first update.
 */
private class TrailingIconTextButton(private val iconName: String, private val iconAssetPath: String) : LegacyButton() {

    private val icon = UISprite()

    private val label = UIText().apply {
        wrapText = false
    }

    private val row = UILinearContainer().apply {
        orientation = Orientation.Horizontal
        anchor = Anchor.Center
        origin = Anchor.Center

        +label
        +icon
    }

    private var shouldLoadTexture = true

    // The label height the icon was last sized to.
    private var iconSize = 0f

    init {
        +row
    }

    fun setText(@StringRes resId: Int) {
        label.text = StringTable.get(resId)
    }

    override fun onApplyLegacySizes() {
        row.spacing = 8f.vu.px
        label.fontSize = LegacyUnits.fontSize("smallFont").vu.px
    }

    override fun applyTint(tint: com.reco1l.verktex.data.Color4) {
        icon.color = tint
        label.color = tint
    }

    override fun onUpdate(clock: TickingClock) {
        if (shouldLoadTexture) {
            shouldLoadTexture = false
            icon.textureRegion = SkinTextures.getAsset(iconName, iconAssetPath)
        }

        // CompoundText sized its trailing icon to the text height on both axes.
        if (iconSize != label.measuredSize.y) {
            iconSize = label.measuredSize.y
            icon.width = iconSize.px
            icon.height = iconSize.px
        }

        super.onUpdate(clock)
    }
}


//region Messages

interface Message {
    val content: String
    val time: Long
}

data class PlayerMessage(
    val player: RoomPlayer,

    override val content: String,
    override val time: Long = System.currentTimeMillis(),
) : Message

data class SystemMessage(
    val color: Color4,

    override val content: String,
    override val time: Long = System.currentTimeMillis(),
) : Message

//endregion
