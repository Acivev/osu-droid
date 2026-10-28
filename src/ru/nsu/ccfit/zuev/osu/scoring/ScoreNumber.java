package ru.nsu.ccfit.zuev.osu.scoring;

import androidx.annotation.NonNull;

import com.osudroid.ui.verktex.ISkinnable;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.Logger;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.math.Easing;
import com.reco1l.verktex.math.FloatAnimation;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import ru.nsu.ccfit.zuev.skins.OsuSkin;

/**
 * A number drawn with the skin's score digits. Values are in virtual units.
 * <p>
 * The legacy layout is kept as is: letters are sized by the scale but placed by their sized width
 * times the scale again, so they only sit next to each other at a scale of 1.
 */
public class ScoreNumber extends UIContainer implements ISkinnable {

    private static final String TAG = "ScoreNumber";

    private static final float GROW_DURATION = 0.2f;

    private static final float SHRINK_DURATION = 0.4f;


    private final float scale;

    private final boolean center;

    private final Function<String, GraphicsTexture.Region> lookup;

    private final List<Bounce> bounces = new ArrayList<>();

    private String text = "";


    private float height;


    public ScoreNumber(final float x, final float y, final String text, final float scale, final boolean center) {
        this(x, y, text, scale, center, ScoreNumber::skinLookup);
    }

    public ScoreNumber(final float x, final float y, final String text, final float scale, final boolean center,
                       final Function<String, GraphicsTexture.Region> lookup) {
        this.scale = scale;
        this.center = center;
        this.lookup = lookup;
        // The AndEngine entity rotated around its top left corner (the fail wind-down rotates it).
        setRotationOrigin(Anchor.TopLeft);
        setX(DimensionKt.getPx(x));
        setY(DimensionKt.getPx(y));
        setText(text);
    }


    public void setText(String text) {
        this.text = text;

        clearChildren();
        bounces.clear();

        float totalWidth = 0;
        List<UISprite> letters = new ArrayList<>();
        List<Float> positions = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {

            var ch = text.charAt(i);
            String textureName = textureNameFor(ch);
            GraphicsTexture.Region region = lookup.apply(textureName);

            if (region == null) {
                Logger.INSTANCE.w(TAG, "Missing score texture \"" + textureName + "\", skipping '" + ch + "'.");
                continue;
            }

            float width = region.getWidth() * scale;
            float letterHeight = region.getHeight() * scale;

            positions.add(totalWidth * scale);

            UISprite letter = new UISprite(region);
            letter.setScaleType(UISprite.ScaleType.Stretch);
            letter.setX(DimensionKt.getPx(totalWidth * scale));
            letter.setWidth(DimensionKt.getPx(width));
            letter.setHeight(DimensionKt.getPx(letterHeight));
            letters.add(letter);

            totalWidth += width * scale;
            height = letterHeight * scale;
        }

        if (center) {
            totalWidth /= 2 * scale;
            for (int i = 0, count = letters.size(); i < count; i++) {
                var sp = letters.get(i);
                sp.setX(DimensionKt.getPx(positions.get(i) - totalWidth));
                sp.setScale(scale);
                bounces.add(new Bounce(sp));
            }
        }

        for (var letter : letters) {
            plusAssign(letter);
        }
    }


    @Override
    protected void onUpdate(@NonNull TickingClock clock) {
        if (!bounces.isEmpty()) {
            for (var bounce : bounces) {
                bounce.update(clock);
            }
            bounces.removeIf(bounce -> bounce.isFinished);
        }

        super.onUpdate(clock);
    }

    @Override
    public void onSkinChanged() {
        setText(text);
    }


    // Leaving this to support old usages.
    public void attachToScene(final UIContainer scene) {
        scene.plusAssign(this);
    }

    // Leaving this to support old usages.
    public void detachFromScene(final UIContainer scene) {
        scene.minusAssign(this);
    }

    /**
     * The height of the letters, 0 until a non-empty text was set.
     */
    public float getTextHeight() {
        return height;
    }


    /**
     * The texture name for a character without the skin's score prefix.
     */
    public static String textureNameFor(final char ch) {
        if (ch <= '9' && ch >= '0') {
            return String.valueOf(ch);
        } else if (ch == '.' || ch == ',') {
            return "comma";
        } else if (ch == '%') {
            return "percent";
        } else {
            return "x";
        }
    }

    /**
     * Looks up "&lt;prefix&gt;-&lt;name&gt;" with the skin's score prefix like
     * {@code ResourceManager.getTextureWithPrefix}. Must be called on the update thread.
     */
    public static GraphicsTexture.Region skinLookup(final String name) {
        return SkinTextures.getWithPrefix(OsuSkin.get().getScorePrefix(), name);
    }


    /**
     * The legacy {@code ScaleModifier} sequence: up to 1.5 times the scale in 0.2 s, back in 0.4 s.
     */
    private final class Bounce {

        private final UISprite letter;

        private final FloatAnimation animation;

        private float elapsed = 0;

        private boolean shrinking = false;

        private boolean isFinished = false;

        Bounce(final UISprite letter) {
            this.letter = letter;
            animation = new FloatAnimation(scale, 0f, Easing.Companion.getNone());
            animation.animateTo(scale * 1.5f, GROW_DURATION, Easing.Companion.getNone());
        }

        void update(final TickingClock clock) {
            elapsed += clock.getElapsedTickTime();

            if (!shrinking && elapsed >= GROW_DURATION) {
                animation.jumpTo(scale * 1.5f);
                animation.animateTo(scale, SHRINK_DURATION, Easing.Companion.getNone());
                shrinking = true;
            }

            letter.setScale(animation.update(clock));

            if (shrinking && elapsed >= GROW_DURATION + SHRINK_DURATION) {
                letter.setScale(scale);
                isFinished = true;
            }
        }
    }
}
