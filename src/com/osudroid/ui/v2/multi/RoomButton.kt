package com.osudroid.ui.v2.multi

import com.osudroid.resources.R.string
import com.osudroid.multiplayer.*
import com.osudroid.multiplayer.api.*
import com.osudroid.multiplayer.api.data.*
import com.osudroid.ui.v2.modmenu.LegacyButton
import com.osudroid.ui.verktex.*
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.utils.async
import com.osudroid.utils.updateThread
import com.reco1l.andengine.ui.SizeVariant
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.*
import com.reco1l.verktex.ui.*
import com.reco1l.verktex.ui.container.*
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.dialog.UIDialog
import com.reco1l.verktex.ui.form.*
import com.reco1l.verktex.ui.text.*
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.osu.helper.*
import ru.nsu.ccfit.zuev.osu.menu.*
import ru.nsu.ccfit.zuev.osu.online.*

class RoomButton(val lobbyScene: LobbyScene, val room: Room) : LegacyButton() {

    init {
        width = fill()
        backgroundColor = Color4.Black.copy(alpha = 0.25f)

        // Override the default background
        style = Style {}

        container {
            width = fill()

            linearContainer {
                orientation = Orientation.Vertical
                spacing = 8f.vu.px

                linearContainer {
                    spacing = 4f.vu.px

                    // Verktex linear containers ignore the anchor of their children, so the icon sits in a
                    // container as high as the row.
                    container { height = fill() }.sprite {
                        origin = Anchor.CenterLeft
                        anchor = Anchor.CenterLeft
                        textureRegion = SkinTextures.getAsset(if (room.isLocked) "lock" else "unlock", if (room.isLocked) "lock.png" else "unlock.png")
                        width = 20f.vu.px
                        height = 20f.vu.px
                        style = Style { theme -> color = theme.palette.accent }
                    }

                    text {
                        fontSize = LegacyUnits.fontSize("smallFont").vu.px
                        wrapText = false
                        text = room.name
                        style = Style { theme -> color = theme.palette.accent }
                    }
                }

                text {
                    fontSize = LegacyUnits.fontSize("xs").vu.px
                    wrapText = false
                    text = room.playerNames.takeUnless { it.isEmpty() }
                        ?: if (room.playerCount > 0) "${room.playerCount} player${if (room.playerCount != 1) "s" else ""}"
                           else StringTable.get(string.multiplayer_room_no_players)
                    style = Style { theme ->
                        color = theme.palette.accent
                        alpha = 0.95f
                    }
                }

                linearContainer {
                    spacing = 8f.vu.px

                    legacyBadge {
                        sizeVariant = SizeVariant.Small
                        setText(when (room.teamMode) {
                            TeamMode.HeadToHead -> string.multiplayer_room_head_to_head
                            TeamMode.TeamVS -> string.multiplayer_room_team_versus
                        })
                    }

                    legacyBadge {
                        sizeVariant = SizeVariant.Small
                        setText(when (room.winCondition) {
                            WinCondition.ScoreV1 -> string.multiplayer_room_score_v1
                            WinCondition.ScoreV2 -> string.multiplayer_room_score_v2
                            WinCondition.Accuracy -> string.multiplayer_room_highest_accuracy
                            WinCondition.MaxCombo -> string.multiplayer_room_maximum_combo
                        })
                    }

                    legacyLabeledBadge {
                        sizeVariant = SizeVariant.Small
                        label = StringTable.get(string.multiplayer_room_players)
                        value = "${room.playerCount}/${room.maxPlayers}"
                    }

                    if (room.gameplaySettings.isFreeMod) {
                        legacyBadge {
                            sizeVariant = SizeVariant.Small
                            style = Style { theme ->
                                color = theme.palette.accent * 0.1f
                                backgroundColor = theme.palette.accent
                            }
                            setText(string.multiplayer_room_free_mods)
                        }
                    }
                }

                if (room.mods.isNotEmpty()) {
                    +ModsIndicator().apply {
                        mods = room.mods.values
                        iconSize = 24f
                    }
                }
            }

            text {
                origin = Anchor.TopRight
                anchor = Anchor.TopRight
                fontSize = LegacyUnits.fontSize("smallFont").vu.px
                wrapText = false
                // setScale() scaled around the origin.
                scaleOrigin = Anchor.TopRight
                scale = 0.85f
                text = StringTable.get(when (room.status) {
                    RoomStatus.ChangingBeatmap -> string.multiplayer_room_status_changing_beatmap
                    RoomStatus.Playing -> string.multiplayer_room_status_playing
                    else -> string.multiplayer_room_status_idle
                })
                style = Style { theme -> color = theme.palette.accent }
            }
        }

        onActionUp = {
            if (room.isLocked) {

                val form: FormContainer

                object : UIDialog<FormContainer>(FormContainer().apply {
                    width = fill()
                    form = this

                    +FormInput().apply {
                        key = "password"
                        width = fill()
                        label = StringTable.get(string.multiplayer_lobby_room_password)
                    }

                    onSubmit = {
                        connectToRoom(it.getString("password"))
                    }
                }) {
                    init {
                        title = StringTable.get(string.multiplayer_lobby_join_room)

                        addButton(UITextButton().apply {
                            text = StringTable.get(string.multiplayer_lobby_join_room_accept)
                            isSelected = true
                            onActionUp = { form.submit() }
                        })

                        addButton(UITextButton().apply {
                            text = StringTable.get(string.multiplayer_lobby_join_room_cancel)
                            onActionUp = { hide() }
                        })
                    }
                }.also { lobbyScene += it }.show()
            } else {
                connectToRoom()
            }
        }
    }

    // The content keeps its own colours, the theme does not tint it.
    override fun onApplyLegacySizes() = Unit

    override fun applyTint(tint: Color4) = Unit


    fun connectToRoom(password: String? = null) {

        Multiplayer.log("Trying to connect socket...")
        LoadingScreen.show()

        async {

            try {
                RoomAPI.connectToRoom(
                    roomId = room.id,
                    userId = OnlineManager.getInstance().userId,
                    gameSessionId = OnlineManager.getInstance().sessionId,
                    roomPassword = password
                )
            } catch (e: Exception) {
                ToastLogger.showText("Failed to connect to the room: ${e.javaClass} - ${e.message}", true)
                Multiplayer.log(e)

                updateThread { Verktex.withTreeLock { lobbyScene.show() } }
            }
        }

    }

}
