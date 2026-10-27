package com.osudroid.ui.v2.multi

import com.osudroid.resources.R.string
import com.osudroid.multiplayer.api.*
import com.osudroid.multiplayer.api.data.*
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.utils.async
import com.osudroid.utils.updateThread
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.*
import com.reco1l.verktex.ui.*
import com.reco1l.verktex.ui.container.*
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.dialog.UIDialog
import com.reco1l.verktex.ui.form.*
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.osu.helper.*
import ru.nsu.ccfit.zuev.osu.menu.*
import ru.nsu.ccfit.zuev.osu.online.*

class RoomCreateDialog(lobbyScene: LobbyScene) : UIDialog<UIScrollableContainer>(UIScrollableContainer().apply {
    scrollAxes = Axis.Y
    width = fill()
    height = 360f.vu.px
    clipping = ClipMode.Bounds
}) {
    init {

        val form = FormContainer().apply {
            width = fill()
            orientation = Orientation.Vertical

            onSubmit = { data ->
                async {
                    LoadingScreen.show()

                    val name = data.getString("name") ?: StringTable.format(string.multiplayer_lobby_create_room_name_default, OnlineManager.getInstance().username)
                    val password = data.optString("password").takeUnless(String::isBlank)
                    val capacity = data.getDouble("capacity").toInt()

                    val beatmap = GlobalManager.getInstance().selectedBeatmap?.let {

                        RoomBeatmap(
                            md5 = it.md5,
                            title = it.title,
                            artist = it.artist,
                            creator = it.creator,
                            version = it.version
                        )
                    }

                    var signStr = "${OnlineManager.getInstance().userId}_${name}_${capacity}"
                    if (password != null) {
                        signStr += "_${password}"
                    }
                    signStr += "_${RoomAPI.API_VERSION}_${OnlineManager.getInstance().sessionId}"

                    try {

                        val roomId = LobbyAPI.createRoom(
                            name = name,
                            beatmap = beatmap,
                            hostUID = OnlineManager.getInstance().userId,
                            sessionId = OnlineManager.getInstance().sessionId,
                            sign = SecurityUtils.signRequest(signStr),
                            password = password,
                            maxPlayers = capacity
                        )

                        RoomAPI.connectToRoom(
                            roomId = roomId,
                            userId = OnlineManager.getInstance().userId,
                            gameSessionId = OnlineManager.getInstance().sessionId,
                            roomPassword = password
                        )

                    } catch (e: Exception) {
                        updateThread { Verktex.withTreeLock { lobbyScene.show() } }
                        ToastLogger.showText("Failed to create a room: ${e.message}", true)
                        e.printStackTrace()
                    }

                }
            }

            +FormInput(StringTable.format(string.multiplayer_lobby_create_room_name_default, OnlineManager.getInstance().username)).apply {
                key = "name"
                width = fill()
                label = StringTable.get(string.multiplayer_lobby_room_name)
            }

            +FormInput().apply {
                key = "password"
                width = fill()
                label = StringTable.get(string.multiplayer_lobby_room_password)
            }

            +FormSlider(8f).apply {
                key = "capacity"
                width = fill()
                label = StringTable.get(string.multiplayer_lobby_room_capacity)
                control.max = 16f
                control.min = 2f
                control.step = 1f
                valueFormatter = { it.toInt().toString() }
            }

        }

        innerContent += form

        title = StringTable.get(string.multiplayer_lobby_create_room)

        addButton(UITextButton().apply {
            text = StringTable.get(string.multiplayer_lobby_create_room_accept)
            isSelected = true
            onActionUp = {
                form.submit()
            }
        })

        addButton(UITextButton().apply {
            text = StringTable.get(string.multiplayer_lobby_create_room_cancel)
            onActionUp = { hide() }
        })
    }
}
