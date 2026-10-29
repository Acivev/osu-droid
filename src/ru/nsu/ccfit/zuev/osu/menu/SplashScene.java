package ru.nsu.ccfit.zuev.osu.menu;

import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.utils.Execution;

import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.Padding;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.math.Easing;
import com.reco1l.verktex.math.FloatAnimation;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UIScene;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.text.UIText;

import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.ResourceManager;

/**
 * Created by Fuuko on 2015/4/25.
 */
public class SplashScene extends UIScene {

    public static final SplashScene INSTANCE = new SplashScene();
    private UIText infoText;
    private UIText progressText;
    private UISprite mLoading;
    private UISprite welcomeSprite;
    private boolean mStarting = true;

    private final FloatAnimation loadingAlpha = new FloatAnimation(0f, 0.2f, Easing.Companion.getNone());
    private final FloatAnimation welcomeAlpha = new FloatAnimation(0f, 2.5f, Easing.Companion.getNone());
    private final FloatAnimation welcomeScaleY = new FloatAnimation(0f, 0.25f, Easing.Companion.getNone());
    private final FloatAnimation welcomeScale = new FloatAnimation(1f, 2.25f, Easing.Companion.getNone());

    public SplashScene() {
        // GL-bound entities (Sprite, Text) are NOT created here because this constructor
        // runs as a Java static field initializer at class-load time, before any engine or
        // ResourceManager is set up.  Call initialize() explicitly once resources are ready.
    }

    /**
     * Creates (or recreates) all GL-bound child entities using the current engine and
     * ResourceManager.  Must be called after ResourceManager.Init() and the initial font/
     * texture loads, and before the scene is set on the engine.
     *
     * Calling this more than once (e.g. on Activity re-creation within the same process)
     * safely detaches stale entities and rebuilds them against the live engine resources,
     * preventing black fonts and broken textures caused by stale GL references.
     */
    public void initialize() {
        clearChildren();
        welcomeSprite = null;
        mStarting = true;
        loadingAlpha.jumpTo(0f);
        welcomeAlpha.jumpTo(0f);
        welcomeScaleY.jumpTo(0f);
        welcomeScale.jumpTo(1f);
        initializeLoading();
        initializeProgress();
        initializeInfo();
    }

    private void initializeLoading() {
        var loadTex = SkinTextures.get("loading_start", "gfx/loading.png");

        mLoading = new UISprite(loadTex);
        mLoading.setAnchor(Anchor.Center);
        mLoading.setOrigin(Anchor.Center);
        mLoading.setScale(0.4f);
        mLoading.setAlpha(0);

        plusAssign(mLoading);
    }

    private void initializeInfo() {
        infoText = new UIText();
        infoText.setColor(Color4.Companion.getWhite());
        infoText.setAlignment(Anchor.TopCenter);
        infoText.setWrapText(false);
        infoText.setAnchor(Anchor.BottomCenter);
        infoText.setOrigin(Anchor.BottomCenter);
        infoText.setScale(0.6f);
        plusAssign(infoText);
    }

    public UIScene getScene() { return this; }

    public void playWelcomeAnimation()
    {
        Execution.updateThreadUnderTreeLock(() -> {
            mStarting = false;

            loadingAlpha.animateTo(0f, 0.2f, Easing.Companion.getNone());

            // ChangeableText isn't compatible with animations unfortunately (still valid?)
            infoText.removeSelf();
            progressText.removeSelf();
        });

        try
        {
            Thread.sleep(220);
        }
        catch (InterruptedException ignored)
        {
        }

        Execution.updateThreadUnderTreeLock(() -> {
            var welcomeTex = SkinTextures.get("welcome", "gfx/welcome.png");
            welcomeSprite = new UISprite(welcomeTex);

            var welcomeSound = ResourceManager.getInstance().getSound("welcome");
            var welcomePiano = ResourceManager.getInstance().getSound("welcome_piano");

            welcomeSprite.setAnchor(Anchor.Center);
            welcomeSprite.setOrigin(Anchor.Center);
            welcomeSprite.setAlpha(0);
            welcomeSprite.setScaleY(0);
            plusAssign(welcomeSprite);
            welcomeSound.play();
            welcomePiano.play();

            welcomeAlpha.animateTo(1f, 2.5f, Easing.Companion.getNone());
            welcomeScaleY.animateTo(1f, 0.25f, Easing.Companion.getNone());
        });
    }

    private void initializeProgress() {
        progressText = new UIText();
        progressText.setColor(Color4.Companion.getWhite());
        progressText.setAlignment(Anchor.TopCenter);
        progressText.setWrapText(false);
        progressText.setAnchor(Anchor.Center);
        progressText.setOrigin(Anchor.TopCenter);
        progressText.setText("0 %");
        progressText.setScale(0.5f);
        plusAssign(progressText);
    }

    @Override
    public void onPerformLayout() {
        // The legacy scene covers the whole window, the safe area is not applied.
        setPadding(Padding.Companion.getZero());

        final float scale = LegacyUnits.getScale();
        final var fontSize = DimensionKt.getPx(LegacyUnits.fontSize("font") * scale);
        progressText.setFontSize(fontSize);
        infoText.setFontSize(fontSize);
        infoText.setY(DimensionKt.getPx(-20 * scale));

        setLegacySize(mLoading);
        final GraphicsTexture.Region loadTex = mLoading.getTextureRegion();
        progressText.setY(DimensionKt.getPx((loadTex != null ? loadTex.getHeight() : 0f) / 4f * scale));

        if (welcomeSprite != null) {
            setLegacySize(welcomeSprite);
        }

        super.onPerformLayout();
    }

    private static void setLegacySize(final UISprite sprite) {
        final GraphicsTexture.Region region = sprite.getTextureRegion();
        sprite.setWidth(DimensionKt.getPx((region != null ? region.getWidth() : 0f) * LegacyUnits.getScale()));
        sprite.setHeight(DimensionKt.getPx((region != null ? region.getHeight() : 0f) * LegacyUnits.getScale()));
    }

    @Override
    public void onUpdate(final TickingClock clock) {
        if (progressText == null || infoText == null || mLoading == null) {
            // initialize() has not been called yet; nothing to update.
            return;
        }
        // Looked up again on every update: a skin change unloads the regions (GlobalManager.init loads the skin during the splash).
        mLoading.setTextureRegion(SkinTextures.get("loading_start", "gfx/loading.png"));
        if (welcomeSprite != null) {
            welcomeSprite.setTextureRegion(SkinTextures.get("welcome", "gfx/welcome.png"));
        }

        mLoading.setRotationZ((mLoading.getRotationZ() + 360f / 2f * clock.getElapsedTickTime()) % 360f);

        float progress = GlobalManager.getInstance().getLoadingProgress();
        if (mStarting)
        {
            loadingAlpha.jumpTo(Math.min(1f, loadingAlpha.getValue() + 0.1f));
        }
        mLoading.setAlpha(loadingAlpha.update(clock));

        progressText.setText(String.format("%.0f %%", progress));

        if (GlobalManager.getInstance().getInfo() != null) {
            infoText.setText(GlobalManager.getInstance().getInfo());
        }

        if (welcomeSprite != null) {
            welcomeSprite.setAlpha(welcomeAlpha.update(clock));

            final float stretch = welcomeScaleY.update(clock);
            if (stretch >= 1f) {
                welcomeScale.animateTo(1.1f, 2.25f, Easing.Companion.getNone());
            }
            final float grow = welcomeScale.update(clock);
            welcomeSprite.setScaleX(grow);
            welcomeSprite.setScaleY(stretch * grow);
        }

        super.onUpdate(clock);
    }
}

