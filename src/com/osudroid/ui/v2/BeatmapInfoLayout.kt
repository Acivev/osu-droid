package com.osudroid.ui.v2

import android.util.Log
import com.osudroid.GameMode
import com.osudroid.beatmaps.BeatmapCache
import com.osudroid.data.*
import com.osudroid.multiplayer.api.data.*
import com.osudroid.ui.v2.modmenu.*
import com.osudroid.ui.verktex.*
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.utils.async
import com.osudroid.utils.updateThread
import com.osudroid.ui.verktex.SizeVariant
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.*
import com.reco1l.verktex.ui.container.*
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.text.*
import com.reco1l.framework.math.roundBy
import com.osudroid.utils.applyMods
import com.osudroid.utils.calculateRate
import kotlinx.coroutines.Job
import ru.nsu.ccfit.zuev.osu.*
import java.text.*
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

class BeatmapInfoLayout : UILinearContainer() {


    private lateinit var titleText: UIText
    private lateinit var artistText: UIText
    private lateinit var creatorText: UIText

    private lateinit var lengthBadge: LegacyBadge
    private lateinit var bpmBadge: LegacyBadge

    private lateinit var arText: LegacyLabeledBadge
    private lateinit var csText: LegacyLabeledBadge
    private lateinit var odText: LegacyLabeledBadge
    private lateinit var hpText: LegacyLabeledBadge

    private lateinit var circlesBadge: LegacyLabeledBadge
    private lateinit var slidersBadge: LegacyLabeledBadge
    private lateinit var spinnersBadge: LegacyLabeledBadge

    private lateinit var starRatingBadge: StarRatingBadge
    private lateinit var versionText: UIText

    private var calculationJob: Job? = null
    private var displayedBeatmap: BeatmapInfo? = null


    init {
        width = fill()
        orientation = Orientation.Vertical
        spacing = 6f.vu.px

        // The flex row: the column grows into the space the badge leaves.
        linearContainer {
            width = fill()

            linearContainer {
                width = fill()
                orientation = Orientation.Vertical
                padding = Padding(0f.px, 0f.px, 12f.vu.px, 0f.px)

                artistText = text {
                    width = fill()
                    fontSize = LegacyUnits.fontSize("smallFont").vu.px
                    wrapText = false
                    // The AndEngine UIText scrolled overflowing text horizontally by default.
                    autoScrollAxes = Axis.X
                    autoScrollSpeed = 15f.vu
                    text = "Unknown"
                    style = Style { theme -> color = theme.palette.accent * 0.9f }
                    clipping = ClipMode.Bounds
                }

                titleText = text {
                    width = fill()
                    fontSize = LegacyUnits.fontSize("smallFont").vu.px
                    wrapText = false
                    // The AndEngine UIText scrolled overflowing text horizontally by default.
                    autoScrollAxes = Axis.X
                    autoScrollSpeed = 15f.vu
                    text = "No selected beatmap"
                    style = Style { theme -> color = theme.palette.accent }
                    clipping = ClipMode.Bounds
                }

                versionText = text {
                    width = fill()
                    fontSize = LegacyUnits.fontSize("smallFont").vu.px
                    wrapText = false
                    // The AndEngine UIText scrolled overflowing text horizontally by default.
                    autoScrollAxes = Axis.X
                    autoScrollSpeed = 15f.vu
                    text = "Unknown"
                    style = Style { theme -> color = theme.palette.accent * 0.8f }
                    clipping = ClipMode.Bounds
                }

                creatorText = text {
                    fontSize = LegacyUnits.fontSize("smallFont").vu.px
                    wrapText = false
                    // The AndEngine UIText scrolled overflowing text horizontally by default.
                    autoScrollAxes = Axis.X
                    autoScrollSpeed = 15f.vu
                    text = "Unknown"
                    style = Style { theme -> color = theme.palette.accent * 0.8f }
                }
            }

            starRatingBadge = StarRatingBadge()
            +starRatingBadge
        }

        linearContainer {
            orientation = Orientation.Vertical
            spacing = 4f.vu.px

            linearContainer {
                orientation = Orientation.Horizontal
                spacing = 4f.vu.px

                circlesBadge = legacyLabeledBadge {
                    label = "Circles"
                    value = "0"
                    sizeVariant = SizeVariant.Small
                }
                slidersBadge = legacyLabeledBadge {
                    label = "Sliders"
                    value = "0"
                    sizeVariant = SizeVariant.Small
                }
                spinnersBadge = legacyLabeledBadge {
                    label = "Spinners"
                    value = "0"
                    sizeVariant = SizeVariant.Small
                }
                lengthBadge = legacyBadge {
                    setLeadingIcon("clock", "clock.png", SkinTextures::getAsset)
                    text = "00:00"
                    sizeVariant = SizeVariant.Small
                }
            }

            linearContainer {
                orientation = Orientation.Horizontal
                spacing = 4f.vu.px

                arText = legacyLabeledBadge {
                    label = "AR"
                    value = "0.00"
                    sizeVariant = SizeVariant.Small
                }
                odText = legacyLabeledBadge {
                    label = "OD"
                    value = "0.00"
                    sizeVariant = SizeVariant.Small
                }
                csText = legacyLabeledBadge {
                    label = "CS"
                    value = "0.00"
                    sizeVariant = SizeVariant.Small
                }
                hpText = legacyLabeledBadge {
                    label = "HP"
                    value = "0.00"
                    sizeVariant = SizeVariant.Small
                }
                bpmBadge = legacyBadge {
                    setLeadingIcon("bpm", "bpm.png", SkinTextures::getAsset)
                    text = "0"
                    fontSize = LegacyUnits.fontSize("xs").vu.px
                    sizeVariant = SizeVariant.Small
                }
            }
        }
    }


    fun setBeatmapInfo(beatmapInfo: BeatmapInfo?) {
        titleText.text = beatmapInfo?.titleText ?: "No selected beatmap"
        artistText.text = beatmapInfo?.artistText ?: "Unknown"
        creatorText.text = "Mapped by ${beatmapInfo?.creator ?: "Unknown"}"
        versionText.text = beatmapInfo?.version ?: "Unknown"
        setDifficultyStatistics(beatmapInfo)
    }

    fun setBeatmapInfo(roomBeatmap: RoomBeatmap?) {
        titleText.text = roomBeatmap?.title ?: "No selected beatmap"
        artistText.text = roomBeatmap?.artist ?: "Unknown"
        creatorText.text = "Mapped by ${roomBeatmap?.creator ?: "Unknown"}"
        versionText.text = roomBeatmap?.version ?: "Unknown"
        setDifficultyStatistics(null)
    }

    /**
     * Change the displayed difficulty statistics.
     */
    fun setDifficultyStatistics(beatmapInfo: BeatmapInfo?) {
        displayedBeatmap = beatmapInfo
        calculationJob?.cancel()
        calculationJob = null

        if (beatmapInfo == null) {
            circlesBadge.value = "0"
            slidersBadge.value = "0"
            spinnersBadge.value = "0"
            arText.value = "0.00"
            odText.value = "0.00"
            csText.value = "0.00"
            hpText.value = "0.00"
            starRatingBadge.rating = 0.0
            bpmBadge.text = "0"
            lengthBadge.text = "00:00"
            return
        }

        updateDisplay(beatmapInfo)

        if (beatmapInfo.needsDifficultyCalculation) {
            calculationJob = async {
                val beatmap = try {
                    BeatmapCache.getBeatmap(
                        beatmapInfo,
                        true,
                        Config.getDifficultyAlgorithm().toGameMode(),
                        this
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        throw e
                    }

                    Log.e("BeatmapInfoLayout", "Failed to calculate difficulty for ${beatmapInfo.filename}", e)
                    null
                }

                ensureActive()

                if (beatmap != null) {
                    val newInfo = BeatmapInfo(beatmap, beatmapInfo.dateImported, true, this)
                    beatmapInfo.apply(newInfo)
                    DatabaseManager.beatmapInfoTable.update(newInfo)

                    ensureActive()

                    updateThread {
                        Verktex.withTreeLock {
                            if (displayedBeatmap === beatmapInfo) {
                                updateDisplay(beatmapInfo)
                            }
                        }
                    }
                }
            }
        }
    }

    fun cancelCalculation() {
        calculationJob?.cancel()
        calculationJob = null
    }

    private fun updateDisplay(beatmapInfo: BeatmapInfo) {
        circlesBadge.value = beatmapInfo.hitCircleCount.toString()
        slidersBadge.value = beatmapInfo.sliderCount.toString()
        spinnersBadge.value = beatmapInfo.spinnerCount.toString()

        val mods = ModMenu.enabledMods
        val totalSpeedMultiplier = mods.values.calculateRate(Double.POSITIVE_INFINITY)

        val difficulty = beatmapInfo.getBeatmapDifficulty()
        difficulty.applyMods(GameMode.Droid, mods.values, true)

        val minBpm = (beatmapInfo.bpmMin * totalSpeedMultiplier).roundToInt()
        val maxBpm = (beatmapInfo.bpmMax * totalSpeedMultiplier).roundToInt()
        val commonBpm = (beatmapInfo.mostCommonBPM * totalSpeedMultiplier).roundToInt()
        val length = (beatmapInfo.length / totalSpeedMultiplier).toLong()

        lengthBadge.text = formatLength(length)

        bpmBadge.text = formatBpm(minBpm, maxBpm, commonBpm)

        // Round to 2 decimal places.
        arText.value = difficulty.ar.roundBy(2).toString()
        odText.value = difficulty.od.roundBy(2).toString()
        csText.value = difficulty.difficultyCS.roundBy(2).toString()
        hpText.value = difficulty.hp.roundBy(2).toString()
        starRatingBadge.rating = beatmapInfo.getStarRating().toDouble()
    }


    companion object {
        internal fun formatLength(length: Long): String {
            val sdf = SimpleDateFormat(if (length > 3600 * 1000) "HH:mm:ss" else "mm:ss")
            sdf.timeZone = TimeZone.getTimeZone("GMT+0")
            return sdf.format(length)
        }

        internal fun formatBpm(minBpm: Int, maxBpm: Int, commonBpm: Int) =
            if (minBpm == maxBpm) commonBpm.toString() else "$minBpm-$maxBpm ($commonBpm)"
    }
}

