package com.osudroid.verktex.multi

import com.osudroid.multiplayer.api.data.Room
import com.osudroid.multiplayer.api.data.RoomGameplaySettings
import com.osudroid.multiplayer.api.data.RoomMods
import com.osudroid.multiplayer.api.data.RoomStatus
import com.osudroid.multiplayer.api.data.TeamMode
import com.osudroid.multiplayer.api.data.WinCondition
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import java.io.File
import org.robolectric.RuntimeEnvironment
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.helper.StringTable

internal class TestClock(override val elapsedTickTime: Float = 0.016f) : TickingClock() {
    override val lastTickTime = 0f
    override val time = 0f
    override val isRunning = true
}

internal object LobbyTestSupport {

    private val textures = listOf("lock.png", "unlock.png", "back-arrow.png", "plus.png", "refresh.png", "search-small.png")

    private var resWidth = 0
    private var resHeight = 0
    private var skinPath: String? = null
    private var previousFont: FontFamily? = null
    private val previousTextureSizes = mutableMapOf<String, Pair<Float, Float>?>()

    // The static field behind the lateinit BuiltIn.Fonts.Default, to restore it unset.
    private val defaultFontField = BuiltIn.Fonts::class.java.getDeclaredField("Default").apply { isAccessible = true }

    fun setUp() {
        val provider = VerktexTestFixtures.ensureInitialized()

        // UIText takes the default family at construction. Nothing is measured or drawn here, so no glyph is created.
        previousFont = defaultFontField.get(null) as FontFamily?
        if (previousFont == null) {
            BuiltIn.Fonts.Default = FontFamily("test", object : FontFamily.Source {
                override fun onCreateFont(descriptor: Font.Descriptor): Font = object : Font() {
                    override val descriptor = descriptor
                    override val metrics = Font.Metrics(-10f, 4f, 0f)
                    override val isKerningSupported = false
                    override fun onCreateGlyph(codePoint: Int): Glyph = error("No glyphs in JVM tests")
                    override fun onCreatePage(): GraphicsTexture = error("No glyphs in JVM tests")
                    override fun getKerning(first: Int, second: Int) = 0f
                }
            })
        }

        // The textures the lobby and its room buttons look up; the skin directory does not exist.
        textures.forEach {
            previousTextureSizes[it] = provider.textureSizes[it]
            provider.textureSizes[it] = 64f to 64f
        }

        StringTable.setContext(RuntimeEnvironment.getApplication())

        resWidth = Config.getRES_WIDTH()
        resHeight = Config.getRES_HEIGHT()
        skinPath = Config.getSkinPath()
        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)
        Config.setSkinPath(File("no-such-skin").absolutePath + "/")
    }

    fun tearDown() {
        Config.setRES_WIDTH(resWidth)
        Config.setRES_HEIGHT(resHeight)
        Config.setSkinPath(skinPath)

        val provider = VerktexTestFixtures.ensureInitialized()
        previousTextureSizes.forEach { (path, size) ->
            if (size == null) provider.textureSizes.remove(path) else provider.textureSizes[path] = size
        }
        previousTextureSizes.clear()

        defaultFontField.set(null, previousFont)
        previousFont = null
    }

    fun room(
        isLocked: Boolean = false,
        playerCount: Int = 0,
        maxPlayers: Int = 8,
        playerNames: String = ""
    ) = Room(
        id = 1,
        name = "Test Room",
        isLocked = isLocked,
        maxPlayers = maxPlayers,
        mods = RoomMods(),
        gameplaySettings = RoomGameplaySettings(isFreeMod = false, isRemoveSliderLock = false),
        teamMode = TeamMode.HeadToHead,
        winCondition = WinCondition.ScoreV1,
        playerCount = playerCount,
        playerNames = playerNames,
        status = RoomStatus.Idle
    )
}

internal fun UIComponent.descendants(): Sequence<UIComponent> = sequence {
    yield(this@descendants)
    if (this@descendants is UIContainer) {
        for (child in this@descendants.toList()) {
            yieldAll(child.descendants())
        }
    }
}
