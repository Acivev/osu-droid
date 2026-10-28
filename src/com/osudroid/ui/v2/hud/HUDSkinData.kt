package com.osudroid.ui.v2.hud

import com.osudroid.ui.v2.hud.elements.HUDAccuracyCounter
import com.osudroid.ui.v2.hud.elements.HUDAverageOffsetCounter
import com.osudroid.ui.v2.hud.elements.HUDBackButton
import com.osudroid.ui.v2.hud.elements.HUDBarHitErrorMeter
import com.osudroid.ui.v2.hud.elements.HUDComboCounter
import com.osudroid.ui.v2.hud.elements.HUDHealthBar
import com.osudroid.ui.v2.hud.elements.HUDPieSongProgress
import com.osudroid.ui.v2.hud.elements.HUDScoreCounter
import com.osudroid.ui.v2.hud.elements.HUDUnstableRateCounter
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Vec2
import com.osudroid.ui.v2.hud.elements.HUDLeaderboard
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KClass

data class HUDSkinData(val elements: List<HUDElementSkinData>) {


    fun hasElement(type: Class<out HUDElement>) = elements.any { it.type.java == type }


    companion object {

        /**
         * The default layout data for the HUD.
         *
         * Based on the default skin layout from osu!droid version 1.8.2.1.
         */
        @JvmField
        val Default = HUDSkinData(
            listOf(
                HUDElementSkinData(
                    type = HUDAccuracyCounter::class,
                    anchor = Anchor.TopRight,
                    origin = Anchor.TopRight,
                    scale = 0.6f * 0.96f,
                    position = Vec2(-17f, 9f)
                ),
                HUDElementSkinData(
                    type = HUDLeaderboard::class,
                    anchor = Anchor.TopLeft,
                    origin = Anchor.TopLeft,
                    position = Vec2(0f, 83f)
                ),
                HUDElementSkinData(
                    type = HUDComboCounter::class,
                    anchor = Anchor.BottomLeft,
                    origin = Anchor.BottomLeft,
                    position = Vec2(10f, -10f),
                    scale = 1.28f
                ),
                HUDElementSkinData(
                    type = HUDPieSongProgress::class,
                    anchor = Anchor.TopRight,
                    origin = Anchor.CenterRight
                ),
                HUDElementSkinData(
                    type = HUDHealthBar::class,
                    anchor = Anchor.TopLeft,
                    origin = Anchor.TopLeft
                ),
                HUDElementSkinData(
                    type = HUDScoreCounter::class,
                    anchor = Anchor.TopRight,
                    origin = Anchor.TopRight,
                    scale = 0.96f,
                    position = Vec2(-10f, 0f)
                ),
                HUDElementSkinData(
                    type = HUDBackButton::class,
                    anchor = Anchor.CenterRight,
                    origin = Anchor.CenterRight,
                    position = Vec2(-20f, 0f)
                ),
                HUDElementSkinData(
                    type = HUDUnstableRateCounter::class,
                    anchor = Anchor.BottomRight,
                    origin = Anchor.BottomRight,
                    position = Vec2(-10f, -32f)
                ),
                HUDElementSkinData(
                    type = HUDAverageOffsetCounter::class,
                    anchor = Anchor.BottomRight,
                    origin = Anchor.BottomRight,
                    position = Vec2(-10f, -59f)
                ),
                HUDElementSkinData(
                    type = HUDBarHitErrorMeter::class,
                    anchor = Anchor.BottomCenter,
                    origin = Anchor.BottomCenter
                )
            )
        )


        @JvmStatic
        fun writeToJSON(data: HUDSkinData) = JSONArray().apply {
            data.elements.forEach {
                put(JSONObject().apply {
                    put("type", HUDElements[it.type].name)
                    put("x", it.position.x)
                    put("y", it.position.y)
                    put("anchor", AnchorNames.getName(it.anchor))
                    put("origin", AnchorNames.getName(it.origin))
                    put("scale", it.scale)
                })
            }
        }

        @JvmStatic
        fun readFromJSON(json: JSONArray) = HUDSkinData(
            elements = MutableList(json.length()) { i ->

                val element = json.getJSONObject(i)

                HUDElementSkinData(
                    type = HUDElements[element.getString("type")]?.type ?: return@MutableList null,
                    position = Vec2(
                        element.optDouble("x", 0.0).toFloat(),
                        element.optDouble("y", 0.0).toFloat(),
                    ),
                    anchor = AnchorNames.getFromName(element.optString("anchor", "Center")),
                    origin = AnchorNames.getFromName(element.optString("origin", "Center")),
                    scale = element.optDouble("scale", 1.0).toFloat(),
                )
            }.filterNotNull()
        )


    }

}

data class HUDElementSkinData(

    /**
     * The type of the element.
     */
    val type: KClass<out HUDElement>,

    /**
     * The offset of the element from the constraint.
     */
    val position: Vec2 = Vec2.Zero,

    /**
     * The anchor of the element.
     */
    val anchor: Vec2 = Anchor.Center,

    /**
     * The origin of the element.
     */
    val origin: Vec2 = Anchor.Center,

    /**
     * The scale applied to the element.
     */
    val scale: Float = 1f
)

/**
 * The anchor list and names the AndEngine `Anchor` object had, which the Verktex one does not.
 */
internal object AnchorNames {

    val all by lazy {
        listOf(
            Anchor.TopLeft,
            Anchor.TopCenter,
            Anchor.TopRight,
            Anchor.CenterLeft,
            Anchor.Center,
            Anchor.CenterRight,
            Anchor.BottomLeft,
            Anchor.BottomCenter,
            Anchor.BottomRight
        )
    }

    fun getName(anchor: Vec2, throwIfUnknown: Boolean = true): String {
        return when (anchor) {
            Anchor.TopLeft -> "TopLeft"
            Anchor.TopCenter -> "TopCenter"
            Anchor.TopRight -> "TopRight"
            Anchor.CenterLeft -> "CenterLeft"
            Anchor.Center -> "Center"
            Anchor.CenterRight -> "CenterRight"
            Anchor.BottomLeft -> "BottomLeft"
            Anchor.BottomCenter -> "BottomCenter"
            Anchor.BottomRight -> "BottomRight"
            else -> if (throwIfUnknown) throw IllegalArgumentException("Unknown anchor point: $anchor") else "${anchor.x},${anchor.y}"
        }
    }

    fun getFromName(name: String): Vec2 {
        return when (name) {
            "TopLeft" -> Anchor.TopLeft
            "TopCenter" -> Anchor.TopCenter
            "TopRight" -> Anchor.TopRight
            "CenterLeft" -> Anchor.CenterLeft
            "Center" -> Anchor.Center
            "CenterRight" -> Anchor.CenterRight
            "BottomLeft" -> Anchor.BottomLeft
            "BottomCenter" -> Anchor.BottomCenter
            "BottomRight" -> Anchor.BottomRight
            else -> throw IllegalArgumentException("Unknown anchor point: $name")
        }
    }

}
