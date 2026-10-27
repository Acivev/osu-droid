package ru.nsu.ccfit.zuev.osu.menu;

import android.annotation.SuppressLint;

import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.UISprite.ScaleType;
import com.reco1l.verktex.ui.text.UIText;
import com.reco1l.verktex.data.Anchor;

import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.verktex.bridge.EngineRouter;
import com.reco1l.verktex.Verktex;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.Padding;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UIScene;

import java.util.ArrayList;

import ru.nsu.ccfit.zuev.osu.*;
import ru.nsu.ccfit.zuev.osu.GlobalManager;

public class LoadingScreen {
    private final LoadingScene scene;
    private final UIText logText;
    private final UISprite menuBg;
    private final UISprite loadingTitle;
    private final UISprite circle;
    private float percentage;

    public LoadingScreen() {
        ArrayList<String> toastLoggerLog = ToastLogger.getLog();

        if (toastLoggerLog != null) {
            toastLoggerLog.clear();
        }

        scene = new LoadingScene();
        // Default solid-color background (shown when no menu-background texture)
        scene.setBackgroundColor(new Color4(70 / 255f, 129 / 255f, 252 / 255f, 1f));

        // Background sprite
        menuBg = new UISprite();
        menuBg.setScaleType(ScaleType.Crop);
        menuBg.setWidth(DimensionKt.fill(1f));
        menuBg.setHeight(DimensionKt.fill(1f));
        scene.plusAssign(menuBg);

        // Loading title sprite
        loadingTitle = new UISprite();
        loadingTitle.setScaleType(ScaleType.Fit);
        loadingTitle.setWidth(DimensionKt.fill(1f));
        scene.plusAssign(loadingTitle);

        // Percentage log text
        logText = new UIText();
        logText.setColor(Color4.Companion.getWhite());
        logText.setAlignment(Anchor.TopCenter);
        logText.setWrapText(false);
        logText.setAnchor(Anchor.BottomCenter);
        logText.setOrigin(Anchor.TopCenter);
        scene.plusAssign(logText);
        ToastLogger.setPercentage(-1);
        percentage = -1;

        // Spinning loading circle
        circle = new UISprite();
        circle.setAnchor(Anchor.Center);
        circle.setOrigin(Anchor.Center);
        scene.plusAssign(circle);
    }

    public UIScene getScene() {
        return scene;
    }

    /**
     * Shows a new loading screen. Synchronous and callable from any thread, callers usually show it
     * from a background coroutine right before the heavy work.
     */
    public static void show() {
        // Built under the tree lock: the components' constructors read engine state (fonts, theme)
        // that the draw and update workers use.
        EngineRouter.showVerktex(Verktex.INSTANCE.withTreeLock(() -> new LoadingScreen().getScene()));
    }

    @SuppressLint("DefaultLocale")
    public void onUpdate(final float pSecondsElapsed) {
        // Fetched here rather than at construction, which can happen off the update thread; looked up
        // again on every update since a skin change unloads the regions.
        menuBg.setTextureRegion(SkinTextures.get("menu-background"));
        loadingTitle.setTextureRegion(SkinTextures.get("loading-title"));
        circle.setTextureRegion(SkinTextures.get("loading"));

        circle.setRotationZ((circle.getRotationZ() + 360f / 2f * pSecondsElapsed) % 360f);

        if (ToastLogger.getPercentage() != percentage) {
            percentage = ToastLogger.getPercentage();
            logText.setText(String.format("%d%%", (int) percentage));
        }
    }


    public class LoadingScene extends UIScene {

        @Override
        public void onPerformLayout() {
            // The legacy scene covers the whole window, the safe area is not applied.
            setPadding(Padding.Companion.getZero());

            final float scale = LegacyUnits.getScale();

            final GraphicsTexture.Region title = loadingTitle.getTextureRegion();
            loadingTitle.setHeight(DimensionKt.getPx((title != null ? title.getHeight() : 0f) * scale));

            logText.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("logFont") * scale));
            logText.setY(DimensionKt.getPx(-100 * scale));

            final GraphicsTexture.Region loading = circle.getTextureRegion();
            circle.setWidth(DimensionKt.getPx((loading != null ? loading.getWidth() : 0f) * scale));
            circle.setHeight(DimensionKt.getPx((loading != null ? loading.getHeight() : 0f) * scale));

            super.onPerformLayout();
        }

        @Override
        protected void onUpdate(final TickingClock clock) {
            LoadingScreen.this.onUpdate(clock.getElapsedTickTime());
            super.onUpdate(clock);
        }
    }
}
