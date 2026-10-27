package com.osudroid.ui.v2.multi

import com.edlplan.ui.fragment.WebViewFragment
import com.osudroid.multiplayer.Multiplayer
import com.osudroid.multiplayer.api.RoomAPI
import com.osudroid.multiplayer.api.data.*
import com.osudroid.multiplayer.api.data.PlayerStatus.*
import com.osudroid.multiplayer.api.data.RoomTeam.Blue
import com.osudroid.multiplayer.api.data.RoomTeam.Red
import com.osudroid.ui.OsuColors
import com.osudroid.ui.v2.modmenu.LegacyButton
import com.osudroid.ui.verktex.*
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.utils.async
import com.osudroid.utils.updateThread
import com.osudroid.resources.R
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.*
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.*
import com.reco1l.verktex.ui.container.*
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.control.UIDropdown
import com.reco1l.verktex.ui.dialog.UIConfirmDialog
import com.reco1l.verktex.ui.shape.*
import com.reco1l.verktex.ui.text.*
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.helper.MD5Calculator
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import ru.nsu.ccfit.zuev.osu.online.OnlineFileOperator
import ru.nsu.ccfit.zuev.osu.online.OnlineManager

class RoomPlayerCard : UILinearContainer() {
    private val teamColorBar: UIButton
    private val playerButton: RoomPlayerButton

    init {
        width = fill()
        orientation = Orientation.Horizontal
        padding = Padding(12f.vu.px, 8f.vu.px)
        spacing = 12f.vu.px

        teamColorBar = UIButton().apply {
            style = Style {}
            // The width is 0.025 of the card's content width, see onUpdate.
            height = fill()
            radius = 12f.vu.px
        }

        playerButton = RoomPlayerButton()
        +playerButton
    }

    override fun onUpdate(clock: TickingClock) {
        // Verktex has no relative sizes, the AndEngine bar was 0.025 of the card's content width.
        teamColorBar.width = ((bounds.width - paddingLeft.toPixels() - paddingRight.toPixels()) * 0.025f).px

        super.onUpdate(clock)
    }

    fun updateState(room: Room, player: RoomPlayer) {
        playerButton.updateState(room, player)

        if (room.isTeamVersus) {
            if (teamColorBar.parent == null) {
                +teamColorBar
                teamColorBar.moveToBack()
            }

            teamColorBar.backgroundColor = when (player.team) {
                Blue -> Color4("#1E62E8") * 0.8f
                Red -> Color4("#E34444") * 0.8f
                null -> Verktex.theme.palette.accent * 0.6f
            }

            if (player.id == OnlineManager.getInstance().userId) {
                teamColorBar.onActionUp = { showTeamDropdown() }
            } else {
                teamColorBar.onActionUp = null
            }
        } else {
            teamColorBar.removeSelf()
        }
    }

    fun cancelJobs() {
        playerButton.avatarJob?.cancel()
        playerButton.bannerJob?.cancel()
    }

    private fun showTeamDropdown() {
        UIDropdown(teamColorBar).apply dropdown@{
            // The AndEngine dropdown was 100 wide and grew to fit its options, Verktex dropdowns wrap them.

            addButton {
                text = "Red"
                onActionUp = {
                    RoomAPI.setPlayerTeam(Red)
                    this@dropdown.hide()
                }
            }

            addButton {
                text = "Blue"
                onActionUp = {
                    RoomAPI.setPlayerTeam(Blue)
                    this@dropdown.hide()
                }
            }
        }.show()
    }

    private class RoomPlayerButton : LegacyButton(), ISkinnable {

        private lateinit var nameText: CompoundText
        private lateinit var rankText: UIText

        private lateinit var innerContainer: UILinearContainer
        private var modDisplay: UIComponent? = null

        private val bannerSprite: MaskedSprite
        private val avatarSprite: MaskedSprite

        var bannerJob: Job? = null
            private set

        var avatarJob: Job? = null
            private set

        private val defaultBackground = UIBox().apply {
            width = fill()
            height = fill()
            radius = 12f.vu.px
            backgroundColor = (Verktex.theme.palette.accent * 0.15f).copy(alpha = 0.5f)
        }

        private var lastPlayerId = -1L
        // Looked up every time, a skin change unloads the skin regions.
        private val defaultAvatar
            get() = SkinTextures.get("emptyavatar")

        private val hostIcon = FontAwesomeIcon(FAIcon.Crown).apply {
            style = Style { theme -> color = theme.palette.accent }
            boxSize = 24f
        }

        private val mutedIcon = FontAwesomeIcon(FAIcon.MicrophoneSlash).apply {
            style = Style { color = OsuColors.redLight.toVerktex() }
            boxSize = 24f
        }

        private val missingBeatmapIcon = UISprite().apply {
            textureRegion = SkinTextures.getAsset("missing", "missing.png")
            width = 24f.vu.px
            height = 24f.vu.px
        }

        // The AndEngine button laid its children out in a row, its background and foreground covered it whole.
        private val content = UILinearContainer()

        // Verktex components have no background or foreground entity, they are the first and last child.
        private var background: UIComponent? = null
            set(value) {
                if (field !== value) {
                    field?.removeSelf()
                    field = value

                    if (value != null) {
                        +value
                        value.moveToBack()
                    }
                }
            }

        private var foreground: UIBox? = null


        init {
            width = fill()
            // The button's own background and content colours were replaced, see applyTheme in the AndEngine button.
            style = Style {}
            backgroundColor = Color4.Transparent

            +content.apply {
                width = fill()
                orientation = Orientation.Horizontal
                spacing = 12f.vu.px
                padding = Padding(12f.vu.px, 8f.vu.px)
            }

            bannerSprite = MaskedSprite().apply {
                width = fill()
                height = fill()
                cornerRadius = 12f.vu

                scaleType = UISprite.ScaleType.Crop
                color = Color4(0.25f, 0.25f, 0.25f)
            }

            avatarSprite = MaskedSprite().apply {
                anchor = Anchor.CenterLeft
                origin = Anchor.CenterLeft
                width = 50f.vu.px
                height = 50f.vu.px
                cornerRadius = 8f.vu

                scaleType = UISprite.ScaleType.Crop
                textureRegion = defaultAvatar
            }

            background = defaultBackground

            foreground = UIBox().apply {
                width = fill()
                height = fill()
                radius = 12f.vu.px
                // The AndEngine outline was a GL line of one window pixel.
                borderWidth = 1f.px
            }

            content.apply {
                // Verktex linear containers ignore the anchor of their children, so the avatar sits in a
                // container as high as the row.
                container { height = fill() }.apply { +avatarSprite }

                container {
                    width = fill()
                    height = fill()

                    innerContainer = linearContainer {
                        anchor = Anchor.CenterLeft
                        origin = Anchor.CenterLeft
                        orientation = Orientation.Vertical

                        nameText = compoundText {
                            spacing = 6f.vu.px
                            // The AndEngine UIText scrolled overflowing text horizontally by default.
                            textEntity.autoScrollAxes = Axis.X
                            textEntity.autoScrollSpeed = 15f.vu
                            style = Style { theme -> textEntity.color = theme.palette.accent }
                        }
                    }

                    rankText = text {
                        anchor = Anchor.CenterRight
                        origin = Anchor.CenterRight
                        // AndEngine multiplied the colour by the button's accent.
                        style = Style { theme -> color = theme.palette.accent * 0.8f * theme.palette.accent }
                        fontSize = LegacyUnits.fontSize("CaptionFont").vu.px
                        wrapText = false
                        // The AndEngine UIText scrolled overflowing text horizontally by default.
                        autoScrollAxes = Axis.X
                        autoScrollSpeed = 15f.vu
                    }
                }
            }

            +foreground!!
        }

        override fun onApplyLegacySizes() {
            // The content row has the padding, the background and foreground cover the whole button.
            padding = Padding.Zero
        }

        override fun applyTint(tint: Color4) = Unit

        override fun onSkinChanged() {
            // The default avatar is a skin region, a loaded avatar is not.
            if (Textures[MD5Calculator.getStringMD5(OnlineManager.getAvatarURL(lastPlayerId))] == null) {
                avatarSprite.textureRegion = defaultAvatar
            }
        }

        override fun onDetached() {
            super.onDetached()

            avatarJob?.cancel()
            bannerJob?.cancel()
        }

        fun updateState(room: Room, player: RoomPlayer) {
            foreground!!.borderColor = when (player.status) {
                Playing -> Verktex.theme.palette.accent
                Ready -> Color4("#A0FFA0")
                NotReady, MissingBeatmap -> Color4("#FFA0A0")
            }

            if (lastPlayerId != player.id) {
                loadAvatar(player.id)
                loadBanner(player.id)
            }

            lastPlayerId = player.id
            nameText.text = player.name
            rankText.text = "#${player.rank}"

            updatePlayerIcons(room, player)

            missingBeatmapIcon.isVisible = player.status == MissingBeatmap

            if (Config.isPreferModAcronymInMultiplayer()) {
                if (modDisplay !is UIText) {
                    modDisplay?.removeSelf()

                    modDisplay = UIText().apply {
                        height = 24f.vu.px // Force to take space even if no mods are enabled
                        fontSize = LegacyUnits.fontSize("xs").vu.px
                        wrapText = false
                        // The AndEngine UIText scrolled overflowing text horizontally by default.
                        autoScrollAxes = Axis.X
                        autoScrollSpeed = 15f.vu
                        style = Style { theme -> color = theme.palette.accent * 0.8f }
                    }

                    innerContainer += modDisplay!!
                }

                (modDisplay as UIText).text = player.mods.toDisplayModString()
            } else {
                if (modDisplay !is ModsIndicator) {
                    modDisplay?.removeSelf()

                    modDisplay = ModsIndicator().apply {
                        height = 24f.vu.px // Force to take space even if no mods are enabled
                        iconSize = 24f
                    }

                    innerContainer += modDisplay!!
                }

                (modDisplay as ModsIndicator).mods = if (room.gameplaySettings.isFreeMod) player.mods.values else null
            }

            onActionLongPress = {
                // The AndEngine dropdown had a maxWidth of 260, Verktex dropdowns wrap their options.
                UIDropdown(this@RoomPlayerButton).apply dropdown@{

                    addButton {
                        text = StringTable.get(R.string.multiplayer_room_player_menu_view_profile)
                        onActionUp = {
                            WebViewFragment()
                                .setURL(WebViewFragment.PROFILE_URL + player.id)
                                .show()
                            this@dropdown.hide()
                        }
                    }

                    if (player.id != Multiplayer.player!!.id) {
                        addButton {
                            text = StringTable.get(if (player.isMuted) R.string.multiplayer_room_player_menu_unmute else R.string.multiplayer_room_player_menu_mute)
                            onActionUp = {
                                player.isMuted = !player.isMuted
                                updatePlayerIcons(room, player)
                                this@dropdown.hide()
                            }
                        }

                        if (Multiplayer.isRoomHost) {
                            addButton {
                                text = StringTable.get(R.string.multiplayer_room_player_menu_transfer_host)
                                onActionUp = {
                                    // Verktex modals attach as an overlay unless they have a parent, the dialog belongs to the room.
                                    UIConfirmDialog().apply {
                                        title = StringTable.format(R.string.multiplayer_room_player_transfer_dialog_title, player.name)
                                        text = StringTable.get(R.string.multiplayer_room_player_transfer_dialog_message)
                                        onConfirm = {
                                            if (Multiplayer.isConnected) {
                                                RoomAPI.setRoomHost(player.id)
                                            }
                                        }
                                    }.also { Verktex.scene += it }.show()
                                    this@dropdown.hide()
                                }
                            }

                            addButton {
                                text = StringTable.get(R.string.multiplayer_room_player_menu_kick)
                                // The text is the only child of a Verktex text button, the colour follows its style.
                                val kickText = this[0]
                                kickText.style = kickText.style + Style { kickText.color = Color4("#FFBFBF") }
                                onActionUp = {
                                    UIConfirmDialog().apply {
                                        title = StringTable.format(R.string.multiplayer_room_player_kick_dialog_title, player.name)
                                        text = StringTable.get(R.string.multiplayer_room_player_kick_dialog_message)
                                        onConfirm = {
                                            if (Multiplayer.isConnected) {
                                                RoomAPI.kickPlayer(player.id)
                                            }
                                        }
                                    }.also { Verktex.scene += it }.show()
                                    this@dropdown.hide()
                                }
                            }
                        }
                    }

                }.show()
            }
        }

        private fun loadAvatar(userId: Long) {
            val avatarUrl = OnlineManager.getAvatarURL(userId)

            avatarJob?.cancel()
            avatarJob = null

            val avatarKey = MD5Calculator.getStringMD5(avatarUrl)
            val loadedTexture = Textures[avatarKey]

            if (loadedTexture != null) {
                avatarSprite.textureRegion = loadedTexture
            } else {
                avatarSprite.textureRegion = defaultAvatar

                avatarJob = async {
                    ensureActive()

                    val avatar = fetchAvatar(avatarUrl)

                    if (avatar != null) {
                        ensureActive()

                        updateThread {
                            Verktex.withTreeLock {
                                val texture = SkinTextures.register(avatar.first, avatar.second, true)

                                if (lastPlayerId == userId) {
                                    avatarSprite.textureRegion = texture ?: defaultAvatar
                                }
                            }
                        }
                    } else {
                        updateThread {
                            Verktex.withTreeLock {
                                if (lastPlayerId == userId) {
                                    avatarSprite.textureRegion = defaultAvatar
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun loadBanner(userId: Long) {
            val bannerUrl = OnlineManager.getProfileBannerURL(userId)

            bannerJob?.cancel()
            bannerJob = null

            val bannerKey = MD5Calculator.getStringMD5(bannerUrl)
            val loadedTexture = Textures[bannerKey]

            if (loadedTexture != null) {
                bannerSprite.textureRegion = loadedTexture
                background = bannerSprite
            } else {
                bannerSprite.textureRegion = null
                background = defaultBackground

                bannerJob = async {
                    ensureActive()

                    val source = fetchBanner(bannerUrl)

                    if (source != null) {
                        ensureActive()

                        updateThread {
                            Verktex.withTreeLock {
                                val texture = SkinTextures.register(bannerKey, source, true)

                                if (lastPlayerId == userId) {
                                    bannerSprite.textureRegion = texture
                                    if (texture != null) {
                                        background = bannerSprite
                                    }
                                }
                            }
                        }
                    } else {
                        updateThread {
                            Verktex.withTreeLock {
                                if (lastPlayerId == userId) {
                                    background = defaultBackground
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * `OnlineManager.loadAvatarToTextureManager` without the AndEngine upload: the avatar the server gave
         * under its key, else the default avatar under its own. Null when neither decodes.
         */
        private fun fetchAvatar(avatarUrl: String): Pair<String, GraphicsTexture.Source>? {
            val avatarKey = MD5Calculator.getStringMD5(avatarUrl)
            val avatarFile = File(Config.getCachePath(), avatarKey)
            OnlineFileOperator.downloadFile(avatarUrl, avatarFile.absolutePath, true)
            SkinTextures.decode(avatarFile)?.let { return avatarKey to it }

            val defaultAvatarKey = MD5Calculator.getStringMD5(OnlineManager.defaultAvatarURL)
            val defaultAvatarFile = File(Config.getCachePath(), defaultAvatarKey)
            OnlineFileOperator.downloadFile(OnlineManager.defaultAvatarURL, defaultAvatarFile.absolutePath)
            return SkinTextures.decode(defaultAvatarFile)?.let { defaultAvatarKey to it }
        }

        /**
         * `OnlineManager.loadProfileBannerToTextureManager` without the AndEngine upload. Null when the banner
         * does not decode.
         */
        private fun fetchBanner(bannerUrl: String): GraphicsTexture.Source? {
            val bannerFile = File(Config.getCachePath(), MD5Calculator.getStringMD5(bannerUrl))
            OnlineFileOperator.downloadFile(bannerUrl, bannerFile.absolutePath, true)
            return SkinTextures.decode(bannerFile)
        }

        private fun updatePlayerIcons(room: Room, player: RoomPlayer) {
            hostIcon.removeSelf()
            mutedIcon.removeSelf()
            missingBeatmapIcon.removeSelf()

            nameText.trailingIcon = UILinearContainer().apply {
                orientation = Orientation.Horizontal
                spacing = 6f.vu.px

                if (player.id == room.host) {
                    +hostIcon
                }

                if (player.isMuted) {
                    +mutedIcon
                }

                if (player.status == MissingBeatmap) {
                    +missingBeatmapIcon
                }
            }
        }
    }
}
