package ru.nsu.ccfit.zuev.osu.game;

import com.osudroid.ui.v2.hud.GameplayHUD;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.rian.andengine.modifier.ModifierRegistry;
import com.rian.andengine.modifier.VerktexModifiers;
import com.reco1l.verktex.Verktex;
import kotlin.Unit;
import com.osudroid.multiplayer.Multiplayer;
import org.andengine.entity.primitive.Rectangle;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

public class BreakAnimator extends GameObject {
    private final UIContainer scene;
    private final StatisticV2 stat;
    private final UISprite[] arrows = new UISprite[4];
    private Rectangle dimRectangle;
    private final GameplayHUD hud;
    private float length;
    private float time;
    private UISprite passfail;
    private String ending;
    private UISprite mark = null;
    private boolean isbreak = false;
    private boolean over = false;
    private float dimBrightness = Config.getBackgroundBrightness();

    public BreakAnimator(final UIContainer scene, final StatisticV2 stat, GameplayHUD hud) {
        length = 0;
        this.scene = scene;
        this.stat = stat;
        this.hud = hud;

        for (int i = 0; i < 4; i++) {
            final GraphicsTexture.Region arrowRegion = SkinTextures.getWithBeatmapSkin("play-warningarrow");
            arrows[i] = new UISprite(new GraphicsTexture.Region(arrowRegion.getTexture(), arrowRegion.getSource(),
                    arrowRegion.getX(), arrowRegion.getY(), arrowRegion.getWidth(), arrowRegion.getHeight()));
            // The AndEngine LoopEntityModifier is started with each attach in init(), as Verktex drops the
            // modifiers of a detached component.
            if (i > 1) {
                arrows[i].setFlippedHorizontal(true);
            }
        }
        setArrowPosition(arrows[0], Utils.toRes(64), Utils.toRes(72));
        setArrowPosition(arrows[1], Utils.toRes(64), Config.getRES_HEIGHT()
                - arrows[1].getTextureRegion().getHeight());
        setArrowPosition(arrows[2], Config.getRES_WIDTH() - arrows[1].getTextureRegion().getWidth()
                - Utils.toRes(64), Utils.toRes(72));
        setArrowPosition(arrows[3], Config.getRES_WIDTH() - arrows[1].getTextureRegion().getWidth()
                        - Utils.toRes(64),
                Config.getRES_HEIGHT() - arrows[1].getTextureRegion().getHeight());

    }

    public void setDimRectangle(Rectangle dimRectangle) {
        this.dimRectangle = dimRectangle;
    }

    public void setDimBrightness(float brightness) {
        dimBrightness = brightness;
    }

    public boolean isBreak() {
        return isbreak;
    }

    public boolean isOver() {
        final boolean isover = over;
        over = false;
        return isover;
    }

    public void reset() {
        isbreak = false;
        over = false;
        length = 0;
        time = 0;

        if (mark != null) {
            underTreeLock(mark::removeSelf);
            mark = null;
        }

        if (passfail != null) {
            underTreeLock(passfail::removeSelf);
            passfail = null;
        }

        for (final UISprite sp : arrows) {
            sp.removeSelf();
        }

        resumeBgFade();
    }

    public void init(final float length) {
        init(length, 0);
    }

    public void init(final float length, final float initialTime) {
        if (this.length > 0 && time < this.length) {
            return;
        }
        isbreak = true;
        over = false;
        this.length = length;
        time = initialTime;
        ending = stat.getHp() > 0.5f ? "pass" : "fail";

        passfail = new UISprite();
        passfail.setOrigin(Anchor.Center);
        setLegacyPosition(passfail, Config.getRES_WIDTH() / 2f, Config.getRES_HEIGHT() / 2f);
        setLegacyTexture(passfail, SkinTextures.getWithBeatmapSkin("section-" + ending));

        final UISprite passfailSprite = passfail;
        underTreeLock(() -> hud.addToBack(passfailSprite));
        passfail.setVisible(false);

        for (int i = 0; i < 4; i++) {
            // If we are seeking into a break that is already within 1 second of ending,
            // the warning arrows should be visible immediately.
            boolean arrowsVisible = length - initialTime <= 1;
            arrows[i].setVisible(arrowsVisible);
            ModifierRegistry.setPaused(arrows[i], !arrowsVisible);
            scene.addToBack(arrows[i]);
            VerktexModifiers.clearModifiers(arrows[i]);
            loopArrow(arrows[i]);
        }

        final GraphicsTexture.Region zeroRect = SkinTextures.getWithPrefix(OsuSkin.get().getScorePrefix(), "0");
        final float zeroWidth = zeroRect != null ? zeroRect.getWidth() : 0f;

        mark = new UISprite();
        setLegacyTexture(mark, getRankingTexture());
        setLegacyPosition(mark, Config.getRES_WIDTH() - zeroWidth * 11, 5);
        // The AndEngine sprite scaled around its top left corner.
        mark.setScaleOrigin(Anchor.TopLeft);
        mark.setScale(1.2f);
        final UISprite markSprite = mark;
        underTreeLock(() -> hud.addToBack(markSprite));

        // Apply the background brightness that matches the current position within the break.
        // The update() loop only uses crossing-event checks to set this, so we must prime it
        // here for seek cases where time starts past those thresholds.
        if (length > 1) {
            if (initialTime < 0.5f) {
                setBgFade(initialTime * 2);
            } else if (length - initialTime < 0.5f) {
                setBgFade((length - initialTime) * 2);
            } else {
                setBgFade(1);
            }
        }
    }

    private void setBgFade(float percent) {
        if (dimRectangle != null && !Config.isNoChangeDimInBreaks()) {
            dimRectangle.setAlpha((1 - dimBrightness) * (1 - percent));
        }
    }

    private void resumeBgFade() {
        if (dimRectangle != null && !Config.isNoChangeDimInBreaks()) {
            dimRectangle.setAlpha(1 - dimBrightness);
        }
    }

    private GraphicsTexture.Region getRankingTexture() {
        return SkinTextures.getWithBeatmapSkin("ranking-" + stat.getMark() + "-small");
    }

    /**
     * Runs a Verktex tree edit or modifier start under the tree lock.
     */
    private static void underTreeLock(final Runnable action) {
        Verktex.INSTANCE.withTreeLock(() -> {
            action.run();
            return Unit.INSTANCE;
        });
    }

    /**
     * The AndEngine sprite position of a break arrow, which is in the playfield's virtual units.
     */
    private static void setArrowPosition(final UISprite arrow, final float x, final float y) {
        arrow.setX(DimensionKt.getPx(x));
        arrow.setY(DimensionKt.getPx(y));
    }

    /**
     * The AndEngine {@code LoopEntityModifier(Sequence(FadeIn 0.05, Delay 0.1, FadeOut 0.1))}: the
     * sequence starts again when it ends.
     */
    private static void loopArrow(final UISprite arrow) {
        VerktexModifiers.beginModifierSequence(arrow, sequence -> sequence
                .fadeInFromZero(0.05f)
                .then(0.1f)
                .fadeOutFromOne(0.1f)
                .after(e -> loopArrow(arrow)));
    }

    /**
     * The AndEngine sprite position, in virtual units.
     */
    private static void setLegacyPosition(final UISprite sprite, final float x, final float y) {
        sprite.setX(DimensionKt.getPx(x * LegacyUnits.getScale()));
        sprite.setY(DimensionKt.getPx(y * LegacyUnits.getScale()));
    }

    /**
     * The AndEngine sprite took the size of its texture, in virtual units.
     */
    private static void setLegacyTexture(final UISprite sprite, final GraphicsTexture.Region region) {
        sprite.setTextureRegion(region);
        sprite.setScaleType(UISprite.ScaleType.Stretch);
        sprite.setWidth(DimensionKt.getPx((region != null ? region.getWidth() : 0f) * LegacyUnits.getScale()));
        sprite.setHeight(DimensionKt.getPx((region != null ? region.getHeight() : 0f) * LegacyUnits.getScale()));
    }

    @Override
    public void update(final float dt) {
        if (length == 0 || time >= length) {
            return;
        }
        time += dt;

        if (length > 3 && time > (length - 1) / 2
                && time - dt < (length - 1) / 2) {
            passfail.setVisible(true);
            underTreeLock(() -> VerktexModifiers.beginModifierSequence(passfail, sequence -> {
                // Delay 0.25, fade out 0.025, delay 0.025, fade in 0.025, delay 0.6725, fade out 0.3.
                sequence.delay(0.25f);
                sequence.fadeOut(0.025f);
                sequence.then(0.025f);
                sequence.fadeIn(0.025f);
                sequence.then(0.6725f);
                sequence.fadeOut(0.3f);
            }));

            var sound = ResourceManager.getInstance().getCustomSound("section" + ending, 1);
            if (sound != null)
                sound.play();
        }
        if (length - time <= 1 && length - time + dt > 1) {
            for (final UISprite sp : arrows) {
                sp.setVisible(true);
                ModifierRegistry.setPaused(sp, false);
            }

            if (Multiplayer.isMultiplayer) {
                Multiplayer.roomScene.getChat().hide();
            }
        }
        if (length > 1) {
            if (time < 0.5f) {
                setBgFade(time * 2);
            } else if (length - time < 0.5f) {
                setBgFade((length - time) * 2);
            } else if (time >= 0.5f && time - dt < 0.5f)
                setBgFade(1);
        }

        setLegacyTexture(mark, getRankingTexture());

        if (time >= length) {
            isbreak = false;
            over = true;
            resumeBgFade();
            if (mark != null) {
                underTreeLock(mark::removeSelf);
            }
            for (final UISprite sp : arrows) {
                sp.removeSelf();
            }
            underTreeLock(passfail::removeSelf);
        }
    }

}
