package ru.nsu.ccfit.zuev.osu.game.cursor.flashlight;

import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.ui.shape.UIBox;
import com.rian.andengine.modifier.UniversalModifier;
import com.rian.andengine.modifier.VerktexModifiers;

/**
 * The 1x1 black region of the AndEngine sprite is a black box of 1x1, scaled to the flashlight texture's size.
 */
public class FlashLightDimLayerSprite extends UIBox {
    public final float BASE_SLIDER_DIM_ALPHA = 0.8f;
    private boolean isTrackingSliders;
    private UniversalModifier modifier;

    public FlashLightDimLayerSprite() {
        // The FlashlightAreaSizedSprite constructor.
        setOrigin(Anchor.Center);
        setScale(FlashlightAreaSizedSprite.BASE_SIZE);
        setWidth(DimensionKt.getPx(1));
        setHeight(DimensionKt.getPx(1));
        setBackgroundColor(new Color4(0f, 0f, 0f, 1f));

        setScaleX(MainFlashLightSprite.TEXTURE_WIDTH);
        setScaleY(MainFlashLightSprite.TEXTURE_HEIGHT);
        setAlpha(0);
    }

    public void onTrackingSliders(boolean isTrackingSliders) {
        if (this.isTrackingSliders == isTrackingSliders) {
            return;
        }

        this.isTrackingSliders = isTrackingSliders;

        if (modifier != null) {
            VerktexModifiers.removeModifier(this, modifier);
            modifier = null;
        }

        VerktexModifiers.clearModifiers(this);
        modifier = VerktexModifiers.fadeTo(this, isTrackingSliders ? BASE_SLIDER_DIM_ALPHA : 0, 0.05f).after(e -> modifier = null);
    }
}
