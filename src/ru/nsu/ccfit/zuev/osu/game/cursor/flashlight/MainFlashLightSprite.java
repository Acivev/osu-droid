package ru.nsu.ccfit.zuev.osu.game.cursor.flashlight;

import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.rian.andengine.modifier.UniversalModifier;
import com.rian.andengine.modifier.VerktexModifiers;


public class MainFlashLightSprite extends FlashlightAreaSizedSprite {
    // Looked up per sprite: a skin change unloads the skin textures, a region kept in a static would stay
    // on an unloaded texture.
    private static GraphicsTexture.Region getDefaultTexture() {
        return SkinTextures.getWithBeatmapSkin("flashlight_cursor", "flashlight_cursor.png");
    }
    public static final int TEXTURE_WIDTH = (int) getDefaultTexture().getWidth();
    public static final int TEXTURE_HEIGHT = (int) getDefaultTexture().getHeight();
    public final float AREA_CHANGE_FADE_DURATION = 0.8f;
    public float currentSize = BASE_SIZE;
    private UniversalModifier modifier;
    private final float sizeMultiplier;
    private final boolean comboBasedSize;
    private boolean isBreak;

    public MainFlashLightSprite(final float sizeMultiplier, final boolean comboBasedSize) {
        super(getDefaultTexture());

        this.sizeMultiplier = sizeMultiplier;
        this.comboBasedSize = comboBasedSize;
    }

    private void changeArea(float fromScale, float toScale) {
        if (modifier != null && modifier.getTarget() == VerktexModifiers.getModifierTarget(this) && !modifier.isAppliedToEnd()) {
            VerktexModifiers.removeModifier(this, modifier);
            modifier = null;
        }

        setScale(fromScale * sizeMultiplier);
        modifier = VerktexModifiers.scaleTo(this, toScale * sizeMultiplier, AREA_CHANGE_FADE_DURATION).after(e -> modifier = null);
    }

    public void onUpdate(int combo) {
        handleAreaShrinking(combo);
    }

    public void handleAreaShrinking(int combo) {
        if (!comboBasedSize) {
            return;
        }

        // Area stops shrinking at 200 combo
        if (combo <= 200 && combo % 100 == 0) {
            // For every 100 combo, the size is decreased by 10%
            final float newSize = (1 - 0.1f * combo / 100f) * BASE_SIZE;

            // Only change the area when gameplay is not in break period
            if (!isBreak) {
                changeArea(currentSize, newSize);
            }

            currentSize = newSize;
        }
    }

    public void updateBreak(boolean isBreak) {
        if (this.isBreak == isBreak) {
            return;
        }

        this.isBreak = isBreak;

        float fromScale = isBreak ? currentSize : 1.5f * BASE_SIZE;
        float toScale = isBreak ? 1.5f * BASE_SIZE : currentSize;

        changeArea(fromScale, toScale);
    }
}
