package ru.nsu.ccfit.zuev.osu.menu;

import com.osudroid.ui.verktex.ColorsKt;
import com.osudroid.ui.verktex.IPressable;
import com.osudroid.ui.verktex.ISkinnable;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.framework.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.verktex.ui.text.UIText;

import ru.nsu.ccfit.zuev.osu.helper.MathUtils;

import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.skins.OsuSkin;
import ru.nsu.ccfit.zuev.osu.Utils;

/**
 * The card of an unselected beatmap set. A Verktex sprite cannot hold children, so the
 * `menu-button-background` sprite and the two texts are children of this container, sized to the texture.
 * The texts are siblings of the sprite: the AndEngine texts did not take the sprite's colour.
 */
public class MenuItemBackground extends UIContainer implements ISkinnable, IPressable {

    private static final Color4 DEFAULT_COLOR = new Color4(240 / 255f, 150 / 255f, 0 / 255f);
    private static final Color4 ON_TOUCH_COLOR = new Color4(1f, 1f, 1f);
    private final UISprite sprite;
    private final UIText title, author;
    private final com.reco1l.verktex.data.Color4 defColor = ColorsKt.toVerktex(OsuSkin.get().getColor("MenuItemDefaultColor", DEFAULT_COLOR));
    private final com.reco1l.verktex.data.Color4 onTouchColor = ColorsKt.toVerktex(OsuSkin.get().getColor("MenuItemOnTouchColor", ON_TOUCH_COLOR));
    private boolean moved = false;
    private float initialX = 0, initialY = 0;
    private BeatmapSetItem item;

    // Legacy position, in virtual units.
    private float positionX = 0, positionY = 0;

    private boolean textureInvalid = true;

    // LegacyUnits.scale the sizes were last applied for.
    private float appliedScale = 0;

    public MenuItemBackground() {
        sprite = new UISprite(null);
        sprite.setScaleType(UISprite.ScaleType.Stretch);

        title = new UIText();
        title.setWrapText(false);
        author = new UIText();
        author.setWrapText(false);

        // The AndEngine setAlpha(0.8f) was replaced by the alpha of this colour.
        sprite.setColor(defColor);
        plusAssign(sprite);
        plusAssign(title);
        plusAssign(author);
    }

    /** Height of the texture, in virtual units (legacy {@code getHeight()}). */
    public float getVirtualHeight() {
        updateTexture();
        final GraphicsTexture.Region region = sprite.getTextureRegion();
        return region != null ? region.getHeight() : 0;
    }

    public void reset() {
        sprite.setColor(defColor);
    }

    public void setItem(final BeatmapSetItem it) {
        item = it;
    }

    public void setTitle(final String newTitle) {
        title.setText(newTitle);
    }

    public void setAuthor(final String newAuthor) {
        author.setText(newAuthor);
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
        textureInvalid = true;
    }

    private void updateTexture() {
        if (!textureInvalid) {
            return;
        }
        textureInvalid = false;

        sprite.setTextureRegion(SkinTextures.get("menu-button-background"));
        appliedScale = 0;
    }

    @Override
    protected void onUpdate(final TickingClock clock) {
        updateTexture();

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

            title.setX(DimensionKt.getPx(Utils.toRes(32) * scale));
            title.setY(DimensionKt.getPx(Utils.toRes(25) * scale));
            author.setX(DimensionKt.getPx(Utils.toRes(150) * scale));
            author.setY(DimensionKt.getPx(Utils.toRes(60) * scale));

            final var fontSize = DimensionKt.getPx(LegacyUnits.fontSize("font") * scale);
            title.setFontSize(fontSize);
            author.setFontSize(fontSize);
        }

        super.onUpdate(clock);
    }

    @Override
    public boolean onInputEvent(final InputEvent inputEvent) {
        if (!(inputEvent instanceof PointerEvent event) || !isVisible()) {
            return false;
        }

        // AndEngine only reported touches inside the touch area.
        if (!containsScenePoint(event.getX(), event.getY())) {
            return false;
        }

        // Since this entity is "scrolling", the local Y coordinate may stay the same as the position of the card also
        // moves. Using local coordinates here would cause scrolling cancellation below to not work as expected since
        // the disposition stays the same regardless of scrolling distance. The slop is in virtual units.
        float x = event.getX() / LegacyUnits.getScale();
        float y = event.getY() / LegacyUnits.getScale();

        if (event.getAction() == PointerEvent.Action.Down) {
            moved = false;
            sprite.setColor(onTouchColor);
            if (item != null) {
                item.stopScroll(positionY + toLocalSpaceFromScene(event.getX(), event.getY()).getY() / LegacyUnits.getScale());
            }
            initialX = x;
            initialY = y;
            return true;
        } else if (event.getAction() == PointerEvent.Action.Up && !moved) {
            ResourceManager.getInstance().getSound("menuclick").play();
            sprite.setColor(defColor);
            if (item != null) {
                item.select();
            }
            return true;
        } else if (event.getAction() == PointerEvent.Action.Move && !moved
                && MathUtils.distance(initialX, initialY, x, y) > 50) {
            sprite.setColor(defColor);
            moved = true;
        }
        return false;
    }

    @Override
    public void cancelPress() {
        sprite.setColor(defColor);
    }
}
