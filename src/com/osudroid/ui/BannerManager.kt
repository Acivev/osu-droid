package com.osudroid.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.osudroid.ui.verktex.IPressable
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.framework.net.JsonArrayRequest
import com.reco1l.framework.net.WebRequest
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.online.OnlineManager
import java.io.File

object BannerManager {

    private const val BANNER_DURATION = 10f // Seconds


    /**
     * Downloads the banners into the cache. Network, call off the update thread; the result goes
     * to [loadBannerSprite] on the update thread.
     */
    @JvmStatic
    fun downloadBanners(): List<DownloadedBanner> {

        val banners = mutableListOf<DownloadedBanner>()

        try {

            val bannersFolder = File(Config.getCachePath(), "banners").apply {
                delete()
                mkdirs()
            }

            JsonArrayRequest(OnlineManager.endpoint + "game/banner.php").use { request ->
                request.execute()

                for (i in 0 until request.json.length()) {

                    try {
                        val banner = request.json.getJSONObject(i)

                        val url = banner.getString("Url")
                        val imageLink = banner.getString("ImageLink")

                        WebRequest(imageLink).use { imageRequest ->

                            val bannerFile = File(bannersFolder, "${i}.png")
                            bannerFile.createNewFile()

                            imageRequest.execute()
                            imageRequest.responseBody.byteStream().use { input ->
                                bannerFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            banners.add(DownloadedBanner(i, url, bannerFile))
                        }

                    } catch (e: Exception) {
                        Log.e("BannerManager", "Failed to get banner at index ${i}.", e)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("BannerManager", "Failed to get banners from server.", e)
        }

        return banners
    }

    /**
     * Loads the downloaded banners into textures and creates their sprite, null when none loaded.
     * Must be called on the update thread.
     */
    @JvmStatic
    fun loadBannerSprite(downloaded: List<DownloadedBanner>): BannerSprite? {

        val banners = mutableListOf<Banner>()

        for (banner in downloaded) {
            val image = try {
                SkinTextures.loadFile("banner@${banner.index}", banner.file)
            } catch (e: Exception) {
                Log.e("BannerManager", "Failed to load banner at index ${banner.index}.", e)
                null
            }

            if (image != null) {
                banners.add(Banner(banner.url, image))
            }
        }

        if (banners.isEmpty()) {
            return null
        }

        return BannerSprite(banners)
    }


    data class DownloadedBanner(
        val index: Int,
        val url: String,
        val file: File,
    )

    data class Banner(
        val url: String,
        val image: GraphicsTexture.Region,
    )

    /**
     * The server banners at the bottom-right of the main menu, cycled every [BANNER_DURATION] seconds.
     * Opens the banner's URL when tapped. Sized in [performLayout] by the host scene.
     */
    class BannerSprite(private val banners: List<Banner>) : UISprite(), IPressable {


        private var currentBannerIndex = 0

        private var elapsedTimeSinceLastChange = 0f

        /** Whether the next banner is swapped in once the fade out finished. */
        private var isChanging = false

        private val fade = FloatAnimation(0f, 0f)
        private val pressScale = FloatAnimation(1f, 0f)

        /** The finger pressing the banner, -1 while it is not pressed. */
        private var pressPointerId = -1


        init {
            anchor = Anchor.BottomRight
            origin = Anchor.BottomRight
            scaleOrigin = Anchor.BottomRight

            textureRegion = banners[currentBannerIndex].image
            alpha = 0f
            fade.animateTo(1f, 0.75f, Easing.None)
        }


        /**
         * Applies the texture's virtual-unit size in window pixels. Called by the host scene's
         * onPerformLayout().
         */
        fun performLayout() {
            width = (textureRegion?.width ?: 0f).vu.px
            height = (textureRegion?.height ?: 0f).vu.px
        }


        override fun onUpdate(clock: TickingClock) {
            if (banners.size > 1) {

                if (elapsedTimeSinceLastChange > BANNER_DURATION) {
                    elapsedTimeSinceLastChange %= BANNER_DURATION

                    currentBannerIndex++
                    currentBannerIndex %= banners.size

                    isChanging = true
                    fade.animateTo(0f, 0.5f, Easing.None)
                }
                elapsedTimeSinceLastChange += clock.elapsedTickTime
            }

            alpha = fade.update(clock)

            if (isChanging && alpha <= 0f) {
                isChanging = false
                textureRegion = banners[currentBannerIndex].image
                fade.animateTo(1f, 0.5f, Easing.None)
            }

            scale = pressScale.update(clock)

            super.onUpdate(clock)
        }

        override fun onInputEvent(event: InputEvent): Boolean {
            if (event !is PointerEvent) return false

            if (event.action == PointerEvent.Action.Down) {
                if (pressPointerId != -1) return false

                pressPointerId = event.pointerId
                elapsedTimeSinceLastChange = 0f
                pressScale.animateTo(0.95f, 0.1f, Easing.None)
                return true
            }

            if (event.pointerId != pressPointerId) return false

            elapsedTimeSinceLastChange = 0f

            when (event.action) {
                PointerEvent.Action.Up -> {
                    pressPointerId = -1
                    pressScale.animateTo(1f, 0.1f, Easing.None)

                    val banner = banners[currentBannerIndex]

                    // Released off the banner: the press is cancelled.
                    if (containsScenePoint(event.x, event.y) && banner.url.isNotBlank()) {
                        val uri = Uri.parse(banner.url)
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        GlobalManager.getInstance().mainActivity.startActivity(intent)
                    }
                    return true
                }
                PointerEvent.Action.Unknown -> {
                    // Cancel events are dispatched to every component, so they are not consumed.
                    cancelPress()
                    return false
                }
                else -> return true
            }
        }

        override fun cancelPress() {
            pressPointerId = -1
            pressScale.animateTo(1f, 0.1f, Easing.None)
        }
    }

}
