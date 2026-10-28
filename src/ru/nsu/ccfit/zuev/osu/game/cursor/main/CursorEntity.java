package ru.nsu.ccfit.zuev.osu.game.cursor.main;

import androidx.annotation.NonNull;

import com.osudroid.game.cursor.trail.CursorTrail;
import com.osudroid.game.cursor.trail.FancyCursorTrail;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.LayoutConstraints;
import com.reco1l.verktex.data.Vec2;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

public class CursorEntity extends UIContainer {
    protected final CursorSprite cursorSprite;
    private final UISprite cursorMiddleSprite;
    private CursorTrail trail = null;
    private FancyCursorTrail fancyTrail = null;
    private boolean isShowing = false;

    // The position last given to the trail, see onUpdate.
    private float trailX, trailY;

    public CursorEntity() {
        cursorSprite = new CursorSprite();

        // "cursormiddle" is drawn statically on top of the cursor, unlike the cursor itself it never
        // rotates or scales on click. its entirely opt in per skin (see OPTIONAL_TEXTURES).
        var middleTexture = ResourceManager.getInstance().getTextureIfLoaded("cursormiddle") != null
                ? SkinTextures.getWithBeatmapSkin("cursormiddle")
                : null;
        if (middleTexture != null) {
            cursorMiddleSprite = new UISprite();
            cursorMiddleSprite.setAnchor(Anchor.Center);
            cursorMiddleSprite.setOrigin(OsuSkin.get().isCursorCentre() ? Anchor.Center : Anchor.TopLeft);
            // reco1l scaled around the origin.
            cursorMiddleSprite.setScaleOrigin(cursorMiddleSprite.getOrigin());
            cursorMiddleSprite.setScale(cursorSprite.baseSize);
            cursorMiddleSprite.setTextureRegion(middleTexture);
        } else {
            cursorMiddleSprite = null;
        }

        if (Config.isUseFancyCursorTrail()) {
            fancyTrail = new FancyCursorTrail();
        } else if (Config.isUseParticles()) {
            GraphicsTexture.Region trailTex = SkinTextures.getWithBeatmapSkin("cursortrail");
            trail = new CursorTrail(trailTex, cursorSprite);
        }

        plusAssign(cursorSprite);
        if (cursorMiddleSprite != null) {
            plusAssign(cursorMiddleSprite);
        }
        setVisible(false);

        // Not necessary to update by itself since it's done by GameScene, see onUpdate.
    }

    public void setShowing(boolean showing) {
        isShowing = showing;
        setVisible(showing);
        if (trail != null)
            trail.setSpawningEnabled(showing);
        if (fancyTrail != null) {
            fancyTrail.setVisible(showing);
            if (!showing) fancyTrail.resetTrail();
        }
    }

    public void click() {
        cursorSprite.handleClick();
    }

    public void update(float pSecondsElapsed) {
        if (isShowing) {
            cursorSprite.update(pSecondsElapsed);

            if (fancyTrail != null) {
                fancyTrail.setCursorX(getX().resolve(0f));
                fancyTrail.setCursorY(getY().resolve(0f));
                fancyTrail.update(pSecondsElapsed);
            }
        }

        // Runs regardless of isShowing so already-spawned trail parts keep fading out in real
        // time instead of freezing while the cursor is hidden.
        if (trail != null) {
            trail.update(pSecondsElapsed);
        }
    }

    public void attachToScene(UIContainer fgScene) {
        if (trail != null) {
            fgScene.plusAssign(trail);
        }
        if (fancyTrail != null) {
            fgScene.plusAssign(fancyTrail);
        }
        fgScene.plusAssign(this);
    }

    public void setPosition(float pX, float pY) {
        if (trail != null) {
            trail.addPosition(pX, pY);
            trailX = pX;
            trailY = pY;
        }

        setX(DimensionKt.getPx(pX));
        setY(DimensionKt.getPx(pY));

        // The position of the draw thread's fast path is replaced by this one.
        setTranslationX(DimensionKt.getPx(0f));
        setTranslationY(DimensionKt.getPx(0f));
    }

    /**
     * Places the cursor at a position from the draw thread, the raw pointer's fast path. It is given as a
     * translation from the position the next layout applies, the last one given by
     * {@link #setPosition(float, float)}, as the layout of this draw may still move the cursor there; the
     * next {@link #setPosition(float, float)} replaces it.
     */
    public void setDrawPosition(float pX, float pY) {
        if (trail != null) {
            trail.addPosition(pX, pY);
        }

        // Anchored at the top left of a layer without padding, the laid-out position is the position itself.
        setTranslationX(DimensionKt.getPx(pX - getX().resolve(0f)));
        setTranslationY(DimensionKt.getPx(pY - getY().resolve(0f)));
    }

    @Override
    protected void onUpdate(@NonNull TickingClock clock) {
        // A position set by a modifier (the auto cursor's move) does not go through setPosition, it reaches
        // the trail here, as the AndEngine modifier's setPosition did.
        if (trail != null) {
            float x = getX().resolve(0f);
            float y = getY().resolve(0f);

            if (x != trailX || y != trailY) {
                trail.addPosition(x, y);
                trailX = x;
                trailY = y;
            }
        }

        // The children's own updates, as the AndEngine onManagedUpdate in update() ran them.
        super.onUpdate(clock);
    }

    @NonNull
    @Override
    protected Vec2 onMeasure(@NonNull LayoutConstraints parentConstraints, @NonNull LayoutConstraints contentConstraints) {
        // The reco1l component had no size of its own, its children are centred on its position.
        var unbounded = new LayoutConstraints(0f, Float.POSITIVE_INFINITY, 0f, Float.POSITIVE_INFINITY);

        for (var child : this) {
            child.measure(unbounded);
        }

        return new Vec2(0f, 0f);
    }
}
