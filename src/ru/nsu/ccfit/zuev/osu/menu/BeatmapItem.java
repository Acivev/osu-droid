package ru.nsu.ccfit.zuev.osu.menu;

import com.osudroid.data.BeatmapInfo;
import com.osudroid.data.DatabaseManager;
import com.osudroid.ui.verktex.ColorsKt;
import com.osudroid.ui.verktex.IPressable;
import com.osudroid.ui.verktex.ISkinnable;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.framework.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.Vec2;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.verktex.ui.text.UIText;

import org.andengine.util.math.MathUtils;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;

import ru.nsu.ccfit.zuev.osu.*;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

/**
 * The card of a difficulty of the selected beatmap set. As in {@link MenuItemBackground}, the
 * `menu-button-background` sprite is a sibling of the title, the stars and the rank mark.
 */
public class BeatmapItem extends UIContainer implements ISkinnable, IPressable {

    private static final Color4 DEFAULT_COLOR = new Color4(25 / 255f, 25 / 255f, 240 / 255f);
    private static final Color4 SELECTED_COLOR = new Color4(1f, 1f, 1f);

    private static final Color4 DEFAULT_TEXT_COLOR = new Color4(1f, 1f, 1f);
    private static final Color4 SELECTED_TEXT_COLOR = new Color4(0f, 0f, 0f);
    private final UISprite sprite;
    private final UIText beatmapTitle;
    private final UISprite[] stars;
    private final UISprite halfStar;
    private boolean moved = false;
    private float dx = 0, dy = 0;
    private WeakReference<BeatmapSetItem> item;
    private BeatmapInfo beatmapInfo;
    private String currentMark = null;
    private final UISprite mark;
    private float downTime = -1;

    // Legacy positions, in virtual units.
    private float positionX = 0, positionY = 0;
    private float halfStarX = 0;

    private boolean texturesInvalid = true;
    private boolean markInvalid = false;

    // LegacyUnits.scale the sizes were last applied for.
    private float appliedScale = 0;

    public BeatmapItem() {
        sprite = new UISprite(null);
        sprite.setScaleType(UISprite.ScaleType.Stretch);

        beatmapTitle = new UIText();
        beatmapTitle.setWrapText(false);

        sprite.setColor(ColorsKt.toVerktex(OsuSkin.get().getColor("MenuItemVersionsDefaultColor", DEFAULT_COLOR)));

        Color4 menuItemDefaultTextColor = OsuSkin.get().getColor("MenuItemDefaultTextColor", DEFAULT_TEXT_COLOR);
        beatmapTitle.setColor(ColorsKt.toVerktex(menuItemDefaultTextColor));

        sprite.setAlpha(0.8f);
        plusAssign(sprite);
        plusAssign(beatmapTitle);

        stars = new UISprite[10];
        for (int i = 0; i < 10; i++) {
            stars[i] = new UISprite(null);
            stars[i].setScaleType(UISprite.ScaleType.Stretch);
            plusAssign(stars[i]);
        }
        halfStar = new UISprite(null);
        halfStar.setScaleType(UISprite.ScaleType.Stretch);
        plusAssign(halfStar);

        mark = new UISprite(null);
        mark.setScaleType(UISprite.ScaleType.Stretch);
        mark.setVisible(false);
        plusAssign(mark);
    }

    /** Height of the texture, in virtual units (legacy {@code getHeight()}). */
    public float getVirtualHeight() {
        updateTextures();
        final GraphicsTexture.Region region = sprite.getTextureRegion();
        return region != null ? region.getHeight() : 0;
    }

    public void setItem(final BeatmapSetItem it) {
        item = new WeakReference<>(it);
    }

    public void setBeatmapInfo(final BeatmapInfo beatmapInfo) {
        this.beatmapInfo = beatmapInfo;
        beatmapTitle.setText(beatmapInfo.getVersion() + " (" + beatmapInfo.getCreator() + ")");

        for (final UISprite s : stars) {
            s.setVisible(false);
        }
        halfStar.setVisible(false);

        final float diff = Math.min(beatmapInfo.getStarRating(), 10);

        int fInt = (int) (diff);
        BigDecimal b1 = new BigDecimal(Float.toString(diff));
        BigDecimal b2 = new BigDecimal(Integer.toString(fInt));
        float fPoint = b1.subtract(b2).floatValue();

        for (int j = 0; j < fInt; j++) {
            if (j < stars.length) {
                stars[j].setVisible(true);
            }
        }

        if (fPoint > 0 && fInt != 10) {
            halfStar.setVisible(true);
            halfStarX = Utils.toRes(60 + 52 * fInt);
            halfStar.setX(DimensionKt.getPx(halfStarX * LegacyUnits.getScale()));
            halfStar.setScale(fPoint);
        }
        updateMark();
    }

    public void updateMark() {
        if (beatmapInfo == null) {
            return;
        }

        var table = DatabaseManager.getScoreInfoTable();
        table.migrateScores(beatmapInfo.getMD5(), beatmapInfo.getBeatmapDifficulty());
        var newmark = table.getBestMark(beatmapInfo.getMD5(), beatmapInfo.getBeatmapDifficulty());

        if (currentMark != null && currentMark.equals(newmark)) {
            return;
        }

        mark.setVisible(newmark != null);
        currentMark = newmark;
        markInvalid = true;
    }

    public BeatmapInfo getBeatmapInfo() {
        return beatmapInfo;
    }

    public void setDeselectColor() {
        sprite.setColor(ColorsKt.toVerktex(OsuSkin.get().getColor("MenuItemVersionsDefaultColor", DEFAULT_COLOR)));

        Color4 menuItemDefaultTextColor = OsuSkin.get().getColor("MenuItemDefaultTextColor", DEFAULT_TEXT_COLOR);

        beatmapTitle.setColor(ColorsKt.toVerktex(menuItemDefaultTextColor));
    }

    public void setSelectedColor() {

        sprite.setColor(ColorsKt.toVerktex(OsuSkin.get().getColor("MenuItemVersionsSelectedColor", SELECTED_COLOR)));

        Color4 menuItemSelectedTextColor = OsuSkin.get().getColor("MenuItemSelectedTextColor", SELECTED_TEXT_COLOR);
        beatmapTitle.setColor(ColorsKt.toVerktex(menuItemSelectedTextColor));
    }

    /** Legacy {@code setPosition}, in virtual units. */
    public void setPosition(final float x, final float y) {
        positionX = x;
        positionY = y;
        setX(DimensionKt.getPx(x * LegacyUnits.getScale()));
        setY(DimensionKt.getPx(y * LegacyUnits.getScale()));
    }

    @Override
    public void onSkinChanged() {
        texturesInvalid = true;
    }

    private void updateTextures() {
        if (texturesInvalid) {
            texturesInvalid = false;

            sprite.setTextureRegion(SkinTextures.get("menu-button-background"));

            final GraphicsTexture.Region star = SkinTextures.get("star");
            for (final UISprite s : stars) {
                s.setTextureRegion(star);
            }
            halfStar.setTextureRegion(star);

            markInvalid = true;
            appliedScale = 0;
        }

        if (markInvalid) {
            markInvalid = false;

            mark.setTextureRegion(currentMark != null ? SkinTextures.get("ranking-" + currentMark + "-small") : null);
            appliedScale = 0;
        }
    }

    @Override
    protected void onUpdate(final TickingClock clock) {
        updateTextures();

        final float scale = LegacyUnits.getScale();
        if (appliedScale != scale) {
            appliedScale = scale;

            final GraphicsTexture.Region region = sprite.getTextureRegion();
            setWidth(DimensionKt.getPx((region != null ? region.getWidth() : 0) * scale));
            setHeight(DimensionKt.getPx((region != null ? region.getHeight() : 0) * scale));
            sprite.setWidth(getWidth());
            sprite.setHeight(getHeight());

            setX(DimensionKt.getPx(positionX * scale));
            setY(DimensionKt.getPx(positionY * scale));

            beatmapTitle.setX(DimensionKt.getPx(Utils.toRes(32) * scale));
            beatmapTitle.setY(DimensionKt.getPx(Utils.toRes(22) * scale));
            beatmapTitle.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("font") * scale));

            for (int i = 0; i < stars.length; i++) {
                stars[i].setX(DimensionKt.getPx(Utils.toRes(60 + 52 * i) * scale));
                stars[i].setY(DimensionKt.getPx(Utils.toRes(50) * scale));
                sizeToTexture(stars[i], scale);
            }

            halfStar.setX(DimensionKt.getPx(halfStarX * scale));
            halfStar.setY(DimensionKt.getPx(Utils.toRes(50) * scale));
            sizeToTexture(halfStar, scale);

            mark.setX(DimensionKt.getPx(Utils.toRes(25) * scale));
            mark.setY(DimensionKt.getPx(Utils.toRes(55) * scale));
            sizeToTexture(mark, scale);
        }

        super.onUpdate(clock);
    }

    @Override
    public boolean onInputEvent(final InputEvent inputEvent) {
        // A freed item stays attached but invisible, AndEngine had its touch area unregistered.
        if (!(inputEvent instanceof PointerEvent pSceneTouchEvent) || !isVisible()) {
            return false;
        }

        // AndEngine only reported touches inside the touch area.
        if (!containsScenePoint(pSceneTouchEvent.getX(), pSceneTouchEvent.getY())) {
            return false;
        }

        // Local coordinates in virtual units, the slop is in virtual units.
        final Vec2 local = toLocalSpaceFromScene(pSceneTouchEvent.getX(), pSceneTouchEvent.getY());
        final float pTouchAreaLocalX = local.getX() / LegacyUnits.getScale();
        final float pTouchAreaLocalY = local.getY() / LegacyUnits.getScale();

        final PointerEvent.Action action = pSceneTouchEvent.getAction();
        if (action == PointerEvent.Action.Down) {
            downTime = 0;
            moved = false;
            setSelectedColor();
            dx = pTouchAreaLocalX;
            dy = pTouchAreaLocalY;
            if (item != null && item.get() != null) {
                item.get().stopScroll(positionY + pTouchAreaLocalY);
            }
            return true;
        } else if (action == PointerEvent.Action.Up && !moved) {
            downTime = -1;
            if (item == null || item.get() == null) {
                return true;
            }
            if (!item.get().isBeatmapSelected(this)) {
                ResourceManager.getInstance().getSound("menuclick").play();
                item.get().deselectBeatmap();
            }
            item.get().selectBeatmap(this, false);

            return true;
        } else if (action == PointerEvent.Action.Move
                && (MathUtils.distance(dx, dy, pTouchAreaLocalX,
                pTouchAreaLocalY) > 50)) {
            downTime = -1;
            setDeselectColor();
            moved = true;
            return false;
        } else if (action == PointerEvent.Action.Unknown || action == PointerEvent.Action.Cancel) {
            // Cancel events are dispatched to every component, so they are not consumed.
            return false;
        } else {
            return action != PointerEvent.Action.Up;
        }
    }

    @Override
    public void cancelPress() {
        downTime = -1;
    }

    void update(final float dt) {
        if (downTime >= 0) {
            downTime += dt;
        }
        if (downTime > 0.5) {
            setSelectedColor();
            moved = true;
            if (item != null && item.get() != null) {
                item.get().showPropertiesMenu();
            }
            downTime = -1;
        }
    }

    private static void sizeToTexture(final UISprite sprite, final float scale) {
        final GraphicsTexture.Region region = sprite.getTextureRegion();
        sprite.setWidth(DimensionKt.getPx((region != null ? region.getWidth() : 0) * scale));
        sprite.setHeight(DimensionKt.getPx((region != null ? region.getHeight() : 0) * scale));
    }
}
